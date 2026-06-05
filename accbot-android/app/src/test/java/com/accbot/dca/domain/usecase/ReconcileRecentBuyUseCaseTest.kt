package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.testing.FakeExchangeApi
import com.accbot.dca.testing.buildInMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ReconcileRecentBuyUseCaseTest {

    private lateinit var db: DcaDatabase
    private lateinit var useCase: ReconcileRecentBuyUseCase
    private val since: Instant = Instant.parse("2026-05-28T19:31:00Z")

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        useCase = ReconcileRecentBuyUseCase(db.transactionDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun plan(): DcaPlanEntity {
        val id = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = 6,
                crypto = "BTC", fiat = "CZK",
                amount = BigDecimal("50"), frequency = DcaFrequency.EVERY_8_HOURS
            )
        )
        return db.dcaPlanDao().getPlanById(id)!!
    }

    private fun buyTrade(orderId: String, at: Instant, fiat: BigDecimal = BigDecimal("50")) =
        HistoricalTrade(
            orderId = orderId, timestamp = at, crypto = "BTC", fiat = "CZK",
            cryptoAmount = BigDecimal("0.00003"), fiatAmount = fiat,
            price = BigDecimal("1500000"), fee = BigDecimal("0.17"), feeAsset = "CZK", side = "BUY"
        )

    @Test
    fun `Found when a matching buy exists after attemptStart and not yet recorded`() = runTest {
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            TradeHistoryPage(listOf(buyTrade("111", since.plusSeconds(10))), hasMore = false)
        })

        val result = useCase(api, plan(), since, expectedFiat = BigDecimal("50"))

        assertTrue(result is ReconcileResult.Found)
        assertEquals("111", (result as ReconcileResult.Found).trade.orderId)
    }

    @Test
    fun `NotFound when exchange reports no buys after attemptStart`() = runTest {
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            TradeHistoryPage(emptyList(), hasMore = false)
        })

        val result = useCase(api, plan(), since, expectedFiat = BigDecimal("50"))

        assertEquals(ReconcileResult.NotFound, result)
    }

    @Test
    fun `Unknown when the reconciliation query itself fails - caller must stay conservative`() = runTest {
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            throw java.io.IOException("history timed out")
        })

        val result = useCase(api, plan(), since, expectedFiat = BigDecimal("50"))

        assertEquals(ReconcileResult.Unknown, result)
    }

    @Test
    fun `excludes orders already recorded in the DB - no double counting`() = runTest {
        val p = plan()
        db.transactionDao().insertTransaction(
            TransactionEntity(
                planId = p.id, exchange = Exchange.COINMATE, connectionId = 6,
                crypto = "BTC", fiat = "CZK", fiatAmount = BigDecimal("50"),
                cryptoAmount = BigDecimal("0.00003"), price = BigDecimal("1500000"),
                fee = BigDecimal("0.17"), status = TransactionStatus.COMPLETED,
                exchangeOrderId = "111", executedAt = since.plusSeconds(10)
            )
        )
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            TradeHistoryPage(listOf(buyTrade("111", since.plusSeconds(10))), hasMore = false)
        })

        val result = useCase(api, p, since, expectedFiat = BigDecimal("50"))

        assertEquals(ReconcileResult.NotFound, result)
    }

    @Test
    fun `ignores buys that happened before attemptStart`() = runTest {
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            TradeHistoryPage(listOf(buyTrade("old", since.minusSeconds(120))), hasMore = false)
        })

        val result = useCase(api, plan(), since, expectedFiat = BigDecimal("50"))

        assertEquals(ReconcileResult.NotFound, result)
    }
}
