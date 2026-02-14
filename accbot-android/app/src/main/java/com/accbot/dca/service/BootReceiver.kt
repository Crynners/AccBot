package com.accbot.dca.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.accbot.dca.scheduler.DcaAlarmScheduler
import com.accbot.dca.worker.DcaWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receiver to restart DCA service after device boot.
 * Re-arms exact alarm and schedules WorkManager safety net.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Device booted, restarting DCA scheduling")

            // Schedule WorkManager safety net
            DcaWorker.schedule(context)

            // Re-arm exact alarm (requires DB access, so use goAsync + coroutine)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeoutOrNull(9_000) {
                        DcaAlarmScheduler.scheduleNextAlarm(context)
                    } ?: Log.e(TAG, "Timeout scheduling alarm on boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling alarm on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
