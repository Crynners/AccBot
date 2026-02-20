package com.accbot.dca.presentation.screens.exchanges

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.R
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.model.supportsSandbox
import com.accbot.dca.domain.usecase.ApiImportProgress
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.CredentialValidationResult
import com.accbot.dca.domain.usecase.ImportTradeHistoryUseCase
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.exchange.ExchangeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
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

@Immutable
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
    val isSandboxMode: Boolean = false,
    val plansForExchange: List<DcaPlanEntity> = emptyList(),
    val isApiImporting: Boolean = false,
    val apiImportProgress: String = "",
    val apiImportResult: ApiImportResultState? = null
)

@HiltViewModel
class AddExchangeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsStore: CredentialsStore,
    private val validateAndSaveCredentialsUseCase: ValidateAndSaveCredentialsUseCase,
    private val userPreferences: UserPreferences,
    private val dcaPlanDao: DcaPlanDao,
    private val importTradeHistoryUseCase: ImportTradeHistoryUseCase,
    private val exchangeApiFactory: ExchangeApiFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExchangeUiState())
    val uiState: StateFlow<AddExchangeUiState> = _uiState.asStateFlow()
    private var plansCollectionJob: Job? = null

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

        // Cancel previous collection and start new one for selected exchange
        plansCollectionJob?.cancel()
        plansCollectionJob = viewModelScope.launch {
            dcaPlanDao.getPlansByExchange(exchange).collect { plans ->
                _uiState.update { it.copy(plansForExchange = plans) }
            }
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

    fun importViaApi() {
        val state = _uiState.value
        val exchange = state.selectedExchange ?: return
        if (state.isApiImporting) return
        if (state.plansForExchange.isEmpty()) return

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

                for (plan in state.plansForExchange) {
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
                                        R.string.import_api_fetching, progress.page
                                    )
                                ) }
                            }
                            is ApiImportProgress.Deduplicating -> {
                                _uiState.update { it.copy(
                                    apiImportProgress = context.getString(R.string.import_api_deduplicating)
                                ) }
                            }
                            is ApiImportProgress.Importing -> {
                                _uiState.update { it.copy(
                                    apiImportProgress = context.getString(
                                        R.string.import_api_importing, progress.newCount
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

    companion object {
        private const val TAG = "AddExchangeVM"
    }
}
