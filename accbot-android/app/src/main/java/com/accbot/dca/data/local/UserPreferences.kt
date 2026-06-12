package com.accbot.dca.data.local

import android.content.Context
import android.content.SharedPreferences
import com.accbot.dca.domain.model.DcaFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App theme options
 */
enum class AppTheme {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Storage for user preferences like theme and notification settings.
 * Non-sensitive data that doesn't require encryption.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== Theme ====================

    private val _appThemeFlow = MutableStateFlow(readAppTheme())

    /** Observable theme state – emits immediately when theme changes. */
    val appThemeFlow: StateFlow<AppTheme> = _appThemeFlow.asStateFlow()

    private fun readAppTheme(): AppTheme {
        val themeName = prefs.getString(KEY_APP_THEME, AppTheme.DARK.name)
        return try {
            AppTheme.valueOf(themeName ?: AppTheme.DARK.name)
        } catch (e: Exception) {
            AppTheme.DARK
        }
    }

    /**
     * Get app theme preference.
     */
    fun getAppTheme(): AppTheme = _appThemeFlow.value

    /**
     * Set app theme preference.
     */
    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
        _appThemeFlow.value = theme
    }

    // ==================== Notifications ====================

    /**
     * Check if notifications are enabled globally.
     */
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    /**
     * Set notifications enabled status.
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /**
     * Check if purchase notifications are enabled.
     */
    fun arePurchaseNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_PURCHASE_NOTIFICATIONS, true)
    }

    /**
     * Set purchase notifications enabled status.
     */
    fun setPurchaseNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURCHASE_NOTIFICATIONS, enabled).apply()
    }

    /**
     * Check if error notifications are enabled.
     */
    fun areErrorNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_ERROR_NOTIFICATIONS, true)
    }

    /**
     * Set error notifications enabled status.
     */
    fun setErrorNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ERROR_NOTIFICATIONS, enabled).apply()
    }

    /**
     * Check if weekly summary notifications are enabled.
     */
    fun areWeeklySummaryNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEEKLY_SUMMARY_NOTIFICATIONS, false)
    }

    /**
     * Set weekly summary notifications enabled status.
     */
    fun setWeeklySummaryNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY_SUMMARY_NOTIFICATIONS, enabled).apply()
    }

    // ==================== Low Balance Warning ====================

    fun getLowBalanceThresholdDays(): Int {
        return prefs.getInt(KEY_LOW_BALANCE_THRESHOLD_DAYS, 2)
    }

    fun setLowBalanceThresholdDays(days: Int) {
        prefs.edit().putInt(KEY_LOW_BALANCE_THRESHOLD_DAYS, days.coerceIn(1, 14)).apply()
    }

    // ==================== Language ====================

    /**
     * Get saved language tag (e.g., "en", "cs").
     * Empty string means "follow system default".
     */
    fun getLanguageTag(): String {
        return prefs.getString(KEY_LANGUAGE, "") ?: ""
    }

    /**
     * Set language tag. Pass empty string for system default.
     */
    fun setLanguageTag(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
    }

    // ==================== Biometric Lock ====================

    fun isBiometricLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled).apply()
    }

    // ==================== Changelog ====================

    fun getLastSeenVersionCode(): Int {
        return prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0)
    }

    fun setLastSeenVersionCode(code: Int) {
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION_CODE, code).apply()
    }

    // ==================== Market Pulse ====================

    fun isMarketPulseEnabled(): Boolean {
        return prefs.getBoolean(KEY_MARKET_PULSE_ENABLED, false)
    }

    fun setMarketPulseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MARKET_PULSE_ENABLED, enabled).apply()
    }

    fun isMarketPulseExpanded(): Boolean {
        return prefs.getBoolean(KEY_MARKET_PULSE_EXPANDED, true)
    }

    fun setMarketPulseExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_MARKET_PULSE_EXPANDED, expanded).apply()
    }

    // ==================== Experimental Exchanges ====================

    fun areExperimentalExchangesEnabled(): Boolean {
        return prefs.getBoolean(KEY_EXPERIMENTAL_EXCHANGES, false)
    }

    fun setExperimentalExchangesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPERIMENTAL_EXCHANGES, enabled).apply()
    }

    // ==================== Sandbox Mode ====================

    /**
     * Check if sandbox (testnet) mode is enabled.
     * When enabled, only exchanges with full sandbox support are available,
     * and all trades go to testnet endpoints.
     */
    fun isSandboxMode(): Boolean {
        return prefs.getBoolean(KEY_SANDBOX_MODE, false)
    }

    /**
     * Set sandbox mode enabled status.
     * Uses commit() instead of apply() to ensure synchronous write before app restart.
     */
    fun setSandboxMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SANDBOX_MODE, enabled).commit()
    }

    // ==================== Portfolio ====================

    /**
     * Get the persisted portfolio page identifier (e.g., "agg:EUR" or "plan:123").
     * Returns null if not set. The identifier is stable across plan add/remove/reorder,
     * unlike a raw index.
     */
    fun getPortfolioSelectedPageId(): String? {
        return prefs.getString(KEY_PORTFOLIO_SELECTED_PAGE, null)
    }

    fun setPortfolioSelectedPageId(pageId: String) {
        prefs.edit().putString(KEY_PORTFOLIO_SELECTED_PAGE, pageId).apply()
    }

    // ==================== Trading (Sell Extension) ====================

    /**
     * Master trading switch. When false, all sell-order flows (take-profit,
     * trailing, manual limit-sell) are disabled regardless of per-plan settings.
     * Defaults to false so upgrading users must explicitly opt in.
     */
    fun isTradingEnabled(): Boolean {
        return prefs.getBoolean(KEY_TRADING_ENABLED, false)
    }

    fun setTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRADING_ENABLED, enabled).apply()
    }

    // ==================== Sell Polling ====================

    /**
     * Whether the background [SellPollingWorker] is enabled.
     * Independent from [isTradingEnabled] so users can keep polling on for
     * historical fills even after disabling new sell orders, and off by default.
     */
    fun isPeriodicSellPollingEnabled(): Boolean {
        return prefs.getBoolean(KEY_SELL_POLLING_ENABLED, false)
    }

    /**
     * Polling frequency. For CUSTOM, [getSellPollingCronExpression] provides
     * the cron string. Falls back to HOURLY if the stored enum name can't be
     * parsed (e.g. after downgrading from a build that added a new frequency).
     */
    fun getSellPollingFrequency(): DcaFrequency {
        val name = prefs.getString(KEY_SELL_POLLING_FREQUENCY, DcaFrequency.HOURLY.name)
            ?: return DcaFrequency.HOURLY
        return try {
            DcaFrequency.valueOf(name)
        } catch (e: IllegalArgumentException) {
            DcaFrequency.HOURLY
        }
    }

    /**
     * Cron expression used when [getSellPollingFrequency] returns CUSTOM.
     * Null for preset frequencies.
     */
    fun getSellPollingCronExpression(): String? {
        return prefs.getString(KEY_SELL_POLLING_CRON, null)
    }

    /**
     * Serialized visual schedule builder state (JSON). Null for preset frequencies.
     * The worker ignores this - it's only used by the UI to re-hydrate the
     * schedule picker without having to reverse-engineer the cron string.
     */
    fun getSellPollingScheduleConfig(): String? {
        return prefs.getString(KEY_SELL_POLLING_SCHEDULE_CONFIG, null)
    }

    /**
     * Update all sell-polling settings in one edit so readers never see a
     * half-applied state (e.g. CUSTOM frequency with a stale cron from a
     * previous save).
     */
    fun setPeriodicSellPolling(
        enabled: Boolean,
        frequency: DcaFrequency,
        cron: String?,
        scheduleConfig: String?
    ) {
        prefs.edit()
            .putBoolean(KEY_SELL_POLLING_ENABLED, enabled)
            .putString(KEY_SELL_POLLING_FREQUENCY, frequency.name)
            .apply {
                if (cron != null) putString(KEY_SELL_POLLING_CRON, cron)
                else remove(KEY_SELL_POLLING_CRON)
                if (scheduleConfig != null) putString(KEY_SELL_POLLING_SCHEDULE_CONFIG, scheduleConfig)
                else remove(KEY_SELL_POLLING_SCHEDULE_CONFIG)
            }
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "accbot_user_prefs"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_PURCHASE_NOTIFICATIONS = "purchase_notifications"
        private const val KEY_ERROR_NOTIFICATIONS = "error_notifications"
        private const val KEY_WEEKLY_SUMMARY_NOTIFICATIONS = "weekly_summary_notifications"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SANDBOX_MODE = "sandbox_mode"
        private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
        private const val KEY_LOW_BALANCE_THRESHOLD_DAYS = "low_balance_threshold_days"
        private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
        private const val KEY_MARKET_PULSE_ENABLED = "market_pulse_enabled"
        private const val KEY_MARKET_PULSE_EXPANDED = "market_pulse_expanded"
        private const val KEY_EXPERIMENTAL_EXCHANGES = "experimental_exchanges_enabled"
        private const val KEY_PORTFOLIO_SELECTED_PAGE = "portfolio_selected_page"
        private const val KEY_TRADING_ENABLED = "trading_enabled"
        private const val KEY_SELL_POLLING_ENABLED = "sell_polling_enabled"
        private const val KEY_SELL_POLLING_FREQUENCY = "sell_polling_frequency"
        private const val KEY_SELL_POLLING_CRON = "sell_polling_cron"
        private const val KEY_SELL_POLLING_SCHEDULE_CONFIG = "sell_polling_schedule_config"
    }
}
