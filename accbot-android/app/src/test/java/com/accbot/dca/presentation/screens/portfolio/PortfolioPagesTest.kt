package com.accbot.dca.presentation.screens.portfolio

import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.sharedPlanFixture
import com.accbot.dca.domain.share.toChartTransactions
import com.accbot.dca.domain.share.SharedPlanTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PortfolioPagesTest {

    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    private fun plan(id: Long, crypto: String = "BTC", fiat: String = "CZK") = DcaPlanEntity(
        id = id, exchange = Exchange.COINMATE, name = "P$id", crypto = crypto, fiat = fiat,
        amount = BigDecimal("100"), frequency = DcaFrequency.DAILY
    )

    private fun tx(planId: Long, fiat: String = "CZK") = TransactionEntity(
        id = planId, planId = planId, exchange = Exchange.COINMATE, crypto = "BTC", fiat = fiat,
        fiatAmount = BigDecimal("100"), cryptoAmount = BigDecimal("0.001"), price = BigDecimal("100000"),
        fee = BigDecimal.ZERO, status = TransactionStatus.COMPLETED, executedAt = t0
    )

    private fun shared(id: String, sandbox: Boolean = false, amounts: Boolean = true, empty: Boolean = false): SharedPlan =
        sharedPlanFixture(
            shareId = id, isSandbox = sandbox, includesAmounts = amounts,
            trades = if (empty) emptyList<SharedPlanTrade>() else sharedPlanFixture().trades
        )

    @Test
    fun `shared pages come last and only for current environment`() {
        val pages = buildPages(
            dbPlans = listOf(plan(1), plan(2, "ETH", "EUR")),
            shared = listOf(
                shared("a"), shared("b", sandbox = true), shared("c", amounts = false), shared("d", empty = true)
            ),
            isSandbox = false
        )
        assertEquals(
            listOf("plan:1", "plan:2", "shared:a"),
            pages.map { pageIdOf(it) }
        )
        assertTrue(pages.last() is PairPage.Shared)
        val sandbox = buildPages(emptyList(), listOf(shared("a"), shared("b", sandbox = true)), isSandbox = true)
        assertEquals(listOf("shared:b"), sandbox.map { pageIdOf(it) })
    }

    @Test
    fun `shared chart input is buy only`() {
        val sp = shared("a")
        val input = chartTransactionsFor(PairPage.Shared("a", "Friend", "BTC", "CZK"), emptyList(), listOf(sp))
        assertEquals(1, input.size)
        assertTrue(input.all { it.side == TransactionSide.BUY })
        assertEquals(2, sp.toChartTransactions().size)
    }

    @Test
    fun `selection follows the page id when pages change`() {
        val p1 = PairPage.Plan(1, "P1", "BTC", "CZK")
        val old = listOf(p1, PairPage.Shared("a", "A", "BTC", "CZK"))
        val new = listOf(p1, PairPage.Shared("z", "Z", "BTC", "CZK"), PairPage.Shared("a", "A", "BTC", "CZK"))
        assertEquals(2, selectionIndexAfterRebuild(old, 1, new))
        assertEquals(0, selectionIndexAfterRebuild(old, 1, listOf(p1)))
    }

    @Test
    fun `aggregate page is unaffected by shared plans`() {
        val db = listOf(plan(1), plan(2))
        val sharedPlans = listOf(shared("a"))
        val pages = buildPages(db, sharedPlans, isSandbox = false)
        assertEquals(1, pages.filterIsInstance<PairPage.Aggregate>().size)
        assertEquals(PairPage.Aggregate("CZK"), pages.first())

        val room = listOf(tx(1), tx(2))
        val aggInput = chartTransactionsFor(PairPage.Aggregate("CZK"), room, sharedPlans)
        assertEquals(room, aggInput)
        assertTrue(aggInput.none { it.planId == 0L })

        val sharedPage = pages.last() as PairPage.Shared
        val sharedInput = chartTransactionsFor(sharedPage, room, sharedPlans)
        assertEquals(1, sharedInput.size)
        assertTrue(sharedInput.all { it.planId == 0L })
        assertEquals(listOf(tx(1)), chartTransactionsFor(PairPage.Plan(1, "P1", "BTC", "CZK"), room, sharedPlans))
        assertEquals(TransactionSide.BUY, sharedInput.first().side)
    }

    @Test
    fun `selected shared chart reloads only when its trades change`() {
        val before = listOf(shared("a"), shared("b"))
        val page = PairPage.Shared("a", "Friend", "BTC", "CZK")
        val moreTrades = before[0].copy(trades = before[0].trades + before[0].trades.first().copy(executedAt = t0))

        // Same page list (id, name, pair), but the selected plan got a trade.
        assertEquals(buildPages(emptyList(), before, false), buildPages(emptyList(), listOf(moreTrades, before[1]), false))
        assertTrue(selectedSharedTradesChanged(page, before, listOf(moreTrades, before[1])))
        // Only a refresh time changed, another plan changed, or an own page is selected.
        assertFalse(selectedSharedTradesChanged(page, before, listOf(before[0].copy(refreshedAt = t0), before[1])))
        assertFalse(selectedSharedTradesChanged(page, before, listOf(before[0], before[1].copy(trades = emptyList()))))
        assertFalse(selectedSharedTradesChanged(PairPage.Plan(1, "P1", "BTC", "CZK"), before, listOf(moreTrades)))
    }
}
