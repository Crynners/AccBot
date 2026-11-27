package com.accbot.dca.presentation.screens.portfolio

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.data.local.UserPreferences
// TransactionStatus filtering now done in DAO query
import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.toChartTransactions
import com.accbot.dca.domain.usecase.CalculateChartDataUseCase
import com.accbot.dca.domain.usecase.CancelSellOrderUseCase
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.domain.usecase.ExtraPricePair
import com.accbot.dca.domain.usecase.SyncDailyPricesUseCase
import androidx.compose.runtime.Immutable
import android.content.Context
import com.accbot.dca.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class DenominationMode { FIAT, CRYPTO }

sealed class PairPage {
    data class Aggregate(val fiat: String) : PairPage()
    data class Plan(val planId: Long, val name: String, val crypto: String, val fiat: String) : PairPage()
    /** Chart of someone else's shared plan; never part of Aggregate pages or totals. */
    data class Shared(val shareId: String, val name: String, val crypto: String, val fiat: String) : PairPage()
}

/** Pages showing one crypto/fiat pair (own plan or shared plan) rather than a fiat aggregate. */
val PairPage.isSinglePair: Boolean get() = this is PairPage.Plan || this is PairPage.Shared

/** Stable identifier for a page, used to persist and restore the selected chip. */
internal fun pageIdOf(page: PairPage): String = when (page) {
    is PairPage.Aggregate -> "agg:${page.fiat}"
    is PairPage.Plan -> "plan:${page.planId}"
    is PairPage.Shared -> "shared:${page.shareId}"
}

/**
 * Own pages first (aggregate per fiat with 2+ plans, then one per plan), then one page per
 * shared plan of the current environment that has amounts and trades to draw.
 */
internal fun buildPages(
    dbPlans: List<DcaPlanEntity>,
    shared: List<SharedPlan>,
    isSandbox: Boolean
): List<PairPage> {
    val pages = mutableListOf<PairPage>()
    for ((fiat, fiatPlans) in dbPlans.groupBy { it.fiat }) {
        if (fiatPlans.size >= 2) pages.add(PairPage.Aggregate(fiat))
    }
    for (plan in dbPlans) {
        pages.add(PairPage.Plan(
            planId = plan.id,
            name = plan.name.ifBlank { "${plan.crypto}/${plan.fiat}" },
            crypto = plan.crypto,
            fiat = plan.fiat
        ))
    }
    for (plan in shared) {
        if (plan.isSandbox == isSandbox && plan.includesAmounts && plan.trades.isNotEmpty()) {
            pages.add(PairPage.Shared(plan.shareId, plan.displayName, plan.crypto, plan.fiat))
        }
    }
    return pages
}

/** Index of the previously selected page (by id) in [newPages], 0 when it is gone. */
internal fun selectionIndexAfterRebuild(oldPages: List<PairPage>, oldIndex: Int, newPages: List<PairPage>): Int {
    val id = oldPages.getOrNull(oldIndex)?.let { pageIdOf(it) } ?: return 0
    return newPages.indexOfFirst { pageIdOf(it) == id }.takeIf { it >= 0 } ?: 0
}

/**
 * True when [selected] is a Shared page whose plan's trades differ between [old] and [new], so
 * its chart must be redrawn although the page list (id, name, pair) stayed the same.
 */
internal fun selectedSharedTradesChanged(selected: PairPage?, old: List<SharedPlan>, new: List<SharedPlan>): Boolean {
    if (selected !is PairPage.Shared) return false
    val before = old.firstOrNull { it.shareId == selected.shareId }?.trades
    val after = new.firstOrNull { it.shareId == selected.shareId }?.trades
    return before != after
}

/**
 * Transactions the chart of [page] is drawn from. Aggregate and Plan pages use only Room data;
 * a Shared page uses its own in-memory mapped trades.
 */
internal fun chartTransactionsFor(
    page: PairPage?,
    roomTransactions: List<TransactionEntity>,
    shared: List<SharedPlan>
): List<TransactionEntity> = when (page) {
    is PairPage.Plan -> roomTransactions.filter { it.planId == page.planId }
    // The chart pipeline is BUY-only (see TransactionDao.getCompletedTransactionsOrdered).
    is PairPage.Shared -> shared.firstOrNull { it.shareId == page.shareId }
        ?.toChartTransactions().orEmpty().filter { it.side == TransactionSide.BUY }
    is PairPage.Aggregate, null -> roomTransactions
}

enum class PlanLineType { VALUE, INVESTED, AVG_BUY_PRICE, ACCUMULATED }

@Immutable
data class PlanLineInfo(
    val planId: Long,
    val name: String,
    val crypto: String = "",
    val valueSeries: List<Float> = emptyList(),    // aligned to main chartData indices
    val investedSeries: List<Float> = emptyList(), // aligned to main chartData indices
    val avgBuyPriceSeries: List<Float> = emptyList(),
    val accumulatedSeries: List<Float> = emptyList()
)

enum class CryptoGroupLineType { PRICE, TOTAL_ACCUMULATED }

@Immutable
data class CryptoGroupLineInfo(
    val crypto: String,
    val priceSeries: List<Float> = emptyList(),
    val totalAccumulatedSeries: List<Float> = emptyList()
)

@Immutable
data class PortfolioUiState(
    val chartData: List<ChartDataPoint> = emptyList(),
    val zoomLevel: ChartZoomLevel = ChartZoomLevel.Overview,
    val availableYears: List<Int> = emptyList(),
    val availableMonths: List<Int> = emptyList(),
    val canNavigatePrev: Boolean = false,
    val canNavigateNext: Boolean = false,
    val pages: List<PairPage> = emptyList(),
    val selectedPageIndex: Int = 0,
    val denominationMode: DenominationMode = DenominationMode.FIAT,
    val currentPairCrypto: String? = null,
    val currentPairFiat: String? = null,
    val totalTransactions: Int = 0,
    val visibleSeries: Set<Int> = setOf(0, 1),
    val limitLinesVisible: Boolean = false,
    val scrubbedIndex: Int? = null,
    val planLines: List<PlanLineInfo> = emptyList(),
    val visiblePlanLines: Set<Pair<Long, PlanLineType>> = emptySet(),
    val cryptoGroupLines: List<CryptoGroupLineInfo> = emptyList(),
    val visibleCryptoGroupLines: Set<Pair<String, CryptoGroupLineType>> = emptySet(),
    val isAdvancedLegendExpanded: Boolean = false,
    val isLoading: Boolean = true,
    val isChartLoading: Boolean = false,
    val isPriceSyncing: Boolean = false,
    val error: String? = null,
    /**
     * True when the global trading master switch is on. Gates display of the
     * sell-extension summary rows (realized P&L, net P&L) so users without the
     * feature enabled don't see empty/zero rows.
     */
    val showTradingMetrics: Boolean = false,
    /**
     * Sum of fiat received from completed/partial SELL orders for the currently
     * selected fiat. Null when not loaded yet, BigDecimal.ZERO when there are
     * no realized sells.
     */
    val totalRealized: BigDecimal? = null,
    /**
     * Net P&L = currentPortfolioValue + totalRealized - totalInvested.
     * Null when current price is unavailable (matches the existing chart-loading
     * pattern where ROI fields are also null until prices arrive).
     */
    val netPnL: BigDecimal? = null,
    /**
     * True when the currently selected page is a [PairPage.Plan] AND the plan has
     * `allowSells = true`. Drives visibility of the open-orders list and chart
     * horizontal lines on the per-plan page.
     */
    val currentPlanAllowsSells: Boolean = false,
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
    private val dcaPlanDao: DcaPlanDao,
    private val syncDailyPricesUseCase: SyncDailyPricesUseCase,
    private val calculateChartDataUseCase: CalculateChartDataUseCase,
    private val userPreferences: UserPreferences,
    private val cancelSellOrderUseCase: CancelSellOrderUseCase,
    private val sharedPlanStore: SharedPlanStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Consumed once on first loadPortfolio() and then nulled out so a process-death
    // restore (which repopulates SavedStateHandle from the bundle) doesn't override
    // the user's persisted chip selection.
    private var initialCrypto: String? = savedStateHandle["crypto"]
    private var initialFiat: String? = savedStateHandle["fiat"]

    init {
        // Drop the deep-link args from SavedStateHandle so they don't survive process
        // death and re-override the restored chip selection on the next recreate.
        savedStateHandle.remove<String>("crypto")
        savedStateHandle.remove<String>("fiat")
    }

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    /**
     * Stream of open (PENDING / PARTIAL) sell-order transactions for the currently
     * selected per-plan page. Empty when the page is Aggregate, the plan has
     * allowSells=false, or no open sells exist. Drives both the chart's horizontal
     * limit-price lines (Task B) and the collapsible open-orders list (Task C).
     */
    private val _openSells = MutableStateFlow<List<Transaction>>(emptyList())
    val openSells: StateFlow<List<Transaction>> = _openSells.asStateFlow()

    /**
     * Convenience derived flow exposing only the sorted limit prices for the
     * chart's horizontal lines.
     */
    val openSellLimitPrices: StateFlow<List<BigDecimal>> = openSells
        .map { txs -> txs.mapNotNull { it.limitPrice } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var openSellsJob: Job? = null

    private var completedTransactions: List<TransactionEntity> = emptyList()
    /**
     * Cached plan list used by [loadChartData] to build aggregate per-plan lines.
     * Refreshed by [loadPortfolio] and [refreshTransactionsAndPairs]. Without this
     * cache, every chart navigation (zoom, chip tap, navigate prev/next) would
     * re-query the DB once per render, multiplying with per-plan chart calculation
     * into an O(plans × days) blocking call on the main thread.
     */
    private var cachedDbPlans: List<DcaPlanEntity> = emptyList()

    /** Shared plans the pages and chart were last built from (see [rebuildPagesForSharedChange]). */
    private var lastSeenSharedPlans: List<SharedPlan> = emptyList()

    private var portfolioJob: Job? = null
    private var syncJob: Job? = null
    private var chartJob: Job? = null
    private var lastLoadedAt: Long = 0
    private var lastBuiltSandbox: Boolean = userPreferences.processSandboxMode
    private var lastTransactionsFetchedAt: Long = 0

    companion object {
        private const val STALENESS_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        private const val TRANSACTION_REFRESH_THRESHOLD_MS = 30 * 1000L // 30 seconds
    }

    init {
        loadPortfolio()
        observeSharedPlans()
    }

    /** Current environment; remembered so [refreshIfStale] can detect a switch. */
    private fun currentSandbox(): Boolean = userPreferences.processSandboxMode.also { lastBuiltSandbox = it }

    private fun cachedSharedPlans(): List<SharedPlan> = sharedPlanStore.plans.value.orEmpty()

    /**
     * Loads the shared plans once and rebuilds the pages whenever they change (e.g. the first
     * load finishing after the portfolio, or a live refresh adding trades).
     */
    private fun observeSharedPlans() {
        viewModelScope.launch {
            try {
                sharedPlanStore.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PortfolioVM", "Loading shared plans failed", e)
            }
        }
        viewModelScope.launch {
            sharedPlanStore.plans.filterNotNull().collect { rebuildPagesForSharedChange(it) }
        }
    }

    /**
     * Rebuilds the page list after shared plans changed, keeping the selected page by id. With
     * an unchanged page list, the chart is still reloaded when the selected Shared page's trades
     * changed (a refresh or history download).
     */
    private fun rebuildPagesForSharedChange(shared: List<SharedPlan>) {
        val previous = lastSeenSharedPlans
        lastSeenSharedPlans = shared
        val state = _uiState.value
        // loadPortfolio builds pages and the chart from the current store value itself.
        if (state.isLoading) return
        val pages = buildPages(cachedDbPlans, shared, currentSandbox())
        if (pages == state.pages) {
            val selected = state.pages.getOrNull(state.selectedPageIndex)
            if (selectedSharedTradesChanged(selected, previous, shared)) {
                updateNavigationState()
                loadChartData()
            }
            return
        }
        val newIndex = selectionIndexAfterRebuild(state.pages, state.selectedPageIndex, pages)
        _uiState.update { it.copy(pages = pages, selectedPageIndex = newIndex) }
        updateNavigationState()
        refreshOpenSellsForCurrentPage()
        loadChartData()
    }

    private fun loadPortfolio() {
        portfolioJob?.cancel()
        portfolioJob = viewModelScope.launch {
            val tradingEnabled = userPreferences.isTradingEnabled()
            _uiState.update { it.copy(
                isLoading = true,
                error = null,
                showTradingMetrics = tradingEnabled
            ) }
            try {
                // Use pre-filtered, sorted query (avoids loading failed/pending into memory)
                val completed = transactionDao.getCompletedTransactionsOrdered()
                completedTransactions = completed

                // Load all plans (including disabled) for page building
                val allDbPlans = dcaPlanDao.getAllPlansOnceOrdered()
                cachedDbPlans = allDbPlans

                val shared = cachedSharedPlans()
                lastSeenSharedPlans = shared
                val pages = buildPages(allDbPlans, shared, currentSandbox())

                val deepLinkCrypto = initialCrypto
                val deepLinkFiat = initialFiat
                // One-shot consumption: null after first use so subsequent
                // forceRefresh/refreshIfStale calls honour the user's chip selection.
                initialCrypto = null
                initialFiat = null
                val pageIndex = when {
                    deepLinkCrypto != null && deepLinkFiat != null -> {
                        // Explicit deep-link from dashboard takes priority
                        val idx = pages.indexOfFirst { it is PairPage.Plan && it.crypto == deepLinkCrypto && it.fiat == deepLinkFiat }
                        if (idx >= 0) idx else 0
                    }
                    else -> {
                        // Restore from preferences
                        val savedId = userPreferences.getPortfolioSelectedPageId()
                        if (savedId != null) {
                            val idx = pages.indexOfFirst { pageIdOf(it) == savedId }
                            if (idx >= 0) idx else 0
                        } else 0
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        pages = pages,
                        selectedPageIndex = pageIndex,
                        isLoading = false
                    )
                }

                updateNavigationState()
                refreshOpenSellsForCurrentPage()
                syncPricesAndLoadChart()
                lastLoadedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load portfolio"
                    )
                }
            }
        }
    }

    private fun refreshTransactionsAndPairs(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastTransactionsFetchedAt < TRANSACTION_REFRESH_THRESHOLD_MS) return
        viewModelScope.launch {
            try {
                val completed = transactionDao.getCompletedTransactionsOrdered()
                completedTransactions = completed

                val allDbPlans = dcaPlanDao.getAllPlansOnceOrdered()
                cachedDbPlans = allDbPlans
                val pages = buildPages(allDbPlans, cachedSharedPlans(), currentSandbox())

                _uiState.update { state ->
                    val pageIndex = selectionIndexAfterRebuild(state.pages, state.selectedPageIndex, pages)
                    state.copy(
                        pages = pages,
                        selectedPageIndex = pageIndex
                    )
                }
                updateNavigationState()
                refreshOpenSellsForCurrentPage()
                lastTransactionsFetchedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    fun drillDownToYear(year: Int) {
        _uiState.update { it.copy(zoomLevel = ChartZoomLevel.Year(year)) }
        updateNavigationState()
        loadChartData()
    }

    fun drillDownToMonth(year: Int, month: Int) {
        _uiState.update { it.copy(zoomLevel = ChartZoomLevel.Month(year, month)) }
        updateNavigationState()
        loadChartData()
    }

    fun zoomOut() {
        val current = _uiState.value.zoomLevel
        val newLevel = when (current) {
            is ChartZoomLevel.Month -> ChartZoomLevel.Year(current.year)
            is ChartZoomLevel.Year -> ChartZoomLevel.Overview
            is ChartZoomLevel.Overview -> return
        }
        _uiState.update { it.copy(zoomLevel = newLevel) }
        updateNavigationState()
        loadChartData()
    }

    fun navigatePrev() {
        val current = _uiState.value.zoomLevel
        val newLevel = when (current) {
            is ChartZoomLevel.Year -> {
                val years = _uiState.value.availableYears
                val idx = years.indexOf(current.year)
                if (idx > 0) ChartZoomLevel.Year(years[idx - 1]) else return
            }
            is ChartZoomLevel.Month -> {
                val months = _uiState.value.availableMonths
                val idx = months.indexOf(current.month)
                if (idx > 0) {
                    ChartZoomLevel.Month(current.year, months[idx - 1])
                } else {
                    // Try previous year
                    val years = _uiState.value.availableYears
                    val yearIdx = years.indexOf(current.year)
                    if (yearIdx > 0) {
                        val prevYear = years[yearIdx - 1]
                        val prevMonths = calculateChartDataUseCase.getAvailableMonths(
                            getFilteredTransactions(), prevYear
                        )
                        if (prevMonths.isNotEmpty()) {
                            ChartZoomLevel.Month(prevYear, prevMonths.last())
                        } else return
                    } else return
                }
            }
            is ChartZoomLevel.Overview -> return
        }
        _uiState.update { it.copy(zoomLevel = newLevel) }
        updateNavigationState()
        loadChartData()
    }

    fun navigateNext() {
        val current = _uiState.value.zoomLevel
        val today = LocalDate.now()
        val newLevel = when (current) {
            is ChartZoomLevel.Year -> {
                val years = _uiState.value.availableYears
                val idx = years.indexOf(current.year)
                if (idx >= 0 && idx < years.size - 1) {
                    val nextYear = years[idx + 1]
                    if (nextYear <= today.year) ChartZoomLevel.Year(nextYear) else return
                } else return
            }
            is ChartZoomLevel.Month -> {
                val months = _uiState.value.availableMonths
                val idx = months.indexOf(current.month)
                if (idx >= 0 && idx < months.size - 1) {
                    val nextMonth = months[idx + 1]
                    val nextYm = java.time.YearMonth.of(current.year, nextMonth)
                    val todayYm = java.time.YearMonth.from(today)
                    if (!nextYm.isAfter(todayYm)) {
                        ChartZoomLevel.Month(current.year, nextMonth)
                    } else return
                } else {
                    // Try next year
                    val years = _uiState.value.availableYears
                    val yearIdx = years.indexOf(current.year)
                    if (yearIdx >= 0 && yearIdx < years.size - 1) {
                        val nextYear = years[yearIdx + 1]
                        if (nextYear <= today.year) {
                            val nextMonths = calculateChartDataUseCase.getAvailableMonths(
                                getFilteredTransactions(), nextYear
                            )
                            if (nextMonths.isNotEmpty()) {
                                val nextYm = java.time.YearMonth.of(nextYear, nextMonths.first())
                                val todayYm = java.time.YearMonth.from(today)
                                if (!nextYm.isAfter(todayYm)) {
                                    ChartZoomLevel.Month(nextYear, nextMonths.first())
                                } else return
                            } else return
                        } else return
                    } else return
                }
            }
            is ChartZoomLevel.Overview -> return
        }
        _uiState.update { it.copy(zoomLevel = newLevel) }
        updateNavigationState()
        loadChartData()
    }

    fun selectPairPage(index: Int) {
        val page = _uiState.value.pages.getOrNull(index)
        val newMode = if (page is PairPage.Aggregate) DenominationMode.FIAT else _uiState.value.denominationMode
        _uiState.update { it.copy(
            selectedPageIndex = index,
            denominationMode = newMode,
            visibleSeries = setOf(0, 1),
            zoomLevel = ChartZoomLevel.Overview,
            planLines = emptyList(),
            visiblePlanLines = emptySet(),
            cryptoGroupLines = emptyList(),
            visibleCryptoGroupLines = emptySet(),
            isAdvancedLegendExpanded = false
        ) }
        // Persist selection so the same chip is restored on next app launch
        if (page != null) {
            userPreferences.setPortfolioSelectedPageId(pageIdOf(page))
        }
        updateNavigationState()
        loadChartData()
        refreshOpenSellsForCurrentPage()
    }

    /**
     * (Re)subscribe to the open-sells Flow for the currently selected page.
     * Cancels any prior subscription so we never have two collectors competing
     * to push into [_openSells]. Aggregate pages and plans without `allowSells`
     * yield an empty list.
     */
    private fun refreshOpenSellsForCurrentPage() {
        openSellsJob?.cancel()
        val page = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
        if (page !is PairPage.Plan) {
            _openSells.value = emptyList()
            _uiState.update { it.copy(currentPlanAllowsSells = false) }
            return
        }
        val planEntity = cachedDbPlans.firstOrNull { it.id == page.planId }
        val allowSells = planEntity?.allowSells == true
        _uiState.update { it.copy(currentPlanAllowsSells = allowSells) }
        if (!allowSells) {
            _openSells.value = emptyList()
            return
        }
        openSellsJob = viewModelScope.launch {
            transactionDao.observeOpenSellsForPlan(page.planId).collect { entities ->
                _openSells.value = entities.map { it.toDomain() }
            }
        }
    }

    /**
     * Cancel an open limit-sell order for the currently visible plan. On failure
     * a localized message is pushed to [snackbar] for the screen to display.
     */
    fun cancelSell(txId: Long) {
        viewModelScope.launch {
            val result = cancelSellOrderUseCase(txId)
            if (result.isFailure) {
                _snackbar.emit(
                    context.getString(
                        R.string.sell_cancel_failed,
                        result.exceptionOrNull()?.message ?: context.getString(R.string.sell_cancel_failed_unknown)
                    )
                )
            }
        }
    }

    fun toggleDenomination() {
        val page = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
        if (page == null || !page.isSinglePair) return
        _uiState.update { it.copy(
            denominationMode = if (it.denominationMode == DenominationMode.FIAT)
                DenominationMode.CRYPTO else DenominationMode.FIAT,
            visibleSeries = setOf(0, 1)
        ) }
    }

    fun setScrubIndex(index: Int?) {
        _uiState.update { it.copy(scrubbedIndex = index) }
    }

    fun toggleSeriesVisibility(seriesIndex: Int) {
        _uiState.update { state ->
            val current = state.visibleSeries
            val toggled = if (seriesIndex in current) current - seriesIndex else current + seriesIndex
            state.copy(visibleSeries = toggled)
        }
    }

    fun toggleLimitLinesVisibility() {
        _uiState.update { state -> state.copy(limitLinesVisible = !state.limitLinesVisible) }
    }

    fun togglePlanLineVisibility(planId: Long, type: PlanLineType) {
        _uiState.update { state ->
            val key = planId to type
            val current = state.visiblePlanLines
            val toggled = if (key in current) current - key else current + key
            state.copy(visiblePlanLines = toggled)
        }
    }

    fun toggleCryptoGroupLineVisibility(crypto: String, type: CryptoGroupLineType) {
        _uiState.update { state ->
            val key = crypto to type
            val current = state.visibleCryptoGroupLines
            val toggled = if (key in current) current - key else current + key
            state.copy(visibleCryptoGroupLines = toggled)
        }
    }

    fun toggleAdvancedLegendExpanded() {
        _uiState.update { it.copy(isAdvancedLegendExpanded = !it.isAdvancedLegendExpanded) }
    }

    fun syncPricesAndLoadChart() {
        refreshTransactionsAndPairs(force = true)
        loadChartData()
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isPriceSyncing = true) }
            try {
                val sandbox = userPreferences.processSandboxMode
                // Shared pairs the user does not hold have no price rows otherwise.
                val extra = cachedSharedPlans()
                    .filter { it.isSandbox == sandbox && it.includesAmounts && it.trades.isNotEmpty() }
                    .map { plan ->
                        ExtraPricePair(
                            plan.crypto, plan.fiat,
                            plan.trades.minOf { it.executedAt }.atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                syncDailyPricesUseCase.sync(extra)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            _uiState.update { it.copy(isPriceSyncing = false) }
            // Wait for any ongoing chart calculation to finish before starting another
            chartJob?.join()
            loadChartData()
        }
    }

    /** Navigation years/months come from the page's own data: Room for own pages, mapped trades for Shared. */
    private fun getFilteredTransactions(
        page: PairPage? = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
    ): List<TransactionEntity> =
        if (page is PairPage.Shared) chartTransactionsFor(page, completedTransactions, cachedSharedPlans())
        else completedTransactions

    private fun updateNavigationState() {
        val state = _uiState.value
        val filteredTxs = getFilteredTransactions()
        val years = calculateChartDataUseCase.getAvailableYears(filteredTxs)
        val today = LocalDate.now()

        val (months, canPrev, canNext) = when (val zoom = state.zoomLevel) {
            is ChartZoomLevel.Overview -> {
                Triple(emptyList<Int>(), false, false)
            }
            is ChartZoomLevel.Year -> {
                val m = calculateChartDataUseCase.getAvailableMonths(filteredTxs, zoom.year)
                val idx = years.indexOf(zoom.year)
                val prev = idx > 0
                val next = idx >= 0 && idx < years.size - 1 && years[idx + 1] <= today.year
                Triple(m, prev, next)
            }
            is ChartZoomLevel.Month -> {
                val m = calculateChartDataUseCase.getAvailableMonths(filteredTxs, zoom.year)
                val monthIdx = m.indexOf(zoom.month)
                val yearIdx = years.indexOf(zoom.year)

                // Can navigate prev: either prev month in same year, or last month in prev year
                val hasPrevMonth = monthIdx > 0
                val hasPrevYear = yearIdx > 0
                val prev = hasPrevMonth || hasPrevYear

                // Can navigate next: either next month in same year (not future), or first month in next year (not future)
                val todayYm = java.time.YearMonth.from(today)
                val hasNextMonth = monthIdx >= 0 && monthIdx < m.size - 1 &&
                    !java.time.YearMonth.of(zoom.year, m[monthIdx + 1]).isAfter(todayYm)
                val hasNextYear = yearIdx >= 0 && yearIdx < years.size - 1 && years[yearIdx + 1] <= today.year
                val next = hasNextMonth || (!hasNextMonth && monthIdx == m.size - 1 && hasNextYear)

                Triple(m, prev, next)
            }
        }

        _uiState.update { it.copy(
            availableYears = years,
            availableMonths = months,
            canNavigatePrev = canPrev,
            canNavigateNext = canNext
        ) }
    }

    private fun loadChartData() {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _uiState.update { it.copy(isChartLoading = true) }
            // Heavy chart calculations (per-plan and per-crypto-group line alignment)
            // can run for hundreds of ms on datasets with many plans * many days.
            // Offload to the Default dispatcher so we don't stall the main thread
            // during zoom/scrub/navigate interactions.
            val chartResult = try {
                withContext(Dispatchers.Default) {
                    computeChartData()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                Log.e("PortfolioVM", "OOM calculating chart data", e)
                _uiState.update { it.copy(isChartLoading = false, error = "Not enough memory for chart") }
                return@launch
            } catch (e: Exception) {
                Log.e("PortfolioVM", "Error loading chart data", e)
                _uiState.update { it.copy(isChartLoading = false) }
                return@launch
            }

            _uiState.update { it.copy(
                chartData = chartResult.data,
                currentPairCrypto = chartResult.crypto,
                currentPairFiat = chartResult.fiat,
                totalTransactions = chartResult.txCount,
                planLines = chartResult.planLines,
                cryptoGroupLines = chartResult.cryptoGroupLines,
                isChartLoading = false
            ) }

            // Sell-extension: compute realized P&L from SELL transactions and net P&L
            // (currentValue + realized - invested). Gated by the global trading switch.
            recomputeTradingMetrics(chartResult)
        }
    }

    /**
     * Computes [PortfolioUiState.totalRealized] and [PortfolioUiState.netPnL] for
     * the currently selected page. Reads SELL transactions from cache instead of
     * re-querying because they're already in [completedTransactions] (the DAO
     * pre-filter is BUY-only, so we look at the global `db` here via DAO).
     */
    private suspend fun recomputeTradingMetrics(chartResult: ChartComputeResult) {
        if (!_uiState.value.showTradingMetrics) {
            _uiState.update { it.copy(totalRealized = null, netPnL = null) }
            return
        }
        val fiat = chartResult.fiat
        val selected = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
        // Realized P&L comes from Room sells; a shared plan has none of ours.
        if (fiat == null || selected is PairPage.Shared) {
            _uiState.update { it.copy(totalRealized = null, netPnL = null) }
            return
        }

        val realized = try {
            // Query SELL totals scoped to fiat. For per-plan pages we filter further
            // via the page's planId; for aggregate pages we use everything in fiat.
            val state = _uiState.value
            val page = state.pages.getOrNull(state.selectedPageIndex)
            val planId = (page as? PairPage.Plan)?.planId

            if (planId != null) {
                BigDecimal(transactionDao.getRealizedFiatByPlan(planId))
            } else {
                BigDecimal(transactionDao.getRealizedFiatByFiat(fiat))
            }
        } catch (_: Exception) {
            BigDecimal.ZERO
        }

        val lastPoint = chartResult.data.lastOrNull()
        val net = if (lastPoint != null) {
            lastPoint.portfolioValue + realized - lastPoint.totalInvested
        } else null

        _uiState.update { it.copy(totalRealized = realized, netPnL = net) }
    }

    private data class ChartComputeResult(
        val data: List<ChartDataPoint>,
        val crypto: String?,
        val fiat: String?,
        val txCount: Int,
        val planLines: List<PlanLineInfo>,
        val cryptoGroupLines: List<CryptoGroupLineInfo>
    )

    private suspend fun computeChartData(): ChartComputeResult {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.selectedPageIndex)

        val (crypto, fiat, _) = when (page) {
            is PairPage.Aggregate -> Triple(null, page.fiat, null)
            is PairPage.Plan -> Triple(page.crypto, page.fiat, page.planId)
            is PairPage.Shared -> Triple(page.crypto, page.fiat, null)
            null -> Triple(null, null, null)
        }

        val filteredTxs = chartTransactionsFor(page, completedTransactions, cachedSharedPlans())

        val data = if (fiat == null) {
            emptyList()
        } else {
            calculateChartDataUseCase.calculate(
                transactions = filteredTxs,
                crypto = crypto,
                fiat = fiat,
                zoomLevel = state.zoomLevel
            )
        }

        // Use cached plans (refreshed by loadPortfolio / refreshTransactionsAndPairs)
        // instead of hitting the DB on every chart render.
        val relevantPlans = if (page is PairPage.Aggregate) {
            cachedDbPlans.filter { it.fiat == page.fiat }
        } else emptyList()

        // Calculate per-plan lines (only for Aggregate pages - Plan pages show a single plan's main line)
        val planLinesList = if (page is PairPage.Aggregate && data.isNotEmpty()) {
            try {
                if (relevantPlans.size >= 2) {
                    val mainEpochDays = data.map { it.epochDay }
                    relevantPlans.mapNotNull { plan ->
                        val planTxs = completedTransactions.filter { it.planId == plan.id }
                        if (planTxs.isEmpty()) return@mapNotNull null
                        val planData = calculateChartDataUseCase.calculate(
                            transactions = planTxs,
                            crypto = plan.crypto,
                            fiat = plan.fiat,
                            zoomLevel = state.zoomLevel
                        )
                        // Align to main chart's epoch days via forward-fill
                        val planByDay = planData.associateBy { it.epochDay }
                        var lastValue = 0f
                        var lastInvested = 0f
                        var lastAvg = 0f
                        var lastAccum = 0f
                        val valueAligned = mainEpochDays.map { day ->
                            val v = planByDay[day]?.portfolioValue?.toFloat()
                            if (v != null) { lastValue = v; v } else lastValue
                        }
                        val investedAligned = mainEpochDays.map { day ->
                            val v = planByDay[day]?.totalInvested?.toFloat()
                            if (v != null) { lastInvested = v; v } else lastInvested
                        }
                        val avgBuyAligned = mainEpochDays.map { day ->
                            val v = planByDay[day]?.avgBuyPrice?.toFloat()
                            if (v != null) { lastAvg = v; v } else lastAvg
                        }
                        val accumulatedAligned = mainEpochDays.map { day ->
                            val v = planByDay[day]?.cumulativeCrypto?.toFloat()
                            if (v != null) { lastAccum = v; v } else lastAccum
                        }
                        PlanLineInfo(
                            planId = plan.id,
                            name = plan.name.ifBlank { "${plan.crypto}/${plan.fiat} #${plan.id}" },
                            crypto = plan.crypto,
                            valueSeries = valueAligned,
                            investedSeries = investedAligned,
                            avgBuyPriceSeries = avgBuyAligned,
                            accumulatedSeries = accumulatedAligned
                        )
                    }
                } else emptyList()
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        // Calculate per-crypto-group lines (price + total accumulated per unique crypto within the aggregate)
        val cryptoGroupLinesList = if (page is PairPage.Aggregate && data.isNotEmpty()) {
            try {
                val uniqueCryptos = relevantPlans.map { it.crypto }.distinct()
                val mainEpochDays = data.map { it.epochDay }
                uniqueCryptos.mapNotNull { cryptoSym ->
                    val cryptoTxs = completedTransactions.filter { it.crypto == cryptoSym && it.fiat == page.fiat }
                    if (cryptoTxs.isEmpty()) return@mapNotNull null
                    val cryptoChartData = calculateChartDataUseCase.calculate(
                        transactions = cryptoTxs,
                        crypto = cryptoSym,
                        fiat = page.fiat,
                        zoomLevel = state.zoomLevel
                    )
                    val byDay = cryptoChartData.associateBy { it.epochDay }
                    var lastPrice = 0f
                    var lastAccum = 0f
                    val priceAligned = mainEpochDays.map { day ->
                        val v = byDay[day]?.price?.toFloat()
                        if (v != null) { lastPrice = v; v } else lastPrice
                    }
                    val accAligned = mainEpochDays.map { day ->
                        val v = byDay[day]?.cumulativeCrypto?.toFloat()
                        if (v != null) { lastAccum = v; v } else lastAccum
                    }
                    CryptoGroupLineInfo(
                        crypto = cryptoSym,
                        priceSeries = priceAligned,
                        totalAccumulatedSeries = accAligned
                    )
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val txCount = filteredTxs.count { tx ->
            (crypto == null || tx.crypto == crypto) &&
            (fiat == null || tx.fiat == fiat)
        }

        return ChartComputeResult(
            data = data,
            crypto = crypto,
            fiat = fiat,
            txCount = txCount,
            planLines = planLinesList,
            cryptoGroupLines = cryptoGroupLinesList
        )
    }

    fun refreshIfStale() {
        // An environment switch changes which shared plans show, so rebuild right away.
        if (userPreferences.processSandboxMode != lastBuiltSandbox ||
            System.currentTimeMillis() - lastLoadedAt > STALENESS_THRESHOLD_MS) {
            loadPortfolio()
        }
    }

    fun forceRefresh() {
        loadPortfolio()
    }
}
