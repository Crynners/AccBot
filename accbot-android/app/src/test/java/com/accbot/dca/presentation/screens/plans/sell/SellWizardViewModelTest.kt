package com.accbot.dca.presentation.screens.plans.sell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import com.accbot.dca.domain.usecase.CalculatePlanCostBasisUseCase
import com.accbot.dca.domain.usecase.CalculatePlanPnLUseCase
import com.accbot.dca.domain.usecase.PlaceLadderSellUseCase
import com.accbot.dca.domain.usecase.PlaceLimitSellUseCase
import com.accbot.dca.domain.usecase.SellValidation
import com.accbot.dca.domain.usecase.ValidateSellOrderUseCase
import com.accbot.dca.testing.FakeExchangeApi
import com.accbot.dca.testing.buildInMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SellWizardViewModelTest {

    private lateinit var context: Context
    private lateinit var db: DcaDatabase
    private val t0: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val dummyCreds = ExchangeCredentials(
        exchange = Exchange.COINMATE,
        apiKey = "key",
        apiSecret = "secret",
        clientId = "123"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun SellWizardViewModel.awaitIdle(): SellWizardViewModel.UiState =
        withTimeout(5_000L) {
            uiState.first { !it.initializing && !it.validating && !it.submitting && !it.resolvingUncertain }
        }

    private suspend fun SellWizardViewModel.awaitState(
        predicate: (SellWizardViewModel.UiState) -> Boolean
    ): SellWizardViewModel.UiState = withTimeout(5_000L) {
        uiState.first(predicate)
    }

    private suspend fun seedPlan(
        boughtCrypto: String = "1.0",
        buyPrice: String = "1000000",
        name: String = "BTC Plan"
    ): Long {
        val planId = db.dcaPlanDao().insertPlan(
            DcaPlanEntity(
                exchange = Exchange.COINMATE,
                connectionId = 1L,
                name = name,
                crypto = "BTC",
                fiat = "CZK",
                amount = BigDecimal("1000"),
                frequency = DcaFrequency.DAILY,
                allowSells = true
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
                exchangeOrderId = "buy-$planId",
                executedAt = t0,
                side = TransactionSide.BUY
            )
        )
        return planId
    }

    private fun createViewModel(
        api: FakeExchangeApi,
        orderTimeoutMs: Long = 100L,
        onResolvePending: () -> Int = { 0 }
    ): SellWizardViewModel {
        val costBasis = CalculatePlanCostBasisUseCase(db)
        val pnl = CalculatePlanPnLUseCase(db)
        val validate = ValidateSellOrderUseCase(costBasis)
        val placeSingle = PlaceLimitSellUseCase(
            database = db,
            credentialsProvider = { _, _ -> dummyCreds },
            apiProvider = { api },
            isSandboxProvider = { false },
            isTradingEnabledProvider = { true },
            validateSellOrderUseCase = validate,
            resolvePending = { onResolvePending() },
            orderTimeoutMs = orderTimeoutMs
        )
        val placeLadder = PlaceLadderSellUseCase(
            database = db,
            credentialsProvider = { _, _ -> dummyCreds },
            apiProvider = { api },
            isSandboxProvider = { false },
            isTradingEnabledProvider = { true },
            validateSellOrderUseCase = validate,
            resolvePending = { onResolvePending() },
            perOrderTimeoutMs = orderTimeoutMs
        )
        return SellWizardViewModel(
            context = context,
            validateSellOrderUseCase = validate,
            placeLimitSellUseCase = placeSingle,
            placeLadderSellUseCase = placeLadder,
            calculatePlanPnLUseCase = pnl,
            calculatePlanCostBasisUseCase = costBasis,
            database = db,
            credentialsProvider = { _, _ -> dummyCreds },
            apiProvider = { api },
            isSandboxProvider = { false },
            minOrderProvider = { _, _, _ -> BigDecimal("50") },
            resolvePending = { onResolvePending() }
        )
    }

    private fun sellSuccess(orderId: String, amount: BigDecimal, price: BigDecimal) =
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

    // --- Priority 1: Reopen resets stale confirmation, inputs, and capacity ---

    @Test
    fun `reopening wizard resets step, inputs, and reloads fresh capacity`() = runBlocking {
        val planId = seedPlan(boughtCrypto = "1.0")
        val api = FakeExchangeApi(price = BigDecimal("1500000"))
        val vm = createViewModel(api)

        vm.openSession(planId, "session-1")
        vm.awaitIdle()
        assertEquals(0, BigDecimal("1.0").compareTo(vm.uiState.value.availableToSell))

        // Enter inputs and move to CONFIRM step
        vm.setAmount("0.4")
        vm.setPrice("2000000")
        vm.awaitIdle()
        assertTrue(vm.uiState.value.canProceed)
        vm.proceedToConfirm()
        assertEquals(SellWizardViewModel.Step.CONFIRM, vm.uiState.value.step)

        // Close wizard, and meanwhile another open sell reserves 0.6 BTC
        vm.onDismissed()
        db.transactionDao().insertTransaction(
            TransactionEntity(
                planId = planId,
                connectionId = 1L,
                exchange = Exchange.COINMATE,
                crypto = "BTC",
                fiat = "CZK",
                fiatAmount = BigDecimal.ZERO,
                cryptoAmount = BigDecimal.ZERO,
                price = BigDecimal("2100000"),
                fee = BigDecimal.ZERO,
                status = TransactionStatus.PENDING,
                exchangeOrderId = "external-sell",
                executedAt = t0.plusSeconds(7200),
                side = TransactionSide.SELL,
                limitPrice = BigDecimal("2100000"),
                requestedCryptoAmount = BigDecimal("0.6")
            )
        )

        // Reopen with new session ID -> must NOT retain CONFIRM step, inputs, or stale 1.0 capacity!
        vm.openSession(planId, "session-2")
        val state = vm.awaitIdle()

        assertEquals(SellWizardViewModel.Step.INPUT, state.step)
        assertEquals("", state.amountInput)
        assertEquals("", state.priceInput)
        assertEquals("", state.netInput)
        assertFalse(state.ladderEnabled)
        assertEquals(0, BigDecimal("0.4").compareTo(state.availableToSell))
    }

    @Test
    fun `same sessionId preserves edits across rotation, different planId resets`() = runBlocking {
        val plan1 = seedPlan(boughtCrypto = "1.0", name = "Plan 1")
        val plan2 = seedPlan(boughtCrypto = "2.5", name = "Plan 2")
        val vm = createViewModel(FakeExchangeApi())

        vm.openSession(plan1, "same-session")
        vm.awaitIdle()
        vm.setAmount("0.3")
        vm.setPrice("2000000")
        vm.awaitIdle()

        // Same planId + same sessionId (configuration change) keeps edits
        vm.openSession(plan1, "same-session")
        vm.awaitIdle()
        assertEquals("0.3", vm.uiState.value.amountInput)
        assertEquals(plan1, vm.uiState.value.planId)

        // Switching to plan2 resets inputs and loads plan2 capacity
        vm.openSession(plan2, "session-plan2")
        vm.awaitIdle()
        assertEquals(plan2, vm.uiState.value.planId)
        assertEquals("", vm.uiState.value.amountInput)
        assertEquals(0, BigDecimal("2.5").compareTo(vm.uiState.value.availableToSell))
    }

    // --- Priority 2: Timeout preserves result, distinguishes uncertain, blocks resubmit until resolved ---

    @Test
    fun `single timeout sets Uncertain, blocks repeated submit even across reopen, unlocks on resolve`() = runBlocking {
        val planId = seedPlan(boughtCrypto = "1.0")
        val otherPlanId = seedPlan(boughtCrypto = "0.5", name = "Other")
        var resolveCalls = 0
        val api = FakeExchangeApi(
            limitSellHandler = { _, _, _, _ ->
                delay(5_000L)
                sellSuccess("never", BigDecimal("0.2"), BigDecimal("2000000"))
            }
        )
        val vm = createViewModel(api, orderTimeoutMs = 50L, onResolvePending = { resolveCalls++; 0 })

        vm.openSession(planId, "s1")
        vm.awaitIdle()
        vm.setAmount("0.2")
        vm.setPrice("2000000")
        vm.awaitIdle()
        vm.proceedToConfirm()

        vm.submit()
        vm.awaitState { it.submissionState is SellWizardViewModel.SubmissionState.Uncertain }

        assertTrue(vm.uiState.value.submissionState is SellWizardViewModel.SubmissionState.Uncertain)
        assertTrue(vm.uiState.value.showTimeoutDialog)
        assertTrue(vm.uiState.value.isSubmissionBlocked)
        assertFalse(vm.uiState.value.canSubmit)
        assertEquals(1, api.limitSellCallCount)

        // Dismissing dialog does NOT unlock repeated submission!
        vm.dismissTimeoutDialog()
        assertFalse(vm.uiState.value.showTimeoutDialog)
        assertTrue(vm.uiState.value.isSubmissionBlocked)
        vm.submit()
        assertEquals("Repeated submit must be blocked while uncertain", 1, api.limitSellCallCount)

        // Closing and reopening same plan resets inputs/step but KEEPS the uncertainty block!
        vm.onDismissed()
        vm.openSession(planId, "s2")
        vm.awaitIdle()
        assertEquals(SellWizardViewModel.Step.INPUT, vm.uiState.value.step)
        assertEquals("", vm.uiState.value.amountInput)
        assertTrue(vm.uiState.value.hasUnresolvedUncertain)
        assertTrue(vm.uiState.value.isSubmissionBlocked)

        // Other plan is NOT blocked by planId's uncertainty lock
        vm.openSession(otherPlanId, "s-other")
        vm.awaitIdle()
        assertFalse(vm.uiState.value.hasUnresolvedUncertain)
        assertFalse(vm.uiState.value.isSubmissionBlocked)

        // Back to original plan -> attempting unlock BEFORE reviewing DB reservation keeps it blocked!
        vm.openSession(planId, "s3")
        vm.awaitIdle()
        assertTrue(vm.uiState.value.hasUnresolvedUncertain)
        vm.resolveUncertainSubmission()
        vm.awaitIdle()
        assertEquals(1, resolveCalls)
        assertTrue(
            "Must stay blocked while DB still has unreviewed sell intent",
            vm.uiState.value.hasUnresolvedUncertain
        )

        // Acknowledge the unidentified sell in History -> now resolve unlocks cleanly!
        val pendingUnidentified = db.transactionDao().getTransactionsByPlanSync(planId)
            .first { it.side == TransactionSide.SELL && it.exchangeOrderId.isNullOrBlank() }
        db.transactionDao().acknowledgeUnidentifiedSell(pendingUnidentified.id)

        vm.resolveUncertainSubmission()
        vm.awaitState { !it.hasUnresolvedUncertain && !it.initializing && !it.resolvingUncertain }

        assertEquals(2, resolveCalls)
        assertFalse(vm.uiState.value.hasUnresolvedUncertain)
        assertFalse(vm.uiState.value.isSubmissionBlocked)
        assertEquals(SellWizardViewModel.Step.INPUT, vm.uiState.value.step)
        assertEquals(0, BigDecimal("1.0").compareTo(vm.uiState.value.availableToSell))
    }

    @Test
    fun `ladder timeout preserves placed count in Uncertain state and blocks blind resubmission`() = runBlocking {
        val planId = seedPlan(boughtCrypto = "1.0")
        var call = 0
        val api = FakeExchangeApi(
            price = BigDecimal("1500000"),
            limitSellHandler = { _, _, amt, price ->
                call++
                when (call) {
                    1 -> sellSuccess("l-1", amt, price)
                    2 -> sellSuccess("l-2", amt, price)
                    else -> {
                        delay(5_000L)
                        sellSuccess("l-late", amt, price)
                    }
                }
            }
        )
        val vm = createViewModel(api, orderTimeoutMs = 50L)

        vm.openSession(planId, "s-ladder")
        vm.awaitIdle()
        vm.setAmount("0.8")
        vm.setLadderEnabled(true)
        vm.setLadderRangeMode(SellWizardViewModel.LadderRangeMode.PRICE)
        vm.setLadderFrom("2000000")
        vm.setLadderTo("2600000")
        vm.setLadderCount("4")
        vm.awaitIdle()

        assertTrue(vm.uiState.value.canProceed)
        vm.proceedToConfirm()
        vm.submit()
        vm.awaitState { it.submissionState is SellWizardViewModel.SubmissionState.Uncertain }

        val sub = vm.uiState.value.submissionState
        assertTrue("Expected Uncertain, got $sub", sub is SellWizardViewModel.SubmissionState.Uncertain)
        val uncertain = sub as SellWizardViewModel.SubmissionState.Uncertain
        assertTrue(uncertain.isLadder)
        assertEquals(2, uncertain.placedCount)
        assertEquals(4, uncertain.totalCount)
        assertTrue(vm.uiState.value.isSubmissionBlocked)
        assertEquals(3, api.limitSellCallCount)

        // Fresh capacity was already refreshed (1.0 - 0.4 placed - 0.2 uncertain reserved = 0.4 available)
        assertEquals(0, BigDecimal("0.4").compareTo(vm.uiState.value.availableToSell))

        // Repeated submit is blocked!
        vm.dismissTimeoutDialog()
        vm.submit()
        assertEquals(3, api.limitSellCallCount)
    }

    // --- Priority 4: Shared ladder warnings, count cap, EQUAL_FIAT net target ---

    @Test
    fun `ladder enforces MAX_ORDER_COUNT and surfaces shared spot and loss warnings`() = runBlocking {
        val planId = seedPlan(boughtCrypto = "1.0", buyPrice = "1000000")
        val vm = createViewModel(FakeExchangeApi(price = BigDecimal("1200000")))

        vm.openSession(planId, "s-val")
        vm.awaitIdle()
        vm.setAmount("0.5")
        vm.setLadderEnabled(true)
        vm.setLadderRangeMode(SellWizardViewModel.LadderRangeMode.PRICE)
        vm.setLadderFrom("900000") // below avg buy (loss) AND below spot (instant fill)
        vm.setLadderTo("4000000")  // > 3x spot (far from market)
        vm.setLadderCount("25")    // > MAX_ORDER_COUNT (20)

        assertTrue(vm.uiState.value.ladderHardError is LadderError.CountMaxExceeded)
        assertFalse(vm.uiState.value.canProceed)

        // Fix count to 4 -> valid, with all 3 shared warnings populated!
        vm.setLadderCount("4")
        assertNull(vm.uiState.value.ladderHardError)
        assertTrue(vm.uiState.value.canProceed)
        val validations = vm.uiState.value.validations
        assertTrue(validations.any { it is SellValidation.InstantFillInfo })
        assertTrue(validations.any { it is SellValidation.FarFromMarketWarning })
        assertTrue(validations.any { it is SellValidation.LossWarning })
    }

    @Test
    fun `EQUAL_FIAT net target updates upper price accurately and flags unreachable targets`() = runBlocking {
        val planId = seedPlan(boughtCrypto = "1.0", buyPrice = "1000000")
        val vm = createViewModel(FakeExchangeApi(price = BigDecimal("1000000")))

        vm.openSession(planId, "s-fiat")
        vm.awaitIdle()
        vm.setAmount("1")
        vm.setLadderEnabled(true)
        vm.setLadderRangeMode(SellWizardViewModel.LadderRangeMode.PRICE)
        vm.setLadderAmountMode(LadderGenerator.AmountMode.EQUAL_FIAT)
        vm.setLadderFrom("1000000")
        vm.setLadderTo("2000000")
        vm.setLadderCount("4")

        // Reachable target: 1,500,000 net -> updates ladderToInput and preview net matches ~1,500,000
        vm.setLadderNetTarget("1500000")
        assertNull(vm.uiState.value.ladderHardError)
        assertEquals(4, vm.uiState.value.ladderPreview.size)
        val actualNet = LadderGenerator.totalNetProceeds(vm.uiState.value.ladderPreview, vm.uiState.value.feeRate)
        assertTrue(
            "Expected actualNet ($actualNet) near 1500000",
            (actualNet - BigDecimal("1500000")).abs() <= BigDecimal("10")
        )

        // Unreachable target above harmonic asymptote (4 * 1M = 4M) -> clears stale preview & errors
        vm.setLadderNetTarget("4500000")
        assertNotNull(vm.uiState.value.ladderHardError)
        assertTrue(vm.uiState.value.ladderHardError is LadderError.NetTargetUnreachable)
        assertTrue(vm.uiState.value.ladderPreview.isEmpty())
        assertFalse(vm.uiState.value.canProceed)
    }
}
