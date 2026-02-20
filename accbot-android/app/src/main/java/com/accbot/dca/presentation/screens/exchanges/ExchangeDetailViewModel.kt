package com.accbot.dca.presentation.screens.exchanges

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.usecase.ApiImportProgress
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ImportTradeHistoryUseCase
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val isSandboxMode: Boolean = false,
    val credentialsExpanded: Boolean = false,
    val plans: List<DcaPlanEntity> = emptyList(),
    val isApiImporting: Boolean = false,
    val apiImportProgress: String = "",
    val apiImportResult: ApiImportResultState? = null
)

@HiltViewModel
class ExchangeDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val dcaPlanDao: DcaPlanDao,
    private val importTradeHistoryUseCase: ImportTradeHistoryUseCase,
    private val exchangeApiFactory: ExchangeApiFactory,
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

            // Load plans for this exchange
            viewModelScope.launch {
                dcaPlanDao.getPlansByExchange(exchange).collect { plans ->
                    _uiState.update { it.copy(plans = plans) }
                }
            }
        }
    }

    fun toggleCredentials() {
        _uiState.update { it.copy(credentialsExpanded = !it.credentialsExpanded) }
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

    fun importViaApi() {
        val state = _uiState.value
        val exchange = state.exchange ?: return
        if (state.isApiImporting) return
        if (state.plans.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApiImporting = true, apiImportProgress = "", apiImportResult = null) }

            try {
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(exchange, isSandbox)
                if (credentials == null) {
                    _uiState.update { it.copy(
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
                        exchange = exchange
                    ).collect { progress ->
                        when (progress) {
                            is ApiImportProgress.Fetching -> {
                                _uiState.update { it.copy(
                                    apiImportProgress = context.getString(
                                        com.accbot.dca.R.string.import_api_fetching, progress.page
                                    )
                                ) }
                            }
                            is ApiImportProgress.Deduplicating -> {
                                _uiState.update { it.copy(
                                    apiImportProgress = context.getString(com.accbot.dca.R.string.import_api_deduplicating)
                                ) }
                            }
                            is ApiImportProgress.Importing -> {
                                _uiState.update { it.copy(
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
                _uiState.update { it.copy(
                    isApiImporting = false,
                    apiImportProgress = "",
                    apiImportResult = result
                ) }
            } catch (e: Exception) {
                Log.e(TAG, "API import failed", e)
                _uiState.update { it.copy(
                    isApiImporting = false,
                    apiImportProgress = "",
                    apiImportResult = ApiImportResultState.Error(e.message ?: "Import failed")
                ) }
            }
        }
    }

    fun dismissImportResult() {
        _uiState.update { it.copy(apiImportResult = null) }
    }

    fun removeExchange(onRemoved: () -> Unit) {
        val state = _uiState.value
        val exchange = state.exchange ?: return
        credentialsStore.deleteCredentials(exchange, state.isSandboxMode)
        onRemoved()
    }

    companion object {
        private const val TAG = "ExchangeDetailVM"
    }
}
