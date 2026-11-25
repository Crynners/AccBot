package com.accbot.dca.presentation.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat

/**
 * Things outside AccBot that decide whether scheduled purchases run on time:
 * battery optimization (Doze can postpone the alarm by hours), exact alarms
 * (Android 12+ may deny them, so the alarm becomes inexact) and system notifications
 * (without them the user never learns that a purchase failed).
 */
enum class AutomationIssue(val key: String) {
    BATTERY_OPTIMIZED("battery"),
    EXACT_ALARMS_DENIED("exact_alarms"),
    NOTIFICATIONS_BLOCKED("notifications")
}

@Immutable
data class AutomationHealth(val issues: Set<AutomationIssue> = emptySet()) {
    val isOk: Boolean get() = issues.isEmpty()
}

object AutomationHealthChecker {

    fun check(context: Context): AutomationHealth {
        val issues = mutableSetOf<AutomationIssue>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            issues += AutomationIssue.BATTERY_OPTIMIZED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                issues += AutomationIssue.EXACT_ALARMS_DENIED
            }
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            issues += AutomationIssue.NOTIFICATIONS_BLOCKED
        }
        return AutomationHealth(issues)
    }

    /**
     * Opens the system screen that fixes [issue]. Falls back to the app's details page
     * when a vendor ROM lacks the specific screen. Returns false when nothing could open.
     */
    fun openSettings(context: Context, issue: AutomationIssue): Boolean {
        val pkg = context.packageName
        val primary = when (issue) {
            AutomationIssue.BATTERY_OPTIMIZED ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
            AutomationIssue.EXACT_ALARMS_DENIED ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg"))
                } else null
            AutomationIssue.NOTIFICATIONS_BLOCKED ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        for (intent in listOfNotNull(primary, fallback)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // try the next one
            }
        }
        return false
    }
}
