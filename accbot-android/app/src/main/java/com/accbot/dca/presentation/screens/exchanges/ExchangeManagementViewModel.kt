package com.accbot.dca.presentation.screens.exchanges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExchangeManagementUiState(
    val connectedExchanges: List<Exchange> = emptyList(),
    val isLoading: Boolean = false,
    val isSandboxMode: Boolean = false
)

@HiltViewModel
class ExchangeManagementViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExchangeManagementUiState())
    val uiState: StateFlow<ExchangeManagementUiState> = _uiState.asStateFlow()

    init {
        loadConnectedExchanges()
    }

    fun loadConnectedExchanges() {
        val isSandbox = userPreferences.isSandboxMode()
        val connected = credentialsStore.getConfiguredExchanges(isSandbox)
        _uiState.update { it.copy(connectedExchanges = connected, isSandboxMode = isSandbox) }
    }

    fun removeExchange(exchange: Exchange) {
        val isSandbox = userPreferences.isSandboxMode()
        credentialsStore.deleteCredentials(exchange, isSandbox)
        loadConnectedExchanges()
    }

    fun hasExchange(exchange: Exchange): Boolean {
        val isSandbox = userPreferences.isSandboxMode()
        return credentialsStore.hasCredentials(exchange, isSandbox)
    }
}
