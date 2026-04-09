package com.accbot.dca.presentation.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DailyPriceDao
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.ExchangeBalanceDao
import com.accbot.dca.data.local.ExchangeConnectionDao
import com.accbot.dca.data.local.MonthlySummaryDao
import com.accbot.dca.data.local.NotificationDao
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.AppTheme
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.WithdrawalDao
import com.accbot.dca.data.local.WithdrawalThresholdDao
import com.accbot.dca.data.local.WithdrawalThresholdEntity
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.WithdrawalThreshold
import java.math.BigDecimal
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.accbot.dca.service.DcaForegroundService
import com.accbot.dca.worker.DcaWorker
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val configuredExchanges: List<Exchange> = emptyList(),
    /**
     * Total number of exchange *connections* (envelopes) — can exceed
     * `configuredExchanges.size` when the user has multiple connections on the same
     * exchange (e.g. two Coinmate sub-accounts).
     */
    val connectionCount: Int = 0,
    val isBatteryOptimized: Boolean = true,
    val isSandboxMode: Boolean = false,
    val showRestartDialog: Boolean = false,
    val pendingSandboxMode: Boolean = false,
    val lowBalanceThresholdDays: Int = 3,
    val languageTag: String = "",
    val isBiometricLockEnabled: Boolean = false,
    val withdrawalThresholds: List<WithdrawalThreshold> = emptyList(),
    val availableCryptoExchangePairs: List<Pair<String, Exchange>> = emptyList(),
    val isMarketPulseEnabled: Boolean = false,
    val appTheme: AppTheme = AppTheme.DARK,
    val dcaPlanCount: Int = 0,
    val transactionCount: Int = 0,
    val notificationCount: Int = 0,
    val notificationsEnabled: Boolean = true,
    val purchaseNotificationsEnabled: Boolean = true,
    val errorNotificationsEnabled: Boolean = true,
    val weeklySummaryEnabled: Boolean = false,
    val isExperimentalExchangesEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val credentialsStore: CredentialsStore,
    private val onboardingPreferences: OnboardingPreferences,
    private val userPreferences: UserPreferences,
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val notificationDao: NotificationDao,
    private val exchangeBalanceDao: ExchangeBalanceDao,
    private val monthlySummaryDao: MonthlySummaryDao,
    private val dailyPriceDao: DailyPriceDao,
    private val withdrawalDao: WithdrawalDao,
    private val withdrawalThresholdDao: WithdrawalThresholdDao,
    private val exchangeConnectionDao: ExchangeConnectionDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var isDeleting = false

    init {
        loadSettings()
        loadWithdrawalThresholds()
        loadDataCounts()
        observeConnections()
    }

    /**
     * Reactive flow on the connection list so the Settings card subtitle ("X connections
     * connected") and the legacy `configuredExchanges` field stay in sync as the user
     * adds or removes envelopes — no manual reload needed.
     */
    private fun observeConnections() {
        viewModelScope.launch {
            exchangeConnectionDao.getAllFlow().collect { connections ->
                _uiState.update {
                    it.copy(
                        connectionCount = connections.size,
                        configuredExchanges = connections.map { c -> c.exchange }.distinct()
                    )
                }
            }
        }
    }

    /** Re-read non-reactive data (credentials, battery, counts). Called on ON_RESUME. */
    fun refresh() {
        loadSettings()
        loadDataCounts()
    }

    private fun loadSettings() {
        val isSandbox = userPreferences.isSandboxMode()
        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(application.packageName)

        // Sync UI state immediately for non-suspend prefs values.
        // Note: `configuredExchanges` and `connectionCount` are populated reactively by
        // [observeConnections] from the connection DAO flow — no manual lookup here.
        _uiState.update {
            it.copy(
                isBatteryOptimized = isBatteryOptimized,
                isSandboxMode = isSandbox,
                lowBalanceThresholdDays = userPreferences.getLowBalanceThresholdDays(),
                languageTag = userPreferences.getLanguageTag(),
                appTheme = userPreferences.getAppTheme(),
                isBiometricLockEnabled = userPreferences.isBiometricLockEnabled(),
                isMarketPulseEnabled = userPreferences.isMarketPulseEnabled(),
                isExperimentalExchangesEnabled = userPreferences.areExperimentalExchangesEnabled()
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
    fun confirmSandboxModeChange() {
        val newMode = _uiState.value.pendingSandboxMode
        userPreferences.setSandboxMode(newMode)
        restartApp(application)
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

    fun setExperimentalExchangesEnabled(enabled: Boolean) {
        userPreferences.setExperimentalExchangesEnabled(enabled)
        _uiState.update { it.copy(isExperimentalExchangesEnabled = enabled) }
    }

    fun setMarketPulseEnabled(enabled: Boolean) {
        userPreferences.setMarketPulseEnabled(enabled)
        _uiState.update { it.copy(isMarketPulseEnabled = enabled) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        userPreferences.setBiometricLockEnabled(enabled)
        _uiState.update { it.copy(isBiometricLockEnabled = enabled) }
    }

    fun setTheme(theme: AppTheme) {
        userPreferences.setAppTheme(theme)
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun setLanguage(tag: String) {
        userPreferences.setLanguageTag(tag)
        _uiState.update { it.copy(languageTag = tag) }
        val localeList = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setLowBalanceThresholdDays(days: Int) {
        userPreferences.setLowBalanceThresholdDays(days)
        _uiState.update { it.copy(lowBalanceThresholdDays = days) }
    }

    fun refreshBatteryStatus() {
        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(application.packageName)
        _uiState.update { it.copy(isBatteryOptimized = isBatteryOptimized) }
    }

    private fun loadWithdrawalThresholds() {
        viewModelScope.launch {
            // 3-way combine on plans, thresholds AND connections so the UI also reflects
            // connection renames immediately. Pre-loading connections as a Map<id, Entity>
            // avoids the previous N+1 lookup-per-threshold pattern that ran on every emit.
            kotlinx.coroutines.flow.combine(
                dcaPlanDao.getAllPlans(),
                withdrawalThresholdDao.getAll(),
                exchangeConnectionDao.getAllFlow()
            ) { plans, thresholds, connections ->
                val connectionsById = connections.associateBy { it.id }
                val pairs = plans.map { it.crypto to it.exchange }.distinct()
                val thresholdDomains = thresholds.mapNotNull { entity ->
                    connectionsById[entity.connectionId]?.let { entity.toDomain(it) }
                }
                Pair(pairs, thresholdDomains)
            }.collect { (pairs, thresholds) ->
                _uiState.update {
                    it.copy(
                        availableCryptoExchangePairs = pairs,
                        withdrawalThresholds = thresholds
                    )
                }
            }
        }
    }

    /**
     * Resolve the default connection for an exchange. Phase 1 / Phase 7 stop-gap:
     * the Settings UI still picks an [Exchange] enum, so we map it to the first
     * connection of that exchange. After Fáze 7 lands, the UI will pick a connection
     * directly and this helper goes away.
     */
    private suspend fun resolveDefaultConnectionId(exchange: Exchange): Long? =
        exchangeConnectionDao.getDefaultByExchange(exchange)?.id

    fun setWithdrawalThreshold(crypto: String, exchange: Exchange, amount: BigDecimal) {
        viewModelScope.launch {
            val connectionId = resolveDefaultConnectionId(exchange) ?: return@launch
            withdrawalThresholdDao.upsert(
                WithdrawalThresholdEntity(crypto = crypto, connectionId = connectionId, thresholdAmount = amount)
            )
        }
    }

    fun removeWithdrawalThreshold(crypto: String, exchange: Exchange) {
        viewModelScope.launch {
            val connectionId = resolveDefaultConnectionId(exchange) ?: return@launch
            withdrawalThresholdDao.delete(crypto, connectionId)
        }
    }

    private fun loadDataCounts() {
        viewModelScope.launch {
            val plans = dcaPlanDao.getPlanCount()
            val txs = transactionDao.getTransactionCount()
            val notifs = notificationDao.getNotificationCount()
            _uiState.update { it.copy(dcaPlanCount = plans, transactionCount = txs, notificationCount = notifs) }
        }
    }

    fun deletePlans() {
        viewModelScope.launch {
            DcaForegroundService.stop(application)
            DcaWorker.cancel(application)
            dcaPlanDao.deleteAllPlans()
            loadDataCounts()
        }
    }

    fun deleteTransactions() {
        viewModelScope.launch {
            transactionDao.deleteAllTransactions()
            loadDataCounts()
        }
    }

    fun deleteNotifications() {
        viewModelScope.launch {
            notificationDao.deleteAllNotifications()
            loadDataCounts()
        }
    }

    fun removeExchangeCredentials(exchange: Exchange) {
        val isSandbox = userPreferences.isSandboxMode()
        viewModelScope.launch {
            @Suppress("DEPRECATION")
            credentialsStore.deleteCredentials(exchange, isSandbox)
            loadSettings()
        }
    }

    fun deleteAllData() {
        if (isDeleting) return
        isDeleting = true
        viewModelScope.launch {
            try {
                // Stop services
                DcaForegroundService.stop(application)
                DcaWorker.cancel(application)
                kotlinx.coroutines.delay(200) // service intent propagation

                // Clear all credentials for both environments
                credentialsStore.clearAllCredentialsBothEnvironments()

                // Reset onboarding state
                onboardingPreferences.resetOnboarding()

                // Reset sandbox mode to default (off)
                userPreferences.setSandboxMode(false)

                // Clear all database tables using suspend DAO methods
                // (Room runs these on its IO executor, unlike clearAllTables()
                // which is a blocking call that can fail on the main thread)
                dcaPlanDao.deleteAllPlans()
                transactionDao.deleteAllTransactions()
                notificationDao.deleteAllNotifications()
                exchangeBalanceDao.deleteAllBalances()
                monthlySummaryDao.deleteAllSummaries()
                dailyPriceDao.deleteAllPrices()
                withdrawalDao.deleteAllWithdrawals()
                withdrawalThresholdDao.deleteAll()

                // Restart app to apply clean state
                restartApp(application)
            } finally {
                isDeleting = false
            }
        }
    }
}
