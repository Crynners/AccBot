package com.accbot.dca.presentation.credentials

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.accbot.dca.R
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.ExchangeConnectionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.repository.ExchangeConnectionRepository
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
    /**
     * v2.8+: optional connection name for distinguishing multiple envelopes on the same
     * exchange (e.g. "Hlavní", "Spoření"). Required when [requireConnectionName] is true,
     * which is set by the ViewModel based on existing connection count.
     */
    val connectionName: String = "",
    val requireConnectionName: Boolean = false,
    val existingConnectionsForExchange: List<String> = emptyList(),
    /**
     * Full list of existing connection entities for the selected exchange. Used by the
     * AddPlan flow to show a picker when the user has 1+ connections — they can pick an
     * existing envelope instead of being forced to enter new credentials.
     */
    val existingConnections: List<ExchangeConnectionEntity> = emptyList(),
    /**
     * If non-null, the user picked an existing connection from [existingConnections] (or
     * it was auto-selected because exactly one existed). Plan creation should use this
     * connection directly without re-validating credentials. When null, the user is in
     * "create new connection" mode and must fill the credentials form.
     */
    val selectedConnectionId: Long? = null,
    /**
     * True between [selectExchange]/[initWithExchange] and the async load of existing
     * connections completing. The UI must disable the Validate button while this is true,
     * otherwise the user could race past the duplicate-name check and create a duplicate
     * empty-named connection.
     */
    val isLoadingExchangeContext: Boolean = false,
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
    private val coroutineScope: CoroutineScope,
    private val connectionRepository: ExchangeConnectionRepository? = null
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
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        // Pre-fill instructions/exchange synchronously, then load credentials + connections async
        _state.update {
            it.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                isLoadingExchangeContext = true,
                credentialsError = null, credentialsErrorRes = 0
            )
        }
        coroutineScope.launch {
            @Suppress("DEPRECATION")
            val credentials = credentialsStore.getCredentials(exchange, isSandbox)
            val existing = connectionRepository?.getByExchange(exchange) ?: emptyList()
            // Auto-select on single existing connection so the legacy "first plan after
            // onboarding" path keeps working without user interaction.
            val autoSelectedId = if (existing.size == 1) existing.first().id else null
            _state.update {
                it.copy(
                    hasCredentials = credentials != null || autoSelectedId != null,
                    credentialsValid = credentials != null || autoSelectedId != null,
                    apiKey = credentials?.apiKey ?: "",
                    apiSecret = credentials?.apiSecret ?: "",
                    passphrase = credentials?.passphrase ?: "",
                    clientId = credentials?.clientId ?: "",
                    requireConnectionName = existing.isNotEmpty(),
                    existingConnectionsForExchange = existing.map { c -> c.name },
                    existingConnections = existing,
                    selectedConnectionId = autoSelectedId,
                    isLoadingExchangeContext = false
                )
            }
        }
    }

    fun selectExchange(exchange: Exchange) {
        val isSandbox = _state.value.isSandboxMode
        val instructions = ExchangeInstructionsProvider.getInstructions(exchange, isSandbox)
        // Set isLoadingExchangeContext = true synchronously so the Validate button is
        // immediately disabled — prevents race where user clicks Validate before the
        // existing-connections lookup completes.
        _state.update { state ->
            state.copy(
                selectedExchange = exchange,
                selectedExchangeInstructions = instructions,
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                connectionName = "",
                requireConnectionName = false,
                existingConnectionsForExchange = emptyList(),
                existingConnections = emptyList(),
                selectedConnectionId = null,
                isLoadingExchangeContext = true,
                credentialsError = null, credentialsErrorRes = 0
            )
        }
        coroutineScope.launch {
            val existing = connectionRepository?.getByExchange(exchange) ?: emptyList()
            // Auto-select when exactly ONE connection exists — user doesn't need a picker
            // for the trivial case. With 0 connections, fall through to credentials form.
            // With 2+ connections, leave selectedConnectionId null and let the UI render a
            // picker so the user can choose between envelopes (Hlavní vs Spoření).
            val autoSelectedId = if (existing.size == 1) existing.first().id else null
            _state.update {
                it.copy(
                    hasCredentials = autoSelectedId != null,
                    credentialsValid = autoSelectedId != null,
                    requireConnectionName = existing.isNotEmpty(),
                    existingConnectionsForExchange = existing.map { c -> c.name },
                    existingConnections = existing,
                    selectedConnectionId = autoSelectedId,
                    isLoadingExchangeContext = false
                )
            }
        }
    }

    /**
     * User picked an existing connection from [CredentialFormState.existingConnections].
     * Skips the credentials form — plan creation will reuse the existing envelope.
     */
    fun selectExistingConnection(connectionId: Long) {
        _state.update {
            it.copy(
                selectedConnectionId = connectionId,
                hasCredentials = true,
                credentialsValid = true,
                credentialsError = null, credentialsErrorRes = 0
            )
        }
    }

    /**
     * User chose "create a new connection" instead of picking an existing one. Clears
     * any auto-selected connection and reveals the credentials form.
     */
    fun startNewConnection() {
        _state.update {
            it.copy(
                selectedConnectionId = null,
                hasCredentials = false,
                credentialsValid = false,
                clientId = "",
                apiKey = "",
                apiSecret = "",
                passphrase = "",
                connectionName = "",
                credentialsError = null, credentialsErrorRes = 0
            )
        }
    }

    fun setConnectionName(value: String) {
        _state.update { it.copy(connectionName = value, credentialsError = null, credentialsErrorRes = 0) }
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
        // Defensive: should never fire because UI disables Validate while loading,
        // but guards against direct programmatic calls.
        if (state.isLoadingExchangeContext) return

        val exchange = state.selectedExchange ?: return

        // Validate connection name when required (2+ connections on this exchange)
        val trimmedName = state.connectionName.trim()
        if (state.requireConnectionName && trimmedName.isEmpty()) {
            _state.update { it.copy(credentialsErrorRes = R.string.exchanges_connection_name_required) }
            return
        }
        if (trimmedName.isNotEmpty() && trimmedName in state.existingConnectionsForExchange) {
            _state.update { it.copy(credentialsErrorRes = R.string.exchanges_connection_name_taken) }
            return
        }

        coroutineScope.launch {
            _state.update { it.copy(isValidatingCredentials = true, credentialsError = null, credentialsErrorRes = 0) }

            val result = validateAndSaveCredentialsUseCase.execute(
                exchange = exchange,
                apiKey = state.apiKey,
                apiSecret = state.apiSecret,
                passphrase = state.passphrase.takeIf { it.isNotBlank() },
                clientId = state.clientId.takeIf { it.isNotBlank() },
                connectionName = trimmedName.takeIf { it.isNotEmpty() }
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
