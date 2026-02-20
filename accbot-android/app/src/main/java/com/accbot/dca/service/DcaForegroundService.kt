package com.accbot.dca.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.accbot.dca.scheduler.DcaAlarmScheduler
import com.accbot.dca.worker.DcaWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for reliable DCA execution
 * Keeps the app alive and schedules WorkManager tasks
 */
@AndroidEntryPoint
class DcaForegroundService : Service() {

    @Inject
    lateinit var notificationService: NotificationService

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DcaForegroundService started")

        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }

        // START_STICKY ensures service restarts if killed
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = notificationService.createForegroundNotification()
        startForeground(NotificationService.NOTIFICATION_ID_SERVICE, notification)

        // Schedule exact alarm as primary scheduler
        serviceScope.launch {
            try {
                DcaAlarmScheduler.scheduleNextAlarm(this@DcaForegroundService)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling alarm", e)
            }
        }

        // Schedule WorkManager as safety net (hourly)
        DcaWorker.schedule(this)

        Log.d(TAG, "Foreground service started, alarm + WorkManager scheduled")
    }

    private fun stopForegroundService() {
        // Cancel alarm scheduler
        DcaAlarmScheduler.cancelAlarm(this)

        // Cancel WorkManager
        DcaWorker.cancel(this)

        // Stop service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Foreground service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "DcaForegroundService destroyed")
    }

    companion object {
        private const val TAG = "DcaForegroundService"

        const val ACTION_START = "com.accbot.dca.START_DCA_SERVICE"
        const val ACTION_STOP = "com.accbot.dca.STOP_DCA_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, DcaForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DcaForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
