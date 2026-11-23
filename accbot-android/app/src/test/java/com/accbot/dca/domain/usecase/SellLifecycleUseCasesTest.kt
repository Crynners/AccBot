package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.BinanceApi
import com.accbot.dca.exchange.CoinmateApi
import com.accbot.dca.exchange.OrderStatusResult
import com.accbot.dca.presentation.screens.plans.sell.LadderGenerator
import com.accbot.dca.testing.FakeExchangeApi
import com.accbot.dca.testing.buildInMemoryDb
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SellLifecycleUseCasesTest {

    private lateinit var db: DcaDatabase
    private lateinit var costBasisUseCase: CalculatePlanCostBasisUseCase
    private lateinit var pnlUseCase: CalculatePlanPnLUseCase
    private lateinit var validateUseCase: ValidateSellOrderUseCase

    private val dummyCreds = ExchangeCredentials(
        exchange = Exchange.COINMATE,
        apiKey = "key",
        apiSecret = "secret",
        clientId = "123"
    )
    private val t0: Instant = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        costBasisUseCase = CalculatePlanCostBasisUseCase(db)
        pnlUseCase = CalculatePlanPnLUseCase(db)
        validateUseCase = ValidateSellOrderUseCase(costBasisUseCase)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun createPlanWithBuy(
        boughtCrypto: String = "1.0",
        buyPrice: String = "1000000",
        allowSells: Boolean = true
    ): Long {
        val planId = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE,
                connectionId = 1L,
                crypto = "BTC",
                fiat = "CZK",
                amount = BigDecimal("1000"),
                frequency = DcaFrequency.DAILY,
                allowSells = allowSells
            )
        )
        val crypto = BigDecimal(boughtCrypto)
        val price = BigDecimal(buyPrice)
        db.transactionDao().insertTransaction(
            TransactionEntity(
                planId = planId,
                connectionId = 1L,
                exchange = Exchange.COINMATE,
                crypto = "BTC",
                fiat = "CZK",
                fiatAmount = crypto * price,
                cryptoAmount = crypto,
                price = price,
                fee = BigDecimal.ZERO,
                feeAsset = "CZK",
                status = TransactionStatus.COMPLETED,
                exchangeOrderId = "buy-1",
                executedAt = t0,
                side = TransactionSide.BUY
            )
        )
        return planId
    }

    private fun sellSuccess(orderId: String, amount: BigDecimal, price: BigDecimal): DcaResult.Success =
        DcaResult.Success(
            Transaction(
                planId = 0,
                exchange = Exchange.COINMATE,
                crypto = "BTC",
                fiat = "CZK",
                fiatAmount = BigDecimal.ZERO,
                cryptoAmount = BigDecimal.ZERO,
                price = price,
                fee = BigDecimal.ZERO,
                feeAsset = "",
                status = TransactionStatus.PENDING,
                exchangeOrderId = orderId,
                executedAt = t0.plusSeconds(3600),
                side = TransactionSide.SELL,
                limitPrice = price,
                requestedCryptoAmount = amount
            )
        )

    private fun cancelUseCase(api: FakeExchangeApi) = CancelSellOrderUseCase(
        database = db,
        credentialsProvider = { _, _ -> dummyCreds },
        apiProvider = { api },
        isSandboxProvider = { false },
        resolvePending = { 0 },
        verifyRetryDelayMs = 0L
    )

    private fun placeLimitUseCase(
        api: FakeExchangeApi,
        tradingEnabled: Boolean = true,
        timeoutMs: Long = 200L
    ) = PlaceLimitSellUseCase(
        database = db,
        credentialsProvider = { _, _ -> dummyCreds },
        apiProvider = { api },
        isSandboxProvider = { false },
        isTradingEnabledProvider = { tradingEnabled },
        validateSellOrderUseCase = validateUseCase,
        resolvePending = { 0 },
        orderTimeoutMs = timeoutMs
    )

    private fun placeLadderUseCase(
        api: FakeExchangeApi,
        tradingEnabled: Boolean = true,
        perOrderTimeoutMs: Long = 200L
    ) = PlaceLadderSellUseCase(
        database = db,
        credentialsProvider = { _, _ -> dummyCreds },
        apiProvider = { api },
        isSandboxProvider = { false },
        isTradingEnabledProvider = { tradingEnabled },
        validateSellOrderUseCase = validateUseCase,
        resolvePending = { 0 },
        perOrderTimeoutMs = perOrderTimeoutMs
    )

    // --- Priority 3: Cancel verification, terminal partial, reservation release, proceeds ---

    @Test
    fun `cancel verifies final fills, transitions to PARTIAL_CANCELLED, releases reservation and updates proceeds`() = runTest {
        val planId = createPlanWithBuy(boughtCrypto = "1.0", buyPrice = "1000000")
        val sellId = db.transactionDao().insertTransaction(
            TransactionEntity(
                planId = planId,
                connectionId = 1L,
                exchange = Exchange.COINMATE,
                crypto = "BTC",
                fiat = "CZK",
                fiatAmount = BigDecimal.ZERO,
                cryptoAmount = BigDecimal.ZERO, // Local DB still stale at 0 before cancel
                price = BigDecimal("2000000"),
                fee = BigDecimal.ZERO,
                feeAsset = "CZK",
                status = TransactionStatus.PENDING,
                exchangeOrderId = "sell-1",
                executedAt = t0.plusSeconds(3600),
                side = TransactionSide.SELL,
                limitPrice = BigDecimal("2000000"),
                requestedCryptoAmount = BigDecimal("0.8")
            )
        )

        // While open, 0.8 BTC is reserved -> only 0.2 BTC available
        assertEquals(0, BigDecimal("0.2").compareTo(costBasisUseCase(planId).available))

        val api = FakeExchangeApi(
            cancelOrderHandler = { _, _, _ -> Result.success(Unit) },
            orderStatusHandler = { _, _, _ ->
                // 0.3 BTC filled right before cancel landed; remaining 0.5 BTC cancelled
                OrderStatusResult(
                    status = TransactionStatus.PARTIAL_CANCELLED,
                    filledCryptoAmount = BigDecimal("0.3"),
                    filledFiatAmount = BigDecimal("600000.00"),
                    avgFillPrice = BigDecimal("2000000"),
                    fee = BigDecimal("2100.00"),
                    feeAsset = "CZK"
                )
            }
        )

        val result = cancelUseCase(api)(sellId)
        assertTrue(result.isSuccess)

        val updated = db.transactionDao().getTransactionById(sellId)!!
        assertEquals(TransactionStatus.PARTIAL_CANCELLED, updated.status)
        assertEquals(0, BigDecimal("0.3").compareTo(updated.cryptoAmount))
        assertEquals(0, BigDecimal("600000.00").compareTo(updated.fiatAmount))

        // No longer open (not in open sells, not polled, not blocking backup)
        assertTrue(db.transactionDao().observeOpenSellsForPlan(planId).first().isEmpty())
        assertEquals(0, db.transactionDao().countOpenSells())
        assertTrue(db.transactionDao().getResolvablePendingTransactions().isEmpty())
        assertFalse(db.transactionDao().hasOpenTransactions())

        // Unfilled 0.5 BTC reservation is released -> 0.7 BTC available!
        assertEquals(0, BigDecimal("0.7").compareTo(costBasisUseCase(planId).available))

        // Proceeds & PnL properly include the verified 600,000 CZK partial fill!
        val realizedPlan = BigDecimal(db.transactionDao().getRealizedFiatByPlan(planId))
        val realizedFiat = BigDecimal(db.transactionDao().getRealizedFiatByFiat("CZK"))
        assertEquals(0, BigDecimal("600000").compareTo(realizedPlan))
        assertEquals(0, BigDecimal("600000").compareTo(realizedFiat))

        val pnl = pnlUseCase(planId, BigDecimal("1500000"))
        assertEquals(0, BigDecimal("0.3").compareTo(pnl.totalSoldCrypto))
        assertEquals(0, BigDecimal("600000").compareTo(pnl.totalSoldFiat))
        assertEquals(0, BigDecimal("0.7").compareTo(pnl.currentCryptoHeld))
        // realizedPnL = 600,000 - (0.3 * 1,000,000) = 300,000
        assertEquals(0, BigDecimal("300000").compareTo(pnl.realizedPnL!!))
    }

    @Test
    fun `cancel keeps order open and fails if final fills cannot be verified`() = runTest {
        val planId = createPlanWithBuy()
        val sellId = db.transactionDao().insertTransaction(
            TransactionEntity(
                planId = planId,
                connectionId = 1L,
                exchange = Exchange.COINMATE,
                crypto = "BTC",
                fiat = "CZK",
                fiatAmount = BigDecimal.ZERO,
                cryptoAmount = BigDecimal.ZERO,
                price = BigDecimal("2000000"),
                fee = BigDecimal.ZERO,
                status = TransactionStatus.PENDING,
                exchangeOrderId = "sell-2",
                executedAt = t0.plusSeconds(3600),
                side = TransactionSide.SELL,
                limitPrice = BigDecimal("2000000"),
                requestedCryptoAmount = BigDecimal("0.5")
            )
        )
        val api = FakeExchangeApi(
            cancelOrderHandler = { _, _, _ -> Result.success(Unit) },
            orderStatusHandler = { _, _, _ -> null } // Verification failed (network blip)
        )

        val result = cancelUseCase(api)(sellId)
        assertTrue(result.isFailure)
        val row = db.transactionDao().getTransactionById(sellId)!!
        // Must remain open so resolver finishes verifying fills later
        assertEquals(TransactionStatus.PENDING, row.status)
        assertEquals(0, BigDecimal("0.5").compareTo(costBasisUseCase(planId).available))
    }

    @Test
    fun `cancel failure refreshes order that already filled in the race`() = runTest {
        val planId = createPlanWithBuy()
        val sellId = db.transactionDao().insertTransaction(
            TransactionEntity(
                planId = planId,
                connectionId = 1L,
                exchange = Exchange.COINMATE,
                crypto = "BTC",
                fiat = "CZK",
                fiatAmount = BigDecimal.ZERO,
                cryptoAmount = BigDecimal.ZERO,
                price = BigDecimal("2000000"),
                fee = BigDecimal.ZERO,
                status = TransactionStatus.PENDING,
                exchangeOrderId = "sell-3",
                executedAt = t0.plusSeconds(3600),
                side = TransactionSide.SELL,
                limitPrice = BigDecimal("2000000"),
                requestedCryptoAmount = BigDecimal("0.5")
            )
        )
        val api = FakeExchangeApi(
            cancelOrderHandler = { _, _, _ -> Result.failure(IllegalStateException("Order already filled")) },
            orderStatusHandler = { _, _, _ ->
                OrderStatusResult(
                    status = TransactionStatus.COMPLETED,
                    filledCryptoAmount = BigDecimal("0.5"),
                    filledFiatAmount = BigDecimal("1000000"),
                    avgFillPrice = BigDecimal("2000000"),
                    fee = BigDecimal.ZERO,
                    feeAsset = "CZK"
                )
            }
        )

        val result = cancelUseCase(api)(sellId)
        assertTrue(result.isFailure)
        val row = db.transactionDao().getTransactionById(sellId)!!
        assertEquals(TransactionStatus.COMPLETED, row.status)
        assertEquals(0, BigDecimal("0.5").compareTo(row.cryptoAmount))
        assertEquals(0, BigDecimal("1000000").compareTo(row.fiatAmount))
    }

    // --- Priority 2 & 4: PlaceLimitSellUseCase & PlaceLadderSellUseCase ---

    @Test
    fun `single sell rejects over-inventory before calling exchange`() = runTest {
        val planId = createPlanWithBuy(boughtCrypto = "0.5")
        val api = FakeExchangeApi()
        val result = placeLimitUseCase(api)(planId, BigDecimal("0.8"), BigDecimal("2000000"))

        assertTrue(result is PlaceSellResult.Rejected)
        assertEquals(0, api.limitSellCallCount)
        assertTrue((result as PlaceSellResult.Rejected).validationErrors.any {
            it is SellValidation.HardError.InsufficientInventory
        })
    }

    @Test
    fun `single sell distinguishes timeout Uncertain from confirmed Rejected`() = runTest {
        val planId = createPlanWithBuy(boughtCrypto = "1.0")
        val hangingApi = FakeExchangeApi(
            limitSellHandler = { _, _, _, _ ->
                delay(1_000L)
                sellSuccess("late", BigDecimal("0.2"), BigDecimal("2000000"))
            }
        )
        val timeoutResult = placeLimitUseCase(hangingApi, timeoutMs = 50L)(
            planId, BigDecimal("0.2"), BigDecimal("2000000")
        )
        assertTrue("Timeout must be Uncertain", timeoutResult is PlaceSellResult.Uncertain)
        val pending = db.transactionDao().observeOpenSellsForPlan(planId).first().single()
        assertEquals(null, pending.exchangeOrderId)
        assertEquals(0, BigDecimal("0.8").compareTo(costBasisUseCase(planId).available))
        assertTrue(db.transactionDao().hasUnidentifiedSell(planId))

        val rejectedApi = FakeExchangeApi(
            limitSellHandler = { _, _, _, _ ->
                DcaResult.Error("Insufficient funds", retryable = false, confirmedRejection = true)
            }
        )
        val rejectedResult = placeLimitUseCase(rejectedApi)(
            planId, BigDecimal("0.2"), BigDecimal("2000000")
        )
        assertTrue("Unreviewed sell must block a second request", rejectedResult is PlaceSellResult.Rejected)
        assertEquals(0, rejectedApi.limitSellCallCount)
        db.transactionDao().acknowledgeUnidentifiedSell(pending.id)
        assertTrue(placeLimitUseCase(rejectedApi)(
            planId, BigDecimal("0.2"), BigDecimal("2000000")
        ) is PlaceSellResult.Rejected)
        assertEquals(1, rejectedApi.limitSellCallCount)

        val unconfirmedApi = FakeExchangeApi(
            limitSellHandler = { _, _, _, _ ->
                DcaResult.Error("HTTP 502", retryable = true, confirmedRejection = false)
            }
        )
        val unconfirmedResult = placeLimitUseCase(unconfirmedApi)(
            planId, BigDecimal("0.2"), BigDecimal("2000000")
        )
        assertTrue("HTTP 502 must be Uncertain", unconfirmedResult is PlaceSellResult.Uncertain)
    }

    @Test
    fun `ladder timeout on leg 3 preserves legs 1 and 2, skips leg 4, and returns UncertainOutcome`() = runTest {
        val planId = createPlanWithBuy(boughtCrypto = "1.0")
        val orders = LadderGenerator.generate(
            totalAmount = BigDecimal("0.8"),
            from = BigDecimal("2000000"),
            to = BigDecimal("2600000"),
            count = 4,
            mode = LadderGenerator.AmountMode.EQUAL_CRYPTO
        )
        var call = 0
        val api = FakeExchangeApi(
            limitSellHandler = { _, _, amt, price ->
                call++
                when (call) {
                    1 -> sellSuccess("ord-1", amt, price)
                    2 -> sellSuccess("ord-2", amt, price)
                    3 -> {
                        delay(1_000L)
                        sellSuccess("ord-3-late", amt, price)
                    }
                    else -> sellSuccess("ord-$call", amt, price)
                }
            }
        )

        val result = placeLadderUseCase(api, perOrderTimeoutMs = 60L)(planId, orders)
        assertTrue("Expected UncertainOutcome, got $result", result is LadderResult.UncertainOutcome)
        val uncertain = result as LadderResult.UncertainOutcome
        assertEquals(2, uncertain.placedTxIds.size)
        assertEquals(2, uncertain.uncertainAtIndex)
        assertEquals(4, uncertain.totalCount)
        // Leg 4 was never attempted
        assertEquals(3, api.limitSellCallCount)
        // Both accepted orders and the third reservation are persisted.
        val openSells = db.transactionDao().observeOpenSellsForPlan(planId).first()
        assertEquals(3, openSells.size)
        assertEquals(1, openSells.count { it.exchangeOrderId == null })
    }

    @Test
    fun `ladder rejects count above MAX_ORDER_COUNT pre-flight without exchange calls`() = runTest {
        val planId = createPlanWithBuy(boughtCrypto = "10.0")
        val tooMany = (1..21).map {
            LadderOrder(BigDecimal("0.1"), BigDecimal(2_000_000 + it * 10_000))
        }
        val api = FakeExchangeApi()
        val result = placeLadderUseCase(api)(planId, tooMany)
        assertTrue(result is LadderResult.PartialFailure)
        assertEquals(0, api.limitSellCallCount)
        assertTrue((result as LadderResult.PartialFailure).validationErrors.any {
            it is SellValidation.HardError.LadderCountOutOfRange
        })
    }

    // --- Exchange status JSON mapping ---

    @Test
    fun `Coinmate and Binance map cancelled-with-fills to PARTIAL_CANCELLED and open partial to PARTIAL`() {
        val coinmatePartialCancelled = CoinmateApi.parseOrderStatusJson(
            """{"error":false,"data":{"status":"CANCELLED","originalAmount":"0.5","remainingAmount":"0.3","avgPrice":"2000000"}}"""
        )
        assertNotNull(coinmatePartialCancelled)
        assertEquals(TransactionStatus.PARTIAL_CANCELLED, coinmatePartialCancelled!!.status)
        assertEquals(0, BigDecimal("0.2").compareTo(coinmatePartialCancelled.filledCryptoAmount))
        assertEquals(0, BigDecimal("400000.00").compareTo(coinmatePartialCancelled.filledFiatAmount))

        val coinmateOpenPartial = CoinmateApi.parseOrderStatusJson(
            """{"error":false,"data":{"status":"PARTIALLY_FILLED","originalAmount":"0.5","remainingAmount":"0.3","avgPrice":"2000000"}}"""
        )
        assertEquals(TransactionStatus.PARTIAL, coinmateOpenPartial!!.status)

        val binanceCanceledWithFills = BinanceApi.parseOrderStatusJson(
            """{"status":"CANCELED","executedQty":"0.25","cummulativeQuoteQty":"500000"}"""
        )
        assertEquals(TransactionStatus.PARTIAL_CANCELLED, binanceCanceledWithFills!!.status)
        assertEquals(0, BigDecimal("0.25").compareTo(binanceCanceledWithFills.filledCryptoAmount))

        val binancePendingCancel = BinanceApi.parseOrderStatusJson(
            """{"status":"PENDING_CANCEL","executedQty":"0.1","cummulativeQuoteQty":"200000"}"""
        )
        // PENDING_CANCEL is not terminal yet -> must stay open PARTIAL
        assertEquals(TransactionStatus.PARTIAL, binancePendingCancel!!.status)

        val binanceCanceledZeroFills = BinanceApi.parseOrderStatusJson(
            """{"status":"CANCELED","executedQty":"0","cummulativeQuoteQty":"0"}"""
        )
        assertEquals(TransactionStatus.CANCELLED, binanceCanceledZeroFills!!.status)
    }
}
