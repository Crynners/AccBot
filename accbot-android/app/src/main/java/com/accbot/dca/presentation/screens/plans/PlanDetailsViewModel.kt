package com.accbot.dca.presentation.screens.plans

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.*
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaPlan
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.ApiImportProgress
import com.accbot.dca.domain.usecase.ImportTradeHistoryUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.presentation.utils.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

sealed class ApiImportResultState {
    data class Success(val imported: Int, val skipped: Int) : ApiImportResultState()
    data class Error(val message: String) : ApiImportResultState()
}

data class PlanDetailsUiState(
    val plan: DcaPlan? = null,
    val transactions: List<Transaction> = emptyList(),
    val totalInvested: BigDecimal = BigDecimal.ZERO,
    val totalCrypto: BigDecimal = BigDecimal.ZERO,
    val averagePrice: BigDecimal = BigDecimal.ZERO,
    val transactionCount: Int = 0,
    val timeUntilNextExecution: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentPrice: BigDecimal? = null,
    val currentValue: BigDecimal? = null,
    val roiAbsolute: BigDecimal? = null,
    val roiPercent: BigDecimal? = null,
    val fiatBalance: BigDecimal? = null,
    val remainingExecutions: Int? = null,
    val remainingDays: Int? = null,
    val isPriceLoading: Boolean = false,
    val isBalanceLoading: Boolean = false,
    val isApiImporting: Boolean = false,
    val apiImportProgress: String = "",
    val apiImportResult: ApiImportResultState? = null
)

@HiltViewModel
class PlanDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val marketDataService: MarketDataService,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val importTradeHistoryUseCase: ImportTradeHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanDetailsUiState())
    val uiState: StateFlow<PlanDetailsUiState> = _uiState.asStateFlow()

    private var planId: Long = 0
    private var transactionCollectionJob: Job? = null
    private var priceJob: Job? = null
    private var balanceJob: Job? = null

    fun loadPlan(planId: Long) {
        this.planId = planId
        transactionCollectionJob?.cancel()

        transactionCollectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load plan
                val planEntity = dcaPlanDao.getPlanById(planId)
                if (planEntity == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Plan not found") }
                    return@launch
                }

                val plan = planEntity.toDomain()

                // Load transactions for this plan
                transactionDao.getTransactionsByPlan(planId).collect { transactionEntities ->
                    val transactions = transactionEntities.map { it.toDomain() }

                    // Calculate statistics
                    val completedTransactions = transactions.filter { it.status == TransactionStatus.COMPLETED }
                    val totalInvested = completedTransactions.sumOf { it.fiatAmount }
                    val totalCrypto = completedTransactions.sumOf { it.cryptoAmount }
                    val averagePrice = if (totalCrypto > BigDecimal.ZERO) {
                        totalInvested.divide(totalCrypto, 2, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }

                    // Calculate time until next execution
                    val timeUntilNext = TimeUtils.formatTimeUntil(plan.nextExecutionAt, context)

                    _uiState.update { state ->
                        state.copy(
                            plan = plan,
                            transactions = transactions,
                            totalInvested = totalInvested,
                            totalCrypto = totalCrypto,
                            averagePrice = averagePrice,
                            transactionCount = completedTransactions.size,
                            timeUntilNextExecution = timeUntilNext,
                            isLoading = false
                        )
                    }

                    // Fetch price and balance after initial load (cancel previous)
                    priceJob?.cancel()
                    priceJob = fetchCurrentPrice(plan, totalCrypto, totalInvested)
                    balanceJob?.cancel()
                    balanceJob = fetchFiatBalance(plan)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load plan"
                    )
                }
            }
        }
    }

    private fun fetchCurrentPrice(plan: DcaPlan, totalCrypto: BigDecimal, totalInvested: BigDecimal): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }
            try {
                val price = marketDataService.getCachedPrice(plan.crypto, plan.fiat)
                if (price != null && totalCrypto > BigDecimal.ZERO) {
                    val currentValue = totalCrypto.multiply(price).setScale(2, RoundingMode.HALF_UP)
                    val roiAbsolute = currentValue.subtract(totalInvested)
                    val roiPercent = if (totalInvested > BigDecimal.ZERO) {
                        roiAbsolute.multiply(BigDecimal(100)).divide(totalInvested, 2, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }
                    _uiState.update { it.copy(
                        currentPrice = price,
                        currentValue = currentValue,
                        roiAbsolute = roiAbsolute,
                        roiPercent = roiPercent,
                        isPriceLoading = false
                    ) }
                } else {
                    _uiState.update { it.copy(
                        currentPrice = price,
                        isPriceLoading = false
                    ) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch price: ${e.message}")
                _uiState.update { it.copy(isPriceLoading = false) }
            }
        }
    }

    private fun fetchFiatBalance(plan: DcaPlan): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isBalanceLoading = true) }
            try {
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(plan.exchange, isSandbox)
                if (credentials != null) {
                    val api = exchangeApiFactory.create(credentials)
                    val balance = withTimeoutOrNull(10_000) { api.getBalance(plan.fiat) }
                    if (balance != null && plan.amount > BigDecimal.ZERO) {
                        val remainingExec = balance.divide(plan.amount, 0, RoundingMode.DOWN).toInt()
                        val effectiveInterval = if (plan.cronExpression != null) {
                            com.accbot.dca.domain.util.CronUtils.getIntervalMinutesEstimate(plan.cronExpression) ?: 1440L
                        } else {
                            plan.frequency.intervalMinutes
                        }
                        val remainingDays = (remainingExec.toLong() * effectiveInterval / 1440.0).toInt()
                        _uiState.update { it.copy(
                            fiatBalance = balance,
                            remainingExecutions = remainingExec,
                            remainingDays = remainingDays,
                            isBalanceLoading = false
                        ) }
                    } else {
                        _uiState.update { it.copy(isBalanceLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isBalanceLoading = false) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch balance: ${e.message}")
                _uiState.update { it.copy(isBalanceLoading = false) }
            }
        }
    }

    fun togglePlanEnabled() {
        viewModelScope.launch {
            val plan = _uiState.value.plan ?: return@launch
            dcaPlanDao.setEnabled(plan.id, !plan.isEnabled)

            // Reload plan
            val updatedPlan = dcaPlanDao.getPlanById(plan.id)?.toDomain()
            _uiState.update { it.copy(plan = updatedPlan) }
        }
    }

    fun deletePlan(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                dcaPlanDao.deletePlanById(planId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete plan") }
            }
        }
    }

    fun importViaApi() {
        val plan = _uiState.value.plan ?: return
        if (_uiState.value.isApiImporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApiImporting = true, apiImportProgress = "", apiImportResult = null) }

            try {
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(plan.exchange, isSandbox)
                if (credentials == null) {
                    _uiState.update { it.copy(
                        isApiImporting = false,
                        apiImportResult = ApiImportResultState.Error("No credentials found for ${plan.exchange.displayName}")
                    ) }
                    return@launch
                }

                val api = exchangeApiFactory.create(credentials)

                importTradeHistoryUseCase.importFromApi(
                    api = api,
                    planId = plan.id,
                    crypto = plan.crypto,
                    fiat = plan.fiat,
                    exchange = plan.exchange
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
                            _uiState.update { it.copy(
                                isApiImporting = false,
                                apiImportProgress = "",
                                apiImportResult = ApiImportResultState.Success(progress.imported, progress.skipped)
                            ) }
                        }
                        is ApiImportProgress.Error -> {
                            _uiState.update { it.copy(
                                isApiImporting = false,
                                apiImportProgress = "",
                                apiImportResult = ApiImportResultState.Error(progress.message)
                            ) }
                        }
                    }
                }
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

    private fun DcaPlanEntity.toDomain() = DcaPlan(
        id = id,
        exchange = exchange,
        crypto = crypto,
        fiat = fiat,
        amount = amount,
        frequency = frequency,
        cronExpression = cronExpression,
        strategy = strategy,
        isEnabled = isEnabled,
        withdrawalEnabled = withdrawalEnabled,
        withdrawalAddress = withdrawalAddress,
        createdAt = createdAt,
        lastExecutedAt = lastExecutedAt,
        nextExecutionAt = nextExecutionAt
    )

    private fun TransactionEntity.toDomain() = Transaction(
        id = id,
        planId = planId,
        exchange = exchange,
        crypto = crypto,
        fiat = fiat,
        fiatAmount = fiatAmount,
        cryptoAmount = cryptoAmount,
        price = price,
        fee = fee,
        feeAsset = feeAsset,
        status = status,
        exchangeOrderId = exchangeOrderId,
        errorMessage = errorMessage,
        executedAt = executedAt
    )

    companion object {
        private const val TAG = "PlanDetailsViewModel"
    }
}
