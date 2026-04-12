package com.accbot.dca.domain.model

import com.google.gson.annotations.SerializedName
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
 *
 * All fields in this file are annotated with @SerializedName so Gson deserialization
 * survives R8 field renaming even if the containing package is ever moved outside the
 * ProGuard keep rules. Without these, a release build could silently return null
 * fields for restored backups.
 */
data class BackupEnvelope(
    @SerializedName("format") val format: String = FORMAT_IDENTIFIER,
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("createdAt") val createdAt: Long = Instant.now().toEpochMilli(),
    @SerializedName("appVersion") val appVersion: String = "",
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("environment") val environment: String = "prod",
    @SerializedName("encrypted") val encrypted: Boolean = true,
    @SerializedName("compressed") val compressed: Boolean = true,
    @SerializedName("sections") val sections: List<String> = emptyList(),
    @SerializedName("data") val data: String = "" // base64(salt ‖ IV ‖ ciphertext ‖ GCM-tag) or plain JSON
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
    @SerializedName("plans") val plans: List<BackupPlan> = emptyList(),
    @SerializedName("settings") val settings: BackupSettings? = null,
    @SerializedName("withdrawalThresholds") val withdrawalThresholds: List<BackupWithdrawalThreshold> = emptyList(),
    @SerializedName("credentials") val credentials: List<BackupCredentials> = emptyList(),
    @SerializedName("transactions") val transactions: List<BackupTransaction> = emptyList(),
    @SerializedName("notifications") val notifications: List<BackupNotification> = emptyList(),
    @SerializedName("withdrawals") val withdrawals: List<BackupWithdrawal> = emptyList(),
    /** v2+: list of [ExchangeConnectionEntity]-equivalent rows. Empty for legacy v1 backups. */
    @SerializedName("connections") val connections: List<BackupExchangeConnection> = emptyList()
)

/**
 * v2+: serializable exchange connection (envelope) for backup.
 */
data class BackupExchangeConnection(
    /** Source DB's autoincrement id at export time. Used as the join key for plans/etc. */
    @SerializedName("id") val id: Long,
    @SerializedName("exchange") val exchange: String,
    @SerializedName("name") val name: String = "",
    @SerializedName("createdAt") val createdAt: Long = 0,
    @SerializedName("displayOrder") val displayOrder: Int = 0
)

/**
 * Serializable DCA plan for backup (primitives/strings for platform independence).
 */
data class BackupPlan(
    @SerializedName("id") val id: Long,
    @SerializedName("exchange") val exchange: String,
    @SerializedName("crypto") val crypto: String,
    @SerializedName("fiat") val fiat: String,
    @SerializedName("amount") val amount: String,           // BigDecimal.toPlainString()
    @SerializedName("frequency") val frequency: String,        // DcaFrequency.name
    @SerializedName("cronExpression") val cronExpression: String? = null,
    @SerializedName("strategy") val strategy: String = "Classic", // DcaStrategy DB string
    @SerializedName("isEnabled") val isEnabled: Boolean = true,
    @SerializedName("withdrawalEnabled") val withdrawalEnabled: Boolean = false,
    @SerializedName("withdrawalAddress") val withdrawalAddress: String? = null,
    @SerializedName("createdAt") val createdAt: Long = 0,      // Instant epoch millis
    @SerializedName("lastExecutedAt") val lastExecutedAt: Long? = null,
    @SerializedName("nextExecutionAt") val nextExecutionAt: Long? = null,
    @SerializedName("targetAmount") val targetAmount: String? = null,  // BigDecimal.toPlainString()
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    @SerializedName("connectionId") val connectionId: Long? = null
)

/**
 * Serializable user settings for backup.
 */
data class BackupSettings(
    @SerializedName("appTheme") val appTheme: String = "DARK",
    @SerializedName("notificationsEnabled") val notificationsEnabled: Boolean = true,
    @SerializedName("purchaseNotifications") val purchaseNotifications: Boolean = true,
    @SerializedName("errorNotifications") val errorNotifications: Boolean = true,
    @SerializedName("weeklySummaryNotifications") val weeklySummaryNotifications: Boolean = false,
    @SerializedName("languageTag") val languageTag: String = "",
    @SerializedName("biometricLockEnabled") val biometricLockEnabled: Boolean = false,
    @SerializedName("lowBalanceThresholdDays") val lowBalanceThresholdDays: Int = 2
)

/**
 * Serializable exchange credentials for backup.
 *
 * In v2, [connectionId] identifies which envelope these credentials belong to (matches
 * a row in [BackupPayload.connections]). v1 backups omit it and the restorer falls back
 * to the default connection per exchange.
 */
data class BackupCredentials(
    @SerializedName("exchange") val exchange: String,
    @SerializedName("apiKey") val apiKey: String,
    @SerializedName("apiSecret") val apiSecret: String,
    @SerializedName("passphrase") val passphrase: String? = null,
    @SerializedName("clientId") val clientId: String? = null,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    @SerializedName("connectionId") val connectionId: Long? = null
)

/**
 * Serializable transaction for backup.
 */
data class BackupTransaction(
    @SerializedName("id") val id: Long,
    @SerializedName("planId") val planId: Long,
    @SerializedName("exchange") val exchange: String,
    @SerializedName("crypto") val crypto: String,
    @SerializedName("fiat") val fiat: String,
    @SerializedName("fiatAmount") val fiatAmount: String,
    @SerializedName("cryptoAmount") val cryptoAmount: String,
    @SerializedName("price") val price: String,
    @SerializedName("fee") val fee: String,
    @SerializedName("feeAsset") val feeAsset: String = "",
    @SerializedName("status") val status: String,
    @SerializedName("exchangeOrderId") val exchangeOrderId: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("warningMessage") val warningMessage: String? = null,
    @SerializedName("executedAt") val executedAt: Long = 0,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    @SerializedName("connectionId") val connectionId: Long? = null
)

/**
 * Serializable notification for backup.
 */
data class BackupNotification(
    @SerializedName("id") val id: Long,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("planId") val planId: Long? = null,
    @SerializedName("crypto") val crypto: String? = null,
    @SerializedName("exchange") val exchange: String? = null,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("isArchived") val isArchived: Boolean = false,
    @SerializedName("templateArgs") val templateArgs: String? = null,
    @SerializedName("createdAt") val createdAt: Long = 0,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    @SerializedName("connectionId") val connectionId: Long? = null
)

/**
 * Serializable withdrawal for backup.
 */
data class BackupWithdrawal(
    @SerializedName("id") val id: Long,
    @SerializedName("planId") val planId: Long,
    @SerializedName("exchange") val exchange: String,
    @SerializedName("crypto") val crypto: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("address") val address: String,
    @SerializedName("txHash") val txHash: String? = null,
    @SerializedName("fee") val fee: String,
    @SerializedName("status") val status: String,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("createdAt") val createdAt: Long = 0,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    @SerializedName("connectionId") val connectionId: Long? = null
)

/**
 * Serializable withdrawal threshold for backup.
 *
 * v1 carries [exchange] (Exchange enum string); v2 carries [connectionId] referencing
 * a row in [BackupPayload.connections]. Both fields are kept so a v2 backup can still
 * be parsed by older code that only reads [exchange].
 */
data class BackupWithdrawalThreshold(
    @SerializedName("crypto") val crypto: String,
    @SerializedName("exchange") val exchange: String,
    @SerializedName("thresholdAmount") val thresholdAmount: String,
    /** v2+: source connection id (backup-local). Null for legacy v1 backups. */
    @SerializedName("connectionId") val connectionId: Long? = null
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
