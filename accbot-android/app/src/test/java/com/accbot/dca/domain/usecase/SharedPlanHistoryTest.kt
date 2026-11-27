package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanCodec
import com.accbot.dca.domain.share.SharedPlanTrade
import com.accbot.dca.exchange.ApiKeyPermissions
import com.accbot.dca.exchange.ReadOnlyExchangeClient
import com.accbot.dca.testing.FakeExchangeApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class SharedPlanHistoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val shareId = "3f2b8c1e-5d4a-4f6b-9c2d-7e8f9a0b1c2d"
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")
    private val now = Instant.parse("2026-03-01T09:00:00Z")
    private val overlap = Duration.ofHours(1)
    private val key = ExchangeCredentials(Exchange.COINMATE, "read", "secret", clientId = "1")

    private fun day(days: Long): Instant = t0.plus(Duration.ofDays(days))

    private fun ref(orderId: String) = SharedPlanCodec.orderRef("Coinmate", orderId)

    private fun trade(at: Instant, orderId: String) = SharedPlanTrade(
        executedAt = at, side = TransactionSide.BUY, price = BigDecimal("1000000"),
        cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), fee = null, feeAsset = null,
        ref = ref(orderId)
    )

    private fun fill(
        orderId: String,
        at: Instant,
        crypto: String = "0.001",
        fiat: String = "1000",
        side: String = "BUY"
    ) = HistoricalTrade(
        orderId = orderId, timestamp = at, crypto = "BTC", fiat = "CZK",
        cryptoAmount = BigDecimal(crypto), fiatAmount = BigDecimal(fiat), price = BigDecimal.ONE,
        fee = BigDecimal("1"), feeAsset = "CZK", side = side
    )

    private fun sharedPlan(
        trades: List<SharedPlanTrade>,
        sharedAt: Instant = day(10),
        fromExchange: Boolean = false,
        fullAccessKey: Boolean = false
    ) = SharedPlan(
        shareId = shareId, sharedAt = sharedAt, isSandbox = false, includesAmounts = true,
        name = "Spoření", exchange = Exchange.COINMATE, exchangeName = "Coinmate", crypto = "BTC", fiat = "CZK",
        amount = BigDecimal("500"), frequency = null, cronExpression = "0 9 * * *", strategy = DcaStrategy.Classic,
        isEnabled = true, createdAt = t0, startedAt = t0, statedAvgBuyPrice = null, trades = trades,
        liveUpdates = true, fullAccessKey = fullAccessKey, tradesFromExchange = fromExchange
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

    /** Exchange that serves [pages] in order and records the `since` of every request. */
    private class FakeExchange(vararg pages: List<HistoricalTrade>) {
        val requestedSince = mutableListOf<Instant?>()
        private val remaining = ArrayDeque(pages.toList())
        val api = FakeExchangeApi(tradeHistoryHandler = { _, _, since, _ ->
            requestedSince += since
            val page = remaining.removeFirstOrNull().orEmpty()
            TradeHistoryPage(page, hasMore = remaining.isNotEmpty())
        })
        val clients = ReadOnlyExchangeClient.Factory { _, _ -> api }
    }

    private suspend fun stored(plan: SharedPlan): SharedPlan {
        store.save(plan, key)
        return store.get(shareId)!!
    }

    private fun download(exchange: FakeExchange) = DownloadSharedPlanHistoryUseCase(store, exchange.clients)
    private fun refresh(exchange: FakeExchange) = RefreshSharedPlanUseCase(store, exchange.clients)

    // --- pure functions ---

    @Test
    fun `trades before plan start are dropped, trade at start is kept`() {
        val trades = RefreshSharedPlanUseCase.toSharedTrades(
            sharedPlan(emptyList()),
            listOf(fill("before", t0.minusSeconds(1)), fill("at-start", t0), fill("later", day(1)))
        )
        assertEquals(listOf(ref("at-start"), ref("later")), trades.map { it.ref })
        assertEquals(t0, trades.first().executedAt)
    }

    @Test
    fun `other pair, unknown side and zero amounts are dropped`() {
        val trades = RefreshSharedPlanUseCase.toSharedTrades(
            sharedPlan(emptyList()),
            listOf(
                fill("eth", day(1)).copy(crypto = "ETH"),
                fill("eur", day(1)).copy(fiat = "EUR"),
                fill("deposit", day(1), side = "DEPOSIT"),
                fill("zero-crypto", day(1), crypto = "0"),
                fill("zero-fiat", day(1), fiat = "0"),
                fill("", day(1)),
                fill("split", day(2), crypto = "0.0004", fiat = "400"),
                fill("split", day(2).plusSeconds(1), crypto = "0.0006", fiat = "600")
            )
        )
        assertEquals(1, trades.size)
        val only = trades.single()
        assertEquals(ref("split"), only.ref)
        assertEquals(day(2), only.executedAt)
        assertEquals(0, BigDecimal("0.001").compareTo(only.cryptoAmount))
        assertEquals(0, BigDecimal("1000").compareTo(only.fiatAmount))
    }

    // --- download ranges ---

    @Test
    fun `all history replaces snapshot trades without duplicates`() = runTest {
        // The snapshot names trades by order ID, the exchange reports the same buys by trade ID.
        stored(sharedPlan(listOf(trade(day(1), "order-1"), trade(day(2), "order-2"))))
        val exchange = FakeExchange(listOf(fill("trade-1", day(1)), fill("trade-2", day(2))))

        val result = download(exchange)(shareId, ImportRange.AllHistory, now = now)

        assertTrue(result is RefreshSharedPlanUseCase.Result.Updated)
        val plan = store.get(shareId)!!
        assertEquals(listOf(ref("trade-1"), ref("trade-2")), plan.trades.map { it.ref })
        assertEquals(listOf(t0.minus(overlap)), exchange.requestedSince)
    }

    @Test
    fun `since date keeps older trades and replaces the rest`() = runTest {
        stored(sharedPlan(listOf(trade(day(1), "order-1"), trade(day(3), "order-3"), trade(day(5), "order-5"))))
        val exchange = FakeExchange(
            listOf(
                fill("trade-early", day(2).minusSeconds(60)), // in the fetch overlap, before the date
                fill("trade-3", day(3)),
                fill("trade-4", day(4)),
                fill("trade-5", day(5))
            )
        )

        download(exchange)(shareId, ImportRange.SinceDate(day(2).toEpochMilli()), now = now)

        val plan = store.get(shareId)!!
        assertEquals(
            listOf(ref("order-1"), ref("trade-3"), ref("trade-4"), ref("trade-5")),
            plan.trades.map { it.ref }
        )
        assertEquals(listOf(day(2).minus(overlap)), exchange.requestedSince)
    }

    @Test
    fun `since date before plan start is clamped to start`() = runTest {
        stored(sharedPlan(listOf(trade(day(1), "order-1"))))
        val exchange = FakeExchange(listOf(fill("before-start", t0.minus(Duration.ofMinutes(30))), fill("trade-1", day(1))))

        download(exchange)(shareId, ImportRange.SinceDate(t0.minus(Duration.ofDays(30)).toEpochMilli()), now = now)

        assertEquals(listOf(t0.minus(overlap)), exchange.requestedSince)
        assertEquals(listOf(ref("trade-1")), store.get(shareId)!!.trades.map { it.ref })
    }

    @Test
    fun `since last purchase on exchange-based plan ignores sharedAt cutoff`() = runTest {
        stored(sharedPlan(listOf(trade(day(1), "trade-1")), sharedAt = day(10), fromExchange = true))
        val exchange = FakeExchange(listOf(fill("trade-1", day(1)), fill("trade-2", day(2))))

        val result = download(exchange)(shareId, ImportRange.SinceLastPurchase, now = now)

        assertEquals(1, (result as RefreshSharedPlanUseCase.Result.Updated).newTrades)
        assertEquals(listOf(ref("trade-1"), ref("trade-2")), store.get(shareId)!!.trades.map { it.ref })
    }

    @Test
    fun `qr plan without trades fetches since last purchase from plan start`() = runTest {
        stored(sharedPlan(emptyList(), sharedAt = day(10))) // key without trades = QR import
        val exchange = FakeExchange(listOf(fill("trade-1", day(1))))

        download(exchange)(shareId, ImportRange.SinceLastPurchase, now = now)

        assertEquals(listOf(t0.minus(overlap)), exchange.requestedSince)
        assertEquals(listOf(ref("trade-1")), store.get(shareId)!!.trades.map { it.ref })
    }

    @Test
    fun `exchange-based plan fetches from last trade even when sharedAt is later`() = runTest {
        stored(sharedPlan(listOf(trade(day(1), "trade-1")), sharedAt = day(10), fromExchange = true))
        val exchange = FakeExchange(listOf(fill("trade-1", day(1)), fill("trade-2", day(2)), fill("trade-5", day(5))))

        refresh(exchange)(shareId, now = now)

        assertEquals(3, store.get(shareId)!!.trades.size)
        assertEquals(listOf(day(1).minus(overlap)), exchange.requestedSince)
    }

    @Test
    fun `existing refresh behaviour for file snapshots is unchanged`() = runTest {
        val sharedAt = day(10)
        val lastSnapshotTrade = sharedAt.minus(Duration.ofMinutes(1))
        stored(sharedPlan(listOf(trade(day(1), "order-1"), trade(lastSnapshotTrade, "order-2")), sharedAt = sharedAt))
        val exchange = FakeExchange(
            listOf(
                fill("trade-2", lastSnapshotTrade),                            // snapshot trade under trade ID
                fill("trade-margin", sharedAt.plus(Duration.ofMinutes(2))),   // within SNAPSHOT_MARGIN
                fill("trade-new", sharedAt.plus(Duration.ofMinutes(10)))
            )
        )

        val result = refresh(exchange)(shareId, now = now)

        assertEquals(1, (result as RefreshSharedPlanUseCase.Result.Updated).newTrades)
        assertEquals(listOf(sharedAt.minus(overlap)), exchange.requestedSince)
        val plan = store.get(shareId)!!
        assertEquals(listOf(ref("order-1"), ref("order-2"), ref("trade-new")), plan.trades.map { it.ref })
        assertFalse(plan.tradesFromExchange)
    }

    @Test
    fun `download marks plan exchange-based and clears refreshFailed`() = runTest {
        val saved = stored(sharedPlan(listOf(trade(day(1), "order-1"))))
        store.update(saved.copy(refreshFailed = true))
        val exchange = FakeExchange(listOf(fill("trade-1", day(1))), listOf(fill("trade-2", day(2))))
        val progress = mutableListOf<ApiImportProgress>()

        download(exchange)(shareId, ImportRange.AllHistory, onProgress = { progress += it }, now = now)

        val plan = store.get(shareId)!!
        assertTrue(plan.tradesFromExchange)
        assertFalse(plan.refreshFailed)
        assertEquals(now, plan.refreshedAt)
        assertEquals(saved.latestSharedAt, plan.latestSharedAt)
        assertEquals(2, plan.trades.size)
        assertEquals(listOf(ApiImportProgress.Fetching(1, 1), ApiImportProgress.Fetching(2, 2)), progress)
        assertEquals(listOf(t0.minus(overlap), day(1)), exchange.requestedSince)
    }

    @Test
    fun `key that can trade is removed unless fullAccessKey`() = runTest {
        stored(sharedPlan(listOf(trade(day(1), "order-1"))))
        val exchange = FakeExchange(listOf(fill("trade-2", day(2))))
        exchange.api.keyPermissions = ApiKeyPermissions(canTrade = true, canWithdraw = false)

        val removed = download(exchange)(shareId, ImportRange.AllHistory, now = now)

        assertEquals(RefreshSharedPlanUseCase.Result.KeyNotReadOnly, removed)
        assertNull(store.credentials(shareId))
        assertTrue(exchange.requestedSince.isEmpty())

        stored(sharedPlan(listOf(trade(day(1), "order-1")), sharedAt = day(11), fullAccessKey = true))
        val kept = download(exchange)(shareId, ImportRange.AllHistory, now = now)

        assertTrue(kept is RefreshSharedPlanUseCase.Result.Updated)
        assertNotNull(store.credentials(shareId))
        assertEquals(listOf(ref("trade-2")), store.get(shareId)!!.trades.map { it.ref })
    }

    @Test
    fun `exchange error marks the plan failed and keeps its trades`() = runTest {
        stored(sharedPlan(listOf(trade(day(1), "order-1"))))
        val exchange = FakeExchange()
        exchange.api.tradeHistoryHandler = { _, _, _, _ -> throw java.io.IOException("offline") }

        val result = download(exchange)(shareId, ImportRange.AllHistory, now = now)

        assertTrue(result is RefreshSharedPlanUseCase.Result.Failed)
        val plan = store.get(shareId)!!
        assertTrue(plan.refreshFailed)
        assertEquals(listOf(ref("order-1")), plan.trades.map { it.ref })
    }
}
