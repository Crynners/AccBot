package com.accbot.dca.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.presentation.model.MonthlyCostEstimate
import com.accbot.dca.domain.model.ExchangeFilter
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.MinOrderSizeRepository
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Immutable
data class AddPlanUiState(
    // Sandbox state (immutable after init)
    val isSandboxMode: Boolean = false,
    val availableExchanges: List<Exchange> = emptyList(),

    // Exchange setup
    val selectedExchange: Exchange? = null,
    val selectedExchangeInstructions: ExchangeInstructions? = null,
    val hasCredentials: Boolean = false,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",
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
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
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
        get() {
            if (selectedExchange == null) return false
            if (!hasCredentials) {
                if (apiKey.isBlank() || apiSecret.isBlank()) return false
                // Coinmate requires clientId
                if (selectedExchange == Exchange.COINMATE && clientId.isBlank()) return false
            }
            val amountValue = amount.toBigDecimalOrNull() ?: return false
            if (amountValue <= BigDecimal.ZERO) return false
            if (amountBelowMinimum) return false
            if (withdrawalEnabled && withdrawalAddress.isBlank()) return false
            if (selectedFrequency == DcaFrequency.CUSTOM && !CronUtils.isValidCron(cronExpression)) return false
            return true
        }
}

@HiltViewModel
class AddPlanViewModel @Inject constructor(
    private val dcaPlanDao: DcaPlanDao,
    private val credentialsStore: CredentialsStore,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val userPreferences: UserPreferences,
    private val calculateMonthlyCost: CalculateMonthlyCostUseCase,
    private val minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlanUiState())
    val uiState: StateFlow<AddPlanUiState> = _uiState.asStateFlow()

    // Cache sandbox mode to avoid repeated SharedPreferences reads
    private val isSandbox: Boolean = userPreferences.isSandboxMode()
    private var estimateJob: Job? = null

    init {
        // Initialize sandbox state once - avoids repeated calls during recomposition
        _uiState.update {
            it.copy(
                isSandboxMode = isSandbox,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
            )
        }
    }

    fun selectExchange(exchange: Exchange) {
        val hasCredentials = credentialsStore.hasCredentials(exchange, isSandbox)
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        _uiState.update { state ->
            state.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                hasCredentials = hasCredentials,
                selectedCrypto = exchange.supportedCryptos.firstOrNull() ?: "BTC",
                selectedFiat = exchange.supportedFiats.firstOrNull() ?: "EUR",
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                errorMessage = null,
                minOrderSize = null
            )
        }
        updateMinOrderSize()
    }

    fun setClientId(value: String) {
        _uiState.update { it.copy(clientId = value) }
    }

    fun setApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    fun setApiSecret(value: String) {
        _uiState.update { it.copy(apiSecret = value) }
    }

    fun setPassphrase(value: String) {
        _uiState.update { it.copy(passphrase = value) }
    }

    fun selectCrypto(crypto: String) {
        _uiState.update { it.copy(selectedCrypto = crypto) }
        updateMonthlyCostEstimate()
        updateMinOrderSize()
    }

    fun selectFiat(fiat: String) {
        _uiState.update { it.copy(selectedFiat = fiat) }
        updateMonthlyCostEstimate()
        updateMinOrderSize()
    }

    fun setAmount(amount: String) {
        _uiState.update { it.copy(amount = amount) }
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
        _uiState.update { it.copy(withdrawalAddress = address) }
    }

    private fun updateMinOrderSize() {
        viewModelScope.launch {
            val exchange = _uiState.value.selectedExchange ?: return@launch
            val min = minOrderSizeRepository.getMinOrderSize(
                exchange, _uiState.value.selectedCrypto, _uiState.value.selectedFiat
            )
            _uiState.update { it.copy(minOrderSize = min) }
        }
    }

    private fun updateMonthlyCostEstimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value
            val amount = state.amount.toBigDecimalOrNull()
            if (amount == null) {
                _uiState.update { it.copy(monthlyCostEstimate = null) }
                return@launch
            }
            val estimate = calculateMonthlyCost.computeEstimate(
                amount = amount,
                frequency = state.selectedFrequency,
                cronExpression = state.cronExpression.takeIf { it.isNotBlank() },
                strategy = state.selectedStrategy,
                crypto = state.selectedCrypto,
                fiat = state.selectedFiat
            )
            _uiState.update { it.copy(monthlyCostEstimate = estimate) }
        }
    }

    fun createPlan() {
        val state = _uiState.value
        val exchange = state.selectedExchange ?: return

        // Prevent concurrent plan creation
        if (state.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // Validate and save credentials if new
                if (!state.hasCredentials) {
                    val result = validateAndSaveCredentialsUseCase.execute(
                        exchange = exchange,
                        apiKey = state.apiKey,
                        apiSecret = state.apiSecret,
                        passphrase = state.passphrase.takeIf { it.isNotBlank() },
                        clientId = state.clientId.takeIf { it.isNotBlank() }
                    )

                    when (result) {
                        is CredentialValidationResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = result.message
                                )
                            }
                            return@launch
                        }
                        is CredentialValidationResult.Success -> {
                            // Credentials validated and saved, continue with plan creation
                        }
                    }
                }

                // Create plan
                val amount = state.amount.toBigDecimal()
                val now = Instant.now()
                val nextExecution = if (state.selectedFrequency == DcaFrequency.CUSTOM) {
                    CronUtils.getNextExecution(state.cronExpression, now)
                        ?: now.plus(Duration.ofMinutes(1440))
                } else {
                    now.plus(Duration.ofMinutes(state.selectedFrequency.intervalMinutes))
                }

                val plan = DcaPlanEntity(
                    exchange = exchange,
                    crypto = state.selectedCrypto,
                    fiat = state.selectedFiat,
                    amount = amount,
                    frequency = state.selectedFrequency,
                    cronExpression = if (state.selectedFrequency == DcaFrequency.CUSTOM) state.cronExpression else null,
                    strategy = state.selectedStrategy,
                    isEnabled = true,
                    withdrawalEnabled = state.withdrawalEnabled,
                    withdrawalAddress = if (state.withdrawalEnabled) state.withdrawalAddress.trim() else null,
                    createdAt = now,
                    nextExecutionAt = nextExecution
                )

                dcaPlanDao.insertPlan(plan)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to create plan"
                    )
                }
            }
        }
    }
}
