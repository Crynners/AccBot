package com.accbot.dca.worker

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.util.CronUtils
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the self-perpetuating chain of [SellPollingWorker] runs.
 *
 * Each worker run calls [rescheduleIfEnabled] to enqueue the next one, which
 * means the only way to stop polling is either [cancel] or toggling off the
 * user preference. Using one-shot work with [ExistingWorkPolicy.REPLACE] gives
 * us arbitrary cron-based intervals (and intervals shorter than WorkManager's
 * 15-minute periodic minimum, e.g. EVERY_15_MIN still works, but anything
 * tighter is also supported if a cron is provided).
 */
@Singleton
class SellPollingScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val userPreferences: UserPreferences
) {
    /**
     * Enqueue the next poll if the user has polling enabled; otherwise cancel
     * any outstanding chain. Safe to call from worker completion, app startup,
     * and settings-change listeners.
     */
    fun rescheduleIfEnabled() {
        if (!userPreferences.isPeriodicSellPollingEnabled()) {
            cancel()
            return
        }

        val frequency = userPreferences.getSellPollingFrequency()
        val cron = userPreferences.getSellPollingCronExpression()
        val now = Instant.now()

        val nextFire: Instant = when {
            frequency == DcaFrequency.CUSTOM && cron != null -> {
                CronUtils.getNextExecution(cron, now)
                    ?: now.plus(Duration.ofMinutes(60))
            }
            else -> now.plus(Duration.ofMinutes(frequency.intervalMinutes))
        }

        val delayMs = (nextFire.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<SellPollingWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(SellPollingWorker.WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            SellPollingWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Log.d(TAG, "SellPollingWorker scheduled for $nextFire (in ${delayMs}ms)")
    }

    fun cancel() {
        workManager.cancelUniqueWork(SellPollingWorker.WORK_NAME)
        Log.d(TAG, "SellPollingWorker cancelled")
    }

    companion object {
        private const val TAG = "SellPollingScheduler"
    }
}
