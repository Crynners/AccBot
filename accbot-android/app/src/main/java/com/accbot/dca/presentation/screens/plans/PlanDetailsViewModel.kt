package com.accbot.dca.presentation.screens.plans

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.accbot.dca.data.local.*
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaPlan
import com.accbot.dca.domain.model.PlanPnL
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.scheduler.DcaAlarmScheduler
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.ApiImportProgress
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.domain.usecase.CalculatePlanPnLUseCase
import com.accbot.dca.domain.usecase.CancelSellOrderUseCase
import com.accbot.dca.domain.usecase.ImportTradeHistoryUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.presentation.utils.NumberFormatters
import com.accbot.dca.R
import com.accbot.dca.presentation.utils.TimeUtils
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject

@Immutable
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
    val cryptoBalance: BigDecimal? = null,
    val remainingExecutions: Int? = null,
    val remainingDays: Int? = null,
    val isPriceLoading: Boolean = false,
    val isBalanceLoading: Boolean = false,
    val isApiImporting: Boolean = false,
    val apiImportProgress: String = "",
    val apiImportResult: ApiImportResultState? = null,
    val showImportDialog: Boolean = false,
    val importSinceMillis: Long? = null,
    /** Number of OTHER plans on the same connection. When > 0, import dialog shows a warning. */
    val otherPlansOnSameConnection: Int = 0
)

@HiltViewModel
class PlanDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DcaDatabase,
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val marketDataService: MarketDataService,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val importTradeHistoryUseCase: ImportTradeHistoryUseCase,
    private val calculatePlanPnLUseCase: CalculatePlanPnLUseCase,
    private val cancelSellOrderUseCase: CancelSellOrderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanDetailsUiState())
    val uiState: StateFlow<PlanDetailsUiState> = _uiState.asStateFlow()

    private val _planPnL = MutableStateFlow<PlanPnL?>(null)
    val planPnL: StateFlow<PlanPnL?> = _planPnL.asStateFlow()

    private val _openSells = MutableStateFlow<List<Transaction>>(emptyList())
    val openSells: StateFlow<List<Transaction>> = _openSells.asStateFlow()

    private val _sellUiVisible = MutableStateFlow(false)
    val sellUiVisible: StateFlow<Boolean> = _sellUiVisible.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var planId: Long = 0
    private var transactionCollectionJob: Job? = null
    private var openSellsJob: Job? = null
    private var priceJob: Job? = null
    private var balanceJob: Job? = null

    fun loadPlan(planId: Long) {
        this.planId = planId
        transactionCollectionJob?.cancel()
        openSellsJob?.cancel()

        // Observe open sells for the plan independently of the main transactions flow.
        openSellsJob = viewModelScope.launch {
            transactionDao.observeOpenSellsForPlan(planId).collect { entities ->
                _openSells.value = entities.map { it.toDomain() }
            }
        }

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

                // Compute sell UI visibility: plan opt-in + master switch + exchange capability.
                _sellUiVisible.value = computeSellUiVisible(plan)

                // Check how many OTHER plans share the same connection (for import warning)
                val totalPlansOnConnection = dcaPlanDao.countPlansByConnection(planEntity.connectionId)
                val otherPlans = (totalPlansOnConnection - 1).coerceAtLeast(0)

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

                    // Calculate time until next execution (only when plan is enabled)
                    val timeUntilNext = if (plan.isEnabled) {
                        TimeUtils.formatTimeUntil(plan.nextExecutionAt, context)
                    } else {
                        context.getString(R.string.dashboard_plan_paused)
                    }

                    _uiState.update { state ->
                        state.copy(
                            plan = plan,
                            transactions = transactions,
                            totalInvested = totalInvested,
                            totalCrypto = totalCrypto,
                            averagePrice = averagePrice,
                            transactionCount = completedTransactions.size,
                            timeUntilNextExecution = timeUntilNext,
                            isLoading = false,
                            otherPlansOnSameConnection = otherPlans
                        )
                    }

                    // Fetch price and balance after initial load (cancel previous)
                    priceJob?.cancel()
                    priceJob = fetchCurrentPrice(plan, totalCrypto, totalInvested)
                    balanceJob?.cancel()
                    balanceJob = fetchFiatBalance(plan)

                    // Recompute PnL whenever transactions change (uses last known spot price).
                    recomputePnL(planId, _uiState.value.currentPrice)
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
                    val (roiAbsolute, roiPercent) = NumberFormatters.roiValues(totalInvested, currentValue)
                        ?: (BigDecimal.ZERO to BigDecimal.ZERO)
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
                // Refresh PnL with the (possibly new) spot price.
                recomputePnL(plan.id, price)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch price: ${e.message}")
                _uiState.update { it.copy(isPriceLoading = false) }
            }
        }
    }

    private suspend fun recomputePnL(planId: Long, spot: BigDecimal?) {
        try {
            _planPnL.value = calculatePlanPnLUseCase(planId, spot)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute PnL: ${e.message}")
        }
    }

    /**
     * Sell UI is shown only when plan opted in, global trading is enabled, and the
     * exchange implementation actually supports limit sells.
     */
    private suspend fun computeSellUiVisible(plan: DcaPlan): Boolean {
        if (!plan.allowSells) return false
        if (!userPreferences.isTradingEnabled()) return false
        return try {
            val credentials = credentialsStore.getCredentials(
                plan.connectionId,
                userPreferences.isSandboxMode()
            ) ?: return false
            exchangeApiFactory.create(credentials).supportsLimitSell
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check limit-sell support: ${e.message}")
            false
        }
    }

    fun cancelSell(txId: Long) {
        viewModelScope.launch {
            val result = cancelSellOrderUseCase(txId)
            if (result.isFailure) {
                _snackbar.emit(
                    "Zruseni orderu selhalo: ${result.exceptionOrNull()?.message ?: "neznama chyba"}"
                )
            }
        }
    }

    private fun fetchFiatBalance(plan: DcaPlan): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isBalanceLoading = true) }
            try {
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(plan.connectionId, isSandbox)
                if (credentials != null) {
                    val api = exchangeApiFactory.create(credentials)
                    val balance = withTimeoutOrNull(10_000) { api.getBalance(plan.fiat) }
                    val cryptoBalance = withTimeoutOrNull(10_000) { api.getBalance(plan.crypto) }
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
                            cryptoBalance = cryptoBalance,
                            remainingExecutions = remainingExec,
                            remainingDays = remainingDays,
                            isBalanceLoading = false
                        ) }
                    } else {
                        _uiState.update { it.copy(
                            cryptoBalance = cryptoBalance,
                            isBalanceLoading = false
                        ) }
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

    fun renamePlan(newName: String) {
        viewModelScope.launch {
            dcaPlanDao.renamePlan(planId, newName)
            val updatedPlan = dcaPlanDao.getPlanById(planId)?.toDomain()
            _uiState.update { it.copy(plan = updatedPlan) }
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

    fun deleteAllTransactions(onDeleted: (Int) -> Unit) {
        viewModelScope.launch {
            val count = _uiState.value.transactions.size
            transactionDao.deleteTransactionsByPlanId(planId)
            onDeleted(count)
        }
    }

    fun deletePlan(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                database.withTransaction {
                    transactionDao.deleteTransactionsByPlanId(planId)
                    dcaPlanDao.deletePlanById(planId)
                }
                DcaAlarmScheduler.scheduleNextAlarm(context)
                onDeleted()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete plan") }
            }
        }
    }

    fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importSinceMillis = null) }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false) }
    }

    fun setImportSinceDate(millis: Long?) {
        _uiState.update { it.copy(importSinceMillis = millis) }
    }

    fun confirmImport() {
        val sinceMillis = _uiState.value.importSinceMillis
        _uiState.update { it.copy(showImportDialog = false) }
        importViaApi(sinceMillis?.let { Instant.ofEpochMilli(it) })
    }

    fun importViaApi(sinceDate: Instant? = null) {
        val plan = _uiState.value.plan ?: return
        if (_uiState.value.isApiImporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApiImporting = true, apiImportProgress = "", apiImportResult = null) }

            try {
                val isSandbox = userPreferences.isSandboxMode()
                val credentials = credentialsStore.getCredentials(plan.connectionId, isSandbox)
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
                    exchange = plan.exchange,
                    sinceDate = sinceDate
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

    companion object {
        private const val TAG = "PlanDetailsViewModel"
    }
}
