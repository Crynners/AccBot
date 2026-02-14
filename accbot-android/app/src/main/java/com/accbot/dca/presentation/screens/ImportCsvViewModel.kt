package com.accbot.dca.presentation.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.usecase.CsvImportResult
import com.accbot.dca.domain.usecase.ImportCoinmateCsvUseCase
import com.accbot.dca.domain.usecase.ParsedTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportCsvUiState(
    val planCrypto: String = "",
    val planFiat: String = "",
    val planExchange: String = "",
    val csvLoaded: Boolean = false,
    val newCount: Int = 0,
    val skippedCount: Int = 0,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val importSuccess: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    private val dcaPlanDao: DcaPlanDao,
    private val importUseCase: ImportCoinmateCsvUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCsvUiState())
    val uiState: StateFlow<ImportCsvUiState> = _uiState.asStateFlow()

    private var planId: Long = 0
    private var exchange: Exchange = Exchange.COINMATE
    private var parsedTransactions: List<ParsedTransaction> = emptyList()
    private var entitiesToImport: List<TransactionEntity> = emptyList()

    fun loadPlan(planId: Long) {
        this.planId = planId
        viewModelScope.launch {
            val plan = dcaPlanDao.getPlanById(planId)
            if (plan != null) {
                exchange = plan.exchange
                _uiState.update {
                    it.copy(
                        planCrypto = plan.crypto,
                        planFiat = plan.fiat,
                        planExchange = plan.exchange.displayName,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(error = "Plan not found", isLoading = false)
                }
            }
        }
    }

    fun loadCsv(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }

            try {
                val state = _uiState.value
                parsedTransactions = importUseCase.parseCsv(content, state.planCrypto, state.planFiat)

                if (parsedTransactions.isEmpty()) {
                    _uiState.update {
                        it.copy(csvLoaded = false, error = "no_buy_transactions")
                    }
                    return@launch
                }

                val existingIds = importUseCase.getExistingOrderIds(planId)
                val (newCount, skippedCount) = importUseCase.countNew(parsedTransactions, existingIds)
                entitiesToImport = importUseCase.toEntities(parsedTransactions, planId, exchange, existingIds)

                _uiState.update {
                    it.copy(
                        csvLoaded = true,
                        newCount = newCount,
                        skippedCount = skippedCount,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(csvLoaded = false, error = "parse_error")
                }
            }
        }
    }

    fun importTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }

            when (val result = importUseCase.importTransactions(entitiesToImport)) {
                is CsvImportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importSuccess = true,
                            importedCount = result.importedCount
                        )
                    }
                }
                is CsvImportResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }
}
