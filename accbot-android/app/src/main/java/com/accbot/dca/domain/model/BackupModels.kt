package com.accbot.dca.domain.model

import java.time.Instant

/**
 * Backup envelope – the top-level structure of a backup file (always plaintext JSON).
 *
 * Versions:
 * - v1: legacy single-connection-per-exchange model. Plans/transactions/thresholds/
 *   credentials are keyed by [Exchange] enum string.
 * - v2: connection-aware model. Adds [BackupPayload.connections] and `connectionId`
 *   fields on plans/transactions/withdrawals/notifications/credentials/thresholds so
 *   multiple connections per exchange can roundtrip cleanly. v1 backups are still
 *   restored by auto-creating one default connection per exchange.
 */
data class BackupEnvelope(
    val format: String = FORMAT_IDENTIFIER,
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val appVersion: String = "",
    val platform: String = "android",
    val environment: String = "prod",
    val encrypted: Boolean = true,
    val compressed: Boolean = true,
    val sections: List<String> = emptyList(),
    val data: String = "" // base64(salt ‖ IV ‖ ciphertext ‖ GCM-tag) or plain JSON
) {
    companion object {
        const val FORMAT_IDENTIFIER = "accbot-backup"
        const val CURRENT_VERSION = 2
    }
}

/**
 * Backup payload – the actual data after decryption/decompression.
 *
 * In v2, [connections] carries the multi-connection metadata. Other entries reference
 * a connection by its backup-local id (the source DB's autoincrement value at export
 * time); during restore, BackupDataRestorer remaps these to fresh local IDs.
 */
data class BackupPayload(
    val plans: List<BackupPlan> = emptyList(),
    val settings: BackupSettings? = null,
    val withdrawalThresholds: List<BackupWithdrawalThreshold> = emptyList(),
    val credentials: List<BackupCredentials> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val notifications: List<BackupNotification> = emptyList(),
    val withdrawals: List<BackupWithdrawal> = emptyList(),
    /** v2+: list of [ExchangeConnectionEntity]-equivalent rows. Empty for legacy v1 backups. */
    val connections: List<BackupExchangeConnection> = emptyList()
)

/**
 * v2+: serializable exchange connection (envelope) for backup.
 */
data class BackupExchangeConnection(
    /** Source DB's autoincrement id at export time. Used as the join key for plans/etc. */
    val id: Long,
    val exchange: String,
    val name: String = "",
    val createdAt: Long = 0,
    val displayOrder: Int = 0
)

/**
 * Serializable DCA plan for backup (primitives/strings for platform independence).
 */
data class BackupPlan(
    val id: Long,
    val exchange: String,
    val crypto: String,
    val fiat: String,
    val amount: String,           // BigDecimal.toPlainString()
    val frequency: String,        // DcaFrequency.name
    val cronExpression: String? = null,
    val strategy: String = "Classic", // DcaStrategy DB string
    val isEnabled: Boolean = true,
    val withdrawalEnabled: Boolean = false,
    val withdrawalAddress: String? = null,
    val createdAt: Long = 0,      // Instant epoch millis
    val lastExecutedAt: Long? = null,
    val nextExecutionAt: Long? = null,
    val targetAmount: String? = null,  // BigDecimal.toPlainString()
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    val connectionId: Long? = null
)

/**
 * Serializable user settings for backup.
 */
data class BackupSettings(
    val appTheme: String = "DARK",
    val notificationsEnabled: Boolean = true,
    val purchaseNotifications: Boolean = true,
    val errorNotifications: Boolean = true,
    val weeklySummaryNotifications: Boolean = false,
    val languageTag: String = "",
    val biometricLockEnabled: Boolean = false,
    val lowBalanceThresholdDays: Int = 2
)

/**
 * Serializable exchange credentials for backup.
 *
 * In v2, [connectionId] identifies which envelope these credentials belong to (matches
 * a row in [BackupPayload.connections]). v1 backups omit it and the restorer falls back
 * to the default connection per exchange.
 */
data class BackupCredentials(
    val exchange: String,
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String? = null,
    val clientId: String? = null,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    val connectionId: Long? = null
)

/**
 * Serializable transaction for backup.
 */
data class BackupTransaction(
    val id: Long,
    val planId: Long,
    val exchange: String,
    val crypto: String,
    val fiat: String,
    val fiatAmount: String,
    val cryptoAmount: String,
    val price: String,
    val fee: String,
    val feeAsset: String = "",
    val status: String,
    val exchangeOrderId: String? = null,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val executedAt: Long = 0,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    val connectionId: Long? = null
)

/**
 * Serializable notification for backup.
 */
data class BackupNotification(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val planId: Long? = null,
    val crypto: String? = null,
    val exchange: String? = null,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val templateArgs: String? = null,
    val createdAt: Long = 0,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    val connectionId: Long? = null
)

/**
 * Serializable withdrawal for backup.
 */
data class BackupWithdrawal(
    val id: Long,
    val planId: Long,
    val exchange: String,
    val crypto: String,
    val amount: String,
    val address: String,
    val txHash: String? = null,
    val fee: String,
    val status: String,
    val errorMessage: String? = null,
    val createdAt: Long = 0,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    val connectionId: Long? = null
)

/**
 * Serializable withdrawal threshold for backup.
 *
 * v1 carries [exchange] (Exchange enum string); v2 carries [connectionId] referencing
 * a row in [BackupPayload.connections]. Both fields are kept so a v2 backup can still
 * be parsed by older code that only reads [exchange].
 */
data class BackupWithdrawalThreshold(
    val crypto: String,
    val exchange: String,
    val thresholdAmount: String,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    val connectionId: Long? = null
)

/**
 * Options for creating a backup.
 */
data class BackupExportOptions(
    val includeCredentials: Boolean = false,
    val includeTransactions: Boolean = false,
    val includeNotifications: Boolean = false,
    val includeWithdrawals: Boolean = false,
    val encryptionMode: EncryptionMode = EncryptionMode.Seed,
    val password: String = "",
    val seed: String = ""
)

/**
 * Encryption mode for backup.
 */
enum class EncryptionMode {
    Password,
    Seed
}

/**
 * Restore mode for importing a backup.
 */
enum class RestoreMode {
    Merge,   // Add & update, detect duplicates
    Replace  // Wipe all existing data, then restore
}

/**
 * Preview of a parsed backup before restoring.
 */
data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val environment: String,
    val planCount: Int,
    val hasSettings: Boolean,
    val thresholdCount: Int,
    val credentialCount: Int,
    val transactionCount: Int,
    val notificationCount: Int,
    val withdrawalCount: Int,
    val sections: List<String>
)

/**
 * Result of a backup/restore operation.
 */
sealed class BackupResult {
    data class Success(val message: String = "") : BackupResult()
    data class Error(val message: String) : BackupResult()
}

/**
 * Counts of data available for backup (used in export UI).
 */
data class BackupDataCounts(
    val planCount: Int = 0,
    val thresholdCount: Int = 0,
    val credentialCount: Int = 0,
    val transactionCount: Int = 0,
    val notificationCount: Int = 0,
    val withdrawalCount: Int = 0
)
