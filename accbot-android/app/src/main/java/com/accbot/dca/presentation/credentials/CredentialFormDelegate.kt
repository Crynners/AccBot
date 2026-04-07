package com.accbot.dca.presentation.credentials

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.accbot.dca.R
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeFilter
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CredentialFormState(
    val selectedExchange: Exchange? = null,
    val selectedExchangeInstructions: ExchangeInstructions? = null,
    val hasCredentials: Boolean = false,
    val clientId: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",
    val isValidatingCredentials: Boolean = false,
    val credentialsValid: Boolean = false,
    val credentialsError: String? = null,
    @StringRes val credentialsErrorRes: Int = 0,
    val isSandboxMode: Boolean = false,
    val availableExchanges: List<Exchange> = emptyList(),
    val showExperimental: Boolean = false
) {
    val hasCredentialsError: Boolean
        get() = credentialsError != null || credentialsErrorRes != 0
}

/** Resolve the credentials error to a localized string. */
val CredentialFormState.resolvedCredentialsError: String?
    @Composable get() = when {
        credentialsErrorRes != 0 -> stringResource(credentialsErrorRes)
        else -> credentialsError
    }

/**
 * Shared delegate for credential form state and logic.
 * Used by AddPlanViewModel, OnboardingViewModel, AddExchangeViewModel, and ExchangeDetailViewModel.
 * Not a ViewModel – the owning ViewModel passes its coroutineScope.
 */
class CredentialFormDelegate(
    private val credentialsStore: CredentialsStore,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val userPreferences: UserPreferences,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(CredentialFormState())
    val state: StateFlow<CredentialFormState> = _state.asStateFlow()

    fun initialize() {
        val isSandbox = userPreferences.isSandboxMode()
        val showExperimental = userPreferences.areExperimentalExchangesEnabled()
        _state.update {
            it.copy(
                isSandboxMode = isSandbox,
                showExperimental = showExperimental,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
                    .filter { exchange -> showExperimental || exchange.isStable }
            )
        }
    }

    /** Initialize with a pre-selected exchange and load existing credentials. */
    fun initWithExchange(exchange: Exchange) {
        val isSandbox = _state.value.isSandboxMode
        val credentials = credentialsStore.getCredentials(exchange, isSandbox)
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        _state.update {
            it.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                hasCredentials = credentials != null,
                credentialsValid = credentials != null,
                apiKey = credentials?.apiKey ?: "",
                apiSecret = credentials?.apiSecret ?: "",
                passphrase = credentials?.passphrase ?: "",
                clientId = credentials?.clientId ?: "",
                credentialsError = null, credentialsErrorRes = 0
            )
        }
    }

    fun selectExchange(exchange: Exchange) {
        val isSandbox = _state.value.isSandboxMode
        val hasCredentials = credentialsStore.hasCredentials(exchange, isSandbox)
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        _state.update { state ->
            state.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                hasCredentials = hasCredentials,
                credentialsValid = hasCredentials,
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                credentialsError = null, credentialsErrorRes = 0
            )
        }
    }

    fun setClientId(value: String) {
        _state.update { it.copy(clientId = value, credentialsError = null, credentialsErrorRes = 0) }
    }

    fun setApiKey(value: String) {
        _state.update { it.copy(apiKey = value, credentialsError = null, credentialsErrorRes = 0) }
    }

    fun setApiSecret(value: String) {
        _state.update { it.copy(apiSecret = value, credentialsError = null, credentialsErrorRes = 0) }
    }

    fun setPassphrase(value: String) {
        _state.update { it.copy(passphrase = value, credentialsError = null, credentialsErrorRes = 0) }
    }

    fun validateAndSaveCredentials(onSuccess: () -> Unit) {
        val state = _state.value
        if (state.isValidatingCredentials) return

        val exchange = state.selectedExchange ?: return

        coroutineScope.launch {
            _state.update { it.copy(isValidatingCredentials = true, credentialsError = null, credentialsErrorRes = 0) }

            val result = validateAndSaveCredentialsUseCase.execute(
                exchange = exchange,
                apiKey = state.apiKey,
                apiSecret = state.apiSecret,
                passphrase = state.passphrase.takeIf { it.isNotBlank() },
                clientId = state.clientId.takeIf { it.isNotBlank() }
            )

            when (result) {
                is CredentialValidationResult.Success -> {
                    _state.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsValid = true,
                            hasCredentials = true
                        )
                    }
                    onSuccess()
                }
                is CredentialValidationResult.Error -> {
                    _state.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsError = result.message
                        )
                    }
                }
                is CredentialValidationResult.NetworkError -> {
                    _state.update {
                        it.copy(
                            isValidatingCredentials = false,
                            credentialsErrorRes = R.string.error_no_internet
                        )
                    }
                }
            }
        }
    }

    fun notifyNetworkError() {
        _state.update {
            it.copy(
                isValidatingCredentials = false,
                credentialsErrorRes = R.string.error_no_internet
            )
        }
    }

    fun setExperimentalExchangesEnabled(enabled: Boolean) {
        userPreferences.setExperimentalExchangesEnabled(enabled)
        val isSandbox = _state.value.isSandboxMode
        _state.update {
            it.copy(
                showExperimental = enabled,
                availableExchanges = ExchangeFilter.getAvailableExchanges(isSandbox)
                    .filter { exchange -> enabled || exchange.isStable }
            )
        }
    }
}
