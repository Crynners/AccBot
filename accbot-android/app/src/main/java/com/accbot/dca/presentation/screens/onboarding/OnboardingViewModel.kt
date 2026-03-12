package com.accbot.dca.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeFilter
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.usecase.CreateDcaPlanUseCase
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.plan.PlanFormDelegate
import com.accbot.dca.presentation.plan.PlanFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class OnboardingUiState(
    // Sandbox state (immutable after init)
    val isSandboxMode: Boolean = false,
    val availableExchanges: List<Exchange> = emptyList(),
    val showExperimental: Boolean = false,

    // Exchange setup
    val selectedExchange: Exchange? = null,
    val selectedExchangeInstructions: ExchangeInstructions? = null,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",
    val isValidatingCredentials: Boolean = false,
    val credentialsValid: Boolean = false,
    val credentialsError: String? = null,

    // Plan form (from delegate)
    val planForm: PlanFormState = PlanFormState(),

    // General state
    val isLoading: Boolean = false,
    val error: String? = null,
    val planCreated: Boolean = false  // persisted via OnboardingPreferences
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val onboardingPreferences: OnboardingPreferences,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val createDcaPlanUseCase: CreateDcaPlanUseCase,
    private val userPreferences: UserPreferences,
    calculateMonthlyCost: CalculateMonthlyCostUseCase,
    minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() {

    val planForm = PlanFormDelegate(calculateMonthlyCost, minOrderSizeRepository, viewModelScope)

    private val _localState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> = combine(
        _localState,
        planForm.state
    ) { local, form -> local.copy(planForm = form) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingUiState())

    init {
        val isSandbox = userPreferences.isSandboxMode()
        val showExperimental = userPreferences.areExperimentalExchangesEnabled()
        // Detect already-configured exchange (e.g. credentials saved on ExchangeSetupScreen).
        val configured = credentialsStore.getConfiguredExchanges(isSandbox).firstOrNull()
        _localState.update {
            it.copy(
                isSandboxMode = isSandbox,
                showExperimental = showExperimental,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
                    .filter { exchange -> showExperimental || exchange.isStable },
                selectedExchange = configured,
                credentialsValid = configured != null,
                planCreated = onboardingPreferences.isPlanCreatedDuringOnboarding()
            )
        }
        if (configured != null) {
            planForm.initFromExchange(configured)
        }
    }

    fun setExperimentalExchangesEnabled(enabled: Boolean) {
        userPreferences.setExperimentalExchangesEnabled(enabled)
        val isSandbox = _localState.value.isSandboxMode
        _localState.update {
            it.copy(
                showExperimental = enabled,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
                    .filter { exchange -> enabled || exchange.isStable }
            )
        }
    }

    // Exchange setup functions
    fun selectExchange(exchange: Exchange) {
        val isSandbox = _localState.value.isSandboxMode
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        _localState.update { state ->
            state.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                credentialsValid = false,
                credentialsError = null
            )
        }
        planForm.initFromExchange(exchange)
    }

    fun setClientId(value: String) {
        _localState.update { it.copy(clientId = value, credentialsError = null) }
    }

    fun setApiKey(value: String) {
        _localState.update { it.copy(apiKey = value, credentialsError = null) }
    }

    fun setApiSecret(value: String) {
        _localState.update { it.copy(apiSecret = value, credentialsError = null) }
    }

    fun setPassphrase(value: String) {
        _localState.update { it.copy(passphrase = value, credentialsError = null) }
    }

    fun validateAndSaveCredentials(onSuccess: () -> Unit) {
        val state = _localState.value

        // Guard against concurrent validation calls (race condition prevention)
        if (state.isValidatingCredentials) return

        val exchange = state.selectedExchange ?: return

        viewModelScope.launch {
            _localState.update { it.copy(isValidatingCredentials = true, credentialsError = null) }

            val result = validateAndSaveCredentialsUseCase.execute(
                exchange = exchange,
                apiKey = state.apiKey,
                apiSecret = state.apiSecret,
                passphrase = state.passphrase.takeIf { it.isNotBlank() },
                clientId = state.clientId.takeIf { it.isNotBlank() }
            )

            when (result) {
                is CredentialValidationResult.Success -> {
                    _localState.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsValid = true
                        )
                    }
                    onSuccess()
                }
                is CredentialValidationResult.Error -> {
                    _localState.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsError = result.message
                        )
                    }
                }
            }
        }
    }

    fun createFirstPlan(onSuccess: () -> Unit) {
        val state = uiState.value
        val form = state.planForm

        // Guard against concurrent plan creation calls (race condition prevention)
        if (state.isLoading) return

        val exchange = state.selectedExchange

        if (exchange == null) {
            _localState.update { it.copy(error = "No exchange configured") }
            return
        }

        val amount = form.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _localState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        viewModelScope.launch {
            _localState.update { it.copy(isLoading = true, error = null) }

            try {
                createDcaPlanUseCase.execute(
                    exchange = exchange,
                    crypto = form.selectedCrypto,
                    fiat = form.selectedFiat,
                    amount = amount,
                    frequency = form.selectedFrequency,
                    cronExpression = if (form.selectedFrequency == DcaFrequency.CUSTOM) form.cronExpression else null,
                    strategy = form.selectedStrategy,
                    withdrawalEnabled = form.withdrawalEnabled,
                    withdrawalAddress = if (form.withdrawalEnabled) form.withdrawalAddress.trim() else null,
                    targetAmount = form.targetAmount.toBigDecimalOrNull()
                )

                onboardingPreferences.setPlanCreatedDuringOnboarding(true)
                _localState.update { it.copy(isLoading = false, planCreated = true) }
                onSuccess()
            } catch (e: Exception) {
                _localState.update {
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
        onboardingPreferences.setPlanCreatedDuringOnboarding(false) // cleanup temp flag
    }

    fun hasConfiguredExchange(): Boolean {
        return credentialsStore.getConfiguredExchanges().isNotEmpty()
    }
}
