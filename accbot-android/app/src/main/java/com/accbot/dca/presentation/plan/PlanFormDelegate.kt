package com.accbot.dca.presentation.plan

import androidx.compose.runtime.Immutable
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.domain.util.CryptoAddressValidator
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.model.MonthlyCostEstimate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Immutable
data class PlanFormState(
    val selectedCrypto: String = "BTC",
    val selectedFiat: String = "EUR",
    val amount: String = "100",
    val selectedFrequency: DcaFrequency = DcaFrequency.DAILY,
    val cronExpression: String = "",
    val cronDescription: String? = null,
    val cronError: String? = null,
    val selectedStrategy: DcaStrategy = DcaStrategy.Classic,
    val withdrawalEnabled: Boolean = false,
    val withdrawalAddress: String = "",
    val addressError: String? = null,
    val targetAmount: String = "",
    val minOrderSize: BigDecimal? = null,
    val monthlyCostEstimate: MonthlyCostEstimate? = null
) {
    val amountBelowMinimum: Boolean
        get() {
            val min = minOrderSize ?: return false
            val amt = amount.toBigDecimalOrNull() ?: return false
            return amt < min
        }

    val isAddressValid: Boolean
        get() = !withdrawalEnabled || CryptoAddressValidator.isValid(selectedCrypto, withdrawalAddress)

    val isFormValid: Boolean
        get() {
            val amountValue = amount.toBigDecimalOrNull() ?: return false
            if (amountValue <= BigDecimal.ZERO) return false
            if (amountBelowMinimum) return false
            if (withdrawalEnabled && !isAddressValid) return false
            if (selectedFrequency == DcaFrequency.CUSTOM && !CronUtils.isValidCron(cronExpression)) return false
            return true
        }
}

/**
 * Shared delegate for plan form state and logic.
 * Used by AddPlanViewModel, OnboardingViewModel, and EditPlanViewModel.
 * Not a ViewModel — the owning ViewModel passes its coroutineScope.
 */
class PlanFormDelegate(
    private val calculateMonthlyCost: CalculateMonthlyCostUseCase,
    private val minOrderSizeRepository: MinOrderSizeRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(PlanFormState())
    val state: StateFlow<PlanFormState> = _state.asStateFlow()

    private var estimateJob: Job? = null
    private var currentExchange: Exchange? = null

    fun selectCrypto(crypto: String) {
        _state.update { it.copy(selectedCrypto = crypto) }
        updateMonthlyCostEstimate()
        updateMinOrderSize()
    }

    fun selectFiat(fiat: String) {
        _state.update { it.copy(selectedFiat = fiat) }
        updateMonthlyCostEstimate()
        updateMinOrderSize()
    }

    fun setAmount(amount: String) {
        _state.update { it.copy(amount = amount) }
        updateMonthlyCostEstimate()
    }

    fun selectFrequency(frequency: DcaFrequency) {
        _state.update {
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
        _state.update {
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
        _state.update { it.copy(selectedStrategy = strategy) }
        updateMonthlyCostEstimate()
    }

    fun setWithdrawalEnabled(enabled: Boolean) {
        _state.update { it.copy(withdrawalEnabled = enabled) }
    }

    fun setWithdrawalAddress(address: String) {
        val crypto = _state.value.selectedCrypto
        val addressError = if (address.isNotBlank() && !CryptoAddressValidator.isValid(crypto, address)) {
            "Invalid $crypto address format"
        } else {
            null
        }
        _state.update { it.copy(withdrawalAddress = address, addressError = addressError) }
    }

    fun setTargetAmount(value: String) {
        _state.update { it.copy(targetAmount = value) }
    }

    /** Initialize form defaults from an exchange (used when exchange is selected). */
    fun initFromExchange(exchange: Exchange) {
        currentExchange = exchange
        val fiat = exchange.supportedFiats.firstOrNull() ?: "EUR"
        val minAmount = exchange.minOrderSize[fiat]
        _state.update {
            it.copy(
                selectedCrypto = exchange.supportedCryptos.firstOrNull() ?: "BTC",
                selectedFiat = fiat,
                amount = minAmount?.stripTrailingZeros()?.toPlainString() ?: it.amount,
                minOrderSize = null
            )
        }
        updateMinOrderSize()
    }

    /** Populate form from an existing plan entity (used by EditPlan). */
    fun initFromPlan(
        exchange: Exchange,
        crypto: String,
        fiat: String,
        amount: String,
        frequency: DcaFrequency,
        cronExpression: String,
        strategy: DcaStrategy,
        withdrawalEnabled: Boolean,
        withdrawalAddress: String,
        targetAmount: String
    ) {
        currentExchange = exchange
        val cronDesc = if (cronExpression.isNotBlank()) CronUtils.describeCron(cronExpression) else null
        _state.update {
            PlanFormState(
                selectedCrypto = crypto,
                selectedFiat = fiat,
                amount = amount,
                selectedFrequency = frequency,
                cronExpression = cronExpression,
                cronDescription = cronDesc,
                cronError = null,
                selectedStrategy = strategy,
                withdrawalEnabled = withdrawalEnabled,
                withdrawalAddress = withdrawalAddress,
                targetAmount = targetAmount
            )
        }
        updateMinOrderSize()
        updateMonthlyCostEstimate()
    }

    private fun updateMinOrderSize() {
        val exchange = currentExchange ?: return
        coroutineScope.launch {
            val min = minOrderSizeRepository.getMinOrderSize(
                exchange, _state.value.selectedCrypto, _state.value.selectedFiat
            )
            _state.update { it.copy(minOrderSize = min) }
        }
    }

    private fun updateMonthlyCostEstimate() {
        estimateJob?.cancel()
        estimateJob = coroutineScope.launch {
            delay(300)
            val s = _state.value
            val amount = s.amount.toBigDecimalOrNull()
            if (amount == null) {
                _state.update { it.copy(monthlyCostEstimate = null) }
                return@launch
            }
            val estimate = calculateMonthlyCost.computeEstimate(
                amount = amount,
                frequency = s.selectedFrequency,
                cronExpression = s.cronExpression.takeIf { it.isNotBlank() },
                strategy = s.selectedStrategy,
                crypto = s.selectedCrypto,
                fiat = s.selectedFiat
            )
            _state.update { it.copy(monthlyCostEstimate = estimate) }
        }
    }
}
