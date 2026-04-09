package com.accbot.dca.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.repository.ExchangeConnectionRepository
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
            // Path A: existing connection picked -> credentials already exist, skip checks
            if (cred.selectedConnectionId != null) return planForm.isFormValid
            // Path B: creating a new connection -> validate API key fields
            if (cred.apiKey.isBlank() || cred.apiSecret.isBlank()) return false
            if (cred.selectedExchange == Exchange.COINMATE && cred.clientId.isBlank()) return false
            // When 1+ existing connections, the new one must be named so picker can distinguish
            if (cred.requireConnectionName && cred.connectionName.isBlank()) return false
            return planForm.isFormValid
        }
}

@HiltViewModel
class AddPlanViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val createDcaPlanUseCase: CreateDcaPlanUseCase,
    private val userPreferences: UserPreferences,
    private val connectionRepository: ExchangeConnectionRepository,
    calculateMonthlyCost: CalculateMonthlyCostUseCase,
    minOrderSizeRepository: MinOrderSizeRepository
) : ViewModel() {

    val planForm = PlanFormDelegate(calculateMonthlyCost, minOrderSizeRepository, viewModelScope)
    val credentialForm = CredentialFormDelegate(
        credentialsStore = credentialsStore,
        validateAndSaveCredentialsUseCase = validateAndSaveCredentialsUseCase,
        userPreferences = userPreferences,
        coroutineScope = viewModelScope,
        connectionRepository = connectionRepository
    )

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
                // Two paths:
                //  A) User picked an existing connection (auto-selected when only one
                //     exists, or chosen explicitly via picker when 2+ exist) -> reuse it,
                //     skip the validate-and-save step entirely.
                //  B) User is creating a new connection -> validate API keys, save under
                //     a fresh connection, then use the resulting connectionId.
                val targetConnectionId: Long = if (cred.selectedConnectionId != null) {
                    cred.selectedConnectionId
                } else {
                    val result = validateAndSaveCredentialsUseCase.execute(
                        exchange = exchange,
                        apiKey = cred.apiKey,
                        apiSecret = cred.apiSecret,
                        passphrase = cred.passphrase.takeIf { it.isNotBlank() },
                        clientId = cred.clientId.takeIf { it.isNotBlank() },
                        connectionName = cred.connectionName.trim().takeIf { it.isNotEmpty() }
                    )

                    when (result) {
                        is CredentialValidationResult.Error -> {
                            _localState.update {
                                it.copy(isLoading = false, errorMessage = result.message)
                            }
                            return@launch
                        }
                        is CredentialValidationResult.NetworkError -> {
                            credentialForm.notifyNetworkError()
                            _localState.update { it.copy(isLoading = false) }
                            return@launch
                        }
                        is CredentialValidationResult.Success -> result.connectionId
                    }
                }

                createDcaPlanUseCase.execute(
                    exchange = exchange,
                    connectionId = targetConnectionId,
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

                // Only offer the API import flow when this was a freshly created connection.
                val wasNewConnection = cred.selectedConnectionId == null
                val shouldOfferImport = wasNewConnection && exchange.supportsApiImport
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
