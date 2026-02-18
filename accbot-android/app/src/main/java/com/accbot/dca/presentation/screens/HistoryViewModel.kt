package com.accbot.dca.presentation.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.domain.usecase.CsvExportResult
import com.accbot.dca.domain.usecase.ExportTransactionsToCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class HistoryFilter(
    val crypto: String? = null,
    val exchange: String? = null,
    val status: TransactionStatus? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
)

/**
 * Data class for CSV export result to pass to UI for file handling.
 */
data class CsvExportData(
    val content: String,
    val fileName: String
)

data class HistoryUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val availableCryptos: List<String> = emptyList(),
    val availableExchanges: List<String> = emptyList(),
    val showFilterSheet: Boolean = false,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val exportError: String? = null,
    val exportData: CsvExportData? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
    private val exportTransactionsToCsvUseCase: ExportTransactionsToCsvUseCase
) : ViewModel() {

    private val initialCrypto: String? = savedStateHandle["crypto"]
    private val initialFiat: String? = savedStateHandle["fiat"]

    private val _uiState = MutableStateFlow(
        HistoryUiState(
            filter = HistoryFilter(crypto = initialCrypto)
        )
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val allTransactions = MutableStateFlow<List<TransactionEntity>>(emptyList())

    init {
        loadTransactions()
        loadFilterOptions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            transactionDao.getAllTransactions().collect { transactions ->
                allTransactions.value = transactions
                applyFilter()
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val cryptos = transactionDao.getDistinctCryptos()
            val exchanges = transactionDao.getDistinctExchanges()

            _uiState.update {
                it.copy(
                    availableCryptos = cryptos,
                    availableExchanges = exchanges
                )
            }
        }
    }

    fun setFilter(filter: HistoryFilter) {
        _uiState.update { it.copy(filter = filter) }
        applyFilter()
    }

    fun clearFilter() {
        _uiState.update { it.copy(filter = HistoryFilter()) }
        applyFilter()
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        applyFilter()
    }

    fun toggleFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = !it.showFilterSheet) }
    }

    fun hideFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = false) }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }

    private fun applyFilter() {
        val filter = _uiState.value.filter
        val sortOption = _uiState.value.sortOption

        val filtered = allTransactions.value.filter { tx ->
            val epochMillis = tx.executedAt.toEpochMilli()
            (filter.crypto == null || tx.crypto == filter.crypto) &&
            (filter.exchange == null || tx.exchange.name == filter.exchange) &&
            (filter.status == null || tx.status == filter.status) &&
            (filter.dateFrom == null || epochMillis >= filter.dateFrom) &&
            (filter.dateTo == null || epochMillis <= filter.dateTo + 86_400_000)
        }

        val sorted = when (sortOption) {
            SortOption.DATE_NEWEST -> filtered.sortedByDescending { it.executedAt }
            SortOption.DATE_OLDEST -> filtered.sortedBy { it.executedAt }
            SortOption.AMOUNT_HIGHEST -> filtered.sortedByDescending { it.fiatAmount }
            SortOption.AMOUNT_LOWEST -> filtered.sortedBy { it.fiatAmount }
            SortOption.PRICE_HIGHEST -> filtered.sortedByDescending { it.price }
            SortOption.PRICE_LOWEST -> filtered.sortedBy { it.price }
        }

        _uiState.update { it.copy(transactions = sorted) }
    }

    /**
     * Export transactions to CSV using UseCase.
     * Returns CSV data via state, UI handles file writing and sharing.
     * This removes Context dependency from ViewModel.
     */
    fun exportToCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null, exportSuccess = false, exportData = null) }

            when (val result = exportTransactionsToCsvUseCase.execute()) {
                is CsvExportResult.Success -> {
                    _uiState.update {
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
                    _uiState.update {
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
        _uiState.update { it.copy(exportSuccess = false, exportError = null, exportData = null) }
    }
}
