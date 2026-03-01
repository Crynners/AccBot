import Foundation

/// Collects all app data (DB + preferences) into a BackupPayload.
final class BackupDataCollector {
    private let database: DcaDatabase
    private let credentialsStore: CredentialsStore
    private let userPreferences: UserPreferences

    init(database: DcaDatabase, credentialsStore: CredentialsStore, userPreferences: UserPreferences) {
        self.database = database
        self.credentialsStore = credentialsStore
        self.userPreferences = userPreferences
    }

    func collect(options: BackupExportOptions) throws -> BackupPayload {
        let plans = try database.planDao.getAll().map { $0.toBackup() }

        let settings = BackupSettings(
            appTheme: userPreferences.appTheme.rawValue.uppercased(),
            notificationsEnabled: userPreferences.notificationsEnabled,
            purchaseNotifications: userPreferences.purchaseNotifications,
            errorNotifications: userPreferences.errorNotifications,
            weeklySummaryNotifications: userPreferences.weeklySummaryNotifications,
            languageTag: userPreferences.appLanguage,
            biometricLockEnabled: userPreferences.biometricLockEnabled,
            lowBalanceThresholdDays: userPreferences.lowBalanceThresholdDays
        )

        let thresholds = try database.withdrawalThresholdDao.getAll().map { $0.toBackupThreshold() }

        let credentials: [BackupCredentials]
        if options.includeCredentials {
            let isSandbox = userPreferences.sandboxMode
            credentials = credentialsStore.getConfiguredExchanges(isSandbox: isSandbox).compactMap { exchange in
                credentialsStore.get(for: exchange, isSandbox: isSandbox)?.toBackup()
            }
        } else {
            credentials = []
        }

        let transactions: [BackupTransaction]
        if options.includeTransactions {
            transactions = try database.transactionDao.getAllTransactionsOnce().map { $0.toBackup() }
        } else {
            transactions = []
        }

        let notifications: [BackupNotification]
        if options.includeNotifications {
            notifications = try getAllNotifications().map { $0.toBackup() }
        } else {
            notifications = []
        }

        let withdrawals: [BackupWithdrawal]
        if options.includeWithdrawals {
            withdrawals = try getAllWithdrawals().map { $0.toBackup() }
        } else {
            withdrawals = []
        }

        return BackupPayload(
            plans: plans,
            settings: settings,
            withdrawalThresholds: thresholds,
            credentials: credentials,
            transactions: transactions,
            notifications: notifications,
            withdrawals: withdrawals
        )
    }

    func isSandbox() -> Bool { userPreferences.sandboxMode }

    func getDataCounts() throws -> BackupDataCounts {
        let isSandbox = userPreferences.sandboxMode
        return BackupDataCounts(
            planCount: try database.planDao.getAll().count,
            thresholdCount: try database.withdrawalThresholdDao.getAll().count,
            credentialCount: credentialsStore.getConfiguredExchanges(isSandbox: isSandbox).count,
            transactionCount: try database.transactionDao.getTotalCount(),
            notificationCount: try getAllNotifications().count,
            withdrawalCount: try getAllWithdrawals().count
        )
    }

    // MARK: - Private helpers

    private func getAllNotifications() throws -> [AppNotification] {
        try database.notificationDao.getAll()
    }

    private func getAllWithdrawals() throws -> [Withdrawal] {
        try database.withdrawalDao.getAll()
    }
}

// MARK: - Domain -> Backup mapping

private extension DcaPlan {
    func toBackup() -> BackupPlan {
        BackupPlan(
            id: id,
            exchange: exchange.rawValue,
            crypto: crypto,
            fiat: fiat,
            amount: "\(amount)",
            frequency: frequency.rawValue,
            cronExpression: cronExpression,
            strategy: strategy.dbString,
            isEnabled: isEnabled,
            withdrawalEnabled: withdrawalEnabled,
            withdrawalAddress: withdrawalAddress,
            targetAmount: targetAmount.map { "\($0)" },
            createdAt: Int64(createdAt.timeIntervalSince1970 * 1000),
            lastExecutedAt: lastExecutedAt.map { Int64($0.timeIntervalSince1970 * 1000) },
            nextExecutionAt: nextExecutionAt.map { Int64($0.timeIntervalSince1970 * 1000) }
        )
    }
}

private extension Transaction {
    func toBackup() -> BackupTransaction {
        BackupTransaction(
            id: id,
            planId: planId,
            exchange: exchange.rawValue,
            crypto: crypto,
            fiat: fiat,
            fiatAmount: "\(fiatAmount)",
            cryptoAmount: "\(cryptoAmount)",
            price: "\(price)",
            fee: "\(fee)",
            feeAsset: feeAsset,
            status: status.rawValue,
            exchangeOrderId: exchangeOrderId,
            errorMessage: errorMessage,
            warningMessage: warningMessage,
            executedAt: Int64(executedAt.timeIntervalSince1970 * 1000)
        )
    }
}

private extension AppNotification {
    func toBackup() -> BackupNotification {
        BackupNotification(
            id: id,
            type: type.rawValue,
            title: title,
            message: message,
            planId: planId,
            crypto: crypto,
            exchange: exchange?.rawValue,
            isRead: isRead,
            isArchived: isArchived,
            createdAt: Int64(createdAt.timeIntervalSince1970 * 1000)
        )
    }
}

private extension Withdrawal {
    func toBackup() -> BackupWithdrawal {
        BackupWithdrawal(
            id: id,
            planId: planId,
            exchange: exchange.rawValue,
            crypto: crypto,
            amount: "\(amount)",
            address: address,
            txHash: txHash,
            fee: "\(fee)",
            status: status.rawValue,
            errorMessage: errorMessage,
            createdAt: Int64(createdAt.timeIntervalSince1970 * 1000)
        )
    }
}

private extension WithdrawalThreshold {
    func toBackupThreshold() -> BackupWithdrawalThreshold {
        BackupWithdrawalThreshold(
            crypto: crypto,
            exchange: exchange.rawValue,
            thresholdAmount: "\(thresholdAmount)"
        )
    }
}

private extension ExchangeCredentials {
    func toBackup() -> BackupCredentials {
        BackupCredentials(
            exchange: exchange.rawValue,
            apiKey: apiKey,
            apiSecret: apiSecret,
            passphrase: passphrase,
            clientId: clientId
        )
    }
}
