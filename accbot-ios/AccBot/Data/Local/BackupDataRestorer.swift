import Foundation
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "BackupRestore")

/// Restores a BackupPayload into DB + preferences, remapping plan IDs.
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
            let isSandbox = userPreferences.sandboxMode
            let credentialsToRestore = try payload.credentials.map { item -> ExchangeCredentials in
                guard let exchange = Exchange(rawValue: item.exchange) else {
                    throw NSError(domain: "BackupRestore", code: 3, userInfo: [
                        NSLocalizedDescriptionKey: String(localized: "The backup contains credentials for an unsupported exchange.")
                    ])
                }
                return ExchangeCredentials(
                    exchange: exchange, apiKey: item.apiKey, apiSecret: item.apiSecret,
                    passphrase: item.passphrase, clientId: item.clientId
                )
            }
            var previousCredentials: [Exchange: ExchangeCredentials] = [:]
            for credential in credentialsToRestore {
                previousCredentials[credential.exchange] = try credentialsStore.load(for: credential.exchange, isSandbox: isSandbox)
            }
            var planIdMap = [Int64: Int64]()

            try database.dbPool.write { db in
                // Replace mode: wipe all existing DB data first
                if restoreMode == .replace {
                    let hasPending = try Bool.fetchOne(
                        db, sql: "SELECT EXISTS (SELECT 1 FROM transactions WHERE status IN ('PENDING', 'PARTIAL'))"
                    ) ?? false
                    if hasPending {
                        throw NSError(domain: "BackupRestore", code: 1, userInfo: [
                            NSLocalizedDescriptionKey: String(localized: "Resolve pending orders before replacing current data.")
                        ])
                    }
                    try db.execute(sql: "DELETE FROM transactions")
                    try db.execute(sql: "DELETE FROM withdrawals")
                    try db.execute(sql: "DELETE FROM notifications")
                    try db.execute(sql: "DELETE FROM withdrawal_thresholds")
                    try db.execute(sql: "DELETE FROM dca_plans")
                    try db.execute(sql: "DELETE FROM monthly_summaries")
                    try db.execute(sql: "DELETE FROM exchange_balances")
                    try db.execute(sql: "DELETE FROM daily_prices")
                }

                for credential in credentialsToRestore {
                    let pending = try Bool.fetchOne(
                        db,
                        sql: "SELECT EXISTS (SELECT 1 FROM transactions WHERE exchange = ? AND status = 'PENDING')",
                        arguments: [credential.exchange.rawValue]
                    ) ?? false
                    if pending {
                        throw NSError(domain: "BackupRestore", code: 1, userInfo: [
                            NSLocalizedDescriptionKey: String(localized: "Resolve pending orders before replacing current data.")
                        ])
                    }
                    try db.execute(
                        sql: "UPDATE dca_plans SET isEnabled = 0 WHERE exchange = ?",
                        arguments: [credential.exchange.rawValue]
                    )
                }

                // 1. Plans
                if restoreMode == .merge {
                    var unmatchedPlans = try DcaPlanRecord.order(Column("id")).fetchAll(db)
                    for plan in payload.plans {
                        let record = Self.planRecord(from: plan)
                        let candidates = unmatchedPlans.filter { existing in
                            existing.exchange == plan.exchange &&
                            existing.crypto == plan.crypto &&
                            existing.fiat == plan.fiat &&
                            Decimal(string: existing.amount) == Decimal(string: plan.amount) &&
                            existing.frequency == plan.frequency
                        }
                        let sameCreationTime = candidates.filter {
                            plan.createdAt > 0 && abs($0.createdAt - record.createdAt) < 0.001
                        }
                        let sameMetadata = candidates.filter {
                            (plan.name == nil || $0.name == plan.name) && $0.cronExpression == record.cronExpression
                        }
                        let sameMetadataIds = Set(sameMetadata.compactMap(\.id))
                        let match = sameCreationTime.first { candidate in
                            candidate.id.map { sameMetadataIds.contains($0) } == true
                        } ?? (sameCreationTime.count == 1 ? sameCreationTime.first : nil) ?? sameMetadata.first
                        if let match = match, let matchId = match.id {
                            unmatchedPlans.removeAll { $0.id == matchId }
                            // Update existing plan with backup values
                            var updated = match
                            updated.name = plan.name ?? match.name
                            updated.displayOrder = plan.displayOrder ?? match.displayOrder
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
                            try record.insert(db)
                            planIdMap[plan.id] = db.lastInsertedRowID
                        }
                    }
                } else {
                    for plan in payload.plans {
                        let record = Self.planRecord(from: plan)
                        try record.insert(db)
                        planIdMap[plan.id] = db.lastInsertedRowID
                    }
                }

                // 2. Transactions with remapped planId
                for tx in payload.transactions {
                    guard let remappedPlanId = planIdMap[tx.planId] else {
                        throw Self.missingPlanError
                    }
                    if restoreMode == .merge, let orderId = tx.exchangeOrderId, !orderId.isEmpty {
                        let existing = try TransactionRecord
                            .filter(Column("exchangeOrderId") == orderId)
                            .filter(Column("exchange") == tx.exchange)
                            .filter(Column("crypto") == tx.crypto && Column("fiat") == tx.fiat)
                            .fetchOne(db)
                        if existing != nil { continue }
                    }
                    var record = Self.transactionRecord(from: tx, remappedPlanId: remappedPlanId)
                    try record.insert(db)
                }

                // 3. Withdrawals with remapped planId
                for w in payload.withdrawals {
                    guard let remappedPlanId = planIdMap[w.planId] else {
                        throw Self.missingPlanError
                    }
                    var record = Self.withdrawalRecord(from: w, remappedPlanId: remappedPlanId)
                    try record.insert(db)
                }

                // 4. Notifications with remapped planId
                for n in payload.notifications {
                    let remappedPlanId = n.planId.flatMap { planIdMap[$0] }
                    var record = Self.notificationRecord(from: n, remappedPlanId: remappedPlanId)
                    try record.insert(db)
                }

                // 5. Withdrawal thresholds (upsert)
                for t in payload.withdrawalThresholds {
                    let record = Self.thresholdRecord(from: t)
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
                if let languageTag = settings.languageTag {
                    userPreferences.appLanguage = languageTag
                }
                userPreferences.lowBalanceThresholdDays = settings.lowBalanceThresholdDays
            }

            // The database is durable and restored plans remain paused even if Keychain rejects a key.
            var restoredExchanges = Set<Exchange>()
            do {
                for credential in credentialsToRestore {
                    try credentialsStore.save(credential, isSandbox: isSandbox)
                    restoredExchanges.insert(credential.exchange)
                }
            } catch {
                for exchange in restoredExchanges {
                    if let previous = previousCredentials[exchange] {
                        try? credentialsStore.save(previous, isSandbox: isSandbox)
                    } else {
                        credentialsStore.delete(exchange: exchange, isSandbox: isSandbox)
                    }
                }
                logger.error("Backup credentials could not be saved: \(error.localizedDescription)")
                return .success(String(localized: "Data was restored. Check your API credentials before enabling plans."))
            }

            return .success("")
        } catch {
            return .error(error.localizedDescription)
        }
    }

    // MARK: - Backup -> Record mapping

    private static var missingPlanError: NSError {
        NSError(domain: "BackupRestore", code: 2, userInfo: [
            NSLocalizedDescriptionKey: String(localized: "The backup contains transactions or withdrawals without their plans.")
        ])
    }

    private static func planRecord(from plan: BackupPlan) -> DcaPlanRecord {
        let now = Date()
        let next = plan.nextExecutionAt.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
        let frequency = DcaFrequency(rawValue: plan.frequency) ?? .daily
        let futureExecution = next.flatMap { $0 > now ? $0 : nil }
            ?? plan.cronExpression.flatMap { CronUtils.getNextExecution(cron: $0, from: now) }
            ?? now.addingTimeInterval(TimeInterval(max(frequency.intervalMinutes, 1)) * 60)
        return DcaPlanRecord(
            id: nil,
            name: plan.name ?? "",
            displayOrder: plan.displayOrder ?? 0,
            exchange: plan.exchange,
            crypto: plan.crypto,
            fiat: plan.fiat,
            amount: plan.amount,
            frequency: plan.frequency,
            cronExpression: plan.cronExpression,
            strategy: plan.strategy,
            isEnabled: false,
            withdrawalEnabled: plan.withdrawalEnabled,
            withdrawalAddress: plan.withdrawalAddress,
            targetAmount: plan.targetAmount,
            createdAt: Double(plan.createdAt) / 1000.0,
            lastExecutedAt: plan.lastExecutedAt.map { Double($0) / 1000.0 },
            nextExecutionAt: futureExecution.timeIntervalSince1970
        )
    }

    private static func transactionRecord(from tx: BackupTransaction, remappedPlanId: Int64) -> TransactionRecord {
        let status: String
        switch tx.status {
        case "PARTIAL_CANCELLED": status = TransactionStatus.partial.rawValue
        case "CANCELLED": status = TransactionStatus.failed.rawValue
        default: status = tx.status
        }
        return TransactionRecord(
            id: nil,
            planId: remappedPlanId,
            exchange: tx.exchange,
            crypto: tx.crypto,
            fiat: tx.fiat,
            fiatAmount: tx.fiatAmount,
            cryptoAmount: tx.cryptoAmount,
            price: tx.price,
            fee: tx.fee,
            feeAsset: tx.feeAsset,
            status: status,
            exchangeOrderId: tx.exchangeOrderId,
            errorMessage: tx.errorMessage,
            warningMessage: tx.warningMessage,
            executedAt: Double(tx.executedAt) / 1000.0
        )
    }

    private static func withdrawalRecord(from w: BackupWithdrawal, remappedPlanId: Int64) -> WithdrawalRecord {
        WithdrawalRecord(
            id: nil,
            planId: remappedPlanId,
            exchange: w.exchange,
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

    private static func notificationRecord(from n: BackupNotification, remappedPlanId: Int64?) -> NotificationRecord {
        NotificationRecord(
            id: nil,
            type: n.type,
            title: n.title,
            message: n.message,
            planId: remappedPlanId,
            crypto: n.crypto,
            exchange: n.exchange,
            isRead: n.isRead,
            isArchived: n.isArchived,
            createdAt: Double(n.createdAt) / 1000.0
        )
    }

    private static func thresholdRecord(from t: BackupWithdrawalThreshold) -> WithdrawalThresholdRecord {
        WithdrawalThresholdRecord(
            crypto: t.crypto,
            exchange: t.exchange,
            thresholdAmount: t.thresholdAmount
        )
    }
}
