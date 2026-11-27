package com.accbot.dca.presentation.screens

import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class DashboardSharedPlansTest {

    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    private fun plan(
        id: String,
        name: String = "Plan",
        isSandbox: Boolean = false,
        includesAmounts: Boolean = true,
        crypto: String = "BTC",
        fiat: String = "CZK",
        refreshFailed: Boolean = false
    ) = SharedPlan(
        shareId = id, sharedAt = t0, isSandbox = isSandbox, includesAmounts = includesAmounts,
        name = name, exchange = Exchange.COINMATE, exchangeName = "Coinmate", crypto = crypto, fiat = fiat,
        amount = if (includesAmounts) BigDecimal("500") else null, frequency = DcaFrequency.DAILY,
        cronExpression = null, strategy = DcaStrategy.Classic, isEnabled = true, createdAt = t0,
        startedAt = t0, statedAvgBuyPrice = BigDecimal("1000000"),
        trades = listOf(
            SharedPlanTrade(
                executedAt = t0, side = TransactionSide.BUY, price = BigDecimal("1000000"),
                cryptoAmount = if (includesAmounts) BigDecimal("0.001") else null,
                fiatAmount = if (includesAmounts) BigDecimal("1000") else null,
                fee = null, feeAsset = null
            )
        ),
        liveUpdates = true, refreshFailed = refreshFailed
    )

    @Test
    fun `shared cards only for current environment`() {
        val plans = listOf(
            plan("a", name = "Zeta"),
            plan("b", name = "Sandbox one", isSandbox = true),
            plan("c", name = "Alpha")
        )
        val prod = DashboardViewModel.sharedPlanCards(plans, isSandbox = false, prices = emptyMap())
        assertEquals(listOf("Alpha", "Zeta"), prod.map { it.plan.displayName })
        val sandbox = DashboardViewModel.sharedPlanCards(plans, isSandbox = true, prices = emptyMap())
        assertEquals(listOf("Sandbox one"), sandbox.map { it.plan.displayName })
    }

    @Test
    fun `hidden amounts give no performance`() {
        val prices = mapOf("BTC/CZK" to BigDecimal("2000000"))
        val cards = DashboardViewModel.sharedPlanCards(
            listOf(plan("a", includesAmounts = false), plan("b", includesAmounts = true)),
            isSandbox = false, prices = prices
        )
        val hidden = cards.first { it.plan.shareId == "a" }
        val shown = cards.first { it.plan.shareId == "b" }
        assertNull(hidden.performancePercent)
        assertEquals(BigDecimal("100.00"), shown.performancePercent)
        assertEquals(BigDecimal("2000000"), shown.currentPrice)
    }

    @Test
    fun `refresh failure and missing price are carried over`() {
        val card = DashboardViewModel.sharedPlanCards(
            listOf(plan("a", refreshFailed = true)), isSandbox = false, prices = emptyMap()
        ).single()
        assertNull(card.currentPrice)
        assertNull(card.performancePercent)
        assertEquals(true, card.refreshFailed)
        assertEquals(false, card.keyRemoved)
    }

    @Test
    fun `shared plans never change holdings`() {
        // Holdings are computed from Room transactions only; the shared list lives in a separate field.
        val state = DashboardUiState()
        val cards = DashboardViewModel.sharedPlanCards(listOf(plan("a")), false, emptyMap())
        val withShared = state.copy(sharedPlans = cards)
        assertEquals(state.holdings, withShared.holdings)
        assertEquals(state.missedPurchases, withShared.missedPurchases)
        assertEquals(state.activePlans, withShared.activePlans)
        assertNotNull(withShared.sharedPlans.singleOrNull())
    }
}
