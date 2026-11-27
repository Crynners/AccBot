package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanCodec
import com.accbot.dca.domain.share.SharedPlanTrade
import com.accbot.dca.exchange.ReadOnlyExchangeClient
import com.accbot.dca.testing.FakeExchangeApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RefreshSharedPlansUseCaseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val shareId = "3f2b8c1e-5d4a-4f6b-9c2d-7e8f9a0b1c2d"
    private val otherId = "4a2b8c1e-5d4a-4f6b-9c2d-7e8f9a0b1c2d"
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")
    private val key = ExchangeCredentials(Exchange.COINMATE, "read", "secret", clientId = "1")

    private fun buy(at: Instant, orderId: String) = SharedPlanTrade(
        executedAt = at, side = TransactionSide.BUY, price = BigDecimal("1000000"),
        cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), fee = null, feeAsset = null,
        ref = SharedPlanCodec.orderRef("Coinmate", orderId)
    )

    private fun plan(
        id: String = shareId,
        frequency: DcaFrequency? = DcaFrequency.DAILY,
        cron: String? = null,
        trades: List<SharedPlanTrade> = listOf(buy(t0, "o1")),
        isEnabled: Boolean = true,
        isSandbox: Boolean = false,
        liveUpdates: Boolean = true,
        refreshedAt: Instant? = null
    ) = SharedPlan(
        shareId = id, sharedAt = t0, isSandbox = isSandbox, includesAmounts = true,
        name = "Spoření", exchange = Exchange.COINMATE, exchangeName = "Coinmate", crypto = "BTC", fiat = "CZK",
        amount = BigDecimal("500"), frequency = frequency, cronExpression = cron, strategy = DcaStrategy.Classic,
        isEnabled = isEnabled, createdAt = t0, startedAt = t0, statedAvgBuyPrice = null, trades = trades,
        liveUpdates = liveUpdates, refreshedAt = refreshedAt, tradesFromExchange = true
    )

    private class FakeVault : SharedPlanStore.KeyVault {
        private val keys = mutableMapOf<String, ExchangeCredentials>()
        override fun get(shareId: String) = keys[shareId]
        override fun put(shareId: String, credentials: ExchangeCredentials) { keys[shareId] = credentials }
        override fun remove(shareId: String) { keys.remove(shareId) }
    }

    private val store by lazy {
        SharedPlanStore(tmp.newFolder("shared"), object : SharedPlanStore.ShareIdRegistry {
            override fun shareIdFor(planId: Long, isSandbox: Boolean) = shareId
        }, FakeVault())
    }

    private var tradeHistoryCalls = 0
    private var newFills: List<HistoricalTrade> = emptyList()
    private var failing = false
    private val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
        tradeHistoryCalls++
        yield()
        if (failing) throw java.io.IOException("offline")
        TradeHistoryPage(newFills, hasMore = false)
    })
    private val clients = ReadOnlyExchangeClient.Factory { _, _ -> api }
    private val refreshSingle by lazy { RefreshSharedPlanUseCase(store, clients) }
    private val useCase by lazy { RefreshSharedPlansUseCase(store, refreshSingle) }

    private fun fill(orderId: String, at: Instant) = HistoricalTrade(
        orderId = orderId, timestamp = at, crypto = "BTC", fiat = "CZK",
        cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), price = BigDecimal.ONE,
        fee = BigDecimal("1"), feeAsset = "CZK", side = "BUY"
    )

    @Test
    fun `daily plan is due a day after last buy and not before`() {
        val p = plan()
        val next = RefreshSharedPlansUseCase.nextExpectedPurchase(p)
        assertEquals(t0.plus(Duration.ofDays(1)), next)
        assertFalse(RefreshSharedPlansUseCase.isDue(p, next.minusSeconds(1)))
        assertTrue(RefreshSharedPlansUseCase.isDue(p, next))
    }

    @Test
    fun `custom cron uses next cron time`() {
        val p = plan(frequency = DcaFrequency.CUSTOM, cron = "*/10 * * * *")
        assertEquals(t0.plus(Duration.ofMinutes(10)), RefreshSharedPlansUseCase.nextExpectedPurchase(p))
    }

    @Test
    fun `unknown frequency and invalid cron fall back to a day`() {
        assertEquals(t0.plus(Duration.ofDays(1)), RefreshSharedPlansUseCase.nextExpectedPurchase(plan(frequency = null)))
        val bad = plan(frequency = DcaFrequency.CUSTOM, cron = "not a cron")
        assertEquals(t0.plus(Duration.ofDays(1)), RefreshSharedPlansUseCase.nextExpectedPurchase(bad))
    }

    @Test
    fun `paused plan and plan without key are never due`() {
        val late = t0.plus(Duration.ofDays(30))
        assertFalse(RefreshSharedPlansUseCase.isDue(plan(isEnabled = false), late))
        assertFalse(RefreshSharedPlansUseCase.isDue(plan(liveUpdates = false), late))
    }

    @Test
    fun `plan refreshed after expected purchase is not due`() {
        val next = t0.plus(Duration.ofDays(1))
        val late = next.plus(Duration.ofHours(2))
        assertFalse(RefreshSharedPlansUseCase.isDue(plan(refreshedAt = next.plusSeconds(1)), late))
        assertTrue(RefreshSharedPlansUseCase.isDue(plan(refreshedAt = next.minusSeconds(1)), late))
    }

    @Test
    fun `failed refresh tries once per call and stays due`() = runTest {
        store.save(plan(), key)
        failing = true
        val now = t0.plus(Duration.ofDays(3))

        val results = useCase.refreshDue(isSandbox = false, now = now)

        assertEquals(1, tradeHistoryCalls)
        assertTrue(results.getValue(shareId) is RefreshSharedPlanUseCase.Result.Failed)
        val after = store.get(shareId)!!
        assertTrue(after.refreshFailed)
        assertTrue(RefreshSharedPlansUseCase.isDue(after, now))
    }

    @Test
    fun `refreshDue skips other environment and plans not due`() = runTest {
        store.save(plan(isSandbox = true), key)
        store.save(plan(id = otherId), key)
        val early = t0.plusSeconds(60)
        assertTrue(useCase.refreshDue(isSandbox = false, now = early).isEmpty())
        assertEquals(0, tradeHistoryCalls)

        // Both plans are due; only the one of the requested environment is refreshed.
        val late = t0.plus(Duration.ofDays(2))
        assertEquals(setOf(otherId), useCase.refreshDue(isSandbox = false, now = late).keys)
        assertEquals(setOf(shareId), useCase.refreshDue(isSandbox = true, now = late).keys)

        assertEquals(setOf(otherId), useCase.refreshAll(isSandbox = false, now = early).keys)
    }

    @Test
    fun `concurrent refresh and download do not duplicate trades`() = runTest {
        store.save(plan(), key)
        newFills = listOf(fill("o2", t0.plus(Duration.ofDays(1))))
        val download = DownloadSharedPlanHistoryUseCase(store, clients)
        val now = t0.plus(Duration.ofDays(2))

        val results = listOf(
            async { refreshSingle(shareId, now) },
            async { download(shareId, ImportRange.SinceLastPurchase, now = now) }
        ).awaitAll()

        val newTrades = results.sumOf { (it as RefreshSharedPlanUseCase.Result.Updated).newTrades }
        assertEquals(1, newTrades)
        assertEquals(2, store.get(shareId)!!.trades.size)
    }

    @Test
    fun `early refresh before the purchase does not stop later refreshes`() {
        val d1 = t0.plus(Duration.ofDays(1))
        val p = plan(refreshedAt = d1.plusSeconds(5 * 60))
        assertFalse(RefreshSharedPlansUseCase.isDue(p, d1.plus(Duration.ofHours(1))))
        assertTrue(RefreshSharedPlansUseCase.isDue(p, t0.plus(Duration.ofDays(2)).plusSeconds(30 * 60)))
    }

    @Test
    fun `skipped purchases keep the plan due once per expected purchase`() {
        val d3 = t0.plus(Duration.ofDays(3))
        // Refreshed on day 3 at 09:30 with no new trade: day 1..3 purchases are covered.
        val p = plan(refreshedAt = d3.plusSeconds(30 * 60))
        assertFalse(RefreshSharedPlansUseCase.isDue(p, d3.plus(Duration.ofHours(10))))
        assertTrue(RefreshSharedPlansUseCase.isDue(p, t0.plus(Duration.ofDays(4))))
    }

    @Test
    fun `cron plan rolls forward past a refresh without new trade`() {
        val p = plan(frequency = DcaFrequency.CUSTOM, cron = "*/10 * * * *", refreshedAt = t0.plus(Duration.ofMinutes(25)))
        assertFalse(RefreshSharedPlansUseCase.isDue(p, t0.plus(Duration.ofMinutes(29))))
        assertTrue(RefreshSharedPlansUseCase.isDue(p, t0.plus(Duration.ofMinutes(30))))
    }

    @Test
    fun `import saved while a refresh runs is not overwritten by the refresh`() = runTest {
        store.save(plan(), key)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowApi = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
            started.complete(Unit)
            release.await()
            TradeHistoryPage(emptyList(), hasMore = false)
        })
        val slowRefresh = RefreshSharedPlanUseCase(store, ReadOnlyExchangeClient.Factory { _, _ -> slowApi })
        val refreshing = async { slowRefresh(shareId, t0.plus(Duration.ofDays(2))) }
        started.await()

        val newKey = key.copy(apiKey = "newer-key")
        val importing = async {
            store.withPlanLock(shareId) {
                store.save(plan().copy(sharedAt = t0.plus(Duration.ofDays(1))), newKey)
            }
        }
        yield()
        release.complete(Unit)
        refreshing.await()
        importing.await()

        val after = store.get(shareId)!!
        assertEquals(t0.plus(Duration.ofDays(1)), after.latestSharedAt)
        assertEquals("newer-key", store.credentials(shareId)!!.apiKey)
    }
}
