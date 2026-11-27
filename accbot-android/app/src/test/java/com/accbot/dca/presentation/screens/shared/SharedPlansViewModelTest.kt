package com.accbot.dca.presentation.screens.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.accbot.dca.R
import com.accbot.dca.data.local.Bip39WordList
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanAccessDto
import com.accbot.dca.domain.share.SharedPlanCodec
import com.accbot.dca.domain.share.SharedPlanTrade
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.BackupCryptoUseCase
import com.accbot.dca.domain.usecase.DownloadSharedPlanHistoryUseCase
import com.accbot.dca.domain.usecase.ImportRange
import com.accbot.dca.domain.usecase.ImportSharedPlanUseCase
import com.accbot.dca.domain.usecase.RefreshSharedPlanUseCase
import com.accbot.dca.exchange.ReadOnlyExchangeClient
import com.accbot.dca.testing.FakeExchangeApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SharedPlansViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val shareId = "3f2b8c1e-5d4a-4f6b-9c2d-7e8f9a0b1c2d"
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")
    private val key = ExchangeCredentials(Exchange.COINMATE, "ro-key", "ro-secret", clientId = "42")

    private class FakeVault : SharedPlanStore.KeyVault {
        private val keys = mutableMapOf<String, ExchangeCredentials>()
        override fun get(shareId: String) = keys[shareId]
        override fun put(shareId: String, credentials: ExchangeCredentials) { keys[shareId] = credentials }
        override fun remove(shareId: String) { keys.remove(shareId) }
    }

    private lateinit var store: SharedPlanStore

    /** History the fake exchange returns; [requests] counts every history request. */
    @Volatile
    private var fills: List<HistoricalTrade> = emptyList()
    @Volatile
    private var requests = 0
    private val firstRequest = CompletableDeferred<Unit>()
    private val api = FakeExchangeApi(tradeHistoryHandler = { _, _, _, _ ->
        requests++
        firstRequest.complete(Unit)
        TradeHistoryPage(fills, hasMore = false)
    })
    private val clients = ReadOnlyExchangeClient.Factory { _, _ -> api }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        store = SharedPlanStore(tmp.newFolder("shared"), object : SharedPlanStore.ShareIdRegistry {
            override fun shareIdFor(planId: Long, isSandbox: Boolean) = shareId
        }, FakeVault())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val crypto by lazy { BackupCryptoUseCase(Bip39WordList(context)) }

    private fun listViewModel() =
        SharedPlansViewModel(store, ImportSharedPlanUseCase(store, crypto, clients), context)

    private fun detailViewModel(): SharedPlanDetailViewModel {
        val offline = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
        return SharedPlanDetailViewModel(
            store,
            RefreshSharedPlanUseCase(store, clients),
            DownloadSharedPlanHistoryUseCase(store, clients),
            MarketDataService(offline, Gson()),
            context
        )
    }

    /** A live QR: compact encrypted share without trades, like the sender's app shows it. */
    private fun liveQr(password: String): String {
        val entity = DcaPlanEntity(
            id = 7, exchange = Exchange.COINMATE, connectionId = 3, name = "Spoření", crypto = "BTC", fiat = "CZK",
            amount = BigDecimal("500"), frequency = DcaFrequency.DAILY, strategy = DcaStrategy.Classic, createdAt = t0
        )
        val tx = TransactionEntity(
            id = 1, planId = 7, connectionId = 3, exchange = Exchange.COINMATE, crypto = "BTC", fiat = "CZK",
            fiatAmount = BigDecimal("500"), cryptoAmount = BigDecimal("0.0005"), price = BigDecimal("1000000"),
            fee = BigDecimal("2.5"), feeAsset = "CZK", status = TransactionStatus.COMPLETED,
            exchangeOrderId = "order-1", executedAt = t0, side = TransactionSide.BUY
        )
        val snapshot = SharedPlanCodec.qrSnapshot(
            SharedPlanCodec.createSnapshot(entity, listOf(tx), shareId, isSandbox = false, includeAmounts = true)
        )
        val access = SharedPlanAccessDto(
            exchange = "COINMATE", apiKey = key.apiKey, apiSecret = key.apiSecret, clientId = key.clientId,
            verifiedReadOnly = false
        )
        return SharedPlanCodec.encodeShare(snapshot, access, password, crypto::encrypt, compact = true)
    }

    private fun sharedPlan(trades: List<SharedPlanTrade>) = SharedPlan(
        shareId = shareId, sharedAt = t0.plus(Duration.ofDays(10)), isSandbox = false, includesAmounts = true,
        name = "Spoření", exchange = Exchange.COINMATE, exchangeName = "Coinmate", crypto = "BTC", fiat = "CZK",
        amount = BigDecimal("500"), frequency = null, cronExpression = "0 9 * * *", strategy = DcaStrategy.Classic,
        isEnabled = true, createdAt = t0, startedAt = t0, statedAvgBuyPrice = null, trades = trades, liveUpdates = true
    )

    private fun trade(at: Instant, orderId: String) = SharedPlanTrade(
        executedAt = at, side = TransactionSide.BUY, price = BigDecimal("1000000"),
        cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), fee = null, feeAsset = null,
        ref = SharedPlanCodec.orderRef("Coinmate", orderId)
    )

    private fun fill(orderId: String, at: Instant) = HistoricalTrade(
        orderId = orderId, timestamp = at, crypto = "BTC", fiat = "CZK",
        cryptoAmount = BigDecimal("0.001"), fiatAmount = BigDecimal("1000"), price = BigDecimal("1000000"),
        fee = BigDecimal("1"), feeAsset = "CZK", side = "BUY"
    )

    private suspend fun SharedPlanDetailViewModel.await(
        predicate: (SharedPlanDetailViewModel.UiState) -> Boolean
    ): SharedPlanDetailViewModel.UiState = withTimeout(10_000L) { uiState.first(predicate) }

    @Test
    fun `scanned qr asks for password then opens the plan`() = runBlocking {
        val vm = listViewModel()
        val opened = async(start = CoroutineStart.UNDISPATCHED) { vm.opened.first() }

        vm.importScanned(liveQr("heslo123"))
        val waiting = withTimeout(10_000L) { vm.uiState.first { it.passwordRequired } }
        assertFalse(waiting.wrongPassword)

        vm.submitPassword("heslo123")

        assertEquals(shareId, withTimeout(10_000L) { opened.await() })
        val stored = store.get(shareId)!!
        assertTrue(stored.liveUpdates)
        assertTrue(stored.trades.isEmpty())
        assertFalse(vm.uiState.value.passwordRequired)
    }

    @Test
    fun `scanned garbage shows not-a-share error`() = runBlocking {
        val vm = listViewModel()
        val message = async(start = CoroutineStart.UNDISPATCHED) { vm.messages.first() }

        vm.importScanned("https://example.com/not-a-plan")

        assertEquals(context.getString(R.string.shared_plans_error_not_share), withTimeout(10_000L) { message.await() })
        assertFalse(vm.uiState.value.importing)
        assertNull(store.get(shareId))
    }

    @Test
    fun `plan without trades opens history dialog with from-start range instead of refreshing`() = runBlocking {
        store.save(sharedPlan(emptyList()), key)
        val vm = detailViewModel()

        vm.load(shareId)
        val state = vm.await { it.historyDialog }

        assertEquals(ImportRange.AllHistory, state.historyRange)
        assertFalse(state.refreshing)
        assertEquals(0, requests)
    }

    @Test
    fun `download success shows result and updates plan`() = runBlocking {
        store.save(sharedPlan(listOf(trade(t0.plus(Duration.ofDays(1)), "order-1"))), key)
        val vm = detailViewModel()
        vm.load(shareId)
        // The live plan with trades refreshes on open; let that settle before downloading.
        withTimeout(10_000L) { firstRequest.await() }
        vm.await { !it.refreshing }
        fills = listOf(fill("trade-1", t0.plus(Duration.ofDays(1))), fill("trade-2", t0.plus(Duration.ofDays(2))))

        vm.openHistoryDialog()
        assertEquals(ImportRange.SinceLastPurchase, vm.uiState.value.historyRange)
        vm.setHistoryRange(ImportRange.AllHistory)
        vm.downloadHistory()
        val state = vm.await { it.downloadResult != null }

        assertEquals(ApiImportResultState.Success(imported = 2, skipped = 0), state.downloadResult)
        assertFalse(state.historyDialog)
        assertFalse(state.downloading)
        assertEquals(2, state.plan!!.trades.size)
        assertEquals(2, store.get(shareId)!!.trades.size)

        vm.consumeDownloadResult()
        assertNull(vm.uiState.value.downloadResult)
    }

    @Test
    fun `too long history shows its own message`() = runBlocking {
        store.save(sharedPlan(emptyList()), key)
        // A full page that never moves past the cursor: the range cannot be downloaded whole.
        api.tradeHistoryHandler = { _, _, since, _ ->
            requests++
            TradeHistoryPage(listOf(fill("stuck", since!!)), hasMore = true)
        }
        val vm = detailViewModel()
        vm.load(shareId)
        vm.await { it.historyDialog }

        vm.downloadHistory()
        val state = vm.await { it.downloadResult != null }

        assertEquals(
            ApiImportResultState.Error(context.getString(R.string.shared_plan_history_too_long)),
            state.downloadResult
        )
        assertTrue(store.get(shareId)!!.trades.isEmpty())
    }
}
