package com.accbot.dca.presentation.screens.exchanges

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.ExchangeConnectionEntity
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.repository.ExchangeConnectionRepository
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.usecase.ApiImportProgress
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.domain.usecase.ImportTradeHistoryUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.presentation.credentials.CredentialFormDelegate
import com.accbot.dca.presentation.credentials.CredentialFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import java.time.Instant
import javax.inject.Inject

@Immutable
data class ExchangeDetailUiState(
    val connection: ExchangeConnectionEntity? = null,
    val exchange: Exchange? = null,
    val credentialsExpanded: Boolean = false,
    val plans: List<DcaPlanEntity> = emptyList(),
    val isApiImporting: Boolean = false,
    val apiImportProgress: String = "",
    val apiImportResult: ApiImportResultState? = null,
    val showImportDialog: Boolean = false,
    val importSinceMillis: Long? = null,
    // Credential form (from delegate)
    val credentialForm: CredentialFormState = CredentialFormState()
)

@HiltViewModel
class ExchangeDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val connectionRepository: ExchangeConnectionRepository,
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val importTradeHistoryUseCase: ImportTradeHistoryUseCase,
    private val exchangeApiFactory: ExchangeApiFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val credentialForm = CredentialFormDelegate(
        credentialsStore = credentialsStore,
        validateAndSaveCredentialsUseCase = validateAndSaveCredentialsUseCase,
        userPreferences = userPreferences,
        coroutineScope = viewModelScope,
        connectionRepository = connectionRepository
    )

    private val _localState = MutableStateFlow(ExchangeDetailUiState())
    val uiState: StateFlow<ExchangeDetailUiState> = combine(
        _localState,
        credentialForm.state
    ) { local, cred -> local.copy(credentialForm = cred) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExchangeDetailUiState())

    init {
        credentialForm.initialize()
        // v7+: route is keyed by connectionId (Long).
        val connectionId = savedStateHandle.get<Long>("connectionId")
        val autoImport = savedStateHandle.get<Boolean>("autoImport") ?: false

        if (connectionId != null) {
            viewModelScope.launch {
                val connection = connectionRepository.getById(connectionId) ?: return@launch
                _localState.update { it.copy(connection = connection, exchange = connection.exchange) }
                credentialForm.initWithExchange(connection.exchange)

                // Load plans for this connection (was: per exchange - now per connection envelope)
                var autoImportTriggered = false
                dcaPlanDao.getPlansByConnection(connectionId).collect { plans ->
                    _localState.update { it.copy(plans = plans) }
                    if (autoImport && !autoImportTriggered && plans.isNotEmpty()) {
                        autoImportTriggered = true
                        importViaApi()
                    }
                }
            }
        }
    }

    /**
     * Rename this connection's display label. Empty string resets to "Default".
     */
    fun renameConnection(newName: String) {
        val connection = _localState.value.connection ?: return
        viewModelScope.launch {
            connectionRepository.rename(connection.id, newName)
            // Reload the connection so UI reflects the change
            val updated = connectionRepository.getById(connection.id)
            _localState.update { it.copy(connection = updated) }
        }
    }

    fun toggleCredentials() {
        _localState.update { it.copy(credentialsExpanded = !it.credentialsExpanded) }
    }

    fun saveCredentials(onSuccess: () -> Unit) {
        credentialForm.validateAndSaveCredentials(onSuccess)
    }

    fun showImportDialog() {
        _localState.update { it.copy(showImportDialog = true, importSinceMillis = null) }
    }

    fun dismissImportDialog() {
        _localState.update { it.copy(showImportDialog = false) }
    }

    fun setImportSinceDate(millis: Long?) {
        _localState.update { it.copy(importSinceMillis = millis) }
    }

    fun confirmImport() {
        val sinceMillis = _localState.value.importSinceMillis
        _localState.update { it.copy(showImportDialog = false) }
        importViaApi(sinceMillis?.let { Instant.ofEpochMilli(it) })
    }

    fun importViaApi(sinceDate: Instant? = null) {
        val state = _localState.value
        val exchange = state.exchange ?: return
        if (state.isApiImporting) return
        if (state.plans.isEmpty()) return

        viewModelScope.launch {
            _localState.update { it.copy(isApiImporting = true, apiImportProgress = "", apiImportResult = null) }

            try {
                val isSandbox = userPreferences.isSandboxMode()
                @Suppress("DEPRECATION")
                val credentials = credentialsStore.getCredentials(exchange, isSandbox)
                if (credentials == null) {
                    _localState.update { it.copy(
                        isApiImporting = false,
                        apiImportResult = ApiImportResultState.Error("No credentials found for ${exchange.displayName}")
                    ) }
                    return@launch
                }

                val api = exchangeApiFactory.create(credentials)
                var totalImported = 0
                var totalSkipped = 0
                var errorMessage: String? = null

                for (plan in state.plans) {
                    if (errorMessage != null) break
                    importTradeHistoryUseCase.importFromApi(
                        api = api,
                        planId = plan.id,
                        crypto = plan.crypto,
                        fiat = plan.fiat,
                        exchange = exchange,
                        sinceDate = sinceDate
                    ).collect { progress ->
                        when (progress) {
                            is ApiImportProgress.Fetching -> {
                                _localState.update { it.copy(
                                    apiImportProgress = context.getString(
                                        com.accbot.dca.R.string.import_api_fetching, progress.page
                                    )
                                ) }
                            }
                            is ApiImportProgress.Deduplicating -> {
                                _localState.update { it.copy(
                                    apiImportProgress = context.getString(com.accbot.dca.R.string.import_api_deduplicating)
                                ) }
                            }
                            is ApiImportProgress.Importing -> {
                                _localState.update { it.copy(
                                    apiImportProgress = context.getString(
                                        com.accbot.dca.R.string.import_api_importing, progress.newCount
                                    )
                                ) }
                            }
                            is ApiImportProgress.Complete -> {
                                totalImported += progress.imported
                                totalSkipped += progress.skipped
                            }
                            is ApiImportProgress.Error -> {
                                errorMessage = progress.message
                            }
                        }
                    }
                }

                val result = errorMessage?.let { ApiImportResultState.Error(it) }
                    ?: ApiImportResultState.Success(totalImported, totalSkipped)
                _localState.update { it.copy(
                    isApiImporting = false,
                    apiImportProgress = "",
                    apiImportResult = result
                ) }
            } catch (e: Exception) {
                Log.e(TAG, "API import failed", e)
                _localState.update { it.copy(
                    isApiImporting = false,
                    apiImportProgress = "",
                    apiImportResult = ApiImportResultState.Error(e.message ?: "Import failed")
                ) }
            }
        }
    }

    fun dismissImportResult() {
        _localState.update { it.copy(apiImportResult = null) }
    }

    /**
     * Delete this connection (envelope) and its associated plans/credentials/balances/
     * thresholds. Transactions are kept for history (their `connectionId` becomes orphaned;
     * the History UI falls back to the [Exchange] enum label).
     */
    fun removeExchange(onRemoved: () -> Unit) {
        val state = _localState.value
        val connection = state.connection ?: return
        viewModelScope.launch {
            connectionRepository.delete(connection.id, deletePlans = true)
            onRemoved()
        }
    }

    companion object {
        private const val TAG = "ExchangeDetailVM"
    }
}
