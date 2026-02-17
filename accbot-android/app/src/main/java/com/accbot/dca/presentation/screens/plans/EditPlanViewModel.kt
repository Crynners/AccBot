package com.accbot.dca.presentation.screens.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.model.MonthlyCostEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class EditPlanUiState(
    val planId: Long = 0,
    val crypto: String = "",
    val fiat: String = "",
    val exchangeName: String = "",
    val amount: String = "",
    val selectedFrequency: DcaFrequency = DcaFrequency.DAILY,
    val cronExpression: String = "",
    val cronDescription: String? = null,
    val cronError: String? = null,
    val selectedStrategy: DcaStrategy = DcaStrategy.Classic,
    val withdrawalEnabled: Boolean = false,
    val withdrawalAddress: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val addressError: String? = null,
    val monthlyCostEstimate: MonthlyCostEstimate? = null,
    val minOrderSize: BigDecimal? = null
) {
    val amountBelowMinimum: Boolean
        get() {
            val min = minOrderSize ?: return false
            val amt = amount.toBigDecimalOrNull() ?: return false
            return amt < min
        }

    val isValid: Boolean
        get() = amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                !amountBelowMinimum &&
                (!withdrawalEnabled || isValidCryptoAddress(crypto, withdrawalAddress)) &&
                (selectedFrequency != DcaFrequency.CUSTOM || CronUtils.isValidCron(cronExpression))

    val isAddressValid: Boolean
        get() = !withdrawalEnabled || isValidCryptoAddress(crypto, withdrawalAddress)
}

/**
 * Basic validation for cryptocurrency wallet addresses.
 * This is a simplified validation - actual address format depends on the crypto.
 */
private fun isValidCryptoAddress(crypto: String, address: String): Boolean {
    if (address.isBlank()) return false

    val trimmed = address.trim()

    return when (crypto.uppercase()) {
        "BTC" -> isValidBtcAddress(trimmed)
        "ETH", "SOL", "ADA", "DOT" -> isValidGenericAddress(trimmed, minLength = 26, maxLength = 128)
        "LTC" -> isValidLtcAddress(trimmed)
        else -> isValidGenericAddress(trimmed, minLength = 20, maxLength = 128)
    }
}

private fun isValidBtcAddress(address: String): Boolean {
    // Legacy addresses start with 1 (P2PKH) or 3 (P2SH), 25-34 chars
    // Native SegWit (Bech32) start with bc1, 42-62 chars
    return when {
        address.startsWith("1") || address.startsWith("3") ->
            address.length in 25..34 && address.all { it.isLetterOrDigit() }
        address.startsWith("bc1") ->
            address.length in 42..62 && address.all { it.isLetterOrDigit() }
        else -> false
    }
}

private fun isValidLtcAddress(address: String): Boolean {
    // Litecoin: starts with L, M, or ltc1
    return when {
        address.startsWith("L") || address.startsWith("M") ->
            address.length in 25..34 && address.all { it.isLetterOrDigit() }
        address.startsWith("ltc1") ->
            address.length in 42..62 && address.all { it.isLetterOrDigit() }
        else -> false
    }
}

private fun isValidGenericAddress(address: String, minLength: Int, maxLength: Int): Boolean {
    return address.length in minLength..maxLength &&
            address.all { it.isLetterOrDigit() || it == '_' }
}

@HiltViewModel
class EditPlanViewModel @Inject constructor(
    private val dcaPlanDao: DcaPlanDao,
    private val marketDataService: MarketDataService,
    private val minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditPlanUiState())
    val uiState: StateFlow<EditPlanUiState> = _uiState.asStateFlow()

    private var originalPlan: DcaPlanEntity? = null
    private var estimateJob: Job? = null

    fun loadPlan(planId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val plan = dcaPlanDao.getPlanById(planId)
                if (plan == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Plan not found") }
                    return@launch
                }

                originalPlan = plan

                val cronExpr = plan.cronExpression ?: ""
                _uiState.update {
                    it.copy(
                        planId = plan.id,
                        crypto = plan.crypto,
                        fiat = plan.fiat,
                        exchangeName = plan.exchange.displayName,
                        amount = plan.amount.toPlainString(),
                        selectedFrequency = plan.frequency,
                        cronExpression = cronExpr,
                        cronDescription = if (cronExpr.isNotBlank()) CronUtils.describeCron(cronExpr) else null,
                        cronError = null,
                        selectedStrategy = plan.strategy,
                        withdrawalEnabled = plan.withdrawalEnabled,
                        withdrawalAddress = plan.withdrawalAddress ?: "",
                        isLoading = false
                    )
                }
                updateMonthlyCostEstimate()

                // Fetch min order size for this plan's exchange/pair
                val min = minOrderSizeRepository.getMinOrderSize(plan.exchange, plan.crypto, plan.fiat)
                _uiState.update { it.copy(minOrderSize = min) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load plan"
                    )
                }
            }
        }
    }

    fun setAmount(amount: String) {
        _uiState.update { it.copy(amount = amount, error = null) }
        updateMonthlyCostEstimate()
    }

    fun selectFrequency(frequency: DcaFrequency) {
        _uiState.update {
            it.copy(
                selectedFrequency = frequency,
                cronExpression = if (frequency != DcaFrequency.CUSTOM) "" else it.cronExpression,
                cronDescription = if (frequency != DcaFrequency.CUSTOM) null else it.cronDescription,
                cronError = if (frequency != DcaFrequency.CUSTOM) null else it.cronError
            )
        }
        updateMonthlyCostEstimate()
    }

    fun setCronExpression(cron: String) {
        val isValid = CronUtils.isValidCron(cron)
        val description = if (isValid) CronUtils.describeCron(cron) else null
        val error = if (cron.isNotBlank() && !isValid) "Invalid CRON expression" else null
        _uiState.update {
            it.copy(
                cronExpression = cron,
                cronDescription = description,
                cronError = error
            )
        }
        if (isValid) {
            updateMonthlyCostEstimate()
        }
    }

    fun selectStrategy(strategy: DcaStrategy) {
        _uiState.update { it.copy(selectedStrategy = strategy) }
        updateMonthlyCostEstimate()
    }

    fun setWithdrawalEnabled(enabled: Boolean) {
        _uiState.update { it.copy(withdrawalEnabled = enabled) }
    }

    fun setWithdrawalAddress(address: String) {
        val state = _uiState.value
        val addressError = if (address.isNotBlank() && !isValidCryptoAddress(state.crypto, address)) {
            "Invalid ${state.crypto} address format"
        } else {
            null
        }
        _uiState.update { it.copy(withdrawalAddress = address, addressError = addressError) }
    }

    private fun updateMonthlyCostEstimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(300)
            computeEstimate()
        }
    }

    private fun getEffectiveIntervalMinutes(state: EditPlanUiState): Long {
        return if (state.selectedFrequency == DcaFrequency.CUSTOM) {
            CronUtils.getIntervalMinutesEstimate(state.cronExpression) ?: 1440L
        } else {
            state.selectedFrequency.intervalMinutes
        }
    }

    private suspend fun computeEstimate() {
        val state = _uiState.value
        val amount = state.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(monthlyCostEstimate = null) }
            return
        }

        val intervalMinutes = getEffectiveIntervalMinutes(state)
        if (intervalMinutes <= 0) {
            _uiState.update { it.copy(monthlyCostEstimate = null) }
            return
        }

        val runsPerMonth = BigDecimal(30 * 24 * 60).divide(
            BigDecimal(intervalMinutes),
            2, RoundingMode.HALF_UP
        )

        when (val strategy = state.selectedStrategy) {
            is DcaStrategy.Classic -> {
                val monthly = amount.multiply(runsPerMonth)
                _uiState.update {
                    it.copy(monthlyCostEstimate = MonthlyCostEstimate(
                        minMonthly = monthly,
                        maxMonthly = monthly,
                        currentMonthly = monthly,
                        currentInfo = null
                    ))
                }
            }
            is DcaStrategy.AthBased -> {
                computeStrategyEstimate(
                    amount = amount,
                    runsPerMonth = runsPerMonth,
                    minMult = strategy.tiers.minOf { it.multiplier },
                    maxMult = strategy.tiers.maxOf { it.multiplier },
                    fetchCurrentMultiplier = {
                        val cryptoData = marketDataService.getCryptoData(state.crypto, state.fiat)
                        if (cryptoData != null) {
                            val mult = strategy.tiers.sortedBy { it.maxDistancePercent }
                                .firstOrNull { cryptoData.athDistance <= it.maxDistancePercent }
                                ?.multiplier ?: 1.0f
                            mult to "${state.crypto} is ${cryptoData.athDistancePercent}% below ATH"
                        } else null
                    }
                )
            }
            is DcaStrategy.FearAndGreed -> {
                computeStrategyEstimate(
                    amount = amount,
                    runsPerMonth = runsPerMonth,
                    minMult = strategy.tiers.minOf { it.multiplier },
                    maxMult = strategy.tiers.maxOf { it.multiplier },
                    fetchCurrentMultiplier = {
                        val fngData = marketDataService.getFearGreedIndex()
                        if (fngData != null) {
                            val mult = strategy.tiers.sortedBy { it.maxIndex }
                                .firstOrNull { fngData.value <= it.maxIndex }
                                ?.multiplier ?: 1.0f
                            mult to "Fear & Greed: ${fngData.value} (${fngData.classification})"
                        } else null
                    }
                )
            }
        }
    }

    private suspend fun computeStrategyEstimate(
        amount: BigDecimal,
        runsPerMonth: BigDecimal,
        minMult: Float,
        maxMult: Float,
        fetchCurrentMultiplier: suspend () -> Pair<Float, String>?
    ) {
        val minMonthly = amount.multiply(BigDecimal(minMult.toString())).multiply(runsPerMonth)
        val maxMonthly = amount.multiply(BigDecimal(maxMult.toString())).multiply(runsPerMonth)

        // Show range immediately
        _uiState.update {
            it.copy(monthlyCostEstimate = MonthlyCostEstimate(
                minMonthly = minMonthly, maxMonthly = maxMonthly
            ))
        }

        // Fetch live data, then re-read current state to avoid stale values
        val result = fetchCurrentMultiplier()
        val freshState = _uiState.value
        val freshAmount = freshState.amount.toBigDecimalOrNull() ?: return
        val freshIntervalMinutes = getEffectiveIntervalMinutes(freshState)
        if (freshIntervalMinutes <= 0) return
        val freshRunsPerMonth = BigDecimal(30 * 24 * 60).divide(
            BigDecimal(freshIntervalMinutes),
            2, RoundingMode.HALF_UP
        )
        val freshMinMonthly = freshAmount.multiply(BigDecimal(minMult.toString())).multiply(freshRunsPerMonth)
        val freshMaxMonthly = freshAmount.multiply(BigDecimal(maxMult.toString())).multiply(freshRunsPerMonth)

        val currentMonthly = result?.let { (mult, _) ->
            freshAmount.multiply(BigDecimal(mult.toString())).multiply(freshRunsPerMonth)
        }

        _uiState.update {
            it.copy(monthlyCostEstimate = MonthlyCostEstimate(
                minMonthly = freshMinMonthly,
                maxMonthly = freshMaxMonthly,
                currentMonthly = currentMonthly,
                currentInfo = result?.second
            ))
        }
    }

    fun savePlan(onSuccess: () -> Unit) {
        val state = _uiState.value
        val plan = originalPlan ?: return

        // Guard against concurrent saves
        if (state.isSaving) return

        val amount = state.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        // Validate withdrawal address if enabled
        if (state.withdrawalEnabled && !isValidCryptoAddress(state.crypto, state.withdrawalAddress)) {
            _uiState.update { it.copy(error = "Please enter a valid ${state.crypto} wallet address") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                // Calculate new next execution if frequency changed
                val frequencyChanged = state.selectedFrequency != plan.frequency ||
                    (state.selectedFrequency == DcaFrequency.CUSTOM && state.cronExpression != plan.cronExpression)
                val nextExecution = if (frequencyChanged) {
                    if (state.selectedFrequency == DcaFrequency.CUSTOM) {
                        CronUtils.getNextExecution(state.cronExpression, Instant.now())
                            ?: Instant.now().plus(Duration.ofMinutes(1440))
                    } else {
                        Instant.now().plus(Duration.ofMinutes(state.selectedFrequency.intervalMinutes))
                    }
                } else {
                    plan.nextExecutionAt
                }

                val updatedPlan = plan.copy(
                    amount = amount,
                    frequency = state.selectedFrequency,
                    cronExpression = if (state.selectedFrequency == DcaFrequency.CUSTOM) state.cronExpression else null,
                    strategy = state.selectedStrategy,
                    withdrawalEnabled = state.withdrawalEnabled,
                    withdrawalAddress = if (state.withdrawalEnabled) state.withdrawalAddress.trim() else null,
                    nextExecutionAt = nextExecution
                )

                dcaPlanDao.updatePlan(updatedPlan)

                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save plan"
                    )
                }
            }
        }
    }
}
