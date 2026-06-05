package com.accbot.dca.testing

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

/**
 * Validates that the Robolectric + in-memory Room harness works before we build on it.
 */
@RunWith(RobolectricTestRunner::class)
class HarnessSanityTest {

    private lateinit var db: DcaDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `inserts and reads back a plan`() = runTest {
        val id = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE,
                connectionId = 6,
                crypto = "BTC",
                fiat = "CZK",
                amount = BigDecimal("50"),
                frequency = DcaFrequency.EVERY_8_HOURS
            )
        )

        val plans = db.dcaPlanDao().getEnabledPlans()

        assertEquals(1, plans.size)
        assertEquals(id, plans.first().id)
        assertEquals(6L, plans.first().connectionId)
    }
}
