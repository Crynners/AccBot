package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.BackupDataRestorer
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.BackupEnvelope
import com.accbot.dca.domain.model.BackupPayload
import com.accbot.dca.domain.model.BackupPreview
import com.accbot.dca.domain.model.BackupResult
import com.accbot.dca.domain.model.RestoreMode
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.crypto.AEADBadTagException
import javax.inject.Inject

sealed class RestoreBackupResult {
    data class PreviewReady(
        val preview: BackupPreview,
        val payload: BackupPayload
    ) : RestoreBackupResult()

    data class RestoreComplete(val message: String = "") : RestoreBackupResult()
    data class Error(val message: String) : RestoreBackupResult()
}

class RestoreBackupUseCase @Inject constructor(
    private val restorer: BackupDataRestorer,
    private val crypto: BackupCryptoUseCase,
    private val gson: Gson,
    private val userPreferences: UserPreferences
) {
    companion object {
        internal const val MAX_ENVELOPE_BYTES = 16 * 1024 * 1024
        internal const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024
        internal const val SIZE_ERROR = "backup_too_large"
        internal const val ENVIRONMENT_ERROR = "backup_wrong_environment"
        internal const val CREDENTIALS_ERROR = "backup_credentials_require_encryption"

        internal fun readBounded(input: InputStream, limit: Int): ByteArray {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return output.toByteArray()
                require(count <= limit - output.size()) { SIZE_ERROR }
                output.write(buffer, 0, count)
            }
        }
    }

    private fun acceptsEnvironment(environment: String): Boolean {
        val sandbox = userPreferences.isSandboxMode()
        return sandbox == userPreferences.processSandboxMode && environment == if (sandbox) "sandbox" else "prod"
    }

    fun parseAndPreview(envelopeJson: String, passphrase: String = ""): RestoreBackupResult {
        return try {
            require(envelopeJson.length <= MAX_ENVELOPE_BYTES) { SIZE_ERROR }
            val envelope = gson.fromJson(envelopeJson, BackupEnvelope::class.java)
                ?: return RestoreBackupResult.Error("Invalid backup file")

            if (envelope.format != BackupEnvelope.FORMAT_IDENTIFIER ||
                envelope.version !in 1..BackupEnvelope.CURRENT_VERSION
            ) {
                return RestoreBackupResult.Error("Invalid backup format")
            }
            if (!acceptsEnvironment(envelope.environment)) {
                return RestoreBackupResult.Error(ENVIRONMENT_ERROR)
            }

            // Decrypt or decode
            val compressed = if (envelope.encrypted) {
                if (passphrase.isEmpty()) {
                    return RestoreBackupResult.Error("Password required")
                }
                try {
                    crypto.decrypt(envelope.data, passphrase)
                } catch (_: AEADBadTagException) {
                    return RestoreBackupResult.Error("Wrong password or seed")
                }
            } else {
                android.util.Base64.decode(envelope.data, android.util.Base64.NO_WRAP)
            }

            // Decompress
            val payloadJson = if (envelope.compressed) {
                decompress(compressed)
            } else {
                String(compressed, Charsets.UTF_8)
            }

            val payload = gson.fromJson(payloadJson, BackupPayload::class.java)
                ?: return RestoreBackupResult.Error("Invalid backup data")
            if (!envelope.encrypted && payload.credentials.isNotEmpty()) {
                return RestoreBackupResult.Error(CREDENTIALS_ERROR)
            }

            val preview = BackupPreview(
                createdAt = envelope.createdAt,
                appVersion = envelope.appVersion,
                environment = envelope.environment,
                planCount = payload.plans.size,
                hasSettings = payload.settings != null,
                thresholdCount = payload.withdrawalThresholds.size,
                credentialCount = payload.credentials.size,
                transactionCount = payload.transactions.size,
                notificationCount = payload.notifications.size,
                withdrawalCount = payload.withdrawals.size,
                sections = envelope.sections
            )

            RestoreBackupResult.PreviewReady(preview, payload)
        } catch (_: AEADBadTagException) {
            RestoreBackupResult.Error("Wrong password or seed")
        } catch (e: Exception) {
            RestoreBackupResult.Error(e.message ?: "Failed to parse backup")
        }
    }

    suspend fun restore(
        payload: BackupPayload,
        environment: String,
        restoreMode: RestoreMode = RestoreMode.Merge
    ): RestoreBackupResult {
        if (!acceptsEnvironment(environment)) return RestoreBackupResult.Error(ENVIRONMENT_ERROR)
        return when (val result = restorer.restore(payload, restoreMode)) {
            is BackupResult.Success -> RestoreBackupResult.RestoreComplete(result.message)
            is BackupResult.Error -> RestoreBackupResult.Error(result.message)
        }
    }

    private fun decompress(data: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(data)).use {
            readBounded(it, MAX_PAYLOAD_BYTES).toString(Charsets.UTF_8)
        }
    }
}
