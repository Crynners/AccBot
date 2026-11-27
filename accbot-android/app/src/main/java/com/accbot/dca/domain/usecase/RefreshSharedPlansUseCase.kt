package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.util.CronUtils
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Refreshes the live-update shares of one environment (sandbox and prod never mix). */
class RefreshSharedPlansUseCase @Inject constructor(
    private val sharedPlanStore: SharedPlanStore,
    private val refreshSharedPlan: RefreshSharedPlanUseCase
) {
    /** Pull-to-refresh: every live plan of the environment. Keyed by shareId. */
    suspend fun refreshAll(isSandbox: Boolean, now: Instant = Instant.now()): Map<String, RefreshSharedPlanUseCase.Result> =
        refresh(livePlans(isSandbox), now)

    /** App start: only plans whose scheduled purchase has probably happened. Keyed by shareId. */
    suspend fun refreshDue(isSandbox: Boolean, now: Instant = Instant.now()): Map<String, RefreshSharedPlanUseCase.Result> =
        refresh(livePlans(isSandbox).filter { isDue(it, now) }, now)

    private suspend fun livePlans(isSandbox: Boolean): List<SharedPlan> =
        sharedPlanStore.refresh().filter { it.isSandbox == isSandbox && it.liveUpdates }

    // One attempt per plan; a failure is stored in the plan and does not stop the others.
    private suspend fun refresh(plans: List<SharedPlan>, now: Instant): Map<String, RefreshSharedPlanUseCase.Result> =
        plans.associate { it.shareId to refreshSharedPlan(it.shareId, now) }

    companion object {
        private const val MAX_CRON_STEPS = 1000
        private val FALLBACK_INTERVAL: Duration = Duration.ofDays(1)

        internal fun nextExpectedPurchase(plan: SharedPlan): Instant {
            val base = plan.trades.lastOrNull { it.side == TransactionSide.BUY }?.executedAt ?: plan.startedAt
            val frequency = plan.frequency
            val cron = plan.cronExpression
            return when {
                frequency == DcaFrequency.CUSTOM && cron != null ->
                    CronUtils.getNextExecution(cron, base) ?: base.plus(FALLBACK_INTERVAL)
                frequency != null && frequency != DcaFrequency.CUSTOM ->
                    base.plus(Duration.ofMinutes(frequency.intervalMinutes))
                else -> base.plus(FALLBACK_INTERVAL)
            }
        }

        /**
         * The first expected purchase that the last refresh has not yet covered: a refresh at or
         * after the expected time that found no trade (purchase delayed or skipped) rolls the
         * expectation on to the next scheduled purchase instead of stalling it forever.
         */
        internal fun nextUnrefreshedPurchase(plan: SharedPlan): Instant {
            var next = nextExpectedPurchase(plan)
            val refreshedAt = plan.refreshedAt ?: return next
            if (next.isAfter(refreshedAt)) return next
            val frequency = plan.frequency
            val cron = plan.cronExpression
            if (frequency == DcaFrequency.CUSTOM && cron != null) {
                repeat(MAX_CRON_STEPS) {
                    if (next.isAfter(refreshedAt)) return next
                    next = CronUtils.getNextExecution(cron, next) ?: next.plus(FALLBACK_INTERVAL)
                }
                return next
            }
            val step = if (frequency != null) Duration.ofMinutes(frequency.intervalMinutes) else FALLBACK_INTERVAL
            val steps = Duration.between(next, refreshedAt).toMillis() / step.toMillis() + 1
            return next.plus(step.multipliedBy(steps))
        }

        /** A failed refresh does not disable the next one; each app start tries once. */
        internal fun isDue(plan: SharedPlan, now: Instant): Boolean =
            plan.liveUpdates && plan.isEnabled && !nextUnrefreshedPurchase(plan).isAfter(now)
    }
}
