package com.accbot.dca.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central alarm coordinator using AlarmManager for precise DCA execution timing.
 *
 * Schedules a single exact alarm for the nearest plan execution time.
 * Self-perpetuating: alarm fires -> worker executes -> worker calls scheduleNextAlarm().
 */
object DcaAlarmScheduler {

    private const val TAG = "DcaAlarmScheduler"
    private const val REQUEST_CODE = 0xACCB07  // unique request code
    private const val ACTION_DCA_ALARM = "com.accbot.dca.ACTION_DCA_ALARM"
    private const val MIN_DELAY_MS = 5_000L  // 5 seconds minimum delay for past times

    /**
     * Query the DB for the earliest enabled plan execution time and set an exact alarm.
     * If the time is in the past, fires 5 seconds from now.
     */
    suspend fun scheduleNextAlarm(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val userPreferences = UserPreferences(appContext)
        val database = DcaDatabase.getInstance(appContext, userPreferences.isSandboxMode())
        val earliestMillis = database.dcaPlanDao().getEarliestNextExecution()

        if (earliestMillis == null) {
            Log.d(TAG, "No enabled plans with nextExecutionAt, cancelling alarm")
            cancelAlarm(appContext)
            return@withContext
        }

        val now = System.currentTimeMillis()
        val triggerAt = if (earliestMillis <= now) {
            now + MIN_DELAY_MS
        } else {
            earliestMillis
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Exact alarm permission denied on API 31+, fall back to inexact
            Log.w(TAG, "Exact alarm permission denied, using setAndAllowWhileIdle()")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }

        Log.d(TAG, "Alarm scheduled for ${triggerAt - now}ms from now (triggerAt=$triggerAt)")
    }

    /**
     * Cancel any pending DCA alarm.
     */
    fun cancelAlarm(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(createPendingIntent(appContext))
        Log.d(TAG, "Alarm cancelled")
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DcaAlarmReceiver::class.java).apply {
            action = ACTION_DCA_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
