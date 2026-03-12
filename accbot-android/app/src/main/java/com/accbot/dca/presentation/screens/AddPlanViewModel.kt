package com.accbot.dca.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.model.ExchangeFilter
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.usecase.CreateDcaPlanUseCase
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.plan.PlanFormDelegate
import com.accbot.dca.presentation.plan.PlanFormState
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AddPlanUiState(
    // Sandbox state (immutable after init)
    val isSandboxMode: Boolean = false,
    val availableExchanges: List<Exchange> = emptyList(),
    val showExperimental: Boolean = false,

    // Exchange setup
    val selectedExchange: Exchange? = null,
    val selectedExchangeInstructions: ExchangeInstructions? = null,
    val hasCredentials: Boolean = false,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",

    // Plan form (from delegate)
    val planForm: PlanFormState = PlanFormState(),

    // Action state
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val showImportDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() {
            if (selectedExchange == null) return false
            if (!hasCredentials) {
                if (apiKey.isBlank() || apiSecret.isBlank()) return false
                if (selectedExchange == Exchange.COINMATE && clientId.isBlank()) return false
            }
            return planForm.isFormValid
        }
}

@HiltViewModel
class AddPlanViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val createDcaPlanUseCase: CreateDcaPlanUseCase,
    private val userPreferences: UserPreferences,
    calculateMonthlyCost: CalculateMonthlyCostUseCase,
    minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() {

    val planForm = PlanFormDelegate(calculateMonthlyCost, minOrderSizeRepository, viewModelScope)

    private val _localState = MutableStateFlow(AddPlanUiState())

    val uiState: StateFlow<AddPlanUiState> = combine(
        _localState,
        planForm.state
    ) { local, form -> local.copy(planForm = form) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddPlanUiState())

    // Cache sandbox mode to avoid repeated SharedPreferences reads
    private val isSandbox: Boolean = userPreferences.isSandboxMode()

    init {
        val showExperimental = userPreferences.areExperimentalExchangesEnabled()
        _localState.update {
            it.copy(
                isSandboxMode = isSandbox,
                showExperimental = showExperimental,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
                    .filter { exchange -> showExperimental || exchange.isStable }
            )
        }
    }

    fun setExperimentalExchangesEnabled(enabled: Boolean) {
        userPreferences.setExperimentalExchangesEnabled(enabled)
        _localState.update {
            it.copy(
                showExperimental = enabled,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
                    .filter { exchange -> enabled || exchange.isStable }
            )
        }
    }

    fun selectExchange(exchange: Exchange) {
        val hasCredentials = credentialsStore.hasCredentials(exchange, isSandbox)
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        _localState.update { state ->
            state.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                hasCredentials = hasCredentials,
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                errorMessage = null
            )
        }
        planForm.initFromExchange(exchange)
    }

    fun setClientId(value: String) {
        _localState.update { it.copy(clientId = value) }
    }

    fun setApiKey(value: String) {
        _localState.update { it.copy(apiKey = value) }
    }

    fun setApiSecret(value: String) {
        _localState.update { it.copy(apiSecret = value) }
    }

    fun setPassphrase(value: String) {
        _localState.update { it.copy(passphrase = value) }
    }

    fun createPlan() {
        val state = uiState.value
        val exchange = state.selectedExchange ?: return
        val form = state.planForm

        // Prevent concurrent plan creation
        if (state.isLoading) return

        viewModelScope.launch {
            _localState.update { it.copy(isLoading = true, errorMessage = null) }

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
                            _localState.update {
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

                createDcaPlanUseCase.execute(
                    exchange = exchange,
                    crypto = form.selectedCrypto,
                    fiat = form.selectedFiat,
                    amount = form.amount.toBigDecimal(),
                    frequency = form.selectedFrequency,
                    cronExpression = if (form.selectedFrequency == DcaFrequency.CUSTOM) form.cronExpression else null,
                    strategy = form.selectedStrategy,
                    withdrawalEnabled = form.withdrawalEnabled,
                    withdrawalAddress = if (form.withdrawalEnabled) form.withdrawalAddress.trim() else null,
                    targetAmount = form.targetAmount.toBigDecimalOrNull()
                )

                val shouldOfferImport = !state.hasCredentials && exchange.supportsApiImport
                _localState.update { it.copy(
                    isLoading = false,
                    isSuccess = !shouldOfferImport,
                    showImportDialog = shouldOfferImport
                ) }
            } catch (e: Exception) {
                _localState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to create plan"
                    )
                }
            }
        }
    }

    fun dismissImportDialog() {
        _localState.update { it.copy(showImportDialog = false, isSuccess = true) }
    }
}
