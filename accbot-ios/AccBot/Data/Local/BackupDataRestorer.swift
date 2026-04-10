import Foundation
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "BackupRestore")

/// Restores a BackupPayload into DB + preferences, remapping plan and connection IDs.
/// Supports both v1 (legacy single-connection) and v2 (multi-connection) backup formats.
final class BackupDataRestorer {
    private let database: DcaDatabase
    private let credentialsStore: CredentialsStore
    private let userPreferences: UserPreferences

    init(database: DcaDatabase, credentialsStore: CredentialsStore, userPreferences: UserPreferences) {
        self.database = database
        self.credentialsStore = credentialsStore
        self.userPreferences = userPreferences
    }

    func restore(payload: BackupPayload, restoreMode: RestoreMode = .merge) -> BackupResult {
        do {
            var planIdMap = [Int64: Int64]()
            var connectionIdMap = [Int64: Int64]()

            try database.dbPool.write { db in
                // Replace mode: wipe all existing DB data first
                if restoreMode == .replace {
                    try db.execute(sql: "DELETE FROM transactions")
                    try db.execute(sql: "DELETE FROM withdrawals")
                    try db.execute(sql: "DELETE FROM notifications")
                    try db.execute(sql: "DELETE FROM withdrawal_thresholds")
                    try db.execute(sql: "DELETE FROM dca_plans")
                    try db.execute(sql: "DELETE FROM monthly_summaries")
                    try db.execute(sql: "DELETE FROM exchange_balances")
                    try db.execute(sql: "DELETE FROM daily_prices")
                    try db.execute(sql: "DELETE FROM exchange_connections")
                }

                // 0. Connections: restore or auto-create
                if !payload.connections.isEmpty {
                    // v2 backup: restore connections, build ID remap
                    connectionIdMap = try Self.restoreConnections(
                        payload.connections, db: db, restoreMode: restoreMode
                    )
                } else {
                    // v1 backup: auto-create one default connection per exchange from plans
                    connectionIdMap = try Self.autoCreateConnections(
                        from: payload, db: db
                    )
                }

                // 1. Plans
                if restoreMode == .merge {
                    let existingPlans = try DcaPlanRecord.fetchAll(db)
                    for plan in payload.plans {
                        let localConnectionId = Self.resolveConnectionId(
                            plan.connectionId, exchange: plan.exchange,
                            connectionIdMap: connectionIdMap, db: db
                        )
                        let record = Self.planRecord(from: plan, connectionId: localConnectionId)

                        // Plan dedup MUST include connectionId
                        let match = existingPlans.first { existing in
                            existing.exchange == plan.exchange &&
                            existing.crypto == plan.crypto &&
                            existing.fiat == plan.fiat &&
                            existing.amount == plan.amount &&
                            existing.frequency == plan.frequency &&
                            existing.connectionId == localConnectionId
                        }
                        if let match = match, let matchId = match.id {
                            var updated = match
                            updated.strategy = record.strategy
                            updated.isEnabled = record.isEnabled
                            updated.withdrawalEnabled = record.withdrawalEnabled
                            updated.withdrawalAddress = record.withdrawalAddress
                            updated.cronExpression = record.cronExpression
                            updated.targetAmount = record.targetAmount
                            updated.lastExecutedAt = record.lastExecutedAt
                            updated.nextExecutionAt = record.nextExecutionAt
                            try updated.update(db)
                            planIdMap[plan.id] = matchId
                        } else {
                            var newRecord = record
                            try newRecord.insert(db)
                            guard let newId = newRecord.id else {
                                logger.error("Failed to get ID for restored plan (original id: \(plan.id))")
                                continue
                            }
                            planIdMap[plan.id] = newId
                        }
                    }
                } else {
                    for plan in payload.plans {
                        let localConnectionId = Self.resolveConnectionId(
                            plan.connectionId, exchange: plan.exchange,
                            connectionIdMap: connectionIdMap, db: db
                        )
                        var record = Self.planRecord(from: plan, connectionId: localConnectionId)
                        try record.insert(db)
                        guard let newId = record.id else {
                            logger.error("Failed to get ID for restored plan (original id: \(plan.id))")
                            continue
                        }
                        planIdMap[plan.id] = newId
                    }
                }

                // 2. Transactions with remapped planId and connectionId
                for tx in payload.transactions {
                    let remappedPlanId = planIdMap[tx.planId] ?? tx.planId
                    let localConnectionId = tx.connectionId.flatMap { connectionIdMap[$0] ?? $0 }
                    if restoreMode == .merge, let orderId = tx.exchangeOrderId, !orderId.isEmpty {
                        let existing = try TransactionRecord
                            .filter(Column("exchangeOrderId") == orderId)
                            .fetchOne(db)
                        if existing != nil { continue }
                    }
                    var record = Self.transactionRecord(from: tx, remappedPlanId: remappedPlanId, connectionId: localConnectionId)
                    try record.insert(db)
                }

                // 3. Withdrawals with remapped planId and connectionId
                for w in payload.withdrawals {
                    let remappedPlanId = planIdMap[w.planId] ?? w.planId
                    let localConnectionId = w.connectionId.flatMap { connectionIdMap[$0] ?? $0 }
                    var record = Self.withdrawalRecord(from: w, remappedPlanId: remappedPlanId, connectionId: localConnectionId)
                    try record.insert(db)
                }

                // 4. Notifications with remapped planId and connectionId
                for n in payload.notifications {
                    let remappedPlanId = n.planId.flatMap { planIdMap[$0] ?? $0 }
                    let localConnectionId = n.connectionId.flatMap { connectionIdMap[$0] ?? $0 }
                    var record = Self.notificationRecord(from: n, remappedPlanId: remappedPlanId, connectionId: localConnectionId)
                    try record.insert(db)
                }

                // 5. Withdrawal thresholds with connectionId
                for t in payload.withdrawalThresholds {
                    let localConnectionId = Self.resolveConnectionId(
                        t.connectionId, exchange: t.exchange,
                        connectionIdMap: connectionIdMap, db: db
                    )
                    let record = WithdrawalThresholdRecord(
                        crypto: t.crypto,
                        connectionId: localConnectionId,
                        thresholdAmount: t.thresholdAmount
                    )
                    try record.save(db)
                }
            }

            // Outside transaction: restore settings
            if let settings = payload.settings {
                let theme = AppTheme(rawValue: settings.appTheme.lowercased()) ?? .system
                userPreferences.appTheme = theme
                userPreferences.notificationsEnabled = settings.notificationsEnabled
                userPreferences.purchaseNotifications = settings.purchaseNotifications
                userPreferences.errorNotifications = settings.errorNotifications
                userPreferences.weeklySummaryNotifications = settings.weeklySummaryNotifications
                if !settings.languageTag.isEmpty {
                    userPreferences.appLanguage = settings.languageTag
                }
                userPreferences.biometricLockEnabled = settings.biometricLockEnabled
                userPreferences.lowBalanceThresholdDays = settings.lowBalanceThresholdDays
            }

            // Outside transaction: restore credentials per connectionId
            let isSandbox = userPreferences.sandboxMode
            if restoreMode == .replace {
                credentialsStore.clearAll(isSandbox: isSandbox, using: database.exchangeConnectionDao)
            }
            var restoredConnectionIds: [Int64] = []
            do {
                for cred in payload.credentials {
                    guard let exchange = Exchange(rawValue: cred.exchange) else {
                        logger.warning("Skipping unknown exchange '\(cred.exchange)' during credential restore")
                        continue
                    }
                    let localConnectionId: Int64
                    if let backupConnId = cred.connectionId, let mapped = connectionIdMap[backupConnId] {
                        localConnectionId = mapped
                    } else {
                        // v1 backup or missing connectionId: resolve default connection
                        guard let conn = try? database.exchangeConnectionDao.getDefaultByExchange(exchange) else {
                            logger.warning("No connection found for exchange '\(cred.exchange)', skipping credential restore")
                            continue
                        }
                        localConnectionId = conn.id
                    }

                    try credentialsStore.save(
                        ExchangeCredentials(
                            exchange: exchange,
                            apiKey: cred.apiKey,
                            apiSecret: cred.apiSecret,
                            passphrase: cred.passphrase,
                            clientId: cred.clientId
                        ),
                        connectionId: localConnectionId,
                        isSandbox: isSandbox
                    )
                    restoredConnectionIds.append(localConnectionId)
                }
            } catch {
                // Roll back: remove credentials that were already restored
                for connId in restoredConnectionIds {
                    credentialsStore.delete(connectionId: connId, isSandbox: isSandbox)
                }
                logger.error("Credential restore failed, rolled back \(restoredConnectionIds.count) connections: \(error.localizedDescription)")
                return .error("Credential restore failed: \(error.localizedDescription)")
            }

            return .success("")
        } catch {
            return .error(error.localizedDescription)
        }
    }

    // MARK: - Connection Restore

    /// Restore v2 connections, returning a map from backup connectionId to local connectionId.
    private static func restoreConnections(
        _ connections: [BackupExchangeConnection],
        db: Database,
        restoreMode: RestoreMode
    ) throws -> [Int64: Int64] {
        var idMap = [Int64: Int64]()

        for conn in connections {
            if restoreMode == .merge {
                // Dedup by (exchange, name)
                let existing = try ExchangeConnectionRecord
                    .filter(Column("exchange") == conn.exchange)
                    .filter(Column("name") == conn.name)
                    .fetchOne(db)
                if let existing = existing, let existingId = existing.id {
                    idMap[conn.id] = existingId
                    continue
                }
            }

            var record = ExchangeConnectionRecord(
                id: nil,
                exchange: conn.exchange,
                name: conn.name,
                createdAt: Double(conn.createdAt) / 1000.0,
                displayOrder: conn.displayOrder
            )
            try record.insert(db)
            if let newId = record.id {
                idMap[conn.id] = newId
            }
        }

        return idMap
    }

    /// For v1 backups: auto-create one default (empty-named) connection per exchange.
    private static func autoCreateConnections(
        from payload: BackupPayload,
        db: Database
    ) throws -> [Int64: Int64] {
        // Collect unique exchanges from plans
        var exchanges = Set<String>()
        for plan in payload.plans {
            exchanges.insert(plan.exchange)
        }
        for cred in payload.credentials {
            exchanges.insert(cred.exchange)
        }
        for t in payload.withdrawalThresholds {
            exchanges.insert(t.exchange)
        }

        // No connectionIdMap needed for v1 - we map by exchange name
        // But we need a way to look up connectionId by exchange later.
        // We'll use the database directly in resolveConnectionId.
        for exchangeStr in exchanges {
            // Check if default connection already exists
            let existing = try ExchangeConnectionRecord
                .filter(Column("exchange") == exchangeStr)
                .filter(Column("name") == "")
                .fetchOne(db)
            if existing != nil { continue }

            var record = ExchangeConnectionRecord(
                id: nil,
                exchange: exchangeStr,
                name: "",
                createdAt: Date().timeIntervalSince1970,
                displayOrder: 0
            )
            try record.insert(db)
        }

        // Return empty map - v1 uses exchange-based resolution
        return [:]
    }

    /// Resolve a backup connectionId to a local connectionId.
    /// For v2: uses the connectionIdMap.
    /// For v1 (connectionId is nil): looks up the default connection by exchange.
    private static func resolveConnectionId(
        _ backupConnectionId: Int64?,
        exchange: String,
        connectionIdMap: [Int64: Int64],
        db: Database
    ) -> Int64 {
        // v2: use the map
        if let backupId = backupConnectionId, let localId = connectionIdMap[backupId] {
            return localId
        }

        // v1 fallback: find default connection by exchange
        if let record = try? ExchangeConnectionRecord
            .filter(Column("exchange") == exchange)
            .filter(Column("name") == "")
            .fetchOne(db),
           let id = record.id {
            return id
        }

        // Last resort: any connection for this exchange
        if let record = try? ExchangeConnectionRecord
            .filter(Column("exchange") == exchange)
            .order(Column("displayOrder").asc, Column("createdAt").asc)
            .fetchOne(db),
           let id = record.id {
            return id
        }

        logger.error("No connection found for exchange '\(exchange)' during restore")
        return 0
    }

    // MARK: - Backup -> Record mapping

    private static func planRecord(from plan: BackupPlan, connectionId: Int64) -> DcaPlanRecord {
        DcaPlanRecord(
            id: nil,
            exchange: plan.exchange,
            connectionId: connectionId,
            crypto: plan.crypto,
            fiat: plan.fiat,
            amount: plan.amount,
            frequency: plan.frequency,
            cronExpression: plan.cronExpression,
            strategy: plan.strategy,
            isEnabled: plan.isEnabled,
            withdrawalEnabled: plan.withdrawalEnabled,
            withdrawalAddress: plan.withdrawalAddress,
            targetAmount: plan.targetAmount,
            createdAt: Double(plan.createdAt) / 1000.0,
            lastExecutedAt: plan.lastExecutedAt.map { Double($0) / 1000.0 },
            nextExecutionAt: plan.nextExecutionAt.map { Double($0) / 1000.0 },
            networkRetryCount: 0,
            nextNetworkRetryAt: nil,
            originalScheduledAt: nil,
            missedPurchaseCount: 0
        )
    }

    private static func transactionRecord(from tx: BackupTransaction, remappedPlanId: Int64, connectionId: Int64?) -> TransactionRecord {
        TransactionRecord(
            id: nil,
            planId: remappedPlanId,
            exchange: tx.exchange,
            connectionId: connectionId,
            crypto: tx.crypto,
            fiat: tx.fiat,
            fiatAmount: tx.fiatAmount,
            cryptoAmount: tx.cryptoAmount,
            price: tx.price,
            fee: tx.fee,
            feeAsset: tx.feeAsset,
            status: tx.status,
            exchangeOrderId: tx.exchangeOrderId,
            errorMessage: tx.errorMessage,
            warningMessage: tx.warningMessage,
            executedAt: Double(tx.executedAt) / 1000.0
        )
    }

    private static func withdrawalRecord(from w: BackupWithdrawal, remappedPlanId: Int64, connectionId: Int64?) -> WithdrawalRecord {
        WithdrawalRecord(
            id: nil,
            planId: remappedPlanId,
            exchange: w.exchange,
            connectionId: connectionId,
            crypto: w.crypto,
            amount: w.amount,
            address: w.address,
            txHash: w.txHash,
            fee: w.fee,
            status: w.status,
            errorMessage: w.errorMessage,
            createdAt: Double(w.createdAt) / 1000.0
        )
    }

    private static func notificationRecord(from n: BackupNotification, remappedPlanId: Int64?, connectionId: Int64?) -> NotificationRecord {
        NotificationRecord(
            id: nil,
            type: n.type,
            title: n.title,
            message: n.message,
            planId: remappedPlanId,
            crypto: n.crypto,
            exchange: n.exchange,
            connectionId: connectionId,
            isRead: n.isRead,
            isArchived: n.isArchived,
            templateArgs: nil,
            createdAt: Double(n.createdAt) / 1000.0
        )
    }
}
