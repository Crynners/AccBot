package com.accbot.dca.data.local

import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.testing.buildInMemoryDb
import kotlinx.coroutines.flow.first
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
class FilteredTransactionsByPlanTest {

    private lateinit var db: DcaDatabase

    @Before fun setUp() { db = buildInMemoryDb() }
    @After fun tearDown() = db.close()

    private suspend fun tx(planId: Long) = db.transactionDao().insertTransaction(
        TransactionEntity(
            planId = planId, exchange = Exchange.COINMATE, connectionId = planId,
            crypto = "BTC", fiat = "CZK", fiatAmount = BigDecimal("50"),
            cryptoAmount = BigDecimal("0.00003"), price = BigDecimal("1500000"),
            fee = BigDecimal("0.17"), status = TransactionStatus.COMPLETED,
            exchangeOrderId = "o$planId-${System.nanoTime()}", executedAt = Instant.now()
        )
    )

    @Test
    fun `filters transactions by planId, leaving other plans out`() = runTest {
        tx(2); tx(2); tx(4)  // two BTC/CZK plans share the same pair

        val all = db.transactionDao().getFilteredTransactions(null, null, null, null).first()
        val onlyPlan2 = db.transactionDao().getFilteredTransactions(null, null, null, 2L).first()

        assertEquals(3, all.size)
        assertEquals(2, onlyPlan2.size)
        assertEquals(setOf(2L), onlyPlan2.map { it.planId }.toSet())
    }
}
