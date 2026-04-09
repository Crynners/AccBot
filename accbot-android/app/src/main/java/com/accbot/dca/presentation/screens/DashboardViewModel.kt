package com.accbot.dca.presentation.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.ExchangeBalanceDao
import com.accbot.dca.data.local.ExchangeBalanceEntity
import com.accbot.dca.data.local.ExchangeConnectionDao
import com.accbot.dca.data.local.CryptoFiatHolding
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.WithdrawalThresholdDao
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.data.remote.CryptoData
import com.accbot.dca.data.remote.FearGreedData
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaPlan
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.StrategyMultiplierResult
import com.accbot.dca.domain.usecase.CalculateStrategyMultiplierUseCase
import com.accbot.dca.exchange.ExchangeApiFactory
import com.accbot.dca.scheduler.DcaAlarmScheduler
import com.accbot.dca.service.DcaForegroundService

import com.accbot.dca.worker.DcaWorker
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject

@Immutable
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

@Immutable
data class DcaPlanWithBalance(
    val plan: DcaPlan,
    val fiatBalance: BigDecimal? = null,
    val remainingExecutions: Int? = null,
    val remainingDays: Double? = null,
    val isLowBalance: Boolean = false,
    val isOverWithdrawalThreshold: Boolean = false,
    val exchangeCryptoBalance: BigDecimal? = null,
    val accumulatedCrypto: BigDecimal? = null,
    val strategyMultiplier: StrategyMultiplierResult? = null,
    /** Connection name for display (empty if the connection has no custom name). */
    val connectionName: String = ""
)

@Immutable
data class MissedPurchaseInfo(
    val planId: Long,
    val crypto: String,
    val exchangeName: String,
    val missedCount: Int
)

@Immutable
data class NetworkRetryPlan(
    val planId: Long,
    val crypto: String,
    val exchangeName: String,
    val retryCount: Int,
    val nextRetryAt: Instant?
)

@Immutable
data class NetworkRetryInfo(
    val plans: List<NetworkRetryPlan> = emptyList(),
    val dismissed: Boolean = false
)

@Immutable
data class DashboardUiState(
    val holdings: List<CryptoHoldingWithPrice> = emptyList(),
    val activePlans: List<DcaPlanWithBalance> = emptyList(),
    val isLoading: Boolean = false,
    val isPriceLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSandboxMode: Boolean = false,
    val runNowTriggered: Boolean = false,
    val showRunNowSheet: Boolean = false,
    val fearGreedData: FearGreedData? = null,
    val athDataByCrypto: Map<String, CryptoData> = emptyMap(),
    val isMarketDataLoading: Boolean = false,
    val showMarketPulse: Boolean = true,
    val isMarketPulseExpanded: Boolean = true,
    val networkRetryInfo: NetworkRetryInfo = NetworkRetryInfo(),
    val missedPurchases: List<MissedPurchaseInfo> = emptyList()
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
    private val exchangeBalanceDao: ExchangeBalanceDao,
    private val withdrawalThresholdDao: WithdrawalThresholdDao,
    private val exchangeConnectionDao: ExchangeConnectionDao,
    private val calculateStrategyMultiplier: CalculateStrategyMultiplierUseCase
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DashboardViewModel"
        private const val STALENESS_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var lastServiceRunning: Boolean? = null
    private var loadDataJob: Job? = null
    private var refreshPricesJob: Job? = null
    private var lastLoadedAt: Long = 0
    private var lastMarketDataFetchedAt: Long = 0

    init {
        loadData()
    }

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            val isSandbox = userPreferences.isSandboxMode()
            val showMarketPulse = userPreferences.isMarketPulseEnabled()
            val isMarketPulseExpanded = userPreferences.isMarketPulseExpanded()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isSandboxMode = isSandbox,
                    showMarketPulse = showMarketPulse,
                    isMarketPulseExpanded = isMarketPulseExpanded
                )
            }

            combine(
                dcaPlanDao.getAllPlans(),
                transactionDao.getHoldingsByPairFlow()
            ) { planEntities, dbHoldings ->
                Pair(planEntities, dbHoldings)
            }.collectLatest { (planEntities, dbHoldings) ->
                refreshPricesJob?.cancel()
                val plans = planEntities.map { it.toDomain() }

                val holdings = mapHoldings(dbHoldings)

                val hasEnabledPlans = plans.any { it.isEnabled }
                ensureServiceState(hasEnabledPlans)
                if (hasEnabledPlans) {
                    launch { DcaAlarmScheduler.scheduleNextAlarm(application) }
                }

                // Pre-load connection names for all unique connectionIds in one batch.
                // Avoids one DB call per plan during the map below.
                val connectionNames: Map<Long, String> = plans
                    .map { it.connectionId }
                    .distinct()
                    .mapNotNull { id ->
                        exchangeConnectionDao.getById(id)?.let { id to it.name }
                    }
                    .toMap()

                val plansWithBalance = plans.map { plan ->
                    val accumulated = if (plan.targetAmount != null) {
                        try {
                            BigDecimal(transactionDao.getAccumulatedCryptoByPlan(plan.id))
                        } catch (_: Exception) { null }
                    } else null
                    DcaPlanWithBalance(
                        plan = plan,
                        accumulatedCrypto = accumulated,
                        connectionName = connectionNames[plan.connectionId] ?: ""
                    )
                }

                // Merge existing prices into fresh holdings to avoid KPI flash
                val existingPrices = _uiState.value.holdings.associateBy(
                    { "${it.crypto}/${it.fiat}" },
                    { it }
                )
                val mergedHoldings = holdings.map { h ->
                    val key = "${h.crypto}/${h.fiat}"
                    val existing = existingPrices[key]
                    if (existing?.currentPrice != null) {
                        h.copy(
                            currentPrice = existing.currentPrice,
                            currentValue = existing.currentValue,
                            roiAbsolute = existing.roiAbsolute,
                            roiPercent = existing.roiPercent
                        )
                    } else h
                }

                // Check missed purchases from plans
                val missedPurchases = planEntities
                    .filter { it.missedPurchaseCount > 0 }
                    .map {
                        MissedPurchaseInfo(
                            planId = it.id,
                            crypto = it.crypto,
                            exchangeName = it.exchange.displayName,
                            missedCount = it.missedPurchaseCount
                        )
                    }

                // Check network retry state from plans
                val retryPlans = planEntities
                    .filter { it.networkRetryCount > 0 }
                    .map {
                        NetworkRetryPlan(
                            planId = it.id,
                            crypto = it.crypto,
                            exchangeName = it.exchange.displayName,
                            retryCount = it.networkRetryCount,
                            nextRetryAt = it.nextNetworkRetryAt
                        )
                    }

                _uiState.update { state ->
                    state.copy(
                        activePlans = plansWithBalance,
                        holdings = mergedHoldings,
                        isLoading = false,
                        missedPurchases = missedPurchases,
                        networkRetryInfo = if (retryPlans.isNotEmpty()) {
                            NetworkRetryInfo(plans = retryPlans, dismissed = false)
                        } else {
                            NetworkRetryInfo()
                        }
                    )
                }

                // collectLatest cancels previous block on new emission,
                // so these child coroutines are automatically cancelled
                // Fetch market indicators first (sequentially) to warm CryptoDataCache,
                // so fetchPricesForHoldings gets cache hits via getCachedPrice
                if (showMarketPulse) {
                    launch { fetchMarketIndicators(plans) }
                }
                launch { fetchPricesForHoldings(mergedHoldings) }
                launch { fetchBalancesForPlans(plans, isSandbox) }
                lastLoadedAt = System.currentTimeMillis()
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

    private suspend fun fetchPricesForHoldings(
        holdings: List<CryptoHoldingWithPrice>,
        manageLoadingState: Boolean = true
    ) {
        if (holdings.isEmpty()) return
        if (manageLoadingState) _uiState.update { it.copy(isPriceLoading = true) }
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
        coroutineContext.ensureActive()
        if (manageLoadingState) {
            _uiState.update { it.copy(holdings = updatedHoldings, isPriceLoading = false) }
        } else {
            _uiState.update { it.copy(holdings = updatedHoldings) }
        }
    }

    private suspend fun fetchBalancesForPlans(plans: List<DcaPlan>, isSandbox: Boolean) {
        val enabledPlans = plans.filter { it.isEnabled }
        if (enabledPlans.isEmpty()) return

        val thresholdDays = userPreferences.getLowBalanceThresholdDays()
        val existingAccumulated = _uiState.value.activePlans.associate { it.plan.id to it.accumulatedCrypto }

        // Group by connection+fiat to avoid duplicate API calls. Each connection (envelope)
        // has independent balances even if two connections target the same exchange.
        val balanceCache = mutableMapOf<String, BigDecimal?>()

        val plansWithBalance = plans.map { plan ->
            if (!plan.isEnabled) return@map DcaPlanWithBalance(plan = plan, accumulatedCrypto = existingAccumulated[plan.id])

            val balanceKey = "${plan.connectionId}_${plan.fiat}"
            val balance = balanceCache.getOrPut(balanceKey) {
                try {
                    val credentials = credentialsStore.getCredentials(plan.connectionId, isSandbox)
                        ?: return@getOrPut null
                    val api = exchangeApiFactory.create(credentials)
                    val fetchedBalance = withTimeoutOrNull(10_000) {
                        api.getBalance(plan.fiat)
                    }
                    // Cache in DB per (connectionId, currency)
                    if (fetchedBalance != null) {
                        exchangeBalanceDao.insertBalance(
                            ExchangeBalanceEntity(
                                connectionId = plan.connectionId,
                                currency = plan.fiat,
                                exchange = plan.exchange,
                                balance = fetchedBalance,
                                lastUpdated = Instant.now()
                            )
                        )
                    }
                    fetchedBalance
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching balance for connection=${plan.connectionId}/${plan.fiat}", e)
                    // Try cached balance from DB
                    try {
                        exchangeBalanceDao.getBalance(plan.connectionId, plan.fiat)?.balance
                    } catch (_: Exception) { null }
                }
            }

            // Check withdrawal threshold using live crypto balance from exchange
            val withdrawalThreshold = try {
                withdrawalThresholdDao.getThresholdAmount(plan.connectionId, plan.crypto)
            } catch (_: Exception) { null }
            val cryptoBalanceKey = "${plan.connectionId}_${plan.crypto}"
            val exchangeCryptoBalance = balanceCache.getOrPut(cryptoBalanceKey) {
                try {
                    val creds = credentialsStore.getCredentials(plan.connectionId, isSandbox)
                        ?: return@getOrPut null
                    val api = exchangeApiFactory.create(creds)
                    withTimeoutOrNull(10_000) { api.getBalance(plan.crypto) }
                } catch (_: Exception) { null }
            }
            val isOverThreshold = withdrawalThreshold != null
                && exchangeCryptoBalance != null
                && exchangeCryptoBalance >= withdrawalThreshold

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
                    isLowBalance = remainingDaysVal < thresholdDays,
                    isOverWithdrawalThreshold = isOverThreshold,
                    exchangeCryptoBalance = exchangeCryptoBalance,
                    accumulatedCrypto = existingAccumulated[plan.id]
                )
            } else {
                DcaPlanWithBalance(
                    plan = plan,
                    isOverWithdrawalThreshold = isOverThreshold,
                    exchangeCryptoBalance = exchangeCryptoBalance,
                    accumulatedCrypto = existingAccumulated[plan.id]
                )
            }
        }

        // Guard: only update if the plan set hasn't changed (prevents stale
        // data from a cancelled collectLatest block overwriting fresh data)
        coroutineContext.ensureActive()
        _uiState.update { current ->
            val currentIds = current.activePlans.map { it.plan.id }.toSet()
            val fetchedIds = plansWithBalance.map { it.plan.id }.toSet()
            if (currentIds == fetchedIds) current.copy(activePlans = plansWithBalance) else current
        }
    }

    private suspend fun fetchMarketIndicators(plans: List<DcaPlan>, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastMarketDataFetchedAt < STALENESS_THRESHOLD_MS) return
        _uiState.update { it.copy(isMarketDataLoading = true) }
        try {
            // Fetch Fear & Greed index
            val fearGreed = try {
                marketDataService.getCachedFearGreedIndex()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Fear & Greed index", e)
                null
            }

            // Fetch ATH data for each unique crypto/fiat pair
            val uniquePairs = plans.map { it.crypto to it.fiat }.distinct()
            val athData = mutableMapOf<String, CryptoData>()
            for ((crypto, fiat) in uniquePairs) {
                try {
                    val data = marketDataService.getCachedCryptoData(crypto, fiat)
                    if (data != null) {
                        athData[crypto] = data
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching ATH data for $crypto/$fiat", e)
                }
            }

            coroutineContext.ensureActive()
            lastMarketDataFetchedAt = System.currentTimeMillis()
            val currentCryptos = plans.map { it.crypto }.toSet()
            _uiState.update { it.copy(
                fearGreedData = fearGreed ?: it.fearGreedData,
                athDataByCrypto = if (athData.isNotEmpty()) athData
                    else it.athDataByCrypto.filterKeys { k -> k in currentCryptos },
                isMarketDataLoading = false
            ) }

            // Calculate strategy multipliers for non-Classic plans
            val nonClassicPlans = plans.filter { it.strategy !is DcaStrategy.Classic }
            if (nonClassicPlans.isNotEmpty()) {
                val multipliers = mutableMapOf<Long, StrategyMultiplierResult>()
                for (plan in nonClassicPlans) {
                    try {
                        val result = calculateStrategyMultiplier(plan.strategy, plan.crypto, plan.fiat)
                        multipliers[plan.id] = result
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating multiplier for plan ${plan.id}", e)
                    }
                }

                coroutineContext.ensureActive()
                _uiState.update { current ->
                    current.copy(
                        activePlans = current.activePlans.map { pwb ->
                            val mult = multipliers[pwb.plan.id]
                            if (mult != null) pwb.copy(strategyMultiplier = mult) else pwb
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching market indicators", e)
            _uiState.update { it.copy(isMarketDataLoading = false) }
        }
    }

    fun refreshIfStale() {
        refreshPreferences()
        if (System.currentTimeMillis() - lastLoadedAt > STALENESS_THRESHOLD_MS) {
            marketDataService.invalidateCache()
            loadData()
        }
    }

    fun refreshPreferences() {
        val wasEnabled = _uiState.value.showMarketPulse
        val nowEnabled = userPreferences.isMarketPulseEnabled()
        _uiState.update {
            it.copy(
                showMarketPulse = nowEnabled,
                isMarketPulseExpanded = userPreferences.isMarketPulseExpanded()
            )
        }
        if (!wasEnabled && nowEnabled) {
            viewModelScope.launch {
                fetchMarketIndicators(_uiState.value.activePlans.map { it.plan }, force = true)
            }
        }
    }

    fun toggleMarketPulseExpanded() {
        val newValue = !_uiState.value.isMarketPulseExpanded
        userPreferences.setMarketPulseExpanded(newValue)
        _uiState.update { it.copy(isMarketPulseExpanded = newValue) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isPriceLoading = true) }
            marketDataService.invalidateCache()
            lastMarketDataFetchedAt = 0
            refreshPricesJob?.cancel()
            try {
                val state = _uiState.value
                val plans = state.activePlans.map { it.plan }
                coroutineScope {
                    launch { loadData() }
                    launch { fetchPricesForHoldings(state.holdings, manageLoadingState = false) }
                    if (state.showMarketPulse) {
                        launch { fetchMarketIndicators(plans, force = true) }
                    }
                }
            } finally {
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(isRefreshing = false, isPriceLoading = false) }
            }
        }
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
            DcaAlarmScheduler.scheduleNextAlarm(application)
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

    fun runRetryPlans() {
        val plans = _uiState.value.networkRetryInfo.plans
        if (plans.isEmpty()) return
        _uiState.update {
            it.copy(
                networkRetryInfo = NetworkRetryInfo(dismissed = true),
                runNowTriggered = true
            )
        }
        viewModelScope.launch {
            plans.forEach { dcaPlanDao.resetNetworkRetry(it.planId) }
            plans.forEach { DcaWorker.runPlan(application, it.planId) }
        }
    }

    fun executeMissedPurchases(planId: Long, count: Int) {
        _uiState.update {
            it.copy(
                runNowTriggered = true,
                missedPurchases = it.missedPurchases.filter { m -> m.planId != planId }
            )
        }
        viewModelScope.launch {
            dcaPlanDao.resetMissedPurchaseCount(planId)
            DcaWorker.runMissedPurchases(application, planId, count)
        }
    }

    fun dismissMissedPurchases(planId: Long) {
        _uiState.update {
            it.copy(missedPurchases = it.missedPurchases.filter { m -> m.planId != planId })
        }
        viewModelScope.launch {
            dcaPlanDao.resetMissedPurchaseCount(planId)
        }
    }

    fun dismissRetryBanner() {
        val plans = _uiState.value.networkRetryInfo.plans
        _uiState.update { it.copy(networkRetryInfo = NetworkRetryInfo(dismissed = true)) }
        viewModelScope.launch {
            plans.forEach { dcaPlanDao.resetNetworkRetry(it.planId) }
        }
    }
}
