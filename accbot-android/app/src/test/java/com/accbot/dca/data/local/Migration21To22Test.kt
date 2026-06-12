package com.accbot.dca.data.local

import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.testing.buildInMemoryDb
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

/**
 * Verifies the backfill SQL in MIGRATION_21_22: NULL connectionId rows inherit the plan's
 * connection, but only when the plan actually has one (> 0).
 */
@RunWith(RobolectricTestRunner::class)
class Migration21To22Test {

    private lateinit var db: DcaDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun tx(planId: Long, orderId: String) = db.transactionDao().insertTransaction(
        TransactionEntity(
            planId = planId, exchange = Exchange.COINMATE, connectionId = null,
            crypto = "BTC", fiat = "CZK", fiatAmount = BigDecimal("50"),
            cryptoAmount = BigDecimal("0.00003"), price = BigDecimal("1500000"),
            fee = BigDecimal("0.17"), status = TransactionStatus.COMPLETED,
            exchangeOrderId = orderId, executedAt = Instant.now()
        )
    )

    @Test
    fun `backfills connectionId from plan, leaves rows without a real connection NULL`() = runTest {
        val planWithConn = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = 6, crypto = "BTC", fiat = "CZK",
                amount = BigDecimal("50"), frequency = DcaFrequency.EVERY_8_HOURS
            )
        )
        val planNoConn = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE, connectionId = 0, crypto = "BTC", fiat = "CZK",
                amount = BigDecimal("50"), frequency = DcaFrequency.EVERY_8_HOURS
            )
        )
        tx(planWithConn, "orphan-6")
        tx(planNoConn, "orphan-0")

        DcaDatabase.MIGRATION_21_22.migrate(db.openHelper.writableDatabase)

        assertEquals(6L, db.transactionDao().getByExchangeOrderId("orphan-6")?.connectionId)
        assertNull(db.transactionDao().getByExchangeOrderId("orphan-0")?.connectionId)
    }
}
