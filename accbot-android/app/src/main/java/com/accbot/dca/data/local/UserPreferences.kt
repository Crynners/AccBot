package com.accbot.dca.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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

    /**
     * Get app theme preference.
     */
    fun getAppTheme(): AppTheme {
        val themeName = prefs.getString(KEY_APP_THEME, AppTheme.DARK.name)
        return try {
            AppTheme.valueOf(themeName ?: AppTheme.DARK.name)
        } catch (e: Exception) {
            AppTheme.DARK
        }
    }

    /**
     * Set app theme preference.
     */
    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
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
    }
}
