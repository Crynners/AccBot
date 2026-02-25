package com.accbot.dca.domain.usecase

import com.accbot.dca.BuildConfig
import com.accbot.dca.data.local.BackupDataCollector
import com.accbot.dca.domain.model.BackupEnvelope
import com.accbot.dca.domain.model.BackupExportOptions
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

sealed class CreateBackupResult {
    data class Success(
        val envelopeJson: String,
        val suggestedFileName: String,
        val payloadSizeBytes: Int
    ) : CreateBackupResult()

    data class Error(val message: String) : CreateBackupResult()
}

class CreateBackupUseCase @Inject constructor(
    private val collector: BackupDataCollector,
    private val crypto: BackupCryptoUseCase,
    private val gson: Gson
) {
    companion object {
        private const val QR_MAX_BYTES = 1850
    }

    suspend fun execute(options: BackupExportOptions): CreateBackupResult {
        return try {
            // Validate
            if (options.includeCredentials && options.password.isEmpty() && options.seed.isEmpty()) {
                return CreateBackupResult.Error("Encryption is required when including credentials")
            }

            // Collect data
            val payload = collector.collect(options)
            val payloadJson = gson.toJson(payload)

            // Compress
            val compressed = compress(payloadJson.toByteArray(Charsets.UTF_8))

            // Determine encryption
            val passphrase = crypto.resolvePassphrase(options.encryptionMode, options.password, options.seed)
            val hasPassphrase = passphrase.isNotEmpty()

            // Encrypt or encode
            val data = if (hasPassphrase) {
                crypto.encrypt(compressed, passphrase)
            } else {
                android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP)
            }

            // Build sections list
            val sections = buildList {
                add("plans")
                add("settings")
                if (options.includeCredentials) add("credentials")
                if (options.includeTransactions) add("transactions")
                if (options.includeNotifications) add("notifications")
                if (options.includeWithdrawals) add("withdrawals")
            }

            // Build envelope
            val envelope = BackupEnvelope(
                appVersion = BuildConfig.VERSION_NAME,
                environment = if (collector.isSandbox()) "sandbox" else "prod",
                encrypted = hasPassphrase,
                compressed = true,
                sections = sections,
                data = data
            )

            val envelopeJson = gson.toJson(envelope)

            // Generate filename
            val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
            val fileName = "accbot_backup_$timestamp.json"

            CreateBackupResult.Success(
                envelopeJson = envelopeJson,
                suggestedFileName = fileName,
                payloadSizeBytes = envelopeJson.toByteArray().size
            )
        } catch (e: Exception) {
            CreateBackupResult.Error(e.message ?: "Unknown error creating backup")
        }
    }

    fun isQrFeasible(payloadSizeBytes: Int): Boolean = payloadSizeBytes <= QR_MAX_BYTES

    private fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
