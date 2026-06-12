package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CalculatePlanCostBasisUseCaseTest {

    private val t0: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private fun ts(daysOffset: Long): Instant = t0.plusSeconds(daysOffset * 86_400)

    private fun buy(
        id: Long,
        crypto: BigDecimal,
        price: BigDecimal,
        executedAt: Instant,
        status: TransactionStatus = TransactionStatus.COMPLETED
    ): TransactionEntity = TransactionEntity(
        id = id,
        planId = 1,
        connectionId = 1,
        exchange = Exchange.COINMATE,
        crypto = "BTC",
        fiat = "CZK",
        fiatAmount = crypto * price,
        cryptoAmount = crypto,
        price = price,
        fee = BigDecimal.ZERO,
        feeAsset = "CZK",
        status = status,
        exchangeOrderId = "buy-$id",
        executedAt = executedAt,
        side = TransactionSide.BUY
    )

    private fun sell(
        id: Long,
        crypto: BigDecimal,
        price: BigDecimal,
        executedAt: Instant,
        status: TransactionStatus = TransactionStatus.COMPLETED,
        requested: BigDecimal? = null
    ): TransactionEntity = TransactionEntity(
        id = id,
        planId = 1,
        connectionId = 1,
        exchange = Exchange.COINMATE,
        crypto = "BTC",
        fiat = "CZK",
        fiatAmount = crypto * price,
        cryptoAmount = crypto,
        price = price,
        fee = BigDecimal.ZERO,
        feeAsset = "CZK",
        status = status,
        exchangeOrderId = "sell-$id",
        executedAt = executedAt,
        side = TransactionSide.SELL,
        requestedCryptoAmount = requested ?: crypto,
        limitPrice = price
    )

    @Test
    fun `prazdny plan vraci available nula a avg null`() {
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(emptyList())
        assertEquals(0, BigDecimal.ZERO.compareTo(result.available))
        assertNull(result.weightedAvgPrice)
        assertTrue(result.perBuyDetail.isEmpty())
        assertEquals(0, BigDecimal.ZERO.compareTo(result.deficit))
    }

    @Test
    fun `jeden buy bez sells - available a avg jsou z buyu`() {
        val txs = listOf(buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)))
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("1").compareTo(result.available))
        assertEquals(0, BigDecimal("1000000").compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `dva buys, sell konzumuje cheapest first`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            buy(2, BigDecimal("1"), BigDecimal("2000000"), ts(1)),
            sell(3, BigDecimal("0.5"), BigDecimal("2500000"), ts(2))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        // 0.5 BTC zkonzumovano z 1M buyu, zbyva 0.5 BTC @ 1M + 1 BTC @ 2M
        assertEquals(0, BigDecimal("1.5").compareTo(result.available))
        // weighted avg = (0.5 * 1M + 1 * 2M) / 1.5 = ~1666666.67
        val expected = BigDecimal("1666666.66666667")
        assertEquals(0, expected.compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `novy levny buy po sellu neovlivni avg pro driv prodane`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("0.5"), BigDecimal("2000000"), ts(1)),
            buy(3, BigDecimal("0.5"), BigDecimal("800000"), ts(2))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        // Sell @ts(1) sees only buy 1. Consumes 0.5 from 1M.
        // Remaining: 0.5 @ 1M + 0.5 @ 800k = avg (500k + 400k) / 1.0 = 900k
        assertEquals(0, BigDecimal("1.0").compareTo(result.available))
        assertEquals(0, BigDecimal("900000").compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `PENDING sell rezervuje cheapest`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(
                2, BigDecimal.ZERO, BigDecimal("3000000"), ts(1),
                status = TransactionStatus.PENDING, requested = BigDecimal("0.5")
            )
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("0.5").compareTo(result.available))
        assertEquals(0, BigDecimal("1000000").compareTo(result.weightedAvgPrice!!))
    }

    @Test
    fun `PARTIAL sell pouziva requested ne filled`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(
                2, BigDecimal("0.2"), BigDecimal("3000000"), ts(1),
                status = TransactionStatus.PARTIAL, requested = BigDecimal("0.5")
            )
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("0.5").compareTo(result.available))
    }

    @Test
    fun `FAILED sell se ignoruje`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            sell(
                2, BigDecimal("0.5"), BigDecimal("3000000"), ts(1),
                status = TransactionStatus.FAILED
            )
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("1").compareTo(result.available))
    }

    @Test
    fun `negative inventory - sells presahly buys, deficit non-zero`() {
        val txs = listOf(
            buy(1, BigDecimal("0.5"), BigDecimal("1000000"), ts(0)),
            sell(2, BigDecimal("1"), BigDecimal("2000000"), ts(1))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal.ZERO.compareTo(result.available))
        assertNull(result.weightedAvgPrice)
        assertEquals(0, BigDecimal("0.5").compareTo(result.deficit))
    }

    @Test
    fun `tie na cene - starsi executedAt napred`() {
        val txs = listOf(
            buy(1, BigDecimal("1"), BigDecimal("1000000"), ts(0)),
            buy(2, BigDecimal("1"), BigDecimal("1000000"), ts(1)),
            sell(3, BigDecimal("0.5"), BigDecimal("2000000"), ts(2))
        )
        val result = CalculatePlanCostBasisUseCase.computeCostBasis(txs)
        assertEquals(0, BigDecimal("1.5").compareTo(result.available))
        val cheap1 = result.perBuyDetail.firstOrNull { it.transactionId == 1L }
        assertEquals(0, BigDecimal("0.5").compareTo(cheap1?.remaining ?: BigDecimal.ZERO))
    }
}
