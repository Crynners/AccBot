package com.accbot.dca

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.accbot.dca.data.local.UserPreferences
import dagger.hilt.android.HiltAndroidApp
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
    }

    companion object {
        private const val TAG = "AccBotApplication"
    }
}
