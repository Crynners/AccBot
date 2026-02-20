package com.accbot.dca.presentation.screens.exchanges

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExchangeDetailUiState(
    val exchange: Exchange? = null,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",
    val isValidating: Boolean = false,
    val error: String? = null,
    val isSandboxMode: Boolean = false
)

@HiltViewModel
class ExchangeDetailViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExchangeDetailUiState())
    val uiState: StateFlow<ExchangeDetailUiState> = _uiState.asStateFlow()

    init {
        val isSandbox = userPreferences.isSandboxMode()
        val exchangeName = savedStateHandle.get<String>("exchange")
        val exchange = exchangeName?.let { name ->
            Exchange.entries.find { it.name == name }
        }

        if (exchange != null) {
            val credentials = credentialsStore.getCredentials(exchange, isSandbox)
            _uiState.update {
                it.copy(
                    exchange = exchange,
                    isSandboxMode = isSandbox,
                    apiKey = credentials?.apiKey ?: "",
                    apiSecret = credentials?.apiSecret ?: "",
                    passphrase = credentials?.passphrase ?: "",
                    clientId = credentials?.clientId ?: ""
                )
            }
        }
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

    fun saveCredentials(onSuccess: () -> Unit) {
        val state = _uiState.value
        val exchange = state.exchange ?: return
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
                    _uiState.update { it.copy(isValidating = false) }
                    onSuccess()
                }
                is CredentialValidationResult.Error -> {
                    _uiState.update {
                        it.copy(isValidating = false, error = result.message)
                    }
                }
            }
        }
    }

    fun removeExchange(onRemoved: () -> Unit) {
        val state = _uiState.value
        val exchange = state.exchange ?: return
        credentialsStore.deleteCredentials(exchange, state.isSandboxMode)
        onRemoved()
    }
}
