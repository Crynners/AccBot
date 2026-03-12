package com.accbot.dca.presentation.screens.exchanges

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.UserPreferences
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
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val importTradeHistoryUseCase: ImportTradeHistoryUseCase,
    private val exchangeApiFactory: ExchangeApiFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val credentialForm = CredentialFormDelegate(credentialsStore, validateAndSaveCredentialsUseCase, userPreferences, viewModelScope)

    private val _localState = MutableStateFlow(ExchangeDetailUiState())
    val uiState: StateFlow<ExchangeDetailUiState> = combine(
        _localState,
        credentialForm.state
    ) { local, cred -> local.copy(credentialForm = cred) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExchangeDetailUiState())

    init {
        credentialForm.initialize()
        val exchangeName = savedStateHandle.get<String>("exchange")
        val autoImport = savedStateHandle.get<Boolean>("autoImport") ?: false
        val exchange = exchangeName?.let { name ->
            Exchange.entries.find { it.name == name }
        }

        if (exchange != null) {
            _localState.update { it.copy(exchange = exchange) }
            credentialForm.initWithExchange(exchange)

            // Load plans for this exchange
            var autoImportTriggered = false
            viewModelScope.launch {
                dcaPlanDao.getPlansByExchange(exchange).collect { plans ->
                    _localState.update { it.copy(plans = plans) }
                    // Auto-trigger import when navigated from import offer dialog
                    if (autoImport && !autoImportTriggered && plans.isNotEmpty()) {
                        autoImportTriggered = true
                        importViaApi()
                    }
                }
            }
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

    fun removeExchange(onRemoved: () -> Unit) {
        val state = _localState.value
        val exchange = state.exchange ?: return
        viewModelScope.launch {
            // Delete transactions, plans and credentials for this exchange
            transactionDao.deleteTransactionsByExchange(exchange)
            dcaPlanDao.deletePlansByExchange(exchange)
            credentialsStore.deleteCredentials(exchange, credentialForm.state.value.isSandboxMode)
            onRemoved()
        }
    }

    companion object {
        private const val TAG = "ExchangeDetailVM"
    }
}
