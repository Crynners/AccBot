package com.accbot.dca.presentation.screens.portfolio

import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.sharedPlanFixture
import com.accbot.dca.domain.share.SharedPlanTrade
import org.junit.Assert.assertEquals
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
        assertEquals(2, sharedInput.size)
        assertTrue(sharedInput.all { it.planId == 0L })
        assertEquals(listOf(tx(1)), chartTransactionsFor(PairPage.Plan(1, "P1", "BTC", "CZK"), room, sharedPlans))
        assertEquals(TransactionSide.BUY, sharedInput.first().side)
    }
}
