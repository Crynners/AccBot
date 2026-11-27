package com.accbot.dca.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanCodec
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local storage for read-only plan shares.
 *
 * Received snapshots live as JSON files in `filesDir/shared_plans/<shareId>.json`, outside
 * Room, so they never mix with the user's own plans, portfolio or backups and need no
 * schema migration. They are shared between prod and sandbox mode because they hold no
 * credentials and are never executed.
 *
 * The sender side keeps a stable share ID per own plan so that re-sharing a plan replaces
 * the previous snapshot on the recipient's device instead of adding a duplicate.
 */
@Singleton
class SharedPlanStore internal constructor(
    private val directory: File,
    private val shareIds: ShareIdRegistry,
    private val keys: KeyVault
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        directory = File(context.filesDir, DIRECTORY),
        shareIds = PreferencesShareIdRegistry(context),
        keys = EncryptedKeyVault(context)
    )

    sealed class SaveResult {
        data class Saved(val plan: SharedPlan, val replaced: Boolean) : SaveResult()
        /** A newer snapshot of the same plan is already stored; nothing changed. */
        data class Outdated(val stored: SharedPlan) : SaveResult()
    }

    private val mutex = Mutex()
    private val _plans = MutableStateFlow<List<SharedPlan>?>(null)

    /** Null until the first [refresh]. Newest share first. */
    val plans: StateFlow<List<SharedPlan>?> = _plans.asStateFlow()

    suspend fun refresh(): List<SharedPlan> = mutex.withLock { loadLocked() }

    suspend fun get(shareId: String): SharedPlan? = mutex.withLock {
        (_plans.value ?: loadLocked()).firstOrNull { it.shareId == shareId.lowercase() }
    }

    /**
     * Store a received share. A share without [credentials] removes any key kept for the
     * same plan, so the sender can stop live updates by sending a plain snapshot.
     */
    suspend fun save(plan: SharedPlan, credentials: ExchangeCredentials? = null): SaveResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = (_plans.value ?: loadLocked()).firstOrNull { it.shareId == plan.shareId }
            if (existing != null && existing.sharedAt.isAfter(plan.sharedAt)) {
                return@withContext SaveResult.Outdated(existing)
            }
            // A QR code carries a key but no trades. Re-importing it must not wipe history that
            // was already downloaded (or came from a file snapshot).
            val isQr = credentials != null && plan.trades.isEmpty()
            val stored = when {
                isQr && existing != null -> plan.copy(
                    trades = existing.trades,
                    tradesFromExchange = existing.tradesFromExchange,
                    refreshedAt = existing.refreshedAt,
                    // Keep the snapshot time of file trades so the later merge leaves no gap.
                    sharedAt = if (existing.tradesFromExchange) plan.sharedAt else existing.sharedAt,
                    liveUpdates = true
                )
                isQr -> plan.copy(tradesFromExchange = true, liveUpdates = true)
                else -> plan.copy(liveUpdates = credentials != null)
            }
            if (credentials != null) keys.put(plan.shareId, credentials) else keys.remove(plan.shareId)
            writeLocked(stored)
            SaveResult.Saved(stored, replaced = existing != null)
        }
    }

    /** Persist a refreshed copy of a stored share, keeping its key. */
    suspend fun update(plan: SharedPlan) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if ((_plans.value ?: loadLocked()).none { it.shareId == plan.shareId }) return@withContext
            writeLocked(plan)
        }
    }

    /** Read-only exchange key of a live-update share, or null. */
    suspend fun credentials(shareId: String): ExchangeCredentials? = withContext(Dispatchers.IO) {
        if (SharedPlanCodec.isValidShareId(shareId)) keys.get(shareId.lowercase()) else null
    }

    suspend fun delete(shareId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (SharedPlanCodec.isValidShareId(shareId)) {
                keys.remove(shareId.lowercase())
                fileFor(shareId.lowercase()).delete()
            }
            loadLocked()
        }
    }

    private suspend fun writeLocked(plan: SharedPlan) {
        check(directory.mkdirs() || directory.isDirectory) { "Could not create shared plan storage" }
        val target = fileFor(plan.shareId)
        val temp = File(directory, "${plan.shareId}.tmp")
        temp.writeText(SharedPlanCodec.encode(plan), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Could not store shared plan" }
        }
        loadLocked()
    }

    /** Stable share ID for one of the user's own plans in the given environment. */
    fun shareIdForOwnPlan(planId: Long, isSandbox: Boolean): String = shareIds.shareIdFor(planId, isSandbox)

    private suspend fun loadLocked(): List<SharedPlan> = withContext(Dispatchers.IO) {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }.orEmpty()
        val loaded = files.mapNotNull { file ->
            try {
                SharedPlanCodec.decode(file.readText(Charsets.UTF_8))
                    .takeIf { "${it.shareId}.json" == file.name }
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring unreadable shared plan ${file.name}: ${e.message}")
                null
            }
        }.sortedByDescending { it.sharedAt }
        _plans.value = loaded
        loaded
    }

    private fun fileFor(shareId: String): File {
        require(SharedPlanCodec.isValidShareId(shareId))
        return File(directory, "$shareId.json")
    }

    interface ShareIdRegistry {
        fun shareIdFor(planId: Long, isSandbox: Boolean): String
    }

    /** Encrypted storage for read-only keys of live-update shares, separate from own connections. */
    interface KeyVault {
        fun get(shareId: String): ExchangeCredentials?
        fun put(shareId: String, credentials: ExchangeCredentials)
        fun remove(shareId: String)
    }

    private class EncryptedKeyVault(private val context: Context) : KeyVault {
        private val gson = Gson()
        private val prefs: SharedPreferences by lazy {
            EncryptedSharedPreferences.create(
                context,
                KEYS_PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        override fun get(shareId: String): ExchangeCredentials? = try {
            prefs.getString(shareId, null)?.let { gson.fromJson(it, ExchangeCredentials::class.java) }
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable shared key for $shareId: ${e.message}")
            null
        }

        override fun put(shareId: String, credentials: ExchangeCredentials) {
            check(prefs.edit().putString(shareId, gson.toJson(credentials)).commit()) { "Could not store shared key" }
        }

        override fun remove(shareId: String) {
            prefs.edit().remove(shareId).commit()
        }
    }

    private class PreferencesShareIdRegistry(context: Context) : ShareIdRegistry {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        @Synchronized
        override fun shareIdFor(planId: Long, isSandbox: Boolean): String {
            val key = "${if (isSandbox) "sandbox" else "prod"}_$planId"
            prefs.getString(key, null)?.takeIf(SharedPlanCodec::isValidShareId)?.let { return it }
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(key, created).apply()
            return created
        }
    }

    companion object {
        private const val TAG = "SharedPlanStore"
        private const val DIRECTORY = "shared_plans"
        private const val PREFS_NAME = "accbot_plan_shares"
        private const val KEYS_PREFS_NAME = "accbot_shared_plan_keys"
    }
}
