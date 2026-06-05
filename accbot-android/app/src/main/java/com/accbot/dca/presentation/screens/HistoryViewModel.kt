package com.accbot.dca.presentation.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.CsvExportResult
import com.accbot.dca.domain.usecase.ExportTransactionsToCsvUseCase
import com.accbot.dca.presentation.utils.NumberFormatters
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption {
    DATE_NEWEST,
    DATE_OLDEST,
    AMOUNT_HIGHEST,
    AMOUNT_LOWEST,
    PRICE_HIGHEST,
    PRICE_LOWEST
}

/**
 * Primary BUY/SELL/PENDING filter chips shown at the top of HistoryScreen.
 * Applied in memory over the result of the SQL-level HistoryFilter below.
 */
enum class HistorySideFilter { ALL, BUYS, SELLS, PENDING }

data class HistoryFilter(
    val crypto: String? = null,
    val exchange: String? = null,
    val status: TransactionStatus? = null,
    val planId: Long? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val searchQuery: String = "",
    val sideFilter: HistorySideFilter = HistorySideFilter.ALL
)

/** A plan the user can filter history by, shown in the filter sheet. */
data class HistoryPlanOption(
    val id: Long,
    val label: String
)

/**
 * Data class for CSV export result to pass to UI for file handling.
 */
data class CsvExportData(
    val content: String,
    val fileName: String
)

@Immutable
data class HistoryUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val availableCryptos: List<String> = emptyList(),
    val availableExchanges: List<String> = emptyList(),
    val availablePlans: List<HistoryPlanOption> = emptyList(),
    val showFilterSheet: Boolean = false,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val exportError: String? = null,
    val exportData: CsvExportData? = null,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
    private val dcaPlanDao: DcaPlanDao,
    private val exportTransactionsToCsvUseCase: ExportTransactionsToCsvUseCase
) : ViewModel() {

    private val initialCrypto: String? = savedStateHandle["crypto"]
    private val initialFiat: String? = savedStateHandle["fiat"]
    // planId arrives as a string query param; treat <= 0 / missing as "no plan filter".
    private val initialPlanId: Long? =
        savedStateHandle.get<String>("planId")?.toLongOrNull()?.takeIf { it > 0 }

    private val _filterState = MutableStateFlow(HistoryFilter(crypto = initialCrypto, planId = initialPlanId))
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_NEWEST)

    private val _uiExtras = MutableStateFlow(
        HistoryUiState()
    )

    // Extract SQL-pushable chip filters and switch DAO query only when they change
    private data class ChipFilter(val crypto: String?, val exchange: String?, val status: String?, val planId: Long?)

    @OptIn(FlowPreview::class)
    private val _debouncedSearch = _searchQuery.debounce(300)

    // Stage 1: SQL-filtered data + in-memory date/search/sort
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val _computedTransactions: Flow<HistoryUiState> = _filterState
        .map { ChipFilter(it.crypto, it.exchange, it.status?.name, it.planId) }
        .distinctUntilChanged()
        .flatMapLatest { chip ->
            transactionDao.getFilteredTransactions(chip.crypto, chip.exchange, chip.status, chip.planId)
        }
        .combine(_filterState) { transactions, filter -> transactions to filter }
        .combine(_debouncedSearch) { (transactions, filter), searchQuery ->
            Triple(transactions, filter.copy(searchQuery = searchQuery), searchQuery)
        }
        .combine(_sortOption) { (transactions, filter, _), sortOption ->
            val filtered = transactions.filter { tx ->
                val epochMillis = tx.executedAt.toEpochMilli()
                val matchesDates = (filter.dateFrom == null || epochMillis >= filter.dateFrom) &&
                    (filter.dateTo == null || epochMillis <= filter.dateTo + 86_400_000)
                val matchesSearch = filter.searchQuery.isBlank() || listOf(
                    tx.crypto, tx.fiat, tx.exchange.name, tx.exchange.displayName,
                    NumberFormatters.fiat(tx.fiatAmount), NumberFormatters.crypto(tx.cryptoAmount),
                    tx.exchangeOrderId ?: "", tx.errorMessage ?: ""
                ).any { it.contains(filter.searchQuery, ignoreCase = true) }
                val matchesSide = when (filter.sideFilter) {
                    HistorySideFilter.ALL -> true
                    HistorySideFilter.BUYS -> tx.side == TransactionSide.BUY
                    HistorySideFilter.SELLS -> tx.side == TransactionSide.SELL
                    HistorySideFilter.PENDING -> tx.status == TransactionStatus.PENDING ||
                        tx.status == TransactionStatus.PARTIAL
                }
                matchesDates && matchesSearch && matchesSide
            }

            val sorted = when (sortOption) {
                SortOption.DATE_NEWEST -> filtered.sortedByDescending { it.executedAt }
                SortOption.DATE_OLDEST -> filtered.sortedBy { it.executedAt }
                SortOption.AMOUNT_HIGHEST -> filtered.sortedByDescending { it.fiatAmount }
                SortOption.AMOUNT_LOWEST -> filtered.sortedBy { it.fiatAmount }
                SortOption.PRICE_HIGHEST -> filtered.sortedByDescending { it.price }
                SortOption.PRICE_LOWEST -> filtered.sortedBy { it.price }
            }

            HistoryUiState(transactions = sorted, filter = filter, sortOption = sortOption, isLoading = false)
        }

    // Stage 2: merge transient UI state without re-filtering/re-sorting
    val uiState: StateFlow<HistoryUiState> = combine(
        _computedTransactions,
        _uiExtras
    ) { computed, extras ->
        computed.copy(
            availableCryptos = extras.availableCryptos,
            availableExchanges = extras.availableExchanges,
            availablePlans = extras.availablePlans,
            showFilterSheet = extras.showFilterSheet,
            isExporting = extras.isExporting,
            exportSuccess = extras.exportSuccess,
            exportError = extras.exportError,
            exportData = extras.exportData,
            snackbarMessage = extras.snackbarMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState(filter = HistoryFilter(crypto = initialCrypto, planId = initialPlanId)))

    init {
        loadFilterOptions()
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val cryptosDeferred = async { transactionDao.getDistinctCryptos() }
            val exchangesDeferred = async { transactionDao.getDistinctExchanges() }
            val plansDeferred = async {
                dcaPlanDao.getAllPlansOnceOrdered().map { plan ->
                    HistoryPlanOption(
                        id = plan.id,
                        label = plan.name.ifBlank { "${plan.crypto}/${plan.fiat}" }
                    )
                }
            }

            _uiExtras.update {
                it.copy(
                    availableCryptos = cryptosDeferred.await(),
                    availableExchanges = exchangesDeferred.await(),
                    availablePlans = plansDeferred.await()
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: HistoryFilter) {
        _filterState.value = filter
    }

    fun setSideFilter(side: HistorySideFilter) {
        _filterState.value = _filterState.value.copy(sideFilter = side)
    }

    fun clearFilter() {
        // Reset everything *except* the top-level BUY/SELL/PENDING chip - users
        // typically want that chip as a persistent mode, not a one-off filter.
        _filterState.value = HistoryFilter(sideFilter = _filterState.value.sideFilter)
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleFilterSheet() {
        _uiExtras.update { it.copy(showFilterSheet = !it.showFilterSheet) }
    }

    fun hideFilterSheet() {
        _uiExtras.update { it.copy(showFilterSheet = false) }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }

    /**
     * Export transactions to CSV using UseCase.
     * Returns CSV data via state, UI handles file writing and sharing.
     * This removes Context dependency from ViewModel.
     */
    fun exportToCsv() {
        viewModelScope.launch {
            _uiExtras.update { it.copy(isExporting = true, exportError = null, exportSuccess = false, exportData = null) }

            when (val result = exportTransactionsToCsvUseCase.execute()) {
                is CsvExportResult.Success -> {
                    _uiExtras.update {
                        it.copy(
                            isExporting = false,
                            exportSuccess = true,
                            exportData = CsvExportData(
                                content = result.csvContent,
                                fileName = result.suggestedFileName
                            )
                        )
                    }
                }
                is CsvExportResult.Error -> {
                    _uiExtras.update {
                        it.copy(
                            isExporting = false,
                            exportError = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearExportState() {
        _uiExtras.update { it.copy(exportSuccess = false, exportError = null, exportData = null) }
    }
}
