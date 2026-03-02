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
            var planIdMap = [Int64: Int64]()

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
                }

                // 1. Plans
                if restoreMode == .merge {
                    let existingPlans = try DcaPlanRecord.fetchAll(db)
                    for plan in payload.plans {
                        let record = Self.planRecord(from: plan)
                        let match = existingPlans.first { existing in
                            existing.exchange == plan.exchange &&
                            existing.crypto == plan.crypto &&
                            existing.fiat == plan.fiat &&
                            existing.amount == plan.amount &&
                            existing.frequency == plan.frequency
                        }
                        if let match = match, let matchId = match.id {
                            // Update existing plan with backup values
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
                                logger.error("Failed to get auto-incremented ID for restored plan (original id: \(plan.id))")
                                continue
                            }
                            planIdMap[plan.id] = newId
                        }
                    }
                } else {
                    for plan in payload.plans {
                        var record = Self.planRecord(from: plan)
                        try record.insert(db)
                        guard let newId = record.id else {
                            logger.error("Failed to get auto-incremented ID for restored plan (original id: \(plan.id))")
                            continue
                        }
                        planIdMap[plan.id] = newId
                    }
                }

                // 2. Transactions with remapped planId
                for tx in payload.transactions {
                    let remappedPlanId = planIdMap[tx.planId] ?? tx.planId
                    if restoreMode == .merge, let orderId = tx.exchangeOrderId, !orderId.isEmpty {
                        let existing = try TransactionRecord
                            .filter(Column("exchangeOrderId") == orderId)
                            .fetchOne(db)
                        if existing != nil { continue }
                    }
                    var record = Self.transactionRecord(from: tx, remappedPlanId: remappedPlanId)
                    try record.insert(db)
                }

                // 3. Withdrawals with remapped planId
                for w in payload.withdrawals {
                    let remappedPlanId = planIdMap[w.planId] ?? w.planId
                    var record = Self.withdrawalRecord(from: w, remappedPlanId: remappedPlanId)
                    try record.insert(db)
                }

                // 4. Notifications with remapped planId
                for n in payload.notifications {
                    let remappedPlanId = n.planId.flatMap { planIdMap[$0] ?? $0 }
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
                if !settings.languageTag.isEmpty {
                    userPreferences.appLanguage = settings.languageTag
                }
                userPreferences.biometricLockEnabled = settings.biometricLockEnabled
                userPreferences.lowBalanceThresholdDays = settings.lowBalanceThresholdDays
            }

            // Outside transaction: restore credentials atomically
            // Track successfully restored exchanges so we can roll back on failure
            let isSandbox = userPreferences.sandboxMode
            if restoreMode == .replace {
                credentialsStore.clearAll(isSandbox: isSandbox)
            }
            var restoredExchanges: [Exchange] = []
            do {
                for cred in payload.credentials {
                    guard let exchange = Exchange(rawValue: cred.exchange) else {
                        logger.warning("Skipping unknown exchange '\(cred.exchange)' during credential restore")
                        continue
                    }
                    try credentialsStore.save(
                        ExchangeCredentials(
                            exchange: exchange,
                            apiKey: cred.apiKey,
                            apiSecret: cred.apiSecret,
                            passphrase: cred.passphrase,
                            clientId: cred.clientId
                        ),
                        isSandbox: isSandbox
                    )
                    restoredExchanges.append(exchange)
                }
            } catch {
                // Roll back: remove credentials that were already restored
                for exchange in restoredExchanges {
                    credentialsStore.delete(exchange: exchange, isSandbox: isSandbox)
                }
                logger.error("Credential restore failed, rolled back \(restoredExchanges.count) exchanges: \(error.localizedDescription)")
                return .error("Credential restore failed: \(error.localizedDescription)")
            }

            return .success("")
        } catch {
            return .error(error.localizedDescription)
        }
    }

    // MARK: - Backup -> Record mapping

    private static func planRecord(from plan: BackupPlan) -> DcaPlanRecord {
        DcaPlanRecord(
            id: nil,
            exchange: plan.exchange,
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
            nextExecutionAt: plan.nextExecutionAt.map { Double($0) / 1000.0 }
        )
    }

    private static func transactionRecord(from tx: BackupTransaction, remappedPlanId: Int64) -> TransactionRecord {
        TransactionRecord(
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
            status: tx.status,
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
