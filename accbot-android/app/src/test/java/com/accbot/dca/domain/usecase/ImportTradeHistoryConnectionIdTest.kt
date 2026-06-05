package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.testing.FakeExchangeApi
import com.accbot.dca.testing.buildInMemoryDb
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}
