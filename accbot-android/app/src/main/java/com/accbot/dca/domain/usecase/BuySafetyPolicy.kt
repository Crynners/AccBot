package com.accbot.dca.domain.usecase

/**
 * Pure guard-rails that bound how often a single DCA plan can place orders, independent of
 * the root cause. Two layers:
 *  - [shouldRetryAfterConfirmedFailure] caps the 5-minute network-retry loop.
 *  - [isRunaway] is a circuit breaker: if a plan has bought far more than its schedule
 *    allows in the last 24h, something is wrong and the plan should be auto-disabled.
 */
object BuySafetyPolicy {

    /** Max times a single slot will retry in 5-minute steps before giving up to the next interval. */
    const val MAX_NETWORK_RETRIES = 3

    /** A plan may legitimately buy up to this multiple of its expected daily count (catch-up). */
    const val RUNAWAY_FACTOR = 2

    fun shouldRetryAfterConfirmedFailure(currentRetryCount: Int): Boolean =
        currentRetryCount < MAX_NETWORK_RETRIES

    fun expectedBuysPerDay(intervalMinutes: Long): Int {
        if (intervalMinutes <= 0) return 1
        return (MINUTES_PER_DAY / intervalMinutes).coerceAtLeast(1).toInt()
    }

    fun isRunaway(buysLast24h: Int, expectedBuysPerDay: Int): Boolean {
        val cap = maxOf(expectedBuysPerDay * RUNAWAY_FACTOR, expectedBuysPerDay + 2)
        return buysLast24h > cap
    }

    private const val MINUTES_PER_DAY = 1440L
}
