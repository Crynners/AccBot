package com.accbot.dca.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeFilter
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class OnboardingUiState(
    // Sandbox state (immutable after init)
    val isSandboxMode: Boolean = false,
    val availableExchanges: List<Exchange> = emptyList(),

    // Exchange setup
    val selectedExchange: Exchange? = null,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",
    val isValidatingCredentials: Boolean = false,
    val credentialsValid: Boolean = false,
    val credentialsError: String? = null,

    // First plan setup
    val selectedCrypto: String = "BTC",
    val selectedFiat: String = "EUR",
    val amount: String = "100",
    val selectedFrequency: DcaFrequency = DcaFrequency.DAILY,

    // General state
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val onboardingPreferences: OnboardingPreferences,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val dcaPlanDao: DcaPlanDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Initialize sandbox state once - avoids repeated calls during recomposition
        val isSandbox = userPreferences.isSandboxMode()
        _uiState.update {
            it.copy(
                isSandboxMode = isSandbox,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
            )
        }
    }

    // Exchange setup functions
    fun selectExchange(exchange: Exchange) {
        _uiState.update { state ->
            state.copy(
                selectedExchange = exchange,
                selectedCrypto = exchange.supportedCryptos.firstOrNull() ?: "BTC",
                selectedFiat = exchange.supportedFiats.firstOrNull() ?: "EUR",
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                credentialsValid = false,
                credentialsError = null
            )
        }
    }

    fun setClientId(value: String) {
        _uiState.update { it.copy(clientId = value, credentialsError = null) }
    }

    fun setApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, credentialsError = null) }
    }

    fun setApiSecret(value: String) {
        _uiState.update { it.copy(apiSecret = value, credentialsError = null) }
    }

    fun setPassphrase(value: String) {
        _uiState.update { it.copy(passphrase = value, credentialsError = null) }
    }

    fun validateAndSaveCredentials(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Guard against concurrent validation calls (race condition prevention)
        if (state.isValidatingCredentials) return

        val exchange = state.selectedExchange ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingCredentials = true, credentialsError = null) }

            val result = validateAndSaveCredentialsUseCase.execute(
                exchange = exchange,
                apiKey = state.apiKey,
                apiSecret = state.apiSecret,
                passphrase = state.passphrase.takeIf { it.isNotBlank() },
                clientId = state.clientId.takeIf { it.isNotBlank() }
            )

            when (result) {
                is CredentialValidationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsValid = true
                        )
                    }
                    onSuccess()
                }
                is CredentialValidationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsError = result.message
                        )
                    }
                }
            }
        }
    }

    // First plan functions
    fun selectCrypto(crypto: String) {
        _uiState.update { it.copy(selectedCrypto = crypto) }
    }

    fun selectFiat(fiat: String) {
        _uiState.update { it.copy(selectedFiat = fiat) }
    }

    fun setAmount(amount: String) {
        _uiState.update { it.copy(amount = amount) }
    }

    fun selectFrequency(frequency: DcaFrequency) {
        _uiState.update { it.copy(selectedFrequency = frequency) }
    }

    fun createFirstPlan(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Guard against concurrent plan creation calls (race condition prevention)
        if (state.isLoading) return

        val exchange = state.selectedExchange

        if (exchange == null) {
            _uiState.update { it.copy(error = "No exchange configured") }
            return
        }

        val amount = state.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val now = Instant.now()
                val nextExecution = now.plus(Duration.ofMinutes(state.selectedFrequency.intervalMinutes))

                val plan = DcaPlanEntity(
                    exchange = exchange,
                    crypto = state.selectedCrypto,
                    fiat = state.selectedFiat,
                    amount = amount,
                    frequency = state.selectedFrequency,
                    isEnabled = true,
                    withdrawalEnabled = false,
                    withdrawalAddress = null,
                    createdAt = now,
                    nextExecutionAt = nextExecution
                )

                dcaPlanDao.insertPlan(plan)

                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create plan"
                    )
                }
            }
        }
    }

    // Completion
    fun completeOnboarding() {
        onboardingPreferences.setOnboardingCompleted(true)
    }

    fun hasConfiguredExchange(): Boolean {
        return credentialsStore.getConfiguredExchanges().isNotEmpty()
    }
}
