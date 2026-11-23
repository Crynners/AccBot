package com.accbot.dca.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
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
import com.accbot.dca.domain.usecase.BuySafetyPolicy
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    override suspend fun doWork(): Result = purchaseLock.withLock { executePurchases() }

    private suspend fun executePurchases(): Result {
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        val isSandbox = userPreferences.processSandboxMode
        if (isSandbox != userPreferences.isSandboxMode() ||
            (forceRun && inputData.getString(KEY_SANDBOX_MODE) != isSandbox.toString())
        ) return Result.success()
        val forcePlanId = inputData.getLong(KEY_PLAN_ID, -1L)
        val repeatCount = inputData.getInt(KEY_REPEAT_COUNT, 1)
        Log.d(TAG, "DcaWorker started (forceRun=$forceRun, forcePlanId=$forcePlanId, repeatCount=$repeatCount)")

        // Resolve any PENDING transactions from previous runs before processing new purchases
        try {
            resolvePendingTransactions()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve pending transactions", e)
        }

        // Catch-up progress is persisted in missedPurchaseCount so that a replayed worker
        // (process death mid-loop -> WorkManager re-runs the whole request) resumes where
        // it left off instead of re-buying already-covered slots from iteration 1.
        val requestedAt = inputData.getLong(KEY_REQUESTED_AT, 0).let(Instant::ofEpochMilli)
        val isCatchUp = forceRun && forcePlanId > 0 &&
            (repeatCount > 1 || inputData.getBoolean(KEY_CATCH_UP, false))
        if (forceRun && requestedAt == Instant.EPOCH && runAttemptCount > 0) return Result.failure()
        val totalIterations = if (isCatchUp) {
            val remaining = database.dcaPlanDao().getPlanById(forcePlanId)?.missedPurchaseCount ?: 0
            minOf(repeatCount, remaining)
        } else {
            repeatCount
        }

        try {
            repeats@ for (iteration in 1..totalIterations) {
                if (iteration > 1) {
                    Log.d(TAG, "Repeat iteration $iteration/$repeatCount")
                    kotlinx.coroutines.delay(3_000L) // brief pause between missed purchases
                }

                val enabledPlans = if (forcePlanId > 0) {
                    listOfNotNull(database.dcaPlanDao().getPlanById(forcePlanId)?.takeIf { it.isEnabled })
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
                    // A run that started since the user queued this purchase already covered it.
                    if (forceRun && iteration == 1 && requestedAt != Instant.EPOCH &&
                        plan.lastExecutedAt?.let { !it.isBefore(requestedAt) } == true
                    ) continue
                    if (database.transactionDao().hasUnidentifiedBuy(plan.id)) continue

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
                    val credentials = credentialsStore.getCredentials(plan.connectionId, isSandbox)
                    if (credentials == null) {
                        Log.e(TAG, "No credentials for connection ${plan.connectionId} (${plan.exchange}, sandbox=$isSandbox)")
                        // Notify the user once per plan per worker lifetime so a missing-credentials
                        // plan doesn't just silently fail forever (e.g. after a half-completed
                        // backup restore or a deleted connection). Dedup is in-memory, which
                        // means we re-notify after the worker is recreated - acceptable, since
                        // the user needs to act on it anyway.
                        if (credentialErrorNotifiedPlans.add(plan.id)) {
                            val connectionName = try {
                                database.exchangeConnectionDao().getById(plan.connectionId)?.name ?: ""
                            } catch (_: Exception) { "" }
                            notificationService.showErrorNotification(
                                planId = plan.id,
                                exchange = plan.exchange,
                                connectionId = plan.connectionId,
                                templateArgs = NotificationTemplateArgs.MissingCredentials(
                                    crypto = plan.crypto,
                                    exchangeName = plan.exchange.displayName,
                                    connectionName = connectionName
                                )
                            )
                        }
                        continue
                    }
                    // Credentials are back - allow future notifications if they disappear again
                    credentialErrorNotifiedPlans.remove(plan.id)

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

                    // Circuit breaker: if this plan has already bought far more than its schedule
                    // allows in the last 24h, something is wrong - auto-disable instead of continuing
                    // to spend. Counts reconciled buys too, so it reflects real exchange spend.
                    // Forced runs (run-now / catch-up) are user-initiated and may legitimately
                    // exceed the schedule, so they get a wider allowance (+repeatCount) instead
                    // of bypassing the breaker - a replayed or duplicated force run must still
                    // be bounded.
                    val buysLast24h = database.transactionDao()
                        .countCompletedBuysSinceSync(plan.id, now.minus(Duration.ofHours(24)))
                    val expectedPerDay = BuySafetyPolicy.expectedBuysPerDay(effectiveIntervalMinutes(plan))
                    val allowedPerDay = if (forceRun) expectedPerDay + repeatCount else expectedPerDay
                    if (BuySafetyPolicy.isRunaway(buysLast24h, allowedPerDay)) {
                        Log.e(TAG, "Plan ${plan.id} runaway detected ($buysLast24h buys/24h, allowed ~$allowedPerDay) - auto-disabling")
                        database.dcaPlanDao().setEnabled(plan.id, false)
                        notificationService.showErrorNotification(
                            planId = plan.id,
                            exchange = plan.exchange,
                            connectionId = plan.connectionId,
                            templateArgs = NotificationTemplateArgs.Error(
                                crypto = plan.crypto,
                                errorMessage = context.getString(R.string.notification_runaway_disabled)
                            )
                        )
                        continue
                    }

                    val pending = TransactionEntity(
                        planId = plan.id,
                        exchange = plan.exchange,
                        connectionId = plan.connectionId,
                        crypto = plan.crypto,
                        fiat = plan.fiat,
                        fiatAmount = purchaseAmount,
                        cryptoAmount = BigDecimal.ZERO,
                        price = BigDecimal.ZERO,
                        fee = BigDecimal.ZERO,
                        status = TransactionStatus.PENDING,
                        executedAt = Instant.now()
                    )
                    // Persist the purchase before sending it. A lost response or interrupted worker
                    // leaves a reviewable record and prevents the same plan from placing another order.
                    val nextExec = calculateNextExecution(plan, now)
                    val transactionId = database.withTransaction {
                        val current = database.dcaPlanDao().getPlanById(plan.id)
                        if (current != plan || userPreferences.isSandboxMode() != isSandbox ||
                            database.transactionDao().hasUnidentifiedBuy(plan.id)
                        ) return@withTransaction null

                        if (forceRun) {
                            database.dcaPlanDao().updateExecutionTime(plan.id, now, nextExec)
                        } else if (database.dcaPlanDao().claimPlanForExecutionSync(plan.id, now, nextExec) == 0) {
                            return@withTransaction null
                        }
                        database.dcaPlanDao().resetNetworkRetry(plan.id)
                        database.transactionDao().insertTransaction(pending)
                    } ?: continue

                    val api = exchangeApiFactory.create(credentials, isSandbox)
                    val receipt = withTimeoutOrNull(45_000L) {
                        api.marketBuy(plan.crypto, plan.fiat, purchaseAmount)
                    } ?: DcaResult.Error(context.getString(R.string.notification_purchase_needs_review))
                    val result = if (receipt is DcaResult.Success && receipt.transaction.exchangeOrderId.isNullOrBlank()) {
                        DcaResult.Error(context.getString(R.string.notification_purchase_needs_review))
                    } else {
                        receipt
                    }

                    when (result) {
                        is DcaResult.Success -> {
                            val filled = result.transaction
                            database.withTransaction {
                                database.transactionDao().updateTransaction(pending.copy(
                                    id = transactionId,
                                    fiatAmount = filled.fiatAmount,
                                    cryptoAmount = filled.cryptoAmount,
                                    price = filled.price,
                                    fee = filled.fee,
                                    feeAsset = filled.feeAsset,
                                    status = filled.status,
                                    exchangeOrderId = filled.exchangeOrderId,
                                    executedAt = filled.executedAt
                                ))
                                if (isCatchUp && (filled.status.isOpen || filled.status.hasFilledPortion)) {
                                    database.dcaPlanDao().decrementMissedPurchaseCount(plan.id)
                                }
                            }

                            if (filled.status.isTerminal && !filled.status.hasFilledPortion) {
                                notificationService.showErrorNotification(
                                    planId = plan.id,
                                    exchange = plan.exchange,
                                    connectionId = plan.connectionId,
                                    templateArgs = NotificationTemplateArgs.Error(
                                        crypto = plan.crypto,
                                        errorMessage = context.getString(R.string.notification_purchase_without_fill)
                                    )
                                )
                                if (isCatchUp) break@repeats
                                continue
                            }

                            if (!isCatchUp) {
                                val missedOrigin = plan.originalScheduledAt ?: nextExecution
                                val missed = if (missedOrigin != null) {
                                    (calculateMissedPurchaseCount(plan, missedOrigin, Instant.now()) - 1).coerceAtLeast(0)
                                } else 0
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
                            }
                            val isPending = filled.status.isOpen
                            val scheduledTime = if (!forceRun) (plan.originalScheduledAt ?: nextExecution) else null
                            notificationService.showPurchaseNotification(
                                plan.crypto,
                                filled.cryptoAmount,
                                if (isPending) purchaseAmount else filled.fiatAmount,
                                plan.fiat,
                                filled.price,
                                plan.id,
                                pending = isPending,
                                exchange = plan.exchange,
                                connectionId = plan.connectionId,
                                scheduledAt = scheduledTime,
                                executedAt = if (scheduledTime != null) Instant.now() else null
                            )
                            if (!isPending) {
                                checkWithdrawalThreshold(plan, api)
                                checkLowBalance(
                                    api, plan.exchange.displayName, plan.fiat, plan.amount,
                                    effectiveIntervalMinutes(plan), plan.id, plan.connectionId
                                )
                            }
                        }
                        is DcaResult.Error -> {
                            val message = if (result.confirmedRejection) {
                                database.transactionDao().updateTransaction(pending.copy(
                                    id = transactionId, status = TransactionStatus.FAILED, errorMessage = result.message
                                ))
                                result.message
                            } else {
                                context.getString(R.string.notification_purchase_needs_review)
                            }
                            notificationService.showErrorNotification(
                                planId = plan.id,
                                exchange = plan.exchange,
                                connectionId = plan.connectionId,
                                templateArgs = NotificationTemplateArgs.Error(crypto = plan.crypto, errorMessage = message)
                            )
                            if (isCatchUp) break@repeats
                        }
                    }
                }
            }

            // Re-arm alarm for next execution (self-perpetuating chain)
            DcaAlarmScheduler.scheduleNextAlarm(context)

            return Result.success()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Worker was cancelled (e.g. system reclaimed it mid-run). This is not an error -
            // don't show a scary "Job was cancelled" notification. Let WorkManager reschedule.
            Log.d(TAG, "DcaWorker cancelled", ce)
            try { DcaAlarmScheduler.scheduleNextAlarm(context) } catch (_: Exception) {}
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "DcaWorker error", e)
            notificationService.showErrorNotification(context.getString(R.string.notification_dca_error), e.message ?: "Unknown error")
            // Still try to re-arm alarm even on error
            try { DcaAlarmScheduler.scheduleNextAlarm(context) } catch (_: Exception) {}
            // Forced runs bypass the per-plan claim and due-time guards, so an automatic
            // WorkManager retry could re-buy plans that already bought in this run. They
            // are user-initiated - fail instead; the user sees the error notification
            // and can trigger the run again.
            return if (forceRun) Result.failure() else Result.retry()
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

    /** Best-effort minutes between executions, used by the runaway circuit breaker. */
    private fun effectiveIntervalMinutes(plan: DcaPlanEntity): Long =
        if (plan.cronExpression != null) {
            CronUtils.getIntervalMinutesEstimate(plan.cronExpression) ?: 1440L
        } else {
            plan.frequency.intervalMinutes
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
        private const val KEY_REQUESTED_AT = "requestedAt"
        private const val KEY_CATCH_UP = "catchUp"
        private val purchaseLock = Mutex()

        internal suspend fun tryReviewPurchase(review: suspend () -> Unit): Boolean {
            if (!purchaseLock.tryLock()) return false
            try {
                review()
                return true
            } finally {
                purchaseLock.unlock()
            }
        }
        const val WORK_NAME = "dca_periodic_work"

        /**
         * Single unique-work queue for ALL user-initiated (forceRun) executions.
         * Forced runs bypass the per-plan claim, so they must never run concurrently -
         * APPEND_OR_REPLACE serializes them one after another (and replaces a failed
         * chain instead of blocking future runs).
         */
        private const val FORCE_WORK_NAME = "dca_force_work"

        /**
         * Plan IDs we've already shown a "missing credentials" notification for in
         * this process lifetime. Prevents spamming the notification tray on every
         * alarm tick (~hourly) for a plan whose credentials will stay missing until
         * the user acts. Cleared when credentials reappear for that plan.
         */
        private val credentialErrorNotifiedPlans = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        private const val KEY_SANDBOX_MODE = "sandbox_mode"

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
                .putString(KEY_SANDBOX_MODE, UserPreferences(context).processSandboxMode.toString())
                .putLong(KEY_REQUESTED_AT, System.currentTimeMillis())
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(FORCE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, oneTimeWorkRequest)

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
                .putString(KEY_SANDBOX_MODE, UserPreferences(context).processSandboxMode.toString())
                .putLong(KEY_REQUESTED_AT, System.currentTimeMillis())
                .putLong(KEY_PLAN_ID, planId)
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(FORCE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, oneTimeWorkRequest)

            Log.d(TAG, "DCA one-time work enqueued for plan $planId (forceRun=true)")
        }

        /**
         * Run missed purchases for a plan (user chose to catch up).
         */
        fun runMissedPurchases(context: Context, planId: Long, count: Int) {
            val inputData = Data.Builder()
                .putBoolean(KEY_FORCE_RUN, true)
                .putString(KEY_SANDBOX_MODE, UserPreferences(context).processSandboxMode.toString())
                .putLong(KEY_REQUESTED_AT, System.currentTimeMillis())
                .putLong(KEY_PLAN_ID, planId)
                .putInt(KEY_REPEAT_COUNT, count)
                .putBoolean(KEY_CATCH_UP, true)
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
                .enqueueUniqueWork(FORCE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, oneTimeWorkRequest)

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
         * a restricted "dataSync" foreground service from BOOT_COMPLETED - which is not
         * allowed for apps targeting Android 15+. The alarm wakes the device anyway, so
         * regular OneTimeWorkRequest runs immediately.
         */
        private const val ALARM_WORK_NAME = "dca_alarm_execution"

        fun runFromAlarm(context: Context) {
            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DcaWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                // KEEP, never REPLACE: a re-fired alarm must not cancel a worker that may
                // be mid-buy - REPLACE could abort it after the order POST was sent,
                // leaving a real order unrecorded (invisible to the runaway breaker).
                .enqueueUniqueWork(ALARM_WORK_NAME, ExistingWorkPolicy.KEEP, oneTimeWorkRequest)

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
