package com.accbot.dca.domain.model

import java.time.Instant

/**
 * Backup envelope — the top-level structure of a backup file (always plaintext JSON).
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
        const val CURRENT_VERSION = 1
    }
}

/**
 * Backup payload — the actual data after decryption/decompression.
 */
data class BackupPayload(
    val plans: List<BackupPlan> = emptyList(),
    val settings: BackupSettings? = null,
    val withdrawalThresholds: List<BackupWithdrawalThreshold> = emptyList(),
    val credentials: List<BackupCredentials> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val notifications: List<BackupNotification> = emptyList(),
    val withdrawals: List<BackupWithdrawal> = emptyList()
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
    val nextExecutionAt: Long? = null
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
 */
data class BackupCredentials(
    val exchange: String,
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String? = null,
    val clientId: String? = null
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
    val executedAt: Long = 0
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
    val createdAt: Long = 0
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
    val createdAt: Long = 0
)

/**
 * Serializable withdrawal threshold for backup.
 */
data class BackupWithdrawalThreshold(
    val crypto: String,
    val exchange: String,
    val thresholdAmount: String
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
