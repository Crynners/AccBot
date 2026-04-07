package com.accbot.dca.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.usecase.CalculateMonthlyCostUseCase
import com.accbot.dca.domain.usecase.CreateDcaPlanUseCase
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.MinOrderSizeRepository
import com.accbot.dca.presentation.credentials.CredentialFormDelegate
import com.accbot.dca.presentation.credentials.CredentialFormState
import com.accbot.dca.presentation.plan.PlanFormDelegate
import com.accbot.dca.presentation.plan.PlanFormState
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AddPlanUiState(
    // Credential form (from delegate)
    val credentialForm: CredentialFormState = CredentialFormState(),

    // Plan form (from delegate)
    val planForm: PlanFormState = PlanFormState(),

    // Change tracking
    val hasChanges: Boolean = false,

    // Action state
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val showImportDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() {
            val cred = credentialForm
            if (cred.selectedExchange == null) return false
            if (!cred.hasCredentials) {
                if (cred.apiKey.isBlank() || cred.apiSecret.isBlank()) return false
                if (cred.selectedExchange == Exchange.COINMATE && cred.clientId.isBlank()) return false
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
    val credentialForm = CredentialFormDelegate(credentialsStore, validateAndSaveCredentialsUseCase, userPreferences, viewModelScope)

    private val _localState = MutableStateFlow(AddPlanUiState())

    val uiState: StateFlow<AddPlanUiState> = combine(
        _localState,
        planForm.state,
        credentialForm.state
    ) { local, form, cred ->
        val hasChanges = cred.selectedExchange != null
        local.copy(planForm = form, credentialForm = cred, hasChanges = hasChanges)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddPlanUiState())

    init {
        credentialForm.initialize()
    }

    fun selectExchange(exchange: Exchange) {
        credentialForm.selectExchange(exchange)
        planForm.initFromExchange(exchange)
    }

    fun createPlan() {
        val state = uiState.value
        val cred = state.credentialForm
        val exchange = cred.selectedExchange ?: return
        val form = state.planForm

        if (state.isLoading) return

        viewModelScope.launch {
            _localState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // Validate and save credentials if new
                if (!cred.hasCredentials) {
                    val result = validateAndSaveCredentialsUseCase.execute(
                        exchange = exchange,
                        apiKey = cred.apiKey,
                        apiSecret = cred.apiSecret,
                        passphrase = cred.passphrase.takeIf { it.isNotBlank() },
                        clientId = cred.clientId.takeIf { it.isNotBlank() }
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
                        is CredentialValidationResult.NetworkError -> {
                            credentialForm.notifyNetworkError()
                            _localState.update { it.copy(isLoading = false) }
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

                val shouldOfferImport = !cred.hasCredentials && exchange.supportsApiImport
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
