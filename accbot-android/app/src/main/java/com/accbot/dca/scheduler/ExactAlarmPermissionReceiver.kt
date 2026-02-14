package com.accbot.dca.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+).
 * Re-arms the exact alarm when the user grants permission.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Exact alarm permission state changed")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) {
                    DcaAlarmScheduler.scheduleNextAlarm(context)
                } ?: Log.e(TAG, "Timeout scheduling alarm after permission change")
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling alarm after permission change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ExactAlarmPermReceiver"
    }
}
