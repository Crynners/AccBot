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

        /** A failed refresh does not disable the next one; each app start tries once. */
        internal fun isDue(plan: SharedPlan, now: Instant): Boolean {
            if (!plan.liveUpdates || !plan.isEnabled) return false
            val next = nextExpectedPurchase(plan)
            val refreshedAt = plan.refreshedAt
            return !next.isAfter(now) && (refreshedAt == null || refreshedAt.isBefore(next))
        }
    }
}
