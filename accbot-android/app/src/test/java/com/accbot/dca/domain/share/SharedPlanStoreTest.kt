package com.accbot.dca.domain.share

import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionSide
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SharedPlanStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val shareId = "3f2b8c1e-5d4a-4f6b-9c2d-7e8f9a0b1c2d"
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    private class FakeVault : SharedPlanStore.KeyVault {
        private val keys = mutableMapOf<String, ExchangeCredentials>()
        override fun get(shareId: String) = keys[shareId]
        override fun put(shareId: String, credentials: ExchangeCredentials) { keys[shareId] = credentials }
        override fun remove(shareId: String) { keys.remove(shareId) }
    }

    private fun newStore() = SharedPlanStore(tmp.newFolder("shared"), object : SharedPlanStore.ShareIdRegistry {
        override fun shareIdFor(planId: Long, isSandbox: Boolean) = shareId
    }, FakeVault())

    private fun trades(count: Int) = (1..count).map {
        SharedPlanTrade(
            executedAt = t0.plusSeconds(it * 10L), side = TransactionSide.BUY, price = BigDecimal("1000000"),
            cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), fee = null, feeAsset = null,
            ref = "r$it"
        )
    }

    private fun sharedPlan(
        sharedAtSeconds: Long,
        trades: List<SharedPlanTrade>,
        name: String = "Spoření",
        fromExchange: Boolean = false
    ) = SharedPlan(
        shareId = shareId, sharedAt = t0.plusSeconds(sharedAtSeconds), isSandbox = false, includesAmounts = true,
        name = name, exchange = Exchange.COINMATE, exchangeName = "Coinmate", crypto = "BTC", fiat = "CZK",
        amount = BigDecimal("500"), frequency = null, cronExpression = "0 9 * * *", strategy = DcaStrategy.Classic,
        isEnabled = true, createdAt = t0, startedAt = t0, statedAvgBuyPrice = null, trades = trades,
        tradesFromExchange = fromExchange
    )

    private fun key(secret: String) = ExchangeCredentials(Exchange.COINMATE, "read", secret, clientId = "1")

    @Test
    fun `qr re-import keeps downloaded history and updates key`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, trades(3), fromExchange = true), key("old"))
        val qr = sharedPlan(200, emptyList(), name = "Nový název")
        store.save(qr, key("new"))

        val stored = store.refresh().single()
        assertEquals(3, stored.trades.size)
        assertEquals("Nový název", stored.displayName)
        assertEquals(t0.plusSeconds(200), stored.sharedAt)
        assertTrue(stored.tradesFromExchange)
        assertEquals(key("new"), store.credentials(shareId))
    }

    @Test
    fun `qr after file snapshot keeps snapshot trades and its sharedAt`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, trades(2)))
        store.save(sharedPlan(200, emptyList()), key("k"))

        val stored = store.refresh().single()
        assertEquals(2, stored.trades.size)
        assertEquals(t0.plusSeconds(100), stored.sharedAt)
        assertFalse(stored.tradesFromExchange)
        assertTrue(stored.liveUpdates)
        assertEquals(key("k"), store.credentials(shareId))
    }

    @Test
    fun `file after qr replaces history`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, emptyList()), key("k"))
        store.save(sharedPlan(200, trades(5)))

        val stored = store.refresh().single()
        assertEquals(5, stored.trades.size)
        assertFalse(stored.tradesFromExchange)
    }

    @Test
    fun `new qr share is marked as exchange-based`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, emptyList()), key("k"))

        assertTrue(store.refresh().single().tradesFromExchange)
    }
}
