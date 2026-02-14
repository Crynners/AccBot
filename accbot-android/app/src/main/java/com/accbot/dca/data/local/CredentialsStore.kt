package com.accbot.dca.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for exchange API credentials.
 * Uses AES-256-GCM encryption via Android Keystore.
 * All credentials stay on device - never transmitted to any server.
 *
 * Supports separate credentials for production and sandbox environments.
 * Each environment has its own API keys stored with different prefixes.
 *
 * Security notes:
 * - Uses commit() instead of apply() for immediate persistence
 * - Credentials are encrypted at rest
 * - No cloud backup of this data
 */
@Singleton
class CredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefs ->
            migrateOldCredentials(prefs)
        }
    }

    /**
     * Migrate credentials from old format (credentials_EXCHANGE) to new format (credentials_prod_EXCHANGE).
     * This ensures existing users' production credentials are preserved.
     */
    private fun migrateOldCredentials(prefs: SharedPreferences) {
        val migrationDone = prefs.getBoolean(KEY_MIGRATION_DONE, false)
        if (migrationDone) return

        val editor = prefs.edit()
        Exchange.entries.forEach { exchange ->
            val oldKey = "${KEY_PREFIX_LEGACY}${exchange.name}"
            val newKey = "${KEY_PREFIX_PROD}${exchange.name}"

            val oldValue = prefs.getString(oldKey, null)
            if (oldValue != null && !prefs.contains(newKey)) {
                editor.putString(newKey, oldValue)
                editor.remove(oldKey)
            }
        }
        editor.putBoolean(KEY_MIGRATION_DONE, true)
        editor.commit()
    }

    /**
     * Save exchange credentials.
     * Uses commit() for immediate persistence of security-critical data.
     * @param credentials The credentials to save
     * @param isSandbox Whether these are sandbox credentials (default: false for production)
     * @return true if save was successful
     */
    fun saveCredentials(credentials: ExchangeCredentials, isSandbox: Boolean = false): Boolean {
        val key = getCredentialsKey(credentials.exchange, isSandbox)
        val json = gson.toJson(credentials)
        return encryptedPrefs.edit().putString(key, json).commit()
    }

    /**
     * Get credentials for an exchange.
     * @param exchange The exchange to get credentials for
     * @param isSandbox Whether to get sandbox credentials (default: false for production)
     * @return credentials or null if not found or corrupted
     */
    fun getCredentials(exchange: Exchange, isSandbox: Boolean = false): ExchangeCredentials? {
        val key = getCredentialsKey(exchange, isSandbox)
        val json = encryptedPrefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, ExchangeCredentials::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if credentials exist for an exchange.
     * @param exchange The exchange to check
     * @param isSandbox Whether to check for sandbox credentials (default: false for production)
     */
    fun hasCredentials(exchange: Exchange, isSandbox: Boolean = false): Boolean {
        return encryptedPrefs.contains(getCredentialsKey(exchange, isSandbox))
    }

    /**
     * Delete credentials for an exchange.
     * Uses commit() for immediate persistence.
     * @param exchange The exchange to delete credentials for
     * @param isSandbox Whether to delete sandbox credentials (default: false for production)
     * @return true if deletion was successful
     */
    fun deleteCredentials(exchange: Exchange, isSandbox: Boolean = false): Boolean {
        return encryptedPrefs.edit().remove(getCredentialsKey(exchange, isSandbox)).commit()
    }

    /**
     * Get list of exchanges with stored credentials.
     * @param isSandbox Whether to get exchanges with sandbox credentials (default: false for production)
     */
    fun getConfiguredExchanges(isSandbox: Boolean = false): List<Exchange> {
        return Exchange.entries.filter { hasCredentials(it, isSandbox) }
    }

    /**
     * Delete all stored credentials for a specific environment.
     * Uses commit() for immediate persistence.
     * @param isSandbox Whether to clear sandbox credentials (default: false for production)
     * @return true if clear was successful
     */
    fun clearAllCredentials(isSandbox: Boolean = false): Boolean {
        val editor = encryptedPrefs.edit()
        Exchange.entries.forEach { exchange ->
            editor.remove(getCredentialsKey(exchange, isSandbox))
        }
        return editor.commit()
    }

    /**
     * Delete all stored credentials for both environments.
     * Uses commit() for immediate persistence.
     * @return true if clear was successful
     */
    fun clearAllCredentialsBothEnvironments(): Boolean {
        val editor = encryptedPrefs.edit()
        Exchange.entries.forEach { exchange ->
            editor.remove(getCredentialsKey(exchange, false))
            editor.remove(getCredentialsKey(exchange, true))
        }
        return editor.commit()
    }

    private fun getCredentialsKey(exchange: Exchange, isSandbox: Boolean): String {
        val prefix = if (isSandbox) KEY_PREFIX_SANDBOX else KEY_PREFIX_PROD
        return "$prefix${exchange.name}"
    }

    companion object {
        private const val PREFS_NAME = "accbot_credentials"
        private const val KEY_PREFIX_LEGACY = "credentials_"
        private const val KEY_PREFIX_PROD = "credentials_prod_"
        private const val KEY_PREFIX_SANDBOX = "credentials_sandbox_"
        private const val KEY_MIGRATION_DONE = "credentials_migration_v2_done"
    }
}
