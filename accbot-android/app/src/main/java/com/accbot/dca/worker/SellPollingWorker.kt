package com.accbot.dca.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.domain.usecase.ResolvePendingTransactionsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background worker that resolves PENDING sell orders by polling the
 * exchange API. Scheduled/rescheduled by [SellPollingScheduler] using the
 * user-configured frequency (preset or cron).
 *
 * The worker is a no-op (other than rescheduling) when there are no open sells,
 * keeping battery impact minimal when trading is idle.
 */
@HiltWorker
class SellPollingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: DcaDatabase,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase,
    private val sellPollingScheduler: SellPollingScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val openSells = database.transactionDao().countOpenSells()
            if (openSells > 0) {
                Log.d(TAG, "Resolving $openSells open sell(s)")
                val resolved = resolvePendingTransactionsUseCase()
                Log.d(TAG, "Resolved $resolved transaction(s)")
            } else {
                Log.d(TAG, "No open sells, skipping resolve")
            }
            sellPollingScheduler.rescheduleIfEnabled()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "SellPollingWorker error", e)
            // Always re-arm the chain so a transient failure doesn't stop polling forever.
            try { sellPollingScheduler.rescheduleIfEnabled() } catch (_: Exception) {}
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SellPollingWorker"
        const val WORK_NAME = "sell_polling"
    }
}
