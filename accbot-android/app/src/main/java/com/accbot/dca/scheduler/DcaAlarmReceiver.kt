package com.accbot.dca.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.accbot.dca.worker.DcaWorker

/**
 * Lightweight receiver for exact alarm triggers.
 * Enqueues an expedited OneTimeWorkRequest via DcaWorker to reuse full DI/coroutine support.
 */
class DcaAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "DCA alarm fired")
        DcaWorker.runFromAlarm(context)
    }

    companion object {
        private const val TAG = "DcaAlarmReceiver"
    }
}
