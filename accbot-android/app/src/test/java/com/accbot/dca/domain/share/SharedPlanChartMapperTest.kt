package com.accbot.dca.domain.share

import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SharedPlanChartMapperTest {

    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    @Test
    fun `hidden amounts give no chart transactions`() {
        assertTrue(sharedPlanFixture(includesAmounts = false).toChartTransactions().isEmpty())
    }

    @Test
    fun `mapper keeps amounts, prices and order`() {
        val txs = sharedPlanFixture().toChartTransactions()
        assertEquals(2, txs.size)
        val buy = txs[0]
        assertEquals(TransactionSide.BUY, buy.side)
        assertEquals(TransactionStatus.COMPLETED, buy.status)
        assertEquals(t0, buy.executedAt)
        assertEquals(BigDecimal("1000000"), buy.price)
        assertEquals(BigDecimal("0.0005"), buy.cryptoAmount)
        assertEquals(BigDecimal("500"), buy.fiatAmount)
        assertEquals(BigDecimal("1.5"), buy.fee)
        assertEquals("BTC", buy.crypto)
        assertEquals("CZK", buy.fiat)
        assertEquals(Exchange.KRAKEN, buy.exchange)
        assertEquals(0L, buy.planId)
        assertEquals(0L, buy.id)
        assertEquals(TransactionSide.SELL, txs[1].side)
        assertEquals(BigDecimal.ZERO, txs[1].fee)
    }

    @Test
    fun `unknown exchange falls back to a placeholder`() {
        assertEquals(Exchange.COINMATE, sharedPlanFixture(exchange = null).toChartTransactions().first().exchange)
    }

    @Test
    fun `trades without amounts are skipped`() {
        val p = sharedPlanFixture(trades = listOf(
            SharedPlanTrade(t0, TransactionSide.BUY, BigDecimal.ONE, null, null, null, null)
        ))
        assertTrue(p.toChartTransactions().isEmpty())
    }
}
