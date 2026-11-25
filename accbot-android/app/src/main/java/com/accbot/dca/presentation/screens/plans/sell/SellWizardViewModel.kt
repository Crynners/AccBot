package com.accbot.dca.presentation.screens.plans.sell

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.R
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.RemainingInventory
import com.accbot.dca.domain.usecase.CalculatePlanCostBasisUseCase
import com.accbot.dca.domain.usecase.CalculatePlanPnLUseCase
import com.accbot.dca.domain.usecase.LadderOrder
import com.accbot.dca.domain.usecase.LadderResult
import com.accbot.dca.domain.usecase.PlaceLadderSellUseCase
import com.accbot.dca.domain.usecase.PlaceLimitSellUseCase
import com.accbot.dca.domain.usecase.PlaceSellResult
import com.accbot.dca.domain.usecase.ResolvePendingTransactionsUseCase
import com.accbot.dca.domain.usecase.SellValidation
import com.accbot.dca.domain.usecase.ValidateSellOrderUseCase
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.exchange.MinOrderSizeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

/** Locale-free ladder validation error; UI resolves to a string resource. */
sealed class LadderError {
    object AvgRequired : LadderError()
    object AmountMustBePositive : LadderError()
    object CountMin2 : LadderError()
    data class CountMaxExceeded(val max: Int) : LadderError()
    object ToMustExceedFrom : LadderError()
    object Insufficient : LadderError()
    object InvalidOrder : LadderError()
    data class BelowMin(val smallest: BigDecimal, val min: BigDecimal, val fiat: String) : LadderError()
    data class NetTargetUnreachable(
        val minNet: BigDecimal?,
        val maxNet: BigDecimal?,
        val fiat: String
    ) : LadderError()
}

/**
 * Derived single-order summary: gross proceeds, fees, net, optional profit vs avg buy
 * and amount-as-pct-of-available. Computed in the VM so the UI stays dumb.
 */
data class SellSummary(
    val proceeds: BigDecimal,
    val feeAmount: BigDecimal,
    val netProceeds: BigDecimal,
    val costBasis: BigDecimal?,
    val netProfit: BigDecimal?,
    val netProfitPct: BigDecimal?,
    val amountPct: BigDecimal?,
    val totalProgress: BigDecimal?,
    val targetProgressPct: Int?
)

/**
 * State + actions for the two-step limit-sell wizard (input -> confirm -> submit).
 *
 * Lifecycle / reopen safety: because the dialog ViewModel is scoped to the parent
 * NavBackStackEntry, [openSession] resets all transient inputs, step, and stale capacity
 * whenever a new session ID or different plan ID is opened, while keeping any unresolved
 * [SubmissionState.Uncertain] lock for the same plan until the user explicitly resolves it.
 */
@HiltViewModel
class SellWizardViewModel internal constructor(
    private val context: Context,
    private val validateSellOrderUseCase: ValidateSellOrderUseCase,
    private val placeLimitSellUseCase: PlaceLimitSellUseCase,
    private val placeLadderSellUseCase: PlaceLadderSellUseCase,
    private val calculatePlanPnLUseCase: CalculatePlanPnLUseCase,
    private val calculatePlanCostBasisUseCase: CalculatePlanCostBasisUseCase,
    private val database: DcaDatabase,
    private val credentialsProvider: (connectionId: Long, isSandbox: Boolean) -> ExchangeCredentials?,
    private val apiProvider: (ExchangeCredentials) -> ExchangeApi,
    private val isSandboxProvider: () -> Boolean,
    private val minOrderProvider: suspend (Exchange, String, String) -> BigDecimal,
    private val resolvePending: suspend () -> Int
) : ViewModel() {

    @Inject constructor(
        @ApplicationContext context: Context,
        validateSellOrderUseCase: ValidateSellOrderUseCase,
        placeLimitSellUseCase: PlaceLimitSellUseCase,
        placeLadderSellUseCase: PlaceLadderSellUseCase,
        calculatePlanPnLUseCase: CalculatePlanPnLUseCase,
        calculatePlanCostBasisUseCase: CalculatePlanCostBasisUseCase,
        database: DcaDatabase,
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        userPreferences: UserPreferences,
        minOrderSizeRepository: MinOrderSizeRepository,
        resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
    ) : this(
        context = context,
        validateSellOrderUseCase = validateSellOrderUseCase,
        placeLimitSellUseCase = placeLimitSellUseCase,
        placeLadderSellUseCase = placeLadderSellUseCase,
        calculatePlanPnLUseCase = calculatePlanPnLUseCase,
        calculatePlanCostBasisUseCase = calculatePlanCostBasisUseCase,
        database = database,
        credentialsProvider = { connId, sandbox -> credentialsStore.getCredentials(connId, sandbox) },
        apiProvider = { exchangeApiFactory.create(it) },
        isSandboxProvider = { userPreferences.isSandboxMode() },
        minOrderProvider = { ex, crypto, fiat -> minOrderSizeRepository.getMinOrderSize(ex, crypto, fiat) },
        resolvePending = { resolvePendingTransactionsUseCase() }
    )

    enum class Step { INPUT, CONFIRM }
    enum class LadderRangeMode { PRICE, PROFIT_PCT }

    /** Concrete submission lifecycle state. */
    sealed class SubmissionState {
        object Idle : SubmissionState()
        data class Submitting(val placedSoFar: Int = 0, val totalCount: Int = 1) : SubmissionState()
        /** Definitively rejected (pre-flight or exchange confirmedRejection=true). */
        data class Rejected(
            val reason: String,
            val placedCount: Int = 0,
            val totalCount: Int = 1
        ) : SubmissionState()
        /** Timeout / network drop / unconfirmed error: blocks repeated submission until resolved. */
        data class Uncertain(
            val reason: String,
            val placedCount: Int = 0,
            val totalCount: Int = 1,
            val isLadder: Boolean = false
        ) : SubmissionState()
        data class Completed(
            val placedCount: Int,
            val totalCount: Int,
            val isLadder: Boolean
        ) : SubmissionState()
    }

    /** Snapshot of ladder result so the UI can present a dialog after submit. */
    data class LadderSubmitOutcome(val placed: Int, val total: Int, val reason: String?)

    data class UiState(
        val planId: Long = 0,
        val planName: String = "",
        val exchangeName: String = "",
        val crypto: String = "",
        val fiat: String = "",
        val held: BigDecimal = BigDecimal.ZERO,
        val availableToSell: BigDecimal = BigDecimal.ZERO,
        val spotPrice: BigDecimal? = null,
        /** Auto-computed avg buy price from cheapest-first; null when no buys remain. */
        val avgBuyPriceAuto: BigDecimal? = null,
        /** User-entered text for avg; empty string means "use auto". */
        val avgBuyPriceInput: String = "",
        /** True when [avgBuyPriceInput] differs from auto - drives the reset chip. */
        val avgBuyPriceManual: Boolean = false,
        /** Minimum order size **in fiat** (e.g. 50 CZK on Coinmate). */
        val minOrderFiat: BigDecimal = BigDecimal.ZERO,
        val amountInput: String = "",
        val priceInput: String = "",
        val netInput: String = "",
        val lastTwoEdited: List<SellCalculatorMath.Field> = emptyList(),
        val feeRate: BigDecimal = BigDecimal.ZERO,
        val targetProfitAmount: BigDecimal? = null,
        val realizedPnLSoFar: BigDecimal = BigDecimal.ZERO,
        val inventoryDeficit: BigDecimal = BigDecimal.ZERO,
        val validations: List<SellValidation> = emptyList(),
        val validating: Boolean = false,
        val validationFailed: Boolean = false,
        val ladderEnabled: Boolean = false,
        val ladderRangeMode: LadderRangeMode = LadderRangeMode.PROFIT_PCT,
        val ladderFromInput: String = "",
        val ladderToInput: String = "",
        val ladderCountInput: String = "5",
        val ladderAmountMode: LadderGenerator.AmountMode = LadderGenerator.AmountMode.EQUAL_CRYPTO,
        val ladderPreview: List<LadderOrder> = emptyList(),
        val ladderHardError: LadderError? = null,
        val ladderOutcome: LadderSubmitOutcome? = null,
        val step: Step = Step.INPUT,
        val initializing: Boolean = true,
        val submitting: Boolean = false,
        val resolvingUncertain: Boolean = false,
        val submissionState: SubmissionState = SubmissionState.Idle,
        val submitError: String? = null,
        val showTimeoutDialog: Boolean = false,
        val dismissRequested: Boolean = false
    ) {
        /** Effective avg buy: parsed manual input takes precedence, else auto. */
        val avgBuyPrice: BigDecimal?
            get() = if (avgBuyPriceManual) avgBuyPriceInput.toBigDecimalOrNull() else avgBuyPriceAuto

        val hasUnresolvedUncertain: Boolean
            get() = submissionState is SubmissionState.Uncertain

        val isSubmissionBlocked: Boolean
            get() = submitting ||
                resolvingUncertain ||
                submissionState is SubmissionState.Submitting ||
                submissionState is SubmissionState.Uncertain ||
                submissionState is SubmissionState.Completed ||
                (submissionState is SubmissionState.Rejected && submissionState.placedCount > 0)

        val canProceed: Boolean
            get() {
                if (initializing || validating || validationFailed || isSubmissionBlocked) return false
                return if (ladderEnabled) {
                    ladderHardError == null &&
                        validations.none { it is SellValidation.HardError } &&
                        ladderPreview.size in LadderGenerator.MIN_ORDER_COUNT..LadderGenerator.MAX_ORDER_COUNT &&
                        (amountInput.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true)
                } else {
                    val a = amountInput.toBigDecimalOrNull()
                    val p = priceInput.toBigDecimalOrNull()
                    validations.none { it is SellValidation.HardError } &&
                        a != null && a > BigDecimal.ZERO &&
                        p != null && p > BigDecimal.ZERO
                }
            }

        val canSubmit: Boolean
            get() = canProceed && step == Step.CONFIRM

        /**
         * Single-order summary derived from amount/price/avg/fee. null when amount or
         * price is missing/zero - the UI hides the summary card in that case.
         */
        val summary: SellSummary?
            get() {
                val a = amountInput.toBigDecimalOrNull() ?: return null
                val p = priceInput.toBigDecimalOrNull() ?: return null
                if (a <= BigDecimal.ZERO || p <= BigDecimal.ZERO) return null
                val proceeds = (a * p).setScale(2, RoundingMode.HALF_UP)
                val feeAmount = (proceeds * feeRate).setScale(2, RoundingMode.HALF_UP)
                val netProceeds = (proceeds - feeAmount).setScale(2, RoundingMode.HALF_UP)
                val avg = avgBuyPrice
                val costBasis = avg?.let { a * it }
                val netProfit = costBasis?.let { (netProceeds - it).setScale(2, RoundingMode.HALF_UP) }
                val netProfitPct = if (costBasis != null && netProfit != null && costBasis > BigDecimal.ZERO) {
                    netProfit.divide(costBasis, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                        .setScale(1, RoundingMode.HALF_UP)
                } else null
                val amountPct = if (availableToSell > BigDecimal.ZERO) {
                    a.divide(availableToSell, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                        .setScale(1, RoundingMode.HALF_UP)
                } else null
                val totalProgress = netProfit?.let { realizedPnLSoFar + it }
                val targetProgressPct = if (totalProgress != null &&
                    targetProfitAmount != null && targetProfitAmount > BigDecimal.ZERO
                ) {
                    val raw = totalProgress.divide(targetProfitAmount, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                    raw.toInt().coerceAtLeast(0)
                } else null
                return SellSummary(
                    proceeds, feeAmount, netProceeds,
                    costBasis, netProfit, netProfitPct,
                    amountPct, totalProgress, targetProgressPct
                )
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var activeSessionId: String? = null
    private var activePlanId: Long? = null
    private var initJob: Job? = null
    private var validationJob: Job? = null

    /**
     * Unresolved uncertain outcomes survive wizard dismiss/reopen on the same plan so the
     * user cannot bypass the duplicate-order block simply by closing and reopening the sheet.
     */
    private val unresolvedUncertainByPlan = mutableMapOf<Long, SubmissionState.Uncertain>()

    fun init(planId: Long) {
        openSession(planId, UUID.randomUUID().toString())
    }

    /**
     * Start or resume a wizard session for [planId].
     * Same `(planId, sessionId)` (e.g. configuration change while open) preserves in-progress
     * edits. Any new session (reopen after dismiss) or different [planId] resets all inputs,
     * step, and stale capacity, then loads fresh data from DB and exchange.
     */
    fun openSession(planId: Long, sessionId: String) {
        if (activeSessionId == sessionId && activePlanId == planId) return
        // Do not clobber an active in-flight submission if a spurious recomposition happens.
        if (_uiState.value.submitting && activePlanId == planId) {
            activeSessionId = sessionId
            return
        }

        initJob?.cancel()
        validationJob?.cancel()
        activeSessionId = sessionId
        activePlanId = planId

        val pendingUncertain = unresolvedUncertainByPlan[planId]
        _uiState.value = UiState(
            planId = planId,
            initializing = true,
            submissionState = pendingUncertain ?: SubmissionState.Idle
        )

        initJob = viewModelScope.launch {
            loadPlanState(planId)
        }
    }

    /**
     * Called when the wizard sheet is dismissed. Clears the active session marker so the next
     * open always starts fresh, while retaining any unresolved uncertain-submission lock.
     */
    fun onDismissed() {
        if (_uiState.value.submitting) return
        activeSessionId = null
        initJob?.cancel()
        validationJob?.cancel()
    }

    private suspend fun loadPlanState(planId: Long) {
        val plan = database.dcaPlanDao().getPlanById(planId)
        if (plan == null) {
            _uiState.update { it.copy(initializing = false) }
            return
        }

        val credentials = try {
            credentialsProvider(plan.connectionId, isSandboxProvider())
        } catch (e: Exception) {
            Log.w(TAG, "getCredentials failed: ${e.message}")
            null
        }
        val api = credentials?.let { apiProvider(it) }
        val spot = api?.let {
            try {
                withTimeoutOrNull(INIT_TIMEOUT_MS) { it.getCurrentPrice(plan.crypto, plan.fiat) }
            } catch (e: Exception) {
                Log.w(TAG, "getCurrentPrice failed: ${e.message}")
                null
            }
        }
        val feeRate = api?.estimatedTakerFeeRate ?: BigDecimal.ZERO

        val inventory: RemainingInventory = try {
            calculatePlanCostBasisUseCase(planId)
        } catch (e: Exception) {
            Log.w(TAG, "cost basis calc failed: ${e.message}")
            RemainingInventory(BigDecimal.ZERO, null, emptyList(), BigDecimal.ZERO)
        }

        val pnl = try {
            calculatePlanPnLUseCase(planId, spot)
        } catch (e: Exception) {
            Log.w(TAG, "PnL calc failed: ${e.message}")
            null
        }
        val held = pnl?.currentCryptoHeld ?: inventory.available
        val realizedSoFar = pnl?.realizedPnL ?: BigDecimal.ZERO

        val minOrderFiat = try {
            minOrderProvider(plan.exchange, plan.crypto, plan.fiat)
        } catch (e: Exception) {
            Log.w(TAG, "min order fetch failed: ${e.message}")
            plan.exchange.minOrderSize[plan.fiat] ?: BigDecimal.ZERO
        }

        val hasUnidentifiedSell = try {
            database.transactionDao().hasUnidentifiedSell(planId)
        } catch (_: Exception) {
            false
        }

        _uiState.update { st ->
            val effectiveSubmissionState = when {
                st.submissionState is SubmissionState.Uncertain -> st.submissionState
                hasUnidentifiedSell -> SubmissionState.Uncertain(
                    reason = context.getString(R.string.sell_wizard_timeout_title),
                    placedCount = 0,
                    totalCount = 1,
                    isLadder = false
                )
                else -> st.submissionState
            }
            st.copy(
                planId = planId,
                planName = plan.name.ifBlank { "${plan.crypto}/${plan.fiat}" },
                exchangeName = plan.exchange.displayName,
                crypto = plan.crypto,
                fiat = plan.fiat,
                held = held,
                availableToSell = inventory.available,
                spotPrice = spot,
                avgBuyPriceAuto = inventory.weightedAvgPrice,
                avgBuyPriceInput = inventory.weightedAvgPrice?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "",
                avgBuyPriceManual = false,
                minOrderFiat = minOrderFiat,
                feeRate = feeRate,
                targetProfitAmount = plan.targetProfitAmount,
                realizedPnLSoFar = realizedSoFar,
                inventoryDeficit = inventory.deficit,
                submissionState = effectiveSubmissionState,
                initializing = false
            )
        }
        if (_uiState.value.ladderEnabled) {
            recomputeLadderPreview()
        } else {
            revalidate()
        }
    }

    private suspend fun refreshCapacity(planId: Long) {
        val inventory = try {
            calculatePlanCostBasisUseCase(planId)
        } catch (e: Exception) {
            Log.w(TAG, "refreshCapacity cost basis failed: ${e.message}")
            return
        }
        val pnl = try {
            calculatePlanPnLUseCase(planId, _uiState.value.spotPrice)
        } catch (e: Exception) {
            null
        }
        _uiState.update { st ->
            st.copy(
                availableToSell = inventory.available,
                held = pnl?.currentCryptoHeld ?: inventory.available,
                avgBuyPriceAuto = inventory.weightedAvgPrice,
                avgBuyPriceInput = if (st.avgBuyPriceManual) st.avgBuyPriceInput
                    else inventory.weightedAvgPrice?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "",
                realizedPnLSoFar = pnl?.realizedPnL ?: st.realizedPnLSoFar,
                inventoryDeficit = inventory.deficit
            )
        }
    }

    fun setAmount(value: String) {
        updateCalculatorField(SellCalculatorMath.Field.AMOUNT, value)
    }

    fun setAmountPct(pct: Int) {
        val target = _uiState.value.availableToSell
            .multiply(BigDecimal(pct))
            .divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
        setAmount(target.stripTrailingZeros().toPlainString())
    }

    fun setPrice(value: String) {
        updateCalculatorField(SellCalculatorMath.Field.PRICE, value)
    }

    fun setNetFiat(value: String) {
        updateCalculatorField(SellCalculatorMath.Field.NET, value)
    }

    fun setPriceSpot() {
        _uiState.value.spotPrice?.let {
            setPrice(it.stripTrailingZeros().toPlainString())
        }
    }

    fun setPriceAvgPlus(pct: Int) {
        _uiState.value.avgBuyPrice?.let { avg ->
            val multiplier = BigDecimal.ONE +
                BigDecimal(pct).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
            setPrice((avg * multiplier).setScale(2, RoundingMode.HALF_UP).toPlainString())
        }
    }

    /** Apply a profit-target preset to the net field: N = A * avg * (1 + profitPct). */
    fun applyNetProfitPreset(profitPct: Double) {
        val st = _uiState.value
        val a = st.amountInput.toBigDecimalOrNull() ?: return
        val avg = st.avgBuyPrice ?: return
        if (a <= BigDecimal.ZERO || avg <= BigDecimal.ZERO) return
        val target = a * avg * (BigDecimal.ONE + BigDecimal.valueOf(profitPct))
        setNetFiat(target.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }

    fun setAvgBuyPrice(value: String) {
        _uiState.update { st ->
            val parsed = value.toBigDecimalOrNull()
            // Compare against the same 2dp display rounding the user sees - avoids
            // flagging "manual" on the very first prefill that came from auto.
            val autoDisplay = st.avgBuyPriceAuto?.setScale(2, RoundingMode.HALF_UP)
            val isManual = parsed != null &&
                (autoDisplay == null || parsed.compareTo(autoDisplay) != 0)
            st.copy(avgBuyPriceInput = value, avgBuyPriceManual = isManual)
        }
        if (_uiState.value.ladderEnabled) {
            recomputeLadderPreview()
        } else {
            revalidate()
        }
    }

    fun resetAvgBuyPrice() {
        _uiState.update { st ->
            st.copy(
                avgBuyPriceInput = st.avgBuyPriceAuto?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "",
                avgBuyPriceManual = false
            )
        }
        if (_uiState.value.ladderEnabled) {
            recomputeLadderPreview()
        } else {
            revalidate()
        }
    }

    private fun updateCalculatorField(field: SellCalculatorMath.Field, text: String) {
        _uiState.update { st ->
            val newLastTwo = (listOf(field) + st.lastTwoEdited.filter { it != field }).take(2)
            val current = mapOf(
                SellCalculatorMath.Field.AMOUNT to st.amountInput,
                SellCalculatorMath.Field.PRICE to st.priceInput,
                SellCalculatorMath.Field.NET to st.netInput
            ).toMutableMap()
            current[field] = text
            val a = current[SellCalculatorMath.Field.AMOUNT]?.toBigDecimalOrNull()
            val p = current[SellCalculatorMath.Field.PRICE]?.toBigDecimalOrNull()
            val n = current[SellCalculatorMath.Field.NET]?.toBigDecimalOrNull()
            val (newA, newP, newN) = SellCalculatorMath.recompute(a, p, n, st.feeRate, newLastTwo)

            // Only overwrite the field that wasn't directly edited; the user-typed text stays as-is
            val nextAmount = if (field == SellCalculatorMath.Field.AMOUNT) text
                             else newA?.stripTrailingZeros()?.toPlainString() ?: st.amountInput
            val nextPrice = if (field == SellCalculatorMath.Field.PRICE) text
                            else newP?.toPlainString() ?: st.priceInput
            val nextNet = if (field == SellCalculatorMath.Field.NET) text
                          else newN?.toPlainString() ?: st.netInput

            st.copy(
                amountInput = nextAmount,
                priceInput = nextPrice,
                netInput = nextNet,
                lastTwoEdited = newLastTwo
            )
        }
        if (_uiState.value.ladderEnabled) {
            if (field == SellCalculatorMath.Field.AMOUNT) recomputeLadderPreview()
        } else {
            revalidate()
        }
    }

    // --- Ladder handlers ---

    fun setLadderEnabled(enabled: Boolean) {
        validationJob?.cancel()
        _uiState.update { st ->
            if (enabled) {
                // Single -> Ladder: spread +-10 % around the single price so the middle
                // order matches what the user had already set.
                val price = st.priceInput.toBigDecimalOrNull()
                val avg = st.avgBuyPrice
                val (fromInput, toInput) = if (price != null && price > BigDecimal.ZERO) {
                    val fromPrice = price * BigDecimal("0.90")
                    val toPrice = price * BigDecimal("1.10")
                    when (st.ladderRangeMode) {
                        LadderRangeMode.PRICE ->
                            fromPrice.setScale(2, RoundingMode.HALF_UP).toPlainString() to
                                toPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()
                        LadderRangeMode.PROFIT_PCT -> {
                            val fromPct = LadderGenerator.priceToProfitPct(fromPrice, avg)
                            val toPct = LadderGenerator.priceToProfitPct(toPrice, avg)
                            if (fromPct != null && toPct != null) {
                                fromPct.toPlainString() to toPct.toPlainString()
                            } else st.ladderFromInput to st.ladderToInput
                        }
                    }
                } else st.ladderFromInput to st.ladderToInput
                st.copy(
                    ladderEnabled = true,
                    ladderOutcome = null,
                    ladderFromInput = fromInput,
                    ladderToInput = toInput,
                    validations = emptyList(),
                    validating = false,
                    validationFailed = false
                )
            } else {
                // Ladder -> Single: derive a single price that yields the same total net
                // for the same crypto amount: net = amount * price * (1 - fee), so
                // price = net / (amount * (1 - fee)).
                val amount = st.amountInput.toBigDecimalOrNull()
                val total = st.netInput.toBigDecimalOrNull()
                val factor = BigDecimal.ONE - st.feeRate
                val newPrice = if (amount != null && total != null &&
                    amount > BigDecimal.ZERO && factor > BigDecimal.ZERO
                ) {
                    total.divide(amount * factor, 2, RoundingMode.HALF_UP).toPlainString()
                } else st.priceInput
                st.copy(
                    ladderEnabled = false,
                    ladderOutcome = null,
                    priceInput = newPrice,
                    ladderPreview = emptyList(),
                    ladderHardError = null,
                    validations = emptyList(),
                    validating = false,
                    validationFailed = false
                )
            }
        }
        if (_uiState.value.ladderEnabled) {
            recomputeLadderPreview()
        } else {
            revalidate()
        }
    }

    fun setLadderRangeMode(mode: LadderRangeMode) {
        _uiState.update { st ->
            if (st.ladderRangeMode == mode) return@update st
            val avg = st.avgBuyPrice
            val convert: (String) -> String = { input ->
                val v = input.toBigDecimalOrNull()
                val converted = v?.let {
                    when (mode) {
                        LadderRangeMode.PRICE -> LadderGenerator.profitPctToPrice(it, avg)
                        LadderRangeMode.PROFIT_PCT -> LadderGenerator.priceToProfitPct(it, avg)
                    }
                }
                converted?.toPlainString() ?: ""
            }
            st.copy(
                ladderRangeMode = mode,
                ladderFromInput = convert(st.ladderFromInput),
                ladderToInput = convert(st.ladderToInput)
            )
        }
        recomputeLadderPreview()
    }

    fun setLadderFrom(value: String) {
        _uiState.update { it.copy(ladderFromInput = value) }
        recomputeLadderPreview()
    }

    fun setLadderTo(value: String) {
        _uiState.update { it.copy(ladderToInput = value) }
        recomputeLadderPreview()
    }

    fun setLadderCount(value: String) {
        _uiState.update { it.copy(ladderCountInput = value) }
        recomputeLadderPreview()
    }

    fun setLadderAmountMode(mode: LadderGenerator.AmountMode) {
        _uiState.update { it.copy(ladderAmountMode = mode) }
        recomputeLadderPreview()
    }

    /**
     * Ladder-mode third-field calculator: user enters target net total, we solve the upper
     * bound (`to`) to hit it accurately for BOTH [LadderGenerator.AmountMode.EQUAL_CRYPTO]
     * (closed form) and [LadderGenerator.AmountMode.EQUAL_FIAT] (harmonic-mean bisection).
     * If the target is unreachable (e.g. above the EQUAL_FIAT asymptote), clears the stale
     * preview and surfaces [LadderError.NetTargetUnreachable].
     */
    fun setLadderNetTarget(value: String) {
        _uiState.update { it.copy(netInput = value) }
        val targetNet = value.toBigDecimalOrNull() ?: return
        val st = _uiState.value
        val amount = st.amountInput.toBigDecimalOrNull() ?: return
        val count = st.ladderCountInput.toIntOrNull() ?: return
        if (amount <= BigDecimal.ZERO || targetNet <= BigDecimal.ZERO) return
        if (count !in LadderGenerator.MIN_ORDER_COUNT..LadderGenerator.MAX_ORDER_COUNT) {
            recomputeLadderPreview(syncNetFromTotal = false)
            return
        }
        val factor = BigDecimal.ONE - st.feeRate
        if (factor <= BigDecimal.ZERO) return
        val avg = st.avgBuyPrice
        val fromRaw = st.ladderFromInput.toBigDecimalOrNull() ?: return
        val fromPrice = when (st.ladderRangeMode) {
            LadderRangeMode.PRICE -> fromRaw
            LadderRangeMode.PROFIT_PCT -> LadderGenerator.profitPctToPrice(fromRaw, avg) ?: return
        }
        if (fromPrice <= BigDecimal.ZERO) return

        val solvedTo = LadderGenerator.solveToPriceForNetTarget(
            totalAmount = amount,
            from = fromPrice,
            count = count,
            mode = st.ladderAmountMode,
            feeRate = st.feeRate,
            targetNet = targetNet
        )
        if (solvedTo == null) {
            val minNet = (amount * fromPrice * factor).setScale(2, RoundingMode.HALF_UP)
            val maxNet = LadderGenerator.maxTheoreticalNetProceeds(
                amount, fromPrice, count, st.ladderAmountMode, st.feeRate
            )
            _uiState.update {
                it.copy(
                    ladderPreview = emptyList(),
                    validations = emptyList(),
                    ladderHardError = LadderError.NetTargetUnreachable(
                        minNet = if (targetNet <= minNet) minNet else null,
                        maxNet = if (targetNet > minNet) maxNet else null,
                        fiat = st.fiat
                    )
                )
            }
            return
        }

        val toDisplay = when (st.ladderRangeMode) {
            LadderRangeMode.PRICE -> solvedTo.setScale(2, RoundingMode.HALF_UP).toPlainString()
            LadderRangeMode.PROFIT_PCT ->
                LadderGenerator.priceToProfitPct(solvedTo, avg)?.toPlainString() ?: return
        }
        _uiState.update { it.copy(ladderToInput = toDisplay) }
        recomputeLadderPreview(syncNetFromTotal = false)
    }

    private fun recomputeLadderPreview(syncNetFromTotal: Boolean = true) {
        val st = _uiState.value
        if (!st.ladderEnabled) {
            _uiState.update { it.copy(ladderPreview = emptyList(), ladderHardError = null) }
            return
        }
        val total = st.amountInput.toBigDecimalOrNull()
        val count = st.ladderCountInput.toIntOrNull()
        val avg = st.avgBuyPrice
        val (from, to) = when (st.ladderRangeMode) {
            LadderRangeMode.PRICE -> {
                st.ladderFromInput.toBigDecimalOrNull() to st.ladderToInput.toBigDecimalOrNull()
            }
            LadderRangeMode.PROFIT_PCT -> {
                if (avg == null) {
                    _uiState.update {
                        it.copy(
                            ladderPreview = emptyList(),
                            ladderHardError = LadderError.AvgRequired,
                            validations = emptyList()
                        )
                    }
                    return
                }
                val fPct = st.ladderFromInput.toBigDecimalOrNull()
                val tPct = st.ladderToInput.toBigDecimalOrNull()
                val f = fPct?.let { LadderGenerator.profitPctToPrice(it, avg) }
                val t = tPct?.let { LadderGenerator.profitPctToPrice(it, avg) }
                f to t
            }
        }

        if (total == null || count == null || from == null || to == null) {
            _uiState.update {
                it.copy(ladderPreview = emptyList(), ladderHardError = null, validations = emptyList())
            }
            return
        }
        if (total <= BigDecimal.ZERO) {
            _uiState.update {
                it.copy(ladderPreview = emptyList(), ladderHardError = LadderError.AmountMustBePositive, validations = emptyList())
            }
            return
        }
        if (count < LadderGenerator.MIN_ORDER_COUNT) {
            _uiState.update {
                it.copy(ladderPreview = emptyList(), ladderHardError = LadderError.CountMin2, validations = emptyList())
            }
            return
        }
        if (count > LadderGenerator.MAX_ORDER_COUNT) {
            _uiState.update {
                it.copy(
                    ladderPreview = emptyList(),
                    ladderHardError = LadderError.CountMaxExceeded(LadderGenerator.MAX_ORDER_COUNT),
                    validations = emptyList()
                )
            }
            return
        }
        if (to <= from || from <= BigDecimal.ZERO) {
            _uiState.update {
                it.copy(ladderPreview = emptyList(), ladderHardError = LadderError.ToMustExceedFrom, validations = emptyList())
            }
            return
        }
        if (total > st.availableToSell) {
            _uiState.update {
                it.copy(ladderPreview = emptyList(), ladderHardError = LadderError.Insufficient, validations = emptyList())
            }
            return
        }

        val preview = LadderGenerator.generate(total, from, to, count, st.ladderAmountMode)
        val sharedValidations = ValidateSellOrderUseCase.validateLadderAgainstAvailable(
            orders = preview,
            available = st.availableToSell,
            minOrderFiat = st.minOrderFiat,
            currentSpot = st.spotPrice,
            avgBuyPrice = avg,
            feeRate = st.feeRate
        )

        val hardError: LadderError? = sharedValidations
            .filterIsInstance<SellValidation.HardError>()
            .firstOrNull()
            ?.let { err ->
                when (err) {
                    is SellValidation.HardError.LadderLegBelowMin -> LadderError.BelowMin(
                        smallest = err.smallestFiat,
                        min = err.minOrderFiat,
                        fiat = st.fiat
                    )
                    is SellValidation.HardError.InsufficientInventory -> LadderError.Insufficient
                    is SellValidation.HardError.LadderCountOutOfRange ->
                        if (count < err.min) LadderError.CountMin2 else LadderError.CountMaxExceeded(err.max)
                    SellValidation.HardError.AmountMustBePositive -> LadderError.AmountMustBePositive
                    SellValidation.HardError.LadderOrderInvalid -> LadderError.InvalidOrder
                    else -> LadderError.InvalidOrder
                }
            }

        val totalNet = LadderGenerator.totalNetProceeds(preview, st.feeRate)

        _uiState.update {
            it.copy(
                ladderPreview = preview,
                ladderHardError = hardError,
                validations = sharedValidations,
                netInput = if (syncNetFromTotal) totalNet.toPlainString() else it.netInput
            )
        }
    }

    fun submitLadder() {
        val st = _uiState.value
        if (!st.canSubmit || !st.ladderEnabled || st.ladderPreview.size < LadderGenerator.MIN_ORDER_COUNT) return
        val orders = st.ladderPreview
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    submitting = true,
                    submitError = null,
                    ladderOutcome = null,
                    submissionState = SubmissionState.Submitting(placedSoFar = 0, totalCount = orders.size)
                )
            }
            val result = placeLadderSellUseCase(
                planId = st.planId,
                orders = orders,
                minOrderFiat = st.minOrderFiat,
                onOrderPlaced = { placedCount, totalCount ->
                    _uiState.update {
                        it.copy(submissionState = SubmissionState.Submitting(placedCount, totalCount))
                    }
                }
            )
            when (result) {
                is LadderResult.AllPlaced -> _uiState.update {
                    it.copy(
                        submitting = false,
                        submissionState = SubmissionState.Completed(
                            placedCount = result.placedTxIds.size,
                            totalCount = result.placedTxIds.size,
                            isLadder = true
                        ),
                        ladderOutcome = LadderSubmitOutcome(
                            placed = result.placedTxIds.size,
                            total = result.placedTxIds.size,
                            reason = null
                        )
                    )
                }
                is LadderResult.PartialFailure -> {
                    refreshCapacity(st.planId)
                    if (result.placedTxIds.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                submitting = false,
                                submissionState = SubmissionState.Rejected(
                                    reason = result.reason,
                                    placedCount = 0,
                                    totalCount = result.totalCount
                                ),
                                submitError = result.reason
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                submitting = false,
                                submissionState = SubmissionState.Rejected(
                                    reason = result.reason,
                                    placedCount = result.placedTxIds.size,
                                    totalCount = result.totalCount
                                ),
                                ladderOutcome = LadderSubmitOutcome(
                                    placed = result.placedTxIds.size,
                                    total = result.totalCount,
                                    reason = result.reason
                                )
                            )
                        }
                    }
                }
                is LadderResult.UncertainOutcome -> {
                    refreshCapacity(st.planId)
                    val uncertain = SubmissionState.Uncertain(
                        reason = result.reason,
                        placedCount = result.placedTxIds.size,
                        totalCount = result.totalCount,
                        isLadder = true
                    )
                    unresolvedUncertainByPlan[st.planId] = uncertain
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submissionState = uncertain,
                            showTimeoutDialog = true,
                            ladderOutcome = null
                        )
                    }
                }
            }
        }
    }

    fun consumeLadderOutcome() {
        _uiState.update { it.copy(ladderOutcome = null, dismissRequested = true) }
    }

    fun proceedToConfirm() {
        if (!_uiState.value.canProceed) return
        _uiState.update { it.copy(step = Step.CONFIRM, submitError = null) }
    }

    fun back() {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(step = Step.INPUT, submitError = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        if (state.ladderEnabled) {
            submitLadder()
            return
        }
        val amount = state.amountInput.toBigDecimalOrNull() ?: return
        val price = state.priceInput.toBigDecimalOrNull() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    submitting = true,
                    submitError = null,
                    submissionState = SubmissionState.Submitting(placedSoFar = 0, totalCount = 1)
                )
            }

            when (val result = placeLimitSellUseCase(state.planId, amount, price, minOrderFiat = state.minOrderFiat)) {
                is PlaceSellResult.Placed -> {
                    unresolvedUncertainByPlan.remove(state.planId)
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submissionState = SubmissionState.Completed(1, 1, isLadder = false),
                            dismissRequested = true
                        )
                    }
                }
                is PlaceSellResult.Rejected -> {
                    refreshCapacity(state.planId)
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submissionState = SubmissionState.Rejected(
                                reason = result.reason,
                                placedCount = 0,
                                totalCount = 1
                            ),
                            submitError = result.reason.ifBlank {
                                context.getString(R.string.sell_wizard_submit_error_unknown)
                            }
                        )
                    }
                    revalidate()
                }
                is PlaceSellResult.Uncertain -> {
                    val uncertain = SubmissionState.Uncertain(
                        reason = result.reason,
                        placedCount = 0,
                        totalCount = 1,
                        isLadder = false
                    )
                    unresolvedUncertainByPlan[state.planId] = uncertain
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submissionState = uncertain,
                            showTimeoutDialog = true
                        )
                    }
                }
            }
        }
    }

    /**
     * Dismisses the modal timeout dialog while KEEPING [SubmissionState.Uncertain] active so
     * repeated submission remains blocked until [resolveUncertainSubmission] is invoked.
     */
    fun dismissTimeoutDialog() {
        _uiState.update { it.copy(showTimeoutDialog = false) }
    }

    /**
     * Explicit user resolution after verifying open orders on the exchange.
     * Polls pending orders, refreshes capacity, clears the uncertain-submission lock for the
     * current plan, and returns to a clean INPUT step so stale confirmation is never reused.
     */
    fun resolveUncertainSubmission() {
        val planId = _uiState.value.planId
        if (planId <= 0L || _uiState.value.resolvingUncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(resolvingUncertain = true, showTimeoutDialog = false) }
            try {
                resolvePending()
            } catch (e: Exception) {
                Log.w(TAG, "resolvePending during uncertain unlock failed: ${e.message}")
            }
            val stillHasUnidentified = try {
                database.transactionDao().hasUnidentifiedSell(planId)
            } catch (_: Exception) {
                false
            }
            if (stillHasUnidentified) {
                refreshCapacity(planId)
                _uiState.update { it.copy(resolvingUncertain = false) }
                return@launch
            }
            unresolvedUncertainByPlan.remove(planId)
            _uiState.value = UiState(
                planId = planId,
                initializing = true,
                submissionState = SubmissionState.Idle
            )
            loadPlanState(planId)
        }
    }

    fun consumeDismiss() {
        _uiState.update { it.copy(dismissRequested = false) }
    }

    private fun revalidate() {
        val state = _uiState.value
        if (state.ladderEnabled) return
        val amount = state.amountInput.toBigDecimalOrNull()
        val price = state.priceInput.toBigDecimalOrNull()
        validationJob?.cancel()
        if (amount == null || price == null) {
            _uiState.update { it.copy(validations = emptyList(), validating = false, validationFailed = false) }
            return
        }
        _uiState.update { it.copy(validating = true, validationFailed = false) }
        // Cancel any in-flight validation so a slower earlier coroutine can't overwrite
        // newer results - keystrokes fire revalidate() rapidly and we must keep the latest win.
        validationJob = viewModelScope.launch {
            try {
                val validations = validateSellOrderUseCase(
                    state.planId, amount, price, state.minOrderFiat, state.spotPrice,
                    state.avgBuyPrice, state.feeRate
                )
                _uiState.update {
                    it.copy(validations = validations, validating = false, validationFailed = false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "validate failed: ${e.message}")
                _uiState.update {
                    it.copy(validations = emptyList(), validating = false, validationFailed = true)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SellWizardViewModel"
        private const val INIT_TIMEOUT_MS = 10_000L
    }
}
