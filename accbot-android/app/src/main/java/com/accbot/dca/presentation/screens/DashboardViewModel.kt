package com.accbot.dca.presentation.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.ExchangeBalanceDao
import com.accbot.dca.data.local.ExchangeBalanceEntity
import com.accbot.dca.data.local.CryptoFiatHolding
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaPlan
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.scheduler.DcaAlarmScheduler
import com.accbot.dca.service.DcaForegroundService
import com.accbot.dca.worker.DcaWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject

data class CryptoHoldingWithPrice(
    val crypto: String,
    val fiat: String,
    val totalCryptoAmount: BigDecimal,
    val totalInvested: BigDecimal,
    val averageBuyPrice: BigDecimal,
    val currentPrice: BigDecimal?,
    val currentValue: BigDecimal?,
    val roiAbsolute: BigDecimal?,
    val roiPercent: BigDecimal?,
    val transactionCount: Int
)

data class DcaPlanWithBalance(
    val plan: DcaPlan,
    val fiatBalance: BigDecimal? = null,
    val remainingExecutions: Int? = null,
    val remainingDays: Double? = null,
    val isLowBalance: Boolean = false
)

data class DashboardUiState(
    val holdings: List<CryptoHoldingWithPrice> = emptyList(),
    val activePlans: List<DcaPlanWithBalance> = emptyList(),
    val isLoading: Boolean = false,
    val isPriceLoading: Boolean = false,
    val isSandboxMode: Boolean = false,
    val runNowTriggered: Boolean = false,
    val showRunNowSheet: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val application: Application,
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences,
    private val marketDataService: MarketDataService,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val credentialsStore: CredentialsStore,
    private val exchangeBalanceDao: ExchangeBalanceDao
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var lastServiceRunning: Boolean? = null
    private var priceJob: Job? = null
    private var balanceJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val isSandbox = userPreferences.isSandboxMode()
            _uiState.update { it.copy(isLoading = true, isSandboxMode = isSandbox) }

            combine(
                dcaPlanDao.getAllPlans(),
                transactionDao.getHoldingsByPairFlow()
            ) { planEntities, dbHoldings ->
                Pair(planEntities, dbHoldings)
            }.collect { (planEntities, dbHoldings) ->
                val plans = planEntities.map { entity ->
                    DcaPlan(
                        id = entity.id,
                        exchange = entity.exchange,
                        crypto = entity.crypto,
                        fiat = entity.fiat,
                        amount = entity.amount,
                        frequency = entity.frequency,
                        cronExpression = entity.cronExpression,
                        strategy = entity.strategy,
                        isEnabled = entity.isEnabled,
                        withdrawalEnabled = entity.withdrawalEnabled,
                        withdrawalAddress = entity.withdrawalAddress,
                        createdAt = entity.createdAt,
                        lastExecutedAt = entity.lastExecutedAt,
                        nextExecutionAt = entity.nextExecutionAt
                    )
                }

                val holdings = mapHoldings(dbHoldings)

                val hasEnabledPlans = plans.any { it.isEnabled }
                ensureServiceState(hasEnabledPlans)

                val plansWithBalance = plans.map { DcaPlanWithBalance(plan = it) }

                _uiState.update { state ->
                    state.copy(
                        activePlans = plansWithBalance,
                        holdings = holdings,
                        isLoading = false
                    )
                }

                priceJob?.cancel()
                priceJob = fetchPricesForHoldings(holdings)
                balanceJob?.cancel()
                balanceJob = fetchBalancesForPlans(plans, isSandbox)
            }
        }
    }

    private fun mapHoldings(dbHoldings: List<CryptoFiatHolding>): List<CryptoHoldingWithPrice> {
        return try {
            dbHoldings.map { holding ->
                val totalCrypto = try { BigDecimal(holding.totalCrypto) } catch (_: Exception) { BigDecimal.ZERO }
                val totalFiat = try { BigDecimal(holding.totalFiat) } catch (_: Exception) { BigDecimal.ZERO }
                val avgPrice = if (totalCrypto > BigDecimal.ZERO) {
                    totalFiat.divide(totalCrypto, 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                CryptoHoldingWithPrice(
                    crypto = holding.crypto,
                    fiat = holding.fiat,
                    totalCryptoAmount = totalCrypto,
                    totalInvested = totalFiat,
                    averageBuyPrice = avgPrice,
                    currentPrice = null,
                    currentValue = null,
                    roiAbsolute = null,
                    roiPercent = null,
                    transactionCount = holding.transactionCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping holdings", e)
            emptyList()
        }
    }

    private fun fetchPricesForHoldings(holdings: List<CryptoHoldingWithPrice>): Job? {
        if (holdings.isEmpty()) return null
        return viewModelScope.launch {
            _uiState.update { it.copy(isPriceLoading = true) }
            val updatedHoldings = holdings.map { holding ->
                try {
                    val price = marketDataService.getCachedPrice(holding.crypto, holding.fiat)
                    if (price != null) {
                        val currentValue = holding.totalCryptoAmount.multiply(price)
                        val roiAbsolute = currentValue.subtract(holding.totalInvested)
                        val roiPercent = if (holding.totalInvested > BigDecimal.ZERO) {
                            roiAbsolute.divide(holding.totalInvested, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal(100))
                                .setScale(2, RoundingMode.HALF_UP)
                        } else null
                        holding.copy(
                            currentPrice = price,
                            currentValue = currentValue.setScale(2, RoundingMode.HALF_UP),
                            roiAbsolute = roiAbsolute.setScale(2, RoundingMode.HALF_UP),
                            roiPercent = roiPercent
                        )
                    } else holding
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching price for ${holding.crypto}/${holding.fiat}", e)
                    holding
                }
            }
            _uiState.update { it.copy(holdings = updatedHoldings, isPriceLoading = false) }
        }
    }

    private fun fetchBalancesForPlans(plans: List<DcaPlan>, isSandbox: Boolean): Job? {
        val enabledPlans = plans.filter { it.isEnabled }
        if (enabledPlans.isEmpty()) return null

        val thresholdDays = userPreferences.getLowBalanceThresholdDays()

        return viewModelScope.launch {
            // Group by exchange+fiat to avoid duplicate API calls
            val balanceCache = mutableMapOf<String, BigDecimal?>()

            val plansWithBalance = plans.map { plan ->
                if (!plan.isEnabled) return@map DcaPlanWithBalance(plan = plan)

                val balanceKey = "${plan.exchange}_${plan.fiat}"
                val balance = balanceCache.getOrPut(balanceKey) {
                    try {
                        val credentials = credentialsStore.getCredentials(plan.exchange, isSandbox)
                            ?: return@getOrPut null
                        val api = exchangeApiFactory.create(credentials)
                        val fetchedBalance = withTimeoutOrNull(10_000) {
                            api.getBalance(plan.fiat)
                        }
                        // Cache in DB
                        if (fetchedBalance != null) {
                            exchangeBalanceDao.insertBalance(
                                ExchangeBalanceEntity(
                                    id = balanceKey,
                                    exchange = plan.exchange,
                                    currency = plan.fiat,
                                    balance = fetchedBalance,
                                    lastUpdated = Instant.now()
                                )
                            )
                        }
                        fetchedBalance
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching balance for ${plan.exchange}/${plan.fiat}", e)
                        // Try cached balance from DB
                        try {
                            exchangeBalanceDao.getBalance(balanceKey)?.balance
                        } catch (_: Exception) { null }
                    }
                }

                if (balance != null && plan.amount > BigDecimal.ZERO) {
                    val remainingExec = balance.divide(plan.amount, 0, RoundingMode.DOWN).toInt()
                    val effectiveInterval = if (plan.cronExpression != null) {
                        com.accbot.dca.domain.util.CronUtils.getIntervalMinutesEstimate(plan.cronExpression) ?: 1440L
                    } else {
                        plan.frequency.intervalMinutes
                    }
                    val remainingMinutes = remainingExec.toLong() * effectiveInterval
                    val remainingDaysVal = remainingMinutes / 1440.0
                    DcaPlanWithBalance(
                        plan = plan,
                        fiatBalance = balance,
                        remainingExecutions = remainingExec,
                        remainingDays = remainingDaysVal,
                        isLowBalance = remainingDaysVal < thresholdDays
                    )
                } else {
                    DcaPlanWithBalance(plan = plan)
                }
            }

            _uiState.update { it.copy(activePlans = plansWithBalance) }
        }
    }

    fun refreshPrices() {
        marketDataService.invalidateCache()
        priceJob?.cancel()
        priceJob = fetchPricesForHoldings(_uiState.value.holdings)
    }

    private fun ensureServiceState(shouldRun: Boolean) {
        if (shouldRun == lastServiceRunning) return
        lastServiceRunning = shouldRun

        if (shouldRun) {
            DcaForegroundService.start(application)
            DcaWorker.schedule(application)
        } else {
            DcaForegroundService.stop(application)
            DcaWorker.cancel(application)
            DcaAlarmScheduler.cancelAlarm(application)
        }
    }

    fun togglePlan(planId: Long) {
        viewModelScope.launch {
            val plan = dcaPlanDao.getPlanById(planId) ?: return@launch
            dcaPlanDao.setEnabled(planId, !plan.isEnabled)
        }
    }

    fun runDcaNow() {
        DcaWorker.runNow(application)
        _uiState.update { it.copy(runNowTriggered = true) }
    }

    fun showRunNowSheet() {
        _uiState.update { it.copy(showRunNowSheet = true) }
    }

    fun hideRunNowSheet() {
        _uiState.update { it.copy(showRunNowSheet = false) }
    }

    fun runSelectedPlans(planIds: List<Long>) {
        hideRunNowSheet()
        if (planIds.isEmpty()) return
        val allEnabled = _uiState.value.activePlans
            .filter { it.plan.isEnabled }
            .map { it.plan.id }
        if (planIds.toSet() == allEnabled.toSet()) {
            DcaWorker.runNow(application)
        } else {
            planIds.forEach { DcaWorker.runPlan(application, it) }
        }
        _uiState.update { it.copy(runNowTriggered = true) }
    }

    fun clearRunNowTriggered() {
        _uiState.update { it.copy(runNowTriggered = false) }
    }
}
