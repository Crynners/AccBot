package com.accbot.dca.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.NotificationTemplateArgs
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.domain.usecase.CalculateStrategyMultiplierUseCase
import com.accbot.dca.domain.usecase.ResolvePendingTransactionsUseCase
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.R
import com.accbot.dca.service.NotificationService
import java.math.BigDecimal
import java.math.RoundingMode
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull
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
    private val resolvePendingTransactions: ResolvePendingTransactionsUseCase,
    private val userPreferences: UserPreferences,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val minOrderSizeRepository: MinOrderSizeRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        val forcePlanId = inputData.getLong(KEY_PLAN_ID, -1L)
        val repeatCount = inputData.getInt(KEY_REPEAT_COUNT, 1)
        Log.d(TAG, "DcaWorker started (forceRun=$forceRun, forcePlanId=$forcePlanId, repeatCount=$repeatCount)")

        // Resolve any PENDING transactions from previous runs before processing new purchases
        try {
            resolvePendingTransactions()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve pending transactions", e)
        }

        try {
            for (iteration in 1..repeatCount) {
            if (iteration > 1) {
                Log.d(TAG, "Repeat iteration $iteration/$repeatCount")
                kotlinx.coroutines.delay(3_000L) // brief pause between missed purchases
            }

            val enabledPlans = if (forcePlanId > 0) {
                listOfNotNull(database.dcaPlanDao().getPlanById(forcePlanId))
            } else {
                database.dcaPlanDao().getEnabledPlans()
            }

            if (enabledPlans.isEmpty()) {
                Log.d(TAG, "No enabled DCA plans")
                if (iteration == 1) return Result.success() else break
            }

            for (plan in enabledPlans) {
                // Check if it's time to execute (skip check when forceRun)
                val now = Instant.now()
                val nextExecution = plan.nextExecutionAt

                if (!forceRun && nextExecution != null && nextExecution.isAfter(now)) {
                    Log.d(TAG, "Plan ${plan.id} not due yet, skipping")
                    continue
                }

                // Check if plan has reached its target accumulation amount
                if (plan.targetAmount != null && plan.targetAmount > BigDecimal.ZERO) {
                    val accumulated = BigDecimal(database.transactionDao().getAccumulatedCryptoByPlan(plan.id))
                    if (accumulated >= plan.targetAmount) {
                        database.dcaPlanDao().setEnabled(plan.id, false)
                        Log.d(TAG, "Plan ${plan.id} reached target ${plan.targetAmount}, auto-disabled")
                        notificationService.showErrorNotification(
                            planId = plan.id,
                            exchange = plan.exchange,
                            connectionId = plan.connectionId,
                            templateArgs = NotificationTemplateArgs.TargetReached(
                                targetAmount = plan.targetAmount.toPlainString(),
                                crypto = plan.crypto
                            )
                        )
                        continue
                    }
                }

                // Get credentials for this plan's connection (using current sandbox mode)
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(plan.connectionId, isSandbox)
                if (credentials == null) {
                    Log.e(TAG, "No credentials for connection ${plan.connectionId} (${plan.exchange}, sandbox=$isSandbox)")
                    continue
                }

                // Calculate purchase amount based on strategy
                val strategyResult = calculateStrategyMultiplier(
                    strategy = plan.strategy,
                    crypto = plan.crypto,
                    fiat = plan.fiat
                )

                val purchaseAmount = plan.amount
                    .multiply(BigDecimal(strategyResult.multiplier.toString()))
                    .setScale(2, RoundingMode.HALF_UP)

                Log.d(TAG, "Strategy: ${plan.strategy::class.simpleName}, " +
                        "Base: ${plan.amount}, Multiplier: ${strategyResult.multiplier}, " +
                        "Final: $purchaseAmount (${strategyResult.reason})")

                // Check minimum order size
                val minOrderSize = minOrderSizeRepository.getMinOrderSize(plan.exchange, plan.crypto, plan.fiat)
                if (purchaseAmount < minOrderSize) {
                    Log.w(TAG, "Plan ${plan.id}: purchaseAmount $purchaseAmount < minimum $minOrderSize, skipping")
                    try {
                        val transaction = TransactionEntity(
                            planId = plan.id,
                            exchange = plan.exchange,
                            connectionId = plan.connectionId,
                            crypto = plan.crypto,
                            fiat = plan.fiat,
                            fiatAmount = purchaseAmount,
                            cryptoAmount = BigDecimal.ZERO,
                            price = BigDecimal.ZERO,
                            fee = BigDecimal.ZERO,
                            status = TransactionStatus.FAILED,
                            errorMessage = "Amount $purchaseAmount ${plan.fiat} below minimum $minOrderSize ${plan.fiat}",
                            executedAt = Instant.now()
                        )
                        database.runInTransaction {
                            database.transactionDao().insertTransactionSync(transaction)
                            database.dcaPlanDao().updateExecutionTimeSync(plan.id, now, calculateNextExecution(plan, now))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save below-minimum transaction for plan ${plan.id}", e)
                    }

                    notificationService.showErrorNotification(
                        planId = plan.id,
                        exchange = plan.exchange,
                        connectionId = plan.connectionId,
                        templateArgs = NotificationTemplateArgs.BelowMinimum(
                            crypto = plan.crypto,
                            purchaseAmount = purchaseAmount.toPlainString(),
                            fiat = plan.fiat,
                            minOrderSize = minOrderSize.toPlainString()
                        )
                    )
                    continue
                }

                // Atomically claim the plan to prevent double-purchase from concurrent workers.
                // claimPlanForExecutionSync advances nextExecutionAt only if it's still in the past
                // (or null), returning 0 if another worker already claimed it.
                if (!forceRun) {
                    val nextExec = calculateNextExecution(plan, now)
                    val claimed = database.dcaPlanDao().claimPlanForExecutionSync(plan.id, now, nextExec)
                    if (claimed == 0) {
                        Log.d(TAG, "Plan ${plan.id} already claimed by another worker, skipping")
                        continue
                    }
                    Log.d(TAG, "Plan ${plan.id} claimed for execution, nextExecution advanced to $nextExec")
                }

                // Execute DCA purchase with immediate retry
                val api = exchangeApiFactory.create(credentials)
                val maxAttempts = 3
                val retryDelayMs = 2_000L
                val failedAttemptMessages = mutableListOf<String>()
                var finalResult: DcaResult? = null

                for (attempt in 1..maxAttempts) {
                    val attemptResult = withTimeoutOrNull(30_000L) {
                        api.marketBuy(plan.crypto, plan.fiat, purchaseAmount)
                    } ?: DcaResult.Error("API call timed out after 30s", retryable = true)

                    if (attemptResult is DcaResult.Success) {
                        finalResult = attemptResult
                        break
                    }

                    val error = attemptResult as DcaResult.Error
                    failedAttemptMessages.add("Attempt $attempt: ${error.message}")
                    Log.w(TAG, "Plan ${plan.id} attempt $attempt/$maxAttempts failed: ${error.message}")

                    if (attempt < maxAttempts) {
                        kotlinx.coroutines.delay(retryDelayMs)
                    } else {
                        finalResult = error
                    }
                }

                val warningMessage = if (finalResult is DcaResult.Success && failedAttemptMessages.isNotEmpty()) {
                    failedAttemptMessages.joinToString("; ")
                } else null

                when (finalResult) {
                    is DcaResult.Success -> {
                        // Reset network retry state on success and calculate missed purchases
                        try {
                            // Detect missed purchases: from retry recovery OR device boot/long-off
                            val missedOrigin = plan.originalScheduledAt ?: nextExecution
                            val missed = if (missedOrigin != null) {
                                // Subtract 1: this purchase just executed, covering one of the missed slots
                                (calculateMissedPurchaseCount(plan, missedOrigin, Instant.now()) - 1).coerceAtLeast(0)
                            } else 0
                            database.dcaPlanDao().resetNetworkRetry(plan.id)
                            if (missed > 0) {
                                database.dcaPlanDao().setMissedPurchaseCount(plan.id, missed)
                                notificationService.showMissedPurchasesNotification(
                                    crypto = plan.crypto,
                                    exchangeName = plan.exchange.displayName,
                                    missedCount = missed,
                                    planId = plan.id,
                                    exchange = plan.exchange,
                                    connectionId = plan.connectionId
                                )
                            }
                        } catch (_: Exception) {}

                        // Save transaction atomically with plan update
                        try {
                            val transaction = TransactionEntity(
                                planId = plan.id,
                                exchange = plan.exchange,
                                connectionId = plan.connectionId,
                                crypto = plan.crypto,
                                fiat = plan.fiat,
                                fiatAmount = finalResult.transaction.fiatAmount,
                                cryptoAmount = finalResult.transaction.cryptoAmount,
                                price = finalResult.transaction.price,
                                fee = finalResult.transaction.fee,
                                feeAsset = finalResult.transaction.feeAsset,
                                status = finalResult.transaction.status,
                                exchangeOrderId = finalResult.transaction.exchangeOrderId,
                                warningMessage = warningMessage,
                                executedAt = Instant.now()
                            )
                            database.runInTransaction {
                                database.transactionDao().insertTransactionSync(transaction)
                                // For non-forceRun, nextExecutionAt was already advanced by the claim;
                                // only update for forceRun which bypasses the claim step
                                if (forceRun) {
                                    database.dcaPlanDao().updateExecutionTimeSync(plan.id, now, calculateNextExecution(plan, now))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save transaction for plan ${plan.id}", e)
                        }

                        // Show notification (pending-aware, with delay info)
                        // Use originalScheduledAt if plan went through retries, otherwise nextExecution
                        val isPending = finalResult.transaction.status == TransactionStatus.PENDING
                        val executedNow = Instant.now()
                        val scheduledTime = if (!forceRun) (plan.originalScheduledAt ?: nextExecution) else null
                        notificationService.showPurchaseNotification(
                            plan.crypto,
                            finalResult.transaction.cryptoAmount,
                            if (isPending) purchaseAmount else finalResult.transaction.fiatAmount,
                            plan.fiat,
                            finalResult.transaction.price,
                            plan.id,
                            pending = isPending,
                            exchange = plan.exchange,
                            connectionId = plan.connectionId,
                            scheduledAt = scheduledTime,
                            executedAt = if (scheduledTime != null) executedNow else null
                        )

                        // Check withdrawal threshold
                        checkWithdrawalThreshold(plan, api)

                        Log.d(TAG, "DCA purchase successful: ${finalResult.transaction.cryptoAmount} ${plan.crypto}" +
                            if (isPending) " (pending confirmation)" else "" +
                            if (warningMessage != null) " (with retries: $warningMessage)" else "")

                        // Check remaining balance for low-balance warning
                        val effectiveInterval = if (plan.cronExpression != null) {
                            CronUtils.getIntervalMinutesEstimate(plan.cronExpression) ?: 1440L
                        } else {
                            plan.frequency.intervalMinutes
                        }
                        checkLowBalance(api, plan.exchange.displayName, plan.fiat, plan.amount, effectiveInterval, plan.id, plan.connectionId)
                    }

                    is DcaResult.Error -> {
                        if (finalResult.retryable) {
                            // Network error – retry in 5 min and notify user.
                            // Override the claimed nextExecutionAt with an earlier retry time.
                            try {
                                val retryTime = now.plus(Duration.ofMinutes(5))
                                database.runInTransaction {
                                    database.dcaPlanDao().updateExecutionTimeSync(plan.id, now, retryTime)
                                    database.dcaPlanDao().incrementNetworkRetrySync(plan.id, retryTime, nextExecution ?: now)
                                }
                                Log.w(TAG, "Network error for plan ${plan.id}, will retry at $retryTime: ${finalResult.message}")

                                // Only notify on first failure, not on subsequent retries
                                if (plan.networkRetryCount == 0) {
                                    notificationService.showNetworkRetryNotification(
                                        crypto = plan.crypto,
                                        exchangeName = plan.exchange.displayName,
                                        errorMessage = finalResult.message,
                                        nextRetryAt = retryTime,
                                        attemptCount = 1,
                                        planId = plan.id,
                                        exchange = plan.exchange,
                                        connectionId = plan.connectionId
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update retry time for plan ${plan.id}", e)
                            }
                        } else {
                            // Business error – save failed transaction, notify, advance to next interval
                            val failedWarning = if (failedAttemptMessages.size > 1) {
                                failedAttemptMessages.dropLast(1).joinToString("; ")
                            } else null
                            try {
                                val transaction = TransactionEntity(
                                    planId = plan.id,
                                    exchange = plan.exchange,
                                    connectionId = plan.connectionId,
                                    crypto = plan.crypto,
                                    fiat = plan.fiat,
                                    fiatAmount = plan.amount,
                                    cryptoAmount = BigDecimal.ZERO,
                                    price = BigDecimal.ZERO,
                                    fee = BigDecimal.ZERO,
                                    status = TransactionStatus.FAILED,
                                    errorMessage = finalResult.message,
                                    warningMessage = failedWarning,
                                    executedAt = Instant.now()
                                )
                                database.runInTransaction {
                                    database.transactionDao().insertTransactionSync(transaction)
                                    // For non-forceRun, nextExecutionAt was already advanced by the claim;
                                    // only update for forceRun which bypasses the claim step
                                    if (forceRun) {
                                        database.dcaPlanDao().updateExecutionTimeSync(plan.id, now, calculateNextExecution(plan, now))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save failed transaction for plan ${plan.id}", e)
                            }

                            notificationService.showErrorNotification(
                                planId = plan.id,
                                exchange = plan.exchange,
                                connectionId = plan.connectionId,
                                templateArgs = NotificationTemplateArgs.Error(
                                    crypto = plan.crypto,
                                    errorMessage = finalResult.message
                                )
                            )
                            Log.e(TAG, "DCA purchase failed for plan ${plan.id} after $maxAttempts attempts: ${finalResult.message}")
                        }
                    }

                    null -> {
                        Log.e(TAG, "DCA purchase for plan ${plan.id}: no result (unexpected)")
                    }
                }
            }
            } // repeat loop

            // Re-arm alarm for next execution (self-perpetuating chain)
            DcaAlarmScheduler.scheduleNextAlarm(context)

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "DcaWorker error", e)
            notificationService.showErrorNotification(context.getString(R.string.notification_dca_error), e.message ?: "Unknown error")
            // Still try to re-arm alarm even on error
            try { DcaAlarmScheduler.scheduleNextAlarm(context) } catch (_: Exception) {}
            return Result.retry()
        }
    }

    private fun calculateMissedPurchaseCount(plan: DcaPlanEntity, originalScheduledAt: Instant, now: Instant): Int {
        if (plan.cronExpression != null) {
            var count = 0
            var next = CronUtils.getNextExecution(plan.cronExpression, originalScheduledAt)
            while (next != null && !next.isAfter(now)) {
                count++
                next = CronUtils.getNextExecution(plan.cronExpression, next)
                if (count > 1000) break // safety limit
            }
            return count
        } else {
            val intervalMinutes = plan.frequency.intervalMinutes
            if (intervalMinutes <= 0) return 0
            val elapsedMinutes = Duration.between(originalScheduledAt, now).toMinutes()
            return (elapsedMinutes / intervalMinutes).coerceAtLeast(0).toInt()
        }
    }

    private fun calculateNextExecution(plan: DcaPlanEntity, now: Instant): Instant {
        return if (plan.cronExpression != null) {
            CronUtils.getNextExecution(plan.cronExpression, now)
                ?: now.plus(Duration.ofMinutes(plan.frequency.intervalMinutes.takeIf { it > 0 } ?: 1440))
        } else {
            now.plus(Duration.ofMinutes(plan.frequency.intervalMinutes))
        }
    }

    private suspend fun checkWithdrawalThreshold(plan: DcaPlanEntity, api: ExchangeApi) {
        try {
            // Per-connection threshold lookup; the plan carries connectionId since migration v18→v19.
            val threshold = database.withdrawalThresholdDao().getThresholdAmount(plan.connectionId, plan.crypto) ?: return
            val cryptoBalance = withTimeoutOrNull(10_000) { api.getBalance(plan.crypto) } ?: return
            if (cryptoBalance >= threshold) {
                notificationService.showWithdrawalThresholdNotification(
                    crypto = plan.crypto,
                    exchange = plan.exchange.displayName,
                    amount = cryptoBalance,
                    threshold = threshold,
                    planId = plan.id,
                    connectionId = plan.connectionId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking withdrawal threshold", e)
        }
    }

    private suspend fun checkLowBalance(
        api: ExchangeApi,
        exchangeName: String,
        fiat: String,
        planAmount: BigDecimal,
        intervalMinutes: Long,
        planId: Long,
        connectionId: Long
    ) {
        try {
            val balance = api.getBalance(fiat) ?: return
            val remainingExec = balance.divide(planAmount, 0, RoundingMode.DOWN).toInt()
            val remainingDays = (remainingExec.toLong() * intervalMinutes) / 1440.0
            val thresholdDays = userPreferences.getLowBalanceThresholdDays()
            if (remainingDays < thresholdDays) {
                notificationService.showLowBalanceNotification(exchangeName, fiat, remainingDays, planId, connectionId)
                Log.w(TAG, "Low balance on $exchangeName: ~$remainingDays days of $fiat remaining")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking low balance", e)
        }
    }

    companion object {
        private const val TAG = "DcaWorker"
        private const val KEY_FORCE_RUN = "forceRun"
        private const val KEY_PLAN_ID = "planId"
        private const val KEY_REPEAT_COUNT = "repeatCount"
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
         * Run a single DCA plan immediately (one-time), bypassing nextExecutionAt check
         */
        fun runPlan(context: Context, planId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putBoolean(KEY_FORCE_RUN, true)
                .putLong(KEY_PLAN_ID, planId)
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueue(oneTimeWorkRequest)

            Log.d(TAG, "DCA one-time work enqueued for plan $planId (forceRun=true)")
        }

        /**
         * Run missed purchases for a plan (user chose to catch up).
         */
        fun runMissedPurchases(context: Context, planId: Long, count: Int) {
            val inputData = Data.Builder()
                .putBoolean(KEY_FORCE_RUN, true)
                .putLong(KEY_PLAN_ID, planId)
                .putInt(KEY_REPEAT_COUNT, count)
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueue(oneTimeWorkRequest)

            Log.d(TAG, "Missed purchases enqueued for plan $planId (count=$count)")
        }

        /**
         * Run DCA from an alarm trigger.
         * Creates a OneTimeWorkRequest that respects nextExecutionAt checks
         * (does NOT set KEY_FORCE_RUN).
         *
         * Note: deliberately NOT using setExpedited(). On Android 11 and below, expedited
         * work runs as a foreground service, and when this chain is reachable from a
         * BOOT_COMPLETED broadcast (BootReceiver re-arms the alarm after boot, the alarm
         * fires shortly after, and triggers this work), Google Play flags it as starting
         * a restricted "dataSync" foreground service from BOOT_COMPLETED — which is not
         * allowed for apps targeting Android 15+. The alarm wakes the device anyway, so
         * regular OneTimeWorkRequest runs immediately.
         */
        private const val ALARM_WORK_NAME = "dca_alarm_execution"

        fun runFromAlarm(context: Context) {
            // No network constraint – worker must run even when offline so it can
            // show a network-retry notification instead of silently waiting.
            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(ALARM_WORK_NAME, ExistingWorkPolicy.REPLACE, oneTimeWorkRequest)

            Log.d(TAG, "DCA alarm-triggered work enqueued (unique=$ALARM_WORK_NAME)")
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
