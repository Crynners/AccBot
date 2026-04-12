package com.accbot.dca

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Main Application class for AccBot
 * Initializes Hilt DI and WorkManager
 */
@HiltAndroidApp
class AccBotApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var credentialsStore: CredentialsStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccBot Application started")

        // Restore saved locale preference
        val tag = userPreferences.getLanguageTag()
        if (tag.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }

        // CredentialsStore v2→v3 migration: re-key credentials from
        // `credentials_${env}_${EXCHANGE}` to `credentials_v3_${env}_${connectionId}`.
        // Needs Room DB access (to look up the connection per exchange) so it can't run
        // inside the encryptedPrefs lazy init. Idempotent - safe to call every launch.
        //
        // BLOCKING: must complete before any background worker (DcaWorker) tries to load
        // credentials by connectionId. The migration is fast (single-digit milliseconds for
        // ~14 keys) and runs once per upgrade - acceptable startup cost. The previous
        // background-launch version had a race window where the alarm-triggered DcaWorker
        // could fire between Room migration and CredentialsStore migration completion,
        // failing to find credentials under the new key.
        runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    val prodDb = DcaDatabase.getInstance(this@AccBotApplication, isSandbox = false)
                    val sandboxDb = DcaDatabase.getInstance(this@AccBotApplication, isSandbox = true)
                    credentialsStore.ensureMigrated(prodDb, sandboxDb)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run CredentialsStore migration", e)
            }
        }
    }

    companion object {
        private const val TAG = "AccBotApplication"
    }
}
