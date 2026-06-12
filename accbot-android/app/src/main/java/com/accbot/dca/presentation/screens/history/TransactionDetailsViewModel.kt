package com.accbot.dca.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.usecase.CancelSellOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class TransactionDetailsUiState(
    val transaction: Transaction? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCancelling: Boolean = false
)

@HiltViewModel
class TransactionDetailsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val cancelSellOrderUseCase: CancelSellOrderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailsUiState())
    val uiState: StateFlow<TransactionDetailsUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    fun loadTransaction(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val entity = transactionDao.getTransactionById(transactionId)
                if (entity == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Transaction not found") }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        transaction = entity.toDomain(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load transaction"
                    )
                }
            }
        }
    }

    /**
     * Cancel an open limit sell order. On success the underlying Flow (DAO) will
     * update status to FAILED/COMPLETED per the use case and the next loadTransaction
     * call picks that up. We also re-fetch here to keep the already-open detail
     * screen in sync without waiting for a user navigation round trip.
     */
    fun cancelOrder(txId: Long) {
        if (_uiState.value.isCancelling) return
        _uiState.update { it.copy(isCancelling = true) }
        viewModelScope.launch {
            val result = cancelSellOrderUseCase(txId)
            if (result.isFailure) {
                _snackbar.emit(
                    "Zruseni orderu selhalo: ${result.exceptionOrNull()?.message ?: "neznama chyba"}"
                )
            }
            // Refresh after cancel attempt (success or failure) so displayed status is accurate.
            try {
                val entity = transactionDao.getTransactionById(txId)
                if (entity != null) {
                    _uiState.update { it.copy(transaction = entity.toDomain(), isCancelling = false) }
                } else {
                    _uiState.update { it.copy(isCancelling = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isCancelling = false) }
            }
        }
    }
}
