package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.testing.FakeExchangeApi
import com.accbot.dca.testing.buildInMemoryDb
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ImportTradeHistoryConnectionIdTest {

    private lateinit var db: DcaDatabase
    private lateinit var useCase: ImportTradeHistoryUseCase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        useCase = ImportTradeHistoryUseCase(db.transactionDao(), db.dcaPlanDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `imported transactions inherit the plan's connectionId`() = runTest {
        val planId = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = 6,
                crypto = "BTC", fiat = "CZK",
                amount = BigDecimal("50"), frequency = DcaFrequency.EVERY_8_HOURS
            )
        )
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            TradeHistoryPage(
                listOf(
                    HistoricalTrade(
                        orderId = "999", timestamp = Instant.parse("2026-05-28T19:31:00Z"),
                        crypto = "BTC", fiat = "CZK", cryptoAmount = BigDecimal("0.00003"),
                        fiatAmount = BigDecimal("50"), price = BigDecimal("1500000"),
                        fee = BigDecimal("0.17"), feeAsset = "CZK", side = "BUY"
                    )
                ),
                hasMore = false
            )
        })

        useCase.importFromApi(api, planId, "BTC", "CZK", Exchange.COINMATE).toList()

        val tx = db.transactionDao().getByExchangeOrderIdAndConnection("999", 6)
        assertEquals(6L, tx?.connectionId)
    }

    @Test
    fun `since last purchase starts after the latest filled buy and falls back to all history`() = runTest {
        val planId = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = 6,
                crypto = "BTC", fiat = "CZK",
                amount = BigDecimal("50"), frequency = DcaFrequency.EVERY_8_HOURS
            )
        )
        assertNull(useCase.sinceDateFor(ImportRange.SinceLastPurchase, planId))

        val lastBuy = Instant.parse("2026-11-20T08:00:00Z")
        fun tx(at: Instant, status: TransactionStatus, side: TransactionSide) = TransactionEntity(
            planId = planId, exchange = Exchange.COINMATE, crypto = "BTC", fiat = "CZK",
            fiatAmount = BigDecimal("50"), cryptoAmount = BigDecimal("0.00003"), price = BigDecimal("1500000"),
            fee = BigDecimal.ZERO, status = status, side = side, executedAt = at
        )
        db.transactionDao().insertTransactions(
            listOf(
                tx(Instant.parse("2026-11-01T08:00:00Z"), TransactionStatus.COMPLETED, TransactionSide.BUY),
                tx(lastBuy, TransactionStatus.COMPLETED, TransactionSide.BUY),
                // Neither a failed buy nor a sell counts as a purchase.
                tx(Instant.parse("2026-11-21T08:00:00Z"), TransactionStatus.FAILED, TransactionSide.BUY),
                tx(Instant.parse("2026-11-22T08:00:00Z"), TransactionStatus.COMPLETED, TransactionSide.SELL)
            )
        )

        assertEquals(lastBuy, useCase.lastPurchaseAt(planId))
        assertEquals(
            lastBuy.plus(ImportTradeHistoryUseCase.LAST_PURCHASE_MARGIN),
            useCase.sinceDateFor(ImportRange.SinceLastPurchase, planId)
        )
        assertNull(useCase.sinceDateFor(ImportRange.AllHistory, planId))
        assertEquals(Instant.ofEpochMilli(1_000L), useCase.sinceDateFor(ImportRange.SinceDate(1_000L), planId))
    }
}
