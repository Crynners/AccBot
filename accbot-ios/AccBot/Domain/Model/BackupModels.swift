import Foundation

// MARK: - Envelope (top-level JSON, always plaintext)

struct BackupEnvelope: Codable {
    let format: String
    let version: Int
    let createdAt: Int64
    let appVersion: String
    let platform: String
    let environment: String
    let encrypted: Bool
    let compressed: Bool
    let sections: [String]
    let data: String // base64(salt || IV || ciphertext || GCM-tag) or plain JSON

    static let formatIdentifier = "accbot-backup"
    static let currentVersion = 1

    init(
        appVersion: String = "",
        environment: String = "prod",
        encrypted: Bool = true,
        compressed: Bool = true,
        sections: [String] = [],
        data: String = ""
    ) {
        self.format = Self.formatIdentifier
        self.version = Self.currentVersion
        self.createdAt = Int64(Date().timeIntervalSince1970 * 1000)
        self.appVersion = appVersion
        self.platform = "ios"
        self.environment = environment
        self.encrypted = encrypted
        self.compressed = compressed
        self.sections = sections
        self.data = data
    }
}

// MARK: - Payload (after decryption/decompression)

struct BackupPayload: Codable {
    let plans: [BackupPlan]
    let settings: BackupSettings?
    let withdrawalThresholds: [BackupWithdrawalThreshold]
    let credentials: [BackupCredentials]
    let transactions: [BackupTransaction]
    let notifications: [BackupNotification]
    let withdrawals: [BackupWithdrawal]

    init(
        plans: [BackupPlan] = [],
        settings: BackupSettings? = nil,
        withdrawalThresholds: [BackupWithdrawalThreshold] = [],
        credentials: [BackupCredentials] = [],
        transactions: [BackupTransaction] = [],
        notifications: [BackupNotification] = [],
        withdrawals: [BackupWithdrawal] = []
    ) {
        self.plans = plans
        self.settings = settings
        self.withdrawalThresholds = withdrawalThresholds
        self.credentials = credentials
        self.transactions = transactions
        self.notifications = notifications
        self.withdrawals = withdrawals
    }
}

// MARK: - Serializable entities

struct BackupPlan: Codable {
    let id: Int64
    let exchange: String
    let crypto: String
    let fiat: String
    let amount: String
    let frequency: String
    let cronExpression: String?
    let strategy: String
    let isEnabled: Bool
    let withdrawalEnabled: Bool
    let withdrawalAddress: String?
    let targetAmount: String?
    let createdAt: Int64
    let lastExecutedAt: Int64?
    let nextExecutionAt: Int64?
}

struct BackupSettings: Codable {
    let appTheme: String
    let notificationsEnabled: Bool
    let purchaseNotifications: Bool
    let errorNotifications: Bool
    let weeklySummaryNotifications: Bool
    let languageTag: String
    let biometricLockEnabled: Bool
    let lowBalanceThresholdDays: Int
}

struct BackupCredentials: Codable {
    let exchange: String
    let apiKey: String
    let apiSecret: String
    let passphrase: String?
    let clientId: String?
}

struct BackupTransaction: Codable {
    let id: Int64
    let planId: Int64
    let exchange: String
    let crypto: String
    let fiat: String
    let fiatAmount: String
    let cryptoAmount: String
    let price: String
    let fee: String
    let feeAsset: String
    let status: String
    let exchangeOrderId: String?
    let errorMessage: String?
    let warningMessage: String?
    let executedAt: Int64
}

struct BackupNotification: Codable {
    let id: Int64
    let type: String
    let title: String
    let message: String
    let planId: Int64?
    let crypto: String?
    let exchange: String?
    let isRead: Bool
    let isArchived: Bool
    let createdAt: Int64
}

struct BackupWithdrawal: Codable {
    let id: Int64
    let planId: Int64
    let exchange: String
    let crypto: String
    let amount: String
    let address: String
    let txHash: String?
    let fee: String
    let status: String
    let errorMessage: String?
    let createdAt: Int64
}

struct BackupWithdrawalThreshold: Codable {
    let crypto: String
    let exchange: String
    let thresholdAmount: String
}

// MARK: - Export options

struct BackupExportOptions {
    var includeCredentials = false
    var includeTransactions = false
    var includeNotifications = false
    var includeWithdrawals = false
    var encryptionMode: EncryptionMode = .seed
    var password = ""
    var seed = ""
}

enum EncryptionMode: String, CaseIterable {
    case password = "Password"
    case seed = "Seed"
}

enum RestoreMode: String, CaseIterable {
    case merge = "Merge"
    case replace = "Replace"
}

// MARK: - Preview

struct BackupPreview {
    let createdAt: Int64
    let appVersion: String
    let environment: String
    let planCount: Int
    let hasSettings: Bool
    let thresholdCount: Int
    let credentialCount: Int
    let transactionCount: Int
    let notificationCount: Int
    let withdrawalCount: Int
    let sections: [String]
}

// MARK: - Result

enum BackupResult {
    case success(String)
    case error(String)
}

// MARK: - Data counts

struct BackupDataCounts {
    var planCount = 0
    var thresholdCount = 0
    var credentialCount = 0
    var transactionCount = 0
    var notificationCount = 0
    var withdrawalCount = 0
}
