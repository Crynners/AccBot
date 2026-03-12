package com.accbot.dca.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.OnboardingPreferences
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.usecase.CreateDcaPlanUseCase
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.credentials.CredentialFormDelegate
import com.accbot.dca.presentation.credentials.CredentialFormState
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
    // Credential form (from delegate)
    val credentialForm: CredentialFormState = CredentialFormState(),

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
    val credentialForm = CredentialFormDelegate(credentialsStore, validateAndSaveCredentialsUseCase, userPreferences, viewModelScope)

    private val _localState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> = combine(
        _localState,
        planForm.state,
        credentialForm.state
    ) { local, form, cred -> local.copy(planForm = form, credentialForm = cred) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingUiState())

    init {
        credentialForm.initialize()
        // Detect already-configured exchange (e.g. credentials saved on ExchangeSetupScreen).
        val isSandbox = userPreferences.isSandboxMode()
        val configured = credentialsStore.getConfiguredExchanges(isSandbox).firstOrNull()
        _localState.update {
            it.copy(
                planCreated = onboardingPreferences.isPlanCreatedDuringOnboarding()
            )
        }
        if (configured != null) {
            credentialForm.initWithExchange(configured)
            planForm.initFromExchange(configured)
        }
    }

    // Exchange setup functions
    fun selectExchange(exchange: com.accbot.dca.domain.model.Exchange) {
        credentialForm.selectExchange(exchange)
        planForm.initFromExchange(exchange)
    }

    fun createFirstPlan(onSuccess: () -> Unit) {
        val state = uiState.value
        val form = state.planForm

        // Guard against concurrent plan creation calls (race condition prevention)
        if (state.isLoading) return

        val exchange = state.credentialForm.selectedExchange

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
