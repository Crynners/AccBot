package com.accbot.dca.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.CalculateStrategyMultiplierUseCase
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.R
import com.accbot.dca.service.NotificationService
import java.math.BigDecimal
import java.math.RoundingMode
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import com.accbot.dca.scheduler.DcaAlarmScheduler

/**
 * WorkManager worker for executing DCA purchases in background
 * Guaranteed execution even when app is killed or device restarts
 */
@HiltWorker
class DcaWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val credentialsStore: CredentialsStore,
    private val database: DcaDatabase,
    private val notificationService: NotificationService,
    private val calculateStrategyMultiplier: CalculateStrategyMultiplierUseCase,
    private val userPreferences: UserPreferences,
    private val exchangeApiFactory: ExchangeApiFactory
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        Log.d(TAG, "DcaWorker started (forceRun=$forceRun)")

        try {
            val enabledPlans = database.dcaPlanDao().getEnabledPlans()

            if (enabledPlans.isEmpty()) {
                Log.d(TAG, "No enabled DCA plans")
                return Result.success()
            }

            for (plan in enabledPlans) {
                // Check if it's time to execute (skip check when forceRun)
                val now = Instant.now()
                val nextExecution = plan.nextExecutionAt

                if (!forceRun && nextExecution != null && nextExecution.isAfter(now)) {
                    Log.d(TAG, "Plan ${plan.id} not due yet, skipping")
                    continue
                }

                // Get credentials (using current sandbox mode)
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(plan.exchange, isSandbox)
                if (credentials == null) {
                    Log.e(TAG, "No credentials for ${plan.exchange} (sandbox=$isSandbox)")
                    continue
                }

                // Calculate purchase amount based on strategy
                val strategyResult = calculateStrategyMultiplier(
                    strategy = plan.strategy,
                    crypto = plan.crypto,
                    fiat = plan.fiat
                )

                val purchaseAmount = plan.amount
                    .multiply(BigDecimal(strategyResult.multiplier.toDouble()))
                    .setScale(2, RoundingMode.HALF_UP)

                Log.d(TAG, "Strategy: ${plan.strategy::class.simpleName}, " +
                        "Base: ${plan.amount}, Multiplier: ${strategyResult.multiplier}, " +
                        "Final: $purchaseAmount (${strategyResult.reason})")

                // Execute DCA purchase
                val api = exchangeApiFactory.create(credentials)
                val result = api.marketBuy(plan.crypto, plan.fiat, purchaseAmount)

                when (result) {
                    is DcaResult.Success -> {
                        // Save transaction
                        try {
                            val transaction = TransactionEntity(
                                planId = plan.id,
                                exchange = plan.exchange,
                                crypto = plan.crypto,
                                fiat = plan.fiat,
                                fiatAmount = result.transaction.fiatAmount,
                                cryptoAmount = result.transaction.cryptoAmount,
                                price = result.transaction.price,
                                fee = result.transaction.fee,
                                feeAsset = result.transaction.feeAsset,
                                status = TransactionStatus.COMPLETED,
                                exchangeOrderId = result.transaction.exchangeOrderId,
                                executedAt = Instant.now()
                            )
                            database.transactionDao().insertTransaction(transaction)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save transaction for plan ${plan.id}", e)
                        }

                        // Update plan execution time
                        try {
                            val nextExecutionTime = now.plus(Duration.ofMinutes(plan.frequency.intervalMinutes))
                            database.dcaPlanDao().updateExecutionTime(plan.id, now, nextExecutionTime)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update execution time for plan ${plan.id}", e)
                        }

                        // Show notification
                        notificationService.showPurchaseNotification(
                            plan.crypto,
                            result.transaction.cryptoAmount,
                            result.transaction.fiatAmount,
                            plan.fiat
                        )

                        Log.d(TAG, "DCA purchase successful: ${result.transaction.cryptoAmount} ${plan.crypto}")

                        // Check remaining balance for low-balance warning
                        checkLowBalance(api, plan.exchange.displayName, plan.fiat, plan.amount, plan.frequency.intervalMinutes)
                    }

                    is DcaResult.Error -> {
                        if (result.retryable) {
                            // Network error — silent retry in 5 min, no transaction saved
                            try {
                                val retryTime = now.plus(Duration.ofMinutes(5))
                                database.dcaPlanDao().updateExecutionTime(plan.id, now, retryTime)
                                Log.w(TAG, "Network error for plan ${plan.id}, will retry at $retryTime: ${result.message}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update retry time for plan ${plan.id}", e)
                            }
                        } else {
                            // Business error — save failed transaction, notify, advance to next interval
                            try {
                                val transaction = TransactionEntity(
                                    planId = plan.id,
                                    exchange = plan.exchange,
                                    crypto = plan.crypto,
                                    fiat = plan.fiat,
                                    fiatAmount = plan.amount,
                                    cryptoAmount = BigDecimal.ZERO,
                                    price = BigDecimal.ZERO,
                                    fee = BigDecimal.ZERO,
                                    status = TransactionStatus.FAILED,
                                    errorMessage = result.message,
                                    executedAt = Instant.now()
                                )
                                database.transactionDao().insertTransaction(transaction)

                                val nextExecutionTime = now.plus(Duration.ofMinutes(plan.frequency.intervalMinutes))
                                database.dcaPlanDao().updateExecutionTime(plan.id, now, nextExecutionTime)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save failed transaction for plan ${plan.id}", e)
                            }

                            notificationService.showErrorNotification(
                                context.getString(R.string.notification_dca_failed),
                                context.getString(R.string.notification_dca_failed_text, plan.crypto, result.message)
                            )
                            Log.e(TAG, "DCA purchase failed for plan ${plan.id}: ${result.message}")
                        }
                    }
                }
            }

            // Re-arm alarm for next execution (self-perpetuating chain)
            DcaAlarmScheduler.scheduleNextAlarm(context)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "DcaWorker error", e)
            notificationService.showErrorNotification(context.getString(R.string.notification_dca_error), e.message ?: "Unknown error")
            // Still try to re-arm alarm even on error
            try { DcaAlarmScheduler.scheduleNextAlarm(context) } catch (_: Exception) {}
            return Result.success()
        }
    }

    private suspend fun checkLowBalance(
        api: ExchangeApi,
        exchangeName: String,
        fiat: String,
        planAmount: BigDecimal,
        intervalMinutes: Long
    ) {
        try {
            val balance = api.getBalance(fiat) ?: return
            val remainingExec = balance.divide(planAmount, 0, RoundingMode.DOWN).toInt()
            val remainingDays = (remainingExec.toLong() * intervalMinutes) / 1440.0
            val thresholdDays = userPreferences.getLowBalanceThresholdDays()
            if (remainingDays < thresholdDays) {
                notificationService.showLowBalanceNotification(exchangeName, fiat, remainingDays)
                Log.w(TAG, "Low balance on $exchangeName: ~$remainingDays days of $fiat remaining")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking low balance", e)
        }
    }

    companion object {
        private const val TAG = "DcaWorker"
        private const val KEY_FORCE_RUN = "forceRun"
        const val WORK_NAME = "dca_periodic_work"

        /**
         * Schedule periodic DCA work as a safety net (backs up AlarmManager).
         * Minimum interval is 15 minutes (Android restriction).
         */
        fun schedule(context: Context, intervalMinutes: Long = 60) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<DcaWorker>(
                repeatInterval = maxOf(intervalMinutes, 15),
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )

            Log.d(TAG, "DCA work scheduled every $intervalMinutes minutes")
        }

        /**
         * Run DCA immediately (one-time), bypassing nextExecutionAt check
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putBoolean(KEY_FORCE_RUN, true)
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueue(oneTimeWorkRequest)

            Log.d(TAG, "DCA one-time work enqueued (forceRun=true)")
        }

        /**
         * Run DCA from an alarm trigger.
         * Creates an expedited OneTimeWorkRequest that respects nextExecutionAt checks
         * (does NOT set KEY_FORCE_RUN).
         */
        fun runFromAlarm(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueue(oneTimeWorkRequest)

            Log.d(TAG, "DCA alarm-triggered work enqueued (expedited)")
        }

        /**
         * Cancel all DCA work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)

            Log.d(TAG, "DCA work cancelled")
        }
    }
}
