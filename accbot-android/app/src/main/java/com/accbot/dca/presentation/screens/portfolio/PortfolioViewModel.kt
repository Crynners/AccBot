package com.accbot.dca.presentation.screens.portfolio

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
// TransactionStatus filtering now done in DAO query
import com.accbot.dca.domain.usecase.CalculateChartDataUseCase
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.domain.usecase.SyncDailyPricesUseCase
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class DenominationMode { FIAT, CRYPTO }

sealed class PairPage {
    data class Aggregate(val fiat: String) : PairPage()
    data class Plan(val planId: Long, val name: String, val crypto: String, val fiat: String) : PairPage()
}

@Immutable
data class PlanInfo(val id: Long, val name: String, val crypto: String, val fiat: String)

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
    val scrubbedIndex: Int? = null,
    val isLoading: Boolean = true,
    val isChartLoading: Boolean = false,
    val isPriceSyncing: Boolean = false,
    val error: String? = null,
    val allPlans: List<PlanInfo> = emptyList(),
    val visiblePlanIds: Set<Long> = emptySet(),
    val showTotalLine: Boolean = true
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
    private val dcaPlanDao: DcaPlanDao,
    private val syncDailyPricesUseCase: SyncDailyPricesUseCase,
    private val calculateChartDataUseCase: CalculateChartDataUseCase
) : ViewModel() {

    private val initialCrypto: String? = savedStateHandle["crypto"]
    private val initialFiat: String? = savedStateHandle["fiat"]

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    private var completedTransactions: List<TransactionEntity> = emptyList()

    private var portfolioJob: Job? = null
    private var syncJob: Job? = null
    private var chartJob: Job? = null
    private var lastLoadedAt: Long = 0
    private var lastTransactionsFetchedAt: Long = 0

    companion object {
        private const val STALENESS_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        private const val TRANSACTION_REFRESH_THRESHOLD_MS = 30 * 1000L // 30 seconds
    }

    init {
        loadPortfolio()
    }

    private fun loadPortfolio() {
        portfolioJob?.cancel()
        portfolioJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val completed = transactionDao.getCompletedTransactionsOrdered()
                completedTransactions = completed

                // Load plans for page building
                val allDbPlans = dcaPlanDao.getAllPlansOnceOrdered()
                val planInfos = allDbPlans.map { p ->
                    PlanInfo(
                        id = p.id,
                        name = p.name.ifBlank { "${p.crypto}/${p.fiat}" },
                        crypto = p.crypto,
                        fiat = p.fiat
                    )
                }

                // Build pages: aggregate per fiat (if 2+ plans in same fiat), then per plan
                val plansByFiat = planInfos.groupBy { it.fiat }
                val pages = mutableListOf<PairPage>()
                for ((fiat, fiatPlans) in plansByFiat) {
                    if (fiatPlans.size >= 2) {
                        pages.add(PairPage.Aggregate(fiat))
                    }
                }
                for (plan in planInfos) {
                    pages.add(PairPage.Plan(plan.id, plan.name, plan.crypto, plan.fiat))
                }

                val pageIndex = if (initialCrypto != null && initialFiat != null) {
                    val idx = pages.indexOfFirst { it is PairPage.Plan && it.crypto == initialCrypto && it.fiat == initialFiat }
                    if (idx >= 0) idx else 0
                } else 0

                _uiState.update { state ->
                    state.copy(
                        pages = pages,
                        selectedPageIndex = pageIndex,
                        allPlans = planInfos,
                        visiblePlanIds = emptySet(),
                        showTotalLine = true,
                        isLoading = false
                    )
                }

                updateNavigationState()
                syncPricesAndLoadChart()
                lastLoadedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load portfolio")
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
                val planInfos = allDbPlans.map { p ->
                    PlanInfo(
                        id = p.id,
                        name = p.name.ifBlank { "${p.crypto}/${p.fiat}" },
                        crypto = p.crypto,
                        fiat = p.fiat
                    )
                }

                val plansByFiat = planInfos.groupBy { it.fiat }
                val pages = mutableListOf<PairPage>()
                for ((fiat, fiatPlans) in plansByFiat) {
                    if (fiatPlans.size >= 2) {
                        pages.add(PairPage.Aggregate(fiat))
                    }
                }
                for (plan in planInfos) {
                    pages.add(PairPage.Plan(plan.id, plan.name, plan.crypto, plan.fiat))
                }

                _uiState.update { state ->
                    val pageIndex = state.selectedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    state.copy(
                        pages = pages,
                        selectedPageIndex = pageIndex,
                        allPlans = planInfos
                    )
                }
                updateNavigationState()
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
                            getFilteredTransactions(getCurrentPlanId()), prevYear
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
                                getFilteredTransactions(getCurrentPlanId()), nextYear
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
            visiblePlanIds = emptySet(),
            showTotalLine = true
        ) }
        updateNavigationState()
        loadChartData()
    }

    fun togglePlanVisibility(planId: Long) {
        _uiState.update { state ->
            val current = state.visiblePlanIds
            val toggled = if (planId in current) current - planId else current + planId
            state.copy(visiblePlanIds = toggled)
        }
        loadChartData()
    }

    fun toggleTotalLine() {
        _uiState.update { it.copy(showTotalLine = !it.showTotalLine) }
        loadChartData()
    }

    fun toggleDenomination() {
        val page = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
        if (page !is PairPage.Plan) return
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
            if (toggled.isEmpty()) return
            state.copy(visibleSeries = toggled)
        }
    }

    fun syncPricesAndLoadChart() {
        refreshTransactionsAndPairs(force = true)
        loadChartData()
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isPriceSyncing = true) }
            try {
                syncDailyPricesUseCase.sync()
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

    private fun getFilteredTransactions(planId: Long? = null): List<TransactionEntity> {
        return if (planId != null) {
            completedTransactions.filter { it.planId == planId }
        } else {
            completedTransactions
        }
    }

    private fun getCurrentPlanId(): Long? {
        val page = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
        return (page as? PairPage.Plan)?.planId
    }

    private fun updateNavigationState() {
        val filteredTxs = getFilteredTransactions(getCurrentPlanId())
        val state = _uiState.value
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
            try {
                val state = _uiState.value
                val page = state.pages.getOrNull(state.selectedPageIndex)

                val (crypto, fiat, planId) = when (page) {
                    is PairPage.Aggregate -> Triple(null, page.fiat, null)
                    is PairPage.Plan -> Triple(page.crypto, page.fiat, page.planId)
                    null -> Triple(null, null, null)
                }

                val data = if (fiat == null) {
                    emptyList()
                } else {
                    val filteredTxs = getFilteredTransactions(planId)
                    calculateChartDataUseCase.calculate(
                        transactions = filteredTxs,
                        crypto = crypto,
                        fiat = fiat,
                        zoomLevel = state.zoomLevel
                    )
                }

                val txCount = completedTransactions.count { tx ->
                    (planId == null || tx.planId == planId) &&
                    (crypto == null || tx.crypto == crypto) &&
                    (fiat == null || tx.fiat == fiat)
                }

                _uiState.update { it.copy(
                    chartData = data,
                    currentPairCrypto = crypto,
                    currentPairFiat = fiat,
                    totalTransactions = txCount,
                    isChartLoading = false
                ) }
            } catch (e: OutOfMemoryError) {
                Log.e("PortfolioVM", "OOM calculating chart data", e)
                _uiState.update { it.copy(isChartLoading = false, error = "Not enough memory for chart") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PortfolioVM", "Error loading chart data", e)
                _uiState.update { it.copy(isChartLoading = false) }
            }
        }
    }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastLoadedAt > STALENESS_THRESHOLD_MS) {
            loadPortfolio()
        }
    }

    fun forceRefresh() {
        loadPortfolio()
    }
}
