package com.accbot.dca.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.toDomain
import com.accbot.dca.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class TransactionDetailsUiState(
    val transaction: Transaction? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TransactionDetailsViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailsUiState())
    val uiState: StateFlow<TransactionDetailsUiState> = _uiState.asStateFlow()

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
}
