package com.accbot.dca.presentation.screens.exchanges

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.R
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.model.supportsSandbox
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.ExchangeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps in the add exchange wizard.
 * Using enum instead of magic numbers for better readability and type safety.
 */
enum class ExchangeSetupStep(@StringRes val titleRes: Int) {
    SELECTION(R.string.add_exchange_select),
    INSTRUCTIONS(R.string.add_exchange_instructions),
    CREDENTIALS(R.string.add_exchange_credentials),
    SUCCESS(R.string.add_exchange_success)
}

data class AddExchangeUiState(
    val currentStep: ExchangeSetupStep = ExchangeSetupStep.SELECTION,
    val selectedExchange: Exchange? = null,
    val preSelectedExchange: Boolean = false,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",
    val isValidating: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isSandboxMode: Boolean = false
)

@HiltViewModel
class AddExchangeViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val userPreferences: UserPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExchangeUiState())
    val uiState: StateFlow<AddExchangeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isSandboxMode = userPreferences.isSandboxMode()) }

        // If exchange was passed via navigation, auto-select it
        val exchangeName = savedStateHandle.get<String>("exchange")
        if (exchangeName != null) {
            val exchange = Exchange.entries.find { it.name == exchangeName }
            if (exchange != null) {
                selectExchange(exchange)
                _uiState.update { it.copy(preSelectedExchange = true) }
            }
        }
    }

    fun selectExchange(exchange: Exchange) {
        _uiState.update {
            it.copy(
                selectedExchange = exchange,
                currentStep = ExchangeSetupStep.INSTRUCTIONS,
                error = null
            )
        }
    }

    fun proceedToCredentials() {
        _uiState.update { it.copy(currentStep = ExchangeSetupStep.CREDENTIALS) }
    }

    fun setClientId(value: String) {
        _uiState.update { it.copy(clientId = value, error = null) }
    }

    fun setApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, error = null) }
    }

    fun setApiSecret(value: String) {
        _uiState.update { it.copy(apiSecret = value, error = null) }
    }

    fun setPassphrase(value: String) {
        _uiState.update { it.copy(passphrase = value, error = null) }
    }

    fun validateAndSave(onSuccess: () -> Unit) {
        val state = _uiState.value
        val exchange = state.selectedExchange ?: return

        // Prevent concurrent validation
        if (state.isValidating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, error = null) }

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
                            isValidating = false,
                            isSuccess = true,
                            currentStep = ExchangeSetupStep.SUCCESS
                        )
                    }
                    onSuccess()
                }
                is CredentialValidationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns true if the caller should pop back (navigate away from this screen).
     */
    fun goBack(): Boolean {
        val state = _uiState.value
        // If pre-selected and on INSTRUCTIONS, go back to previous screen entirely
        if (state.preSelectedExchange && state.currentStep == ExchangeSetupStep.INSTRUCTIONS) {
            return true
        }
        val previousStep = when (state.currentStep) {
            ExchangeSetupStep.INSTRUCTIONS -> ExchangeSetupStep.SELECTION
            ExchangeSetupStep.CREDENTIALS -> ExchangeSetupStep.INSTRUCTIONS
            ExchangeSetupStep.SUCCESS -> ExchangeSetupStep.CREDENTIALS
            ExchangeSetupStep.SELECTION -> return true
        }
        _uiState.update { it.copy(currentStep = previousStep, error = null) }
        return false
    }

    fun getAvailableExchanges(): List<Exchange> {
        val isSandbox = userPreferences.isSandboxMode()
        return Exchange.entries
            .filter { !credentialsStore.hasCredentials(it, isSandbox) }
            .filter { !isSandbox || it.supportsSandbox() }
    }

    fun getInstructionsForExchange(exchange: Exchange): ExchangeInstructions {
        val isSandbox = userPreferences.isSandboxMode()
        return ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
    }

    fun getSandboxRegistrationUrl(exchange: Exchange): String? {
        return ExchangeConfig.getSandboxRegistrationUrl(exchange)
    }
}
