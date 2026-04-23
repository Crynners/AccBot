package com.accbot.dca.presentation.screens.plans.sell

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.CalculatePlanPnLUseCase
import com.accbot.dca.domain.usecase.PlaceLimitSellUseCase
import com.accbot.dca.domain.usecase.SellValidation
import com.accbot.dca.domain.usecase.ValidateSellOrderUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
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
 * Lifecycle: the hosting bottom-sheet calls [init] once per open, then [setAmount] /
 * [setPrice] / [proceedToConfirm] / [submit] based on user actions. On successful submit
 * [UiState.dismissRequested] flips true; the sheet consumes via [consumeDismiss] and
 * closes. On timeout [UiState.showTimeoutDialog] is set so the user is warned to check
 * the exchange manually for duplicate orders.
 */
@HiltViewModel
class SellWizardViewModel @Inject constructor(
    private val validateSellOrderUseCase: ValidateSellOrderUseCase,
    private val placeLimitSellUseCase: PlaceLimitSellUseCase,
    private val calculatePlanPnLUseCase: CalculatePlanPnLUseCase,
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences
) : ViewModel() {

    enum class Step { INPUT, CONFIRM }

    data class UiState(
        val planId: Long = 0,
        val planName: String = "",
        val exchangeName: String = "",
        val crypto: String = "",
        val fiat: String = "",
        val held: BigDecimal = BigDecimal.ZERO,
        /** held crypto minus crypto already reserved by other open sells (unfilled). */
        val availableToSell: BigDecimal = BigDecimal.ZERO,
        val spotPrice: BigDecimal? = null,
        val avgBuyPrice: BigDecimal? = null,
        val minOrderSize: BigDecimal = BigDecimal("0.00001"),
        val amountInput: String = "",
        val priceInput: String = "",
        val validations: List<SellValidation> = emptyList(),
        val step: Step = Step.INPUT,
        val initializing: Boolean = true,
        val submitting: Boolean = false,
        val submitError: String? = null,
        val showTimeoutDialog: Boolean = false,
        val dismissRequested: Boolean = false
    ) {
        /** Proceed button enabled when no hard errors + both numeric inputs parse. */
        val canProceed: Boolean
            get() = validations.none { it is SellValidation.HardError } &&
                amountInput.isNotBlank() && priceInput.isNotBlank() &&
                amountInput.toBigDecimalOrNull() != null &&
                priceInput.toBigDecimalOrNull() != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var initialized = false

    /**
     * Load plan state, compute held crypto, available-to-sell (minus reservations from
     * open sells), avg buy price and a best-effort spot price. Idempotent across
     * recompositions thanks to [initialized].
     */
    fun init(planId: Long) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val plan = database.dcaPlanDao().getPlanById(planId)
            if (plan == null) {
                _uiState.update { it.copy(initializing = false) }
                return@launch
            }

            // Best-effort spot price via exchange API; null means UI shows "-".
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

            val pnl = try {
                calculatePlanPnLUseCase(planId, spot)
            } catch (e: Exception) {
                Log.w(TAG, "PnL calc failed: ${e.message}")
                null
            }
            val held = pnl?.currentCryptoHeld ?: BigDecimal.ZERO
            val avgBuy = pnl?.avgBuyPrice

            // Crypto reserved by other open sells (requested - filled) - prevents the
            // user from submitting a sell that, together with existing open sells,
            // exceeds what they actually hold.
            val openSells = database.transactionDao().getTransactionsByPlanSync(planId)
                .filter {
                    it.side == TransactionSide.SELL &&
                        it.status in setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL)
                }
            val reserved = openSells.fold(BigDecimal.ZERO) { acc, tx ->
                acc + ((tx.requestedCryptoAmount ?: BigDecimal.ZERO) - tx.cryptoAmount)
            }

            val minOrder = when (plan.exchange) {
                Exchange.BINANCE ->
                    Exchange.binanceLotStepSize[plan.crypto]?.let(::BigDecimal) ?: BigDecimal("0.00001")
                else -> BigDecimal("0.00001")
            }

            _uiState.update {
                it.copy(
                    planId = planId,
                    planName = plan.name.ifBlank { "${plan.crypto}/${plan.fiat}" },
                    exchangeName = plan.exchange.displayName,
                    crypto = plan.crypto,
                    fiat = plan.fiat,
                    held = held,
                    availableToSell = (held - reserved).max(BigDecimal.ZERO),
                    spotPrice = spot,
                    avgBuyPrice = avgBuy,
                    minOrderSize = minOrder,
                    initializing = false
                )
            }
            revalidate()
        }
    }

    fun setAmount(value: String) {
        _uiState.update { it.copy(amountInput = value) }
        revalidate()
    }

    fun setAmountPct(pct: Int) {
        val target = _uiState.value.availableToSell
            .multiply(BigDecimal(pct))
            .divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
        setAmount(target.stripTrailingZeros().toPlainString())
    }

    fun setPrice(value: String) {
        _uiState.update { it.copy(priceInput = value) }
        revalidate()
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

    fun proceedToConfirm() {
        _uiState.update { it.copy(step = Step.CONFIRM, submitError = null) }
    }

    fun back() {
        _uiState.update { it.copy(step = Step.INPUT, submitError = null) }
    }

    /**
     * Place the limit sell order. On timeout we show a warning dialog (order may be
     * placed but we couldn't confirm). On success the sheet is asked to dismiss.
     */
    fun submit() {
        val state = _uiState.value
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
                            submitError = result.exceptionOrNull()?.message ?: "Neznama chyba"
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
                    state.planId, amount, price, state.minOrderSize, state.spotPrice
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
