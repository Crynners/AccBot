package com.accbot.dca.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for exchange API credentials.
 * Uses AES-256-GCM encryption via Android Keystore.
 * All credentials stay on device - never transmitted to any server.
 *
 * **Connection-keyed (v3+):** each credential set is keyed by a `connectionId`
 * (from [ExchangeConnectionEntity]) plus an environment prefix:
 *   `credentials_v3_prod_${connectionId}` or `credentials_v3_sandbox_${connectionId}`.
 *
 * The env prefix is required because production and sandbox each have their own Room
 * database file with independent autoincrement IDs (so connection #1 in prod and
 * connection #1 in sandbox would otherwise collide here).
 *
 * Migration history:
 * - v1 (legacy): `credentials_${EXCHANGE}` (no env separation, prod-only)
 * - v2: `credentials_prod_${EXCHANGE}` / `credentials_sandbox_${EXCHANGE}`
 * - v3 (this version): `credentials_v3_${env}_${connectionId}`
 *
 * The v1→v2 migration runs synchronously in [encryptedPrefs] lazy init. The v2→v3
 * migration ([ensureMigrated]) needs Room DB access and is therefore called
 * explicitly from `AccBotApplication.onCreate` once both databases are ready.
 *
 * Security notes:
 * - Uses commit() instead of apply() for immediate persistence
 * - Credentials are encrypted at rest
 * - No cloud backup of this data
 */
@Singleton
class CredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    /**
     * DAO for the *current* environment's database (selected at app start by sandbox flag).
     * Used by the legacy [Exchange]-keyed API shims to resolve a default connection per
     * exchange. Phases 6–7 will refactor remaining callers off the legacy API and this
     * field can then be removed.
     */
    private val currentEnvConnectionDao: ExchangeConnectionDao
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
            migrateLegacyToV2(prefs)
        }
    }

    /**
     * v1 → v2 migration. Renames `credentials_${EXCHANGE}` to `credentials_prod_${EXCHANGE}`.
     * Runs synchronously on first prefs access; idempotent via flag.
     */
    private fun migrateLegacyToV2(prefs: SharedPreferences) {
        val migrationDone = prefs.getBoolean(KEY_MIGRATION_V2_DONE, false)
        if (migrationDone) return

        val editor = prefs.edit()
        Exchange.entries.forEach { exchange ->
            val oldKey = "${KEY_PREFIX_LEGACY}${exchange.name}"
            val newKey = "${KEY_PREFIX_PROD_V2}${exchange.name}"

            val oldValue = prefs.getString(oldKey, null)
            if (oldValue != null && !prefs.contains(newKey)) {
                editor.putString(newKey, oldValue)
                editor.remove(oldKey)
            }
        }
        editor.putBoolean(KEY_MIGRATION_V2_DONE, true)
        editor.commit()
    }

    /**
     * v2 → v3 migration. Re-keys `credentials_${env}_${EXCHANGE}` to
     * `credentials_v3_${env}_${connectionId}` by looking up the corresponding
     * [ExchangeConnectionEntity] in the prod/sandbox database.
     *
     * Must be called from `AccBotApplication.onCreate` before any caller uses the
     * connection-aware API. Idempotent via [KEY_MIGRATION_V3_DONE] flag.
     *
     * If the v18→v19 Room migration didn't auto-create a connection for an exchange
     * (e.g. user has saved API keys but no plan yet for that exchange), this method
     * inserts an empty-named connection on the fly so credentials don't end up
     * orphaned.
     *
     * @param prodDb Production database instance (used to look up / create prod connections)
     * @param sandboxDb Sandbox database instance
     */
    suspend fun ensureMigrated(prodDb: DcaDatabase, sandboxDb: DcaDatabase) {
        if (encryptedPrefs.getBoolean(KEY_MIGRATION_V3_DONE, false)) return

        Log.d(TAG, "Running CredentialsStore v2→v3 migration")
        try {
            migrateV2ToV3ForEnv(prodDb, isSandbox = false)
            migrateV2ToV3ForEnv(sandboxDb, isSandbox = true)
            encryptedPrefs.edit().putBoolean(KEY_MIGRATION_V3_DONE, true).commit()
            Log.d(TAG, "CredentialsStore v2→v3 migration complete")
        } catch (e: Exception) {
            // Don't set the flag - next launch will retry. Log so we notice.
            Log.e(TAG, "CredentialsStore v2→v3 migration failed; will retry next launch", e)
        }
    }

    private suspend fun migrateV2ToV3ForEnv(db: DcaDatabase, isSandbox: Boolean) {
        val v2Prefix = if (isSandbox) KEY_PREFIX_SANDBOX_V2 else KEY_PREFIX_PROD_V2
        val v3Prefix = if (isSandbox) KEY_PREFIX_SANDBOX_V3 else KEY_PREFIX_PROD_V3
        val connectionDao = db.exchangeConnectionDao()

        for (exchange in Exchange.entries) {
            val oldKey = "$v2Prefix${exchange.name}"
            val oldValue = encryptedPrefs.getString(oldKey, null) ?: continue

            // Find or create a connection for this exchange in the target DB. The Room
            // migration v18→v19 already auto-created connections for exchanges referenced
            // by data tables; this branch fires only when user has credentials but no
            // plans/transactions yet for that exchange.
            val connectionId = connectionDao.getDefaultByExchange(exchange)?.id ?: try {
                connectionDao.insert(
                    ExchangeConnectionEntity(
                        exchange = exchange,
                        name = "",
                        createdAt = Instant.now()
                    )
                )
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Concurrent insert raced (unlikely under runBlocking init, but safe);
                // re-fetch and use the existing row.
                connectionDao.getDefaultByExchange(exchange)?.id ?: run {
                    Log.e(TAG, "Failed to resolve connection for ${exchange.name} after constraint", e)
                    continue
                }
            }

            val newKey = "$v3Prefix$connectionId"
            // Don't overwrite an existing v3 key (shouldn't happen but defensive)
            if (encryptedPrefs.contains(newKey)) {
                encryptedPrefs.edit().remove(oldKey).commit()
                continue
            }
            encryptedPrefs.edit()
                .putString(newKey, oldValue)
                .remove(oldKey)
                .commit()
            Log.d(TAG, "Migrated credentials for ${exchange.name} (sandbox=$isSandbox) → connectionId=$connectionId")
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Connection-keyed API (v3)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Save exchange credentials for a specific connection.
     * @return true if save was successful
     */
    fun saveCredentials(connectionId: Long, credentials: ExchangeCredentials, isSandbox: Boolean): Boolean {
        val key = v3Key(connectionId, isSandbox)
        val json = gson.toJson(credentials)
        return encryptedPrefs.edit().putString(key, json).commit()
    }

    /**
     * Get credentials for a specific connection. Returns null if not found or corrupted.
     */
    fun getCredentials(connectionId: Long, isSandbox: Boolean): ExchangeCredentials? {
        val key = v3Key(connectionId, isSandbox)
        val json = encryptedPrefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, ExchangeCredentials::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun hasCredentials(connectionId: Long, isSandbox: Boolean): Boolean {
        return encryptedPrefs.contains(v3Key(connectionId, isSandbox))
    }

    fun deleteCredentials(connectionId: Long, isSandbox: Boolean): Boolean {
        return encryptedPrefs.edit().remove(v3Key(connectionId, isSandbox)).commit()
    }

    /**
     * Delete all stored credentials for a specific environment.
     * Iterates the prefs map and removes any v3 key with the matching env prefix.
     * @return true if clear was successful
     */
    fun clearAllCredentials(isSandbox: Boolean): Boolean {
        val prefix = if (isSandbox) KEY_PREFIX_SANDBOX_V3 else KEY_PREFIX_PROD_V3
        val editor = encryptedPrefs.edit()
        encryptedPrefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        return editor.commit()
    }

    /**
     * Delete all stored credentials for both environments. Also clears any leftover
     * v1/v2 keys for cleanliness.
     */
    fun clearAllCredentialsBothEnvironments(): Boolean {
        val editor = encryptedPrefs.edit()
        encryptedPrefs.all.keys
            .filter { key ->
                key.startsWith(KEY_PREFIX_PROD_V3) ||
                    key.startsWith(KEY_PREFIX_SANDBOX_V3) ||
                    key.startsWith(KEY_PREFIX_PROD_V2) ||
                    key.startsWith(KEY_PREFIX_SANDBOX_V2) ||
                    key.startsWith(KEY_PREFIX_LEGACY)
            }
            .forEach { editor.remove(it) }
        return editor.commit()
    }

    private fun v3Key(connectionId: Long, isSandbox: Boolean): String {
        val prefix = if (isSandbox) KEY_PREFIX_SANDBOX_V3 else KEY_PREFIX_PROD_V3
        return "$prefix$connectionId"
    }

    // ───────────────────────────────────────────────────────────────────────
    // Legacy [Exchange]-keyed API shims (to be removed after Phases 6–7).
    //
    // These resolve the *default* (first) connection of the given exchange and
    // delegate to the v3 connection-based API. They are suspend because resolving
    // the connection requires a Room query.
    //
    // The injected [currentEnvConnectionDao] points at the database matching the
    // app's current sandbox flag (set at app start). All legacy callers happen to
    // use the same isSandbox value as the current env, so this is fine.
    // ───────────────────────────────────────────────────────────────────────

    @Deprecated("Use getCredentials(connectionId, isSandbox)")
    suspend fun getCredentials(exchange: Exchange, isSandbox: Boolean = false): ExchangeCredentials? {
        val connectionId = currentEnvConnectionDao.getDefaultByExchange(exchange)?.id ?: return null
        return getCredentials(connectionId, isSandbox)
    }

    @Deprecated("Use saveCredentials(connectionId, credentials, isSandbox) - explicitly create a connection first")
    suspend fun saveCredentials(credentials: ExchangeCredentials, isSandbox: Boolean = false): Boolean {
        // Resolve or create a default connection for this exchange so legacy
        // "save credentials by exchange" callers (Phase 7 candidates) keep working.
        val connectionId = currentEnvConnectionDao.getDefaultByExchange(credentials.exchange)?.id ?: try {
            currentEnvConnectionDao.insert(
                ExchangeConnectionEntity(
                    exchange = credentials.exchange,
                    name = "",
                    createdAt = Instant.now()
                )
            )
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Race lost - another caller just created the default. Re-fetch.
            currentEnvConnectionDao.getDefaultByExchange(credentials.exchange)?.id
                ?: return false
        }
        return saveCredentials(connectionId, credentials, isSandbox)
    }

    @Deprecated("Use hasCredentials(connectionId, isSandbox)")
    suspend fun hasCredentials(exchange: Exchange, isSandbox: Boolean = false): Boolean {
        val connectionId = currentEnvConnectionDao.getDefaultByExchange(exchange)?.id ?: return false
        return hasCredentials(connectionId, isSandbox)
    }

    @Deprecated("Use deleteCredentials(connectionId, isSandbox) and delete the connection itself if needed")
    suspend fun deleteCredentials(exchange: Exchange, isSandbox: Boolean = false): Boolean {
        val connectionId = currentEnvConnectionDao.getDefaultByExchange(exchange)?.id ?: return false
        return deleteCredentials(connectionId, isSandbox)
    }

    /**
     * Legacy: list distinct exchanges that have at least one connection with stored credentials
     * in the given environment. Phase 7 will replace this with a connection-list API.
     */
    @Deprecated("Use ExchangeConnectionDao.getAll() and filter by hasCredentials(connectionId, isSandbox)")
    suspend fun getConfiguredExchanges(isSandbox: Boolean = false): List<Exchange> {
        return currentEnvConnectionDao.getAll()
            .filter { hasCredentials(it.id, isSandbox) }
            .map { it.exchange }
            .distinct()
    }

    companion object {
        private const val TAG = "CredentialsStore"
        private const val PREFS_NAME = "accbot_credentials"
        private const val KEY_PREFIX_LEGACY = "credentials_"
        private const val KEY_PREFIX_PROD_V2 = "credentials_prod_"
        private const val KEY_PREFIX_SANDBOX_V2 = "credentials_sandbox_"
        private const val KEY_PREFIX_PROD_V3 = "credentials_v3_prod_"
        private const val KEY_PREFIX_SANDBOX_V3 = "credentials_v3_sandbox_"
        private const val KEY_MIGRATION_V2_DONE = "credentials_migration_v2_done"
        private const val KEY_MIGRATION_V3_DONE = "credentials_migration_v3_done"
    }
}
