package com.accbot.dca.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic guards that bound how often a single plan can fire. No Android deps.
 */
class BuySafetyPolicyTest {

    @Test
    fun `retries soon while under the network-retry cap`() {
        assertTrue(BuySafetyPolicy.shouldRetryAfterConfirmedFailure(0))
        assertTrue(BuySafetyPolicy.shouldRetryAfterConfirmedFailure(1))
        assertTrue(BuySafetyPolicy.shouldRetryAfterConfirmedFailure(2))
    }

    @Test
    fun `stops the 5-minute retry loop once the cap is reached`() {
        assertFalse(BuySafetyPolicy.shouldRetryAfterConfirmedFailure(BuySafetyPolicy.MAX_NETWORK_RETRIES))
        assertFalse(BuySafetyPolicy.shouldRetryAfterConfirmedFailure(99))
    }

    @Test
    fun `circuit breaker flags a plan that bought far more than expected`() {
        // incident: every-8h plan (3/day) executed 53 times in under 2h
        assertTrue(BuySafetyPolicy.isRunaway(buysLast24h = 53, expectedBuysPerDay = 3))
    }

    @Test
    fun `circuit breaker tolerates normal cadence and modest catch-up`() {
        assertFalse(BuySafetyPolicy.isRunaway(buysLast24h = 3, expectedBuysPerDay = 3))
        assertFalse(BuySafetyPolicy.isRunaway(buysLast24h = 6, expectedBuysPerDay = 3))
    }

    @Test
    fun `circuit breaker trips just above twice the expected daily count`() {
        assertTrue(BuySafetyPolicy.isRunaway(buysLast24h = 7, expectedBuysPerDay = 3))
    }

    @Test
    fun `expected buys per day derived from interval minutes`() {
        assertEquals(3, BuySafetyPolicy.expectedBuysPerDay(480))   // every 8h
        assertEquals(1, BuySafetyPolicy.expectedBuysPerDay(1440))  // daily
        assertEquals(1, BuySafetyPolicy.expectedBuysPerDay(0))     // guard against div-by-zero
    }
}
