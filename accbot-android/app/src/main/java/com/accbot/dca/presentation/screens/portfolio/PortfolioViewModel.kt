package com.accbot.dca.presentation.screens.portfolio

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
// TransactionStatus filtering now done in DAO query
import com.accbot.dca.domain.usecase.CalculateChartDataUseCase
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.domain.usecase.SyncDailyPricesUseCase
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class DenominationMode { FIAT, CRYPTO }

sealed class PairPage {
    data class Aggregate(val fiat: String) : PairPage()
    data class SinglePair(val crypto: String, val fiat: String) : PairPage()
}

@Immutable
data class PortfolioUiState(
    val chartData: List<ChartDataPoint> = emptyList(),
    val zoomLevel: ChartZoomLevel = ChartZoomLevel.Overview,
    val availableYears: List<Int> = emptyList(),
    val availableMonths: List<Int> = emptyList(),
    val canNavigatePrev: Boolean = false,
    val canNavigateNext: Boolean = false,
    val selectedExchangeFilter: String? = null,
    val availableExchanges: List<String> = emptyList(),
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
    val error: String? = null
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
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

    init {
        loadPortfolio()
    }

    private fun loadPortfolio() {
        portfolioJob?.cancel()
        portfolioJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Use pre-filtered, sorted query (avoids loading failed/pending into memory)
                val completed = transactionDao.getCompletedTransactionsOrdered()
                completedTransactions = completed
                val exchanges = completed.map { it.exchange.name }.distinct().sorted()
                val pairs = completed.map { it.crypto to it.fiat }.distinct()

                val pairsByFiat = pairs.groupBy { it.second }
                val pages = mutableListOf<PairPage>()
                for ((fiat, fiatPairs) in pairsByFiat) {
                    if (fiatPairs.size >= 2) {
                        pages.add(PairPage.Aggregate(fiat))
                    }
                }
                for (pair in pairs) {
                    pages.add(PairPage.SinglePair(pair.first, pair.second))
                }

                val pageIndex = if (initialCrypto != null && initialFiat != null) {
                    val idx = pages.indexOfFirst { it is PairPage.SinglePair && it.crypto == initialCrypto && it.fiat == initialFiat }
                    if (idx >= 0) idx else 0
                } else 0

                _uiState.update { state ->
                    state.copy(
                        availableExchanges = exchanges,
                        pages = pages,
                        selectedPageIndex = pageIndex,
                        isLoading = false
                    )
                }

                updateNavigationState()
                syncPricesAndLoadChart()
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

    private fun refreshTransactionsAndPairs() {
        viewModelScope.launch {
            try {
                val completed = transactionDao.getCompletedTransactionsOrdered()
                completedTransactions = completed
                val exchanges = completed.map { it.exchange.name }.distinct().sorted()
                val pairs = completed.map { it.crypto to it.fiat }.distinct()

                val pairsByFiat = pairs.groupBy { it.second }
                val pages = mutableListOf<PairPage>()
                for ((fiat, fiatPairs) in pairsByFiat) {
                    if (fiatPairs.size >= 2) {
                        pages.add(PairPage.Aggregate(fiat))
                    }
                }
                for (pair in pairs) {
                    pages.add(PairPage.SinglePair(pair.first, pair.second))
                }

                _uiState.update { state ->
                    // Keep current page index if still valid, otherwise reset to 0
                    val pageIndex = state.selectedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    state.copy(
                        availableExchanges = exchanges,
                        pages = pages,
                        selectedPageIndex = pageIndex
                    )
                }
                updateNavigationState()
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

    fun selectExchangeFilter(exchange: String?) {
        _uiState.update { it.copy(
            selectedExchangeFilter = exchange,
            selectedPageIndex = 0,
            denominationMode = DenominationMode.FIAT,
            zoomLevel = ChartZoomLevel.Overview
        ) }
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
            zoomLevel = ChartZoomLevel.Overview
        ) }
        updateNavigationState()
        loadChartData()
    }

    fun toggleDenomination() {
        val page = _uiState.value.pages.getOrNull(_uiState.value.selectedPageIndex)
        if (page !is PairPage.SinglePair) return
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
        refreshTransactionsAndPairs()
        loadChartData()
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isPriceSyncing = true) }
            try {
                syncDailyPricesUseCase.sync()
            } catch (_: Exception) {
            }
            _uiState.update { it.copy(isPriceSyncing = false) }
            // Wait for any ongoing chart calculation to finish before starting another
            chartJob?.join()
            loadChartData()
        }
    }

    private fun getFilteredTransactions(): List<TransactionEntity> {
        val state = _uiState.value
        return completedTransactions.filter { tx ->
            state.selectedExchangeFilter == null || tx.exchange.name == state.selectedExchangeFilter
        }
    }

    private fun updateNavigationState() {
        val filteredTxs = getFilteredTransactions()
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

                val (crypto, fiat) = when (page) {
                    is PairPage.Aggregate -> null to page.fiat
                    is PairPage.SinglePair -> page.crypto to page.fiat
                    null -> null to null
                }

                val data = if (crypto == null && fiat == null) {
                    emptyList()
                } else {
                    val filteredTxs = getFilteredTransactions()
                    calculateChartDataUseCase.calculate(
                        transactions = filteredTxs,
                        crypto = crypto,
                        fiat = fiat,
                        zoomLevel = state.zoomLevel
                    )
                }

                val txCount = completedTransactions.count { tx ->
                    (crypto == null || tx.crypto == crypto) &&
                    (fiat == null || tx.fiat == fiat) &&
                    (state.selectedExchangeFilter == null || tx.exchange.name == state.selectedExchangeFilter)
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
            } catch (e: Exception) {
                Log.e("PortfolioVM", "Error loading chart data", e)
                _uiState.update { it.copy(isChartLoading = false) }
            }
        }
    }

    fun refresh() {
        loadPortfolio()
    }
}
