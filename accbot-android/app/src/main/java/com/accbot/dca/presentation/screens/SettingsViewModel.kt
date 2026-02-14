package com.accbot.dca.presentation.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.accbot.dca.service.DcaForegroundService
import com.accbot.dca.worker.DcaWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val configuredExchanges: List<Exchange> = emptyList(),
    val isBatteryOptimized: Boolean = true,
    val isSandboxMode: Boolean = false,
    val showRestartDialog: Boolean = false,
    val pendingSandboxMode: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val credentialsStore: CredentialsStore,
    private val onboardingPreferences: OnboardingPreferences,
    private val userPreferences: UserPreferences,
    private val database: DcaDatabase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var isDeleting = false

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val isSandbox = userPreferences.isSandboxMode()
        val configuredExchanges = credentialsStore.getConfiguredExchanges(isSandbox)

        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(application.packageName)

        _uiState.update {
            it.copy(
                configuredExchanges = configuredExchanges,
                isBatteryOptimized = isBatteryOptimized,
                isSandboxMode = isSandbox
            )
        }
    }

    /**
     * Request sandbox mode change - shows confirmation dialog.
     * Actual mode change requires app restart to switch database.
     */
    fun requestSandboxModeChange() {
        val newMode = !_uiState.value.isSandboxMode
        _uiState.update {
            it.copy(
                showRestartDialog = true,
                pendingSandboxMode = newMode
            )
        }
    }

    /**
     * Dismiss the restart dialog without making changes.
     */
    fun dismissRestartDialog() {
        _uiState.update {
            it.copy(showRestartDialog = false)
        }
    }

    /**
     * Confirm sandbox mode change and restart the app.
     * This is needed because the database singleton is created at app startup.
     */
    fun confirmSandboxModeChange(context: Context) {
        val newMode = _uiState.value.pendingSandboxMode
        userPreferences.setSandboxMode(newMode)
        restartApp(context)
    }

    /**
     * Restart the application to apply sandbox mode changes.
     * This clears the process and starts fresh with the new database.
     */
    private fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    @Deprecated("Use requestSandboxModeChange() instead - requires app restart")
    fun toggleSandboxMode() {
        requestSandboxModeChange()
    }

    fun isBiometricLockEnabled(): Boolean = userPreferences.isBiometricLockEnabled()

    fun setBiometricLockEnabled(enabled: Boolean) {
        userPreferences.setBiometricLockEnabled(enabled)
    }

    fun getCurrentLanguageTag(): String = userPreferences.getLanguageTag()

    fun setLanguage(tag: String) {
        userPreferences.setLanguageTag(tag)
        val localeList = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getLowBalanceThresholdDays(): Int = userPreferences.getLowBalanceThresholdDays()

    fun setLowBalanceThresholdDays(days: Int) {
        userPreferences.setLowBalanceThresholdDays(days)
    }

    fun refreshBatteryStatus() {
        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(application.packageName)
        _uiState.update { it.copy(isBatteryOptimized = isBatteryOptimized) }
    }

    fun removeExchangeCredentials(exchange: Exchange) {
        val isSandbox = userPreferences.isSandboxMode()
        credentialsStore.deleteCredentials(exchange, isSandbox)
        loadSettings()
    }

    fun deleteAllData() {
        if (isDeleting) return
        isDeleting = true
        viewModelScope.launch {
            try {
                // Stop services
                DcaForegroundService.stop(application)
                DcaWorker.cancel(application)

                // Clear all credentials for both environments
                credentialsStore.clearAllCredentialsBothEnvironments()

                // Reset onboarding state
                onboardingPreferences.resetOnboarding()

                // Reset sandbox mode to default (off)
                userPreferences.setSandboxMode(false)

                // Clear database
                database.clearAllTables()

                // Reload settings
                loadSettings()
            } finally {
                isDeleting = false
            }
        }
    }
}
