package com.accbot.dca.domain.share

import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionSide
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun newStore(directory: java.io.File = tmp.newFolder("shared")) = SharedPlanStore(directory, object : SharedPlanStore.ShareIdRegistry {
        override fun shareIdFor(planId: Long, isSandbox: Boolean) = shareId
    }, FakeVault())

    private fun trades(count: Int) = (1..count).map {
        SharedPlanTrade(
            executedAt = t0.plusSeconds(it * 10L), side = TransactionSide.BUY, price = BigDecimal("1000000"),
            cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), fee = null, feeAsset = null,
            ref = "r$it"
        )
    }

    private fun hiddenTrades(count: Int) = trades(count).map { it.copy(cryptoAmount = null, fiatAmount = null) }

    private fun sharedPlan(
        sharedAtSeconds: Long,
        trades: List<SharedPlanTrade>,
        name: String = "Spoření",
        fromExchange: Boolean = false,
        includesAmounts: Boolean = true
    ) = SharedPlan(
        shareId = shareId, sharedAt = t0.plusSeconds(sharedAtSeconds), isSandbox = false, includesAmounts = includesAmounts,
        name = name, exchange = Exchange.COINMATE, exchangeName = "Coinmate", crypto = "BTC", fiat = "CZK",
        amount = if (includesAmounts) BigDecimal("500") else null, frequency = null, cronExpression = "0 9 * * *", strategy = DcaStrategy.Classic,
        isEnabled = true, createdAt = t0, startedAt = t0, statedAvgBuyPrice = null, trades = trades,
        tradesFromExchange = fromExchange
    )

    private fun key(secret: String) = ExchangeCredentials(Exchange.COINMATE, "read", secret, clientId = "1")

    @Test
    fun `qr re-import keeps downloaded history and updates key`() = runTest {
        val store = newStore()
        val refreshed = t0.plusSeconds(150)
        store.save(sharedPlan(100, emptyList()), key("old"))
        store.update(
            store.refresh().single().copy(trades = trades(3), refreshedAt = refreshed, exchangeHistoryFrom = t0)
        )
        store.save(sharedPlan(200, emptyList(), name = "Nový název"), key("new"))

        val stored = store.refresh().single()
        assertEquals(3, stored.trades.size)
        assertEquals("Nový název", stored.displayName)
        assertEquals(t0.plusSeconds(100), stored.sharedAt)
        assertEquals(t0.plusSeconds(200), stored.latestSharedAt)
        assertEquals(refreshed, stored.refreshedAt)
        assertTrue(stored.tradesFromExchange)
        assertEquals(t0, stored.exchangeHistoryFrom)
        assertEquals(key("new"), store.credentials(shareId))
    }

    @Test
    fun `older file after qr is outdated and keeps key`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, trades(2)))
        store.save(sharedPlan(200, emptyList()), key("k"))
        val again = store.save(sharedPlan(100, trades(2)))

        assertTrue(again is SharedPlanStore.SaveResult.Outdated)
        val stored = store.refresh().single()
        assertEquals(2, stored.trades.size)
        assertTrue(stored.liveUpdates)
        assertEquals(key("k"), store.credentials(shareId))
    }

    @Test
    fun `older qr after newer qr is outdated`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, trades(2)))
        store.save(sharedPlan(300, emptyList(), name = "Newer"), key("new"))
        val older = store.save(sharedPlan(200, emptyList(), name = "Older"), key("old"))

        assertTrue(older is SharedPlanStore.SaveResult.Outdated)
        assertEquals("Newer", store.refresh().single().displayName)
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
        store.update(store.refresh().single().copy(trades = trades(2)))
        assertTrue(store.refresh().single().tradesFromExchange)
        store.save(sharedPlan(200, trades(5)))

        val stored = store.refresh().single()
        assertEquals(5, stored.trades.size)
        assertFalse(stored.tradesFromExchange)
        assertFalse(stored.liveUpdates)
        assertNull(store.credentials(shareId))
    }

    @Test
    fun `new qr share is marked as exchange-based`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, emptyList()), key("k"))

        assertTrue(store.refresh().single().tradesFromExchange)
    }

    // --- final review ---

    @Test
    fun `large history is stored compact and survives reload`() = runTest {
        val directory = tmp.newFolder("large")
        val store = newStore(directory)
        store.save(sharedPlan(100, emptyList()), key("k"))
        store.update(store.refresh().single().copy(trades = trades(25_000)))

        assertEquals(25_000, store.refresh().single().trades.size)
        val file = java.io.File(directory, "$shareId.json")
        assertFalse(file.readText().contains('\n'))
        // A new store (app restart) reads the same file.
        assertEquals(25_000, newStore(directory).refresh().single().trades.size)
    }

    @Test
    fun `plan that would not load back is never written and the previous file stays`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, trades(2)))

        // Trades without amounts in a plan that claims amounts cannot be decoded.
        val broken = store.refresh().single().copy(trades = hiddenTrades(3))
        val error = runCatching { store.update(broken) }.exceptionOrNull()

        assertTrue(error is SharedPlanException)
        assertEquals(2, store.refresh().single().trades.size)
        assertFalse(tmp.root.walk().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `qr scan over a snapshot with hidden amounts starts an exchange-based plan`() = runTest {
        val store = newStore()
        store.save(sharedPlan(100, hiddenTrades(2), includesAmounts = false))
        store.save(sharedPlan(200, emptyList()), key("k"))

        val stored = store.refresh().single()
        assertTrue(stored.liveUpdates)
        assertTrue(stored.includesAmounts)
        assertTrue(stored.trades.isEmpty())
        assertTrue(stored.tradesFromExchange)
        assertEquals(t0.plusSeconds(200), stored.sharedAt)
        assertEquals(t0.plusSeconds(200), stored.latestSharedAt)
        assertNull(stored.exchangeHistoryFrom)
        assertEquals(key("k"), store.credentials(shareId))
    }

    @Test
    fun `revoked key is remembered across restarts until a new key is saved`() = runTest {
        val directory = tmp.newFolder("revoked")
        val store = newStore(directory)
        store.save(sharedPlan(100, trades(2)), key("k"))
        assertFalse(store.refresh().single().keyRevoked)

        store.dropKey(shareId)
        val dropped = newStore(directory).refresh().single()
        assertTrue(dropped.keyRevoked)
        assertFalse(dropped.liveUpdates)

        // A plain snapshot keeps the notice; a new key clears it.
        store.save(sharedPlan(150, trades(3)))
        assertTrue(store.refresh().single().keyRevoked)
        store.save(sharedPlan(200, emptyList()), key("new"))
        val renewed = store.refresh().single()
        assertFalse(renewed.keyRevoked)
        assertTrue(renewed.liveUpdates)
    }
}
