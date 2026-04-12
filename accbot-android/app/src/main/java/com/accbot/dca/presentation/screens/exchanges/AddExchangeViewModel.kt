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
import com.accbot.dca.data.repository.ExchangeConnectionRepository
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.ExchangeInstructionsProvider
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.model.supportsSandbox
import com.accbot.dca.domain.usecase.ApiImportProgress
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.ValidateAndSaveCredentialsUseCase
import com.accbot.dca.domain.usecase.ImportTradeHistoryUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.exchange.ExchangeConfig
import com.accbot.dca.presentation.credentials.CredentialFormDelegate
import com.accbot.dca.presentation.credentials.CredentialFormState
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
    val preSelectedExchange: Boolean = false,
    val isSuccess: Boolean = false,
    val isSandboxMode: Boolean = false,
    val plansForExchange: List<DcaPlanEntity> = emptyList(),
    val availableExchanges: List<Exchange> = emptyList(),
    val showImportOffer: Boolean = false,
    val isApiImporting: Boolean = false,
    val apiImportProgress: String = "",
    val apiImportResult: ApiImportResultState? = null,
    // Credential form (from delegate)
    val credentialForm: CredentialFormState = CredentialFormState()
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
    private val connectionRepository: ExchangeConnectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val credentialForm = CredentialFormDelegate(
        credentialsStore = credentialsStore,
        validateAndSaveCredentialsUseCase = validateAndSaveCredentialsUseCase,
        userPreferences = userPreferences,
        coroutineScope = viewModelScope,
        connectionRepository = connectionRepository
    )

    private val _localState = MutableStateFlow(AddExchangeUiState())
    val uiState: StateFlow<AddExchangeUiState> = combine(
        _localState,
        credentialForm.state
    ) { local, cred -> local.copy(credentialForm = cred) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddExchangeUiState())

    private var plansCollectionJob: Job? = null

    init {
        _localState.update { it.copy(isSandboxMode = userPreferences.isSandboxMode()) }
        credentialForm.initialize()

        // Compute supported exchanges (filter on sandbox + experimental flags)
        viewModelScope.launch {
            val exchanges = computeSupportedExchanges()
            _localState.update { it.copy(availableExchanges = exchanges) }
        }

        // If exchange was passed via navigation, auto-select it
        val exchangeName = savedStateHandle.get<String>("exchange")
        if (exchangeName != null) {
            val exchange = Exchange.entries.find { it.name == exchangeName }
            if (exchange != null) {
                selectExchange(exchange)
                _localState.update { it.copy(preSelectedExchange = true) }
            }
        }
    }

    /**
     * List of exchanges shown in the SELECTION step. After Phase 7+ users can add
     * multiple connections per exchange, so we no longer filter out exchanges that
     * already have credentials - every supported exchange is always selectable.
     */
    private suspend fun computeSupportedExchanges(): List<Exchange> {
        val isSandbox = userPreferences.isSandboxMode()
        val showExperimental = userPreferences.areExperimentalExchangesEnabled()
        return Exchange.entries
            .filter { !isSandbox || it.supportsSandbox() }
            .filter { showExperimental || it.isStable }
    }

    fun selectExchange(exchange: Exchange) {
        credentialForm.selectExchange(exchange)
        _localState.update {
            it.copy(
                currentStep = ExchangeSetupStep.INSTRUCTIONS,
            )
        }

        // Cancel previous collection and start new one for selected exchange
        plansCollectionJob?.cancel()
        plansCollectionJob = viewModelScope.launch {
            dcaPlanDao.getPlansByExchange(exchange).collect { plans ->
                _localState.update { it.copy(plansForExchange = plans) }
            }
        }
    }

    fun proceedToCredentials() {
        _localState.update { it.copy(currentStep = ExchangeSetupStep.CREDENTIALS) }
    }

    fun validateAndSave(onSuccess: () -> Unit) {
        credentialForm.validateAndSaveCredentials {
            val exchange = credentialForm.state.value.selectedExchange
            _localState.update {
                it.copy(
                    isSuccess = true,
                    showImportOffer = exchange?.supportsApiImport == true,
                    currentStep = ExchangeSetupStep.SUCCESS
                )
            }
            onSuccess()
        }
    }

    fun importViaApi() {
        val state = uiState.value
        val exchange = state.credentialForm.selectedExchange ?: return
        if (state.isApiImporting) return
        if (state.plansForExchange.isEmpty()) return

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
                                _localState.update { it.copy(
                                    apiImportProgress = context.getString(
                                        R.string.import_api_fetching, progress.page
                                    )
                                ) }
                            }
                            is ApiImportProgress.Deduplicating -> {
                                _localState.update { it.copy(
                                    apiImportProgress = context.getString(R.string.import_api_deduplicating)
                                ) }
                            }
                            is ApiImportProgress.Importing -> {
                                _localState.update { it.copy(
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

    fun dismissImportOffer() {
        _localState.update { it.copy(showImportOffer = false) }
    }

    /**
     * Returns true if the caller should pop back (navigate away from this screen).
     */
    fun goBack(): Boolean {
        val state = _localState.value
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
        _localState.update { it.copy(currentStep = previousStep) }
        return false
    }

    /**
     * Synchronous accessor for the available exchanges currently in UI state.
     * Initial population happens in [init] via [computeAvailableExchanges]; UI should
     * read [uiState] for reactive updates instead of calling this.
     */
    fun getAvailableExchanges(): List<Exchange> = _localState.value.availableExchanges

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
