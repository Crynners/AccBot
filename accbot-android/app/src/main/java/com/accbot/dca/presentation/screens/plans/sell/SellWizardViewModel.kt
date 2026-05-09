package com.accbot.dca.presentation.screens.plans.sell

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.RemainingInventory
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.CalculatePlanCostBasisUseCase
import com.accbot.dca.domain.usecase.CalculatePlanPnLUseCase
import com.accbot.dca.domain.usecase.LadderOrder
import com.accbot.dca.domain.usecase.LadderResult
import com.accbot.dca.domain.usecase.PlaceLadderSellUseCase
import com.accbot.dca.domain.usecase.PlaceLimitSellUseCase
import com.accbot.dca.domain.usecase.SellValidation
import com.accbot.dca.domain.usecase.ValidateSellOrderUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.exchange.MinOrderSizeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * State + actions for the two-step limit-sell wizard (input -> confirm -> submit).
 *
 * Cost basis: the avg buy price prefilled in the UI is computed via timestamp-aware
 * cheapest-first ([CalculatePlanCostBasisUseCase]). Manual override is supported via
 * [setAvgBuyPrice]; [resetAvgBuyPrice] returns to auto.
 *
 * Three-field calculator: [setAmount], [setPrice], [setNetFiat] each record the field as
 * "most recently edited"; [SellCalculatorMath.recompute] fills the third field when the
 * other two are set.
 */
@HiltViewModel
class SellWizardViewModel @Inject constructor(
    private val validateSellOrderUseCase: ValidateSellOrderUseCase,
    private val placeLimitSellUseCase: PlaceLimitSellUseCase,
    private val placeLadderSellUseCase: PlaceLadderSellUseCase,
    private val calculatePlanPnLUseCase: CalculatePlanPnLUseCase,
    private val calculatePlanCostBasisUseCase: CalculatePlanCostBasisUseCase,
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() {

    enum class Step { INPUT, CONFIRM }
    enum class LadderRangeMode { PRICE, PROFIT_PCT }

    /** Snapshot of partial ladder result so the UI can present a dialog after submit. */
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
        val ladderEnabled: Boolean = false,
        val ladderRangeMode: LadderRangeMode = LadderRangeMode.PROFIT_PCT,
        val ladderFromInput: String = "",
        val ladderToInput: String = "",
        val ladderCountInput: String = "5",
        val ladderAmountMode: LadderGenerator.AmountMode = LadderGenerator.AmountMode.EQUAL_CRYPTO,
        val ladderPreview: List<LadderOrder> = emptyList(),
        val ladderHardError: String? = null,
        val ladderOutcome: LadderSubmitOutcome? = null,
        val step: Step = Step.INPUT,
        val initializing: Boolean = true,
        val submitting: Boolean = false,
        val submitError: String? = null,
        val showTimeoutDialog: Boolean = false,
        val dismissRequested: Boolean = false
    ) {
        /** Effective avg buy: parsed manual input takes precedence, else auto. */
        val avgBuyPrice: BigDecimal?
            get() = if (avgBuyPriceManual) avgBuyPriceInput.toBigDecimalOrNull() else avgBuyPriceAuto

        val canProceed: Boolean
            get() = if (ladderEnabled) {
                ladderHardError == null && ladderPreview.size >= 2 &&
                    amountInput.toBigDecimalOrNull() != null
            } else {
                validations.none { it is SellValidation.HardError } &&
                    amountInput.isNotBlank() && priceInput.isNotBlank() &&
                    amountInput.toBigDecimalOrNull() != null &&
                    priceInput.toBigDecimalOrNull() != null
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var initialized = false

    fun init(planId: Long) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val plan = database.dcaPlanDao().getPlanById(planId)
            if (plan == null) {
                _uiState.update { it.copy(initializing = false) }
                return@launch
            }

            val credentials = try {
                credentialsStore.getCredentials(plan.connectionId, userPreferences.isSandboxMode())
            } catch (e: Exception) {
                Log.w(TAG, "getCredentials failed: ${e.message}")
                null
            }
            val api = credentials?.let { exchangeApiFactory.create(it) }
            val spot = api?.let {
                try {
                    withTimeoutOrNull(10_000) { it.getCurrentPrice(plan.crypto, plan.fiat) }
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
                minOrderSizeRepository.getMinOrderSize(plan.exchange, plan.crypto, plan.fiat)
            } catch (e: Exception) {
                Log.w(TAG, "min order fetch failed: ${e.message}")
                BigDecimal.ZERO
            }

            _uiState.update {
                it.copy(
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
                    initializing = false
                )
            }
            revalidate()
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

    fun setPriceBreakeven() {
        _uiState.value.avgBuyPrice?.let {
            setPrice(it.setScale(2, RoundingMode.HALF_UP).toPlainString())
        }
    }

    fun setPriceAvgPlus(pct: Int) {
        _uiState.value.avgBuyPrice?.let { avg ->
            val multiplier = BigDecimal.ONE +
                BigDecimal(pct).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
            setPrice((avg * multiplier).setScale(2, RoundingMode.HALF_UP).toPlainString())
        }
    }

    fun setPriceSpotPlus(pct: Int) {
        _uiState.value.spotPrice?.let { spot ->
            val multiplier = BigDecimal.ONE +
                BigDecimal(pct).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
            setPrice((spot * multiplier).setScale(2, RoundingMode.HALF_UP).toPlainString())
        }
    }

    /** Apply a profit-target preset to the net field: N = A * avg * (1 + profitPct). */
    fun applyNetProfitPreset(profitPct: Double) {
        val st = _uiState.value
        val a = st.amountInput.toBigDecimalOrNull() ?: return
        val avg = st.avgBuyPrice ?: return
        if (a <= BigDecimal.ZERO || avg <= BigDecimal.ZERO) return
        val target = a * avg * (BigDecimal.ONE + BigDecimal(profitPct))
        setNetFiat(target.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }

    fun setAvgBuyPrice(value: String) {
        _uiState.update { st ->
            val parsed = value.toBigDecimalOrNull()
            val isManual = parsed != null && parsed.compareTo(st.avgBuyPriceAuto ?: BigDecimal("-1")) != 0
            st.copy(avgBuyPriceInput = value, avgBuyPriceManual = isManual)
        }
        revalidate()
        recomputeLadderPreview()
    }

    fun resetAvgBuyPrice() {
        _uiState.update { st ->
            st.copy(
                avgBuyPriceInput = st.avgBuyPriceAuto?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "",
                avgBuyPriceManual = false
            )
        }
        revalidate()
        recomputeLadderPreview()
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
        revalidate()
        if (field == SellCalculatorMath.Field.AMOUNT) recomputeLadderPreview()
    }

    // --- Ladder handlers ---

    fun setLadderEnabled(enabled: Boolean) {
        _uiState.update { it.copy(ladderEnabled = enabled, ladderOutcome = null) }
        recomputeLadderPreview()
    }

    fun setLadderRangeMode(mode: LadderRangeMode) {
        _uiState.update { it.copy(ladderRangeMode = mode, ladderFromInput = "", ladderToInput = "") }
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

    private fun recomputeLadderPreview() {
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
                        it.copy(ladderPreview = emptyList(), ladderHardError = "Pro profit % musí být zadaná průměrná nákupní cena")
                    }
                    return
                }
                val fPct = st.ladderFromInput.toBigDecimalOrNull()
                val tPct = st.ladderToInput.toBigDecimalOrNull()
                val f = fPct?.let { avg * (BigDecimal.ONE + it.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)) }
                val t = tPct?.let { avg * (BigDecimal.ONE + it.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)) }
                f to t
            }
        }

        if (total == null || count == null || from == null || to == null) {
            _uiState.update { it.copy(ladderPreview = emptyList(), ladderHardError = null) }
            return
        }
        if (total <= BigDecimal.ZERO) {
            _uiState.update { it.copy(ladderPreview = emptyList(), ladderHardError = "Množství musí být větší než 0") }
            return
        }
        if (count < 2) {
            _uiState.update { it.copy(ladderPreview = emptyList(), ladderHardError = "Počet orderů musí být alespoň 2") }
            return
        }
        if (to <= from || from <= BigDecimal.ZERO) {
            _uiState.update { it.copy(ladderPreview = emptyList(), ladderHardError = "Do musí být větší než Od (oba kladné)") }
            return
        }
        if (total > st.availableToSell) {
            _uiState.update { it.copy(ladderPreview = emptyList(), ladderHardError = "Nemáš tolik k dispozici") }
            return
        }

        val preview = LadderGenerator.generate(total, from, to, count, st.ladderAmountMode)
        val minOrderFiatValue = preview.minOf { it.cryptoAmount * it.limitPrice }
        val hardError = if (st.minOrderFiat > BigDecimal.ZERO && minOrderFiatValue < st.minOrderFiat) {
            "Hodnota nejmenšího orderu (${minOrderFiatValue.setScale(2, RoundingMode.HALF_UP)} ${st.fiat}) je pod minimem ${st.minOrderFiat} ${st.fiat}"
        } else null

        _uiState.update { it.copy(ladderPreview = preview, ladderHardError = hardError) }
    }

    fun submitLadder() {
        val st = _uiState.value
        if (!st.ladderEnabled || st.ladderPreview.size < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, submitError = null, ladderOutcome = null) }
            val result = withTimeoutOrNull(30_000L) {
                placeLadderSellUseCase(st.planId, st.ladderPreview)
            }
            when (result) {
                null -> _uiState.update { it.copy(submitting = false, showTimeoutDialog = true) }
                is LadderResult.AllPlaced -> _uiState.update {
                    it.copy(
                        submitting = false,
                        ladderOutcome = LadderSubmitOutcome(
                            placed = result.placedTxIds.size,
                            total = result.placedTxIds.size,
                            reason = null
                        )
                    )
                }
                is LadderResult.PartialFailure -> _uiState.update {
                    it.copy(
                        submitting = false,
                        ladderOutcome = LadderSubmitOutcome(
                            placed = result.placedTxIds.size,
                            total = result.totalCount,
                            reason = result.reason
                        )
                    )
                }
            }
        }
    }

    fun consumeLadderOutcome() {
        _uiState.update { it.copy(ladderOutcome = null, dismissRequested = true) }
    }

    fun proceedToConfirm() {
        _uiState.update { it.copy(step = Step.CONFIRM, submitError = null) }
    }

    fun back() {
        _uiState.update { it.copy(step = Step.INPUT, submitError = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.ladderEnabled) {
            submitLadder()
            return
        }
        val amount = state.amountInput.toBigDecimalOrNull() ?: return
        val price = state.priceInput.toBigDecimalOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, submitError = null) }

            val result = withTimeoutOrNull(15_000L) {
                placeLimitSellUseCase(state.planId, amount, price)
            }

            when {
                result == null ->
                    _uiState.update { it.copy(submitting = false, showTimeoutDialog = true) }
                result.isSuccess ->
                    _uiState.update { it.copy(submitting = false, dismissRequested = true) }
                else ->
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submitError = result.exceptionOrNull()?.message ?: "Neznámá chyba"
                        )
                    }
            }
        }
    }

    fun dismissTimeoutDialog() {
        _uiState.update { it.copy(showTimeoutDialog = false) }
    }

    fun consumeDismiss() {
        _uiState.update { it.copy(dismissRequested = false) }
    }

    private fun revalidate() {
        val state = _uiState.value
        val amount = state.amountInput.toBigDecimalOrNull()
        val price = state.priceInput.toBigDecimalOrNull()
        if (amount == null || price == null) {
            _uiState.update { it.copy(validations = emptyList()) }
            return
        }
        viewModelScope.launch {
            val validations = try {
                validateSellOrderUseCase(
                    state.planId, amount, price, state.minOrderFiat, state.spotPrice,
                    state.avgBuyPrice, state.feeRate
                )
            } catch (e: Exception) {
                Log.w(TAG, "validate failed: ${e.message}")
                emptyList()
            }
            _uiState.update { it.copy(validations = validations) }
        }
    }

    companion object {
        private const val TAG = "SellWizardViewModel"
    }
}
