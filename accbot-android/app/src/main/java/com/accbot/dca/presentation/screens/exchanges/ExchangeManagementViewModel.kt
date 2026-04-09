package com.accbot.dca.presentation.screens.exchanges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accbot.dca.data.local.ExchangeConnectionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.repository.ExchangeConnectionRepository
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.isStable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

/**
 * UI state for the exchange management screen. Lists individual connections (envelopes)
 * rather than exchanges, since v2.8 a single exchange can have multiple credential sets.
 */
@Immutable
data class ExchangeManagementUiState(
    val connections: List<ExchangeConnectionEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSandboxMode: Boolean = false,
    val showExperimental: Boolean = false
)

@HiltViewModel
class ExchangeManagementViewModel @Inject constructor(
    private val connectionRepository: ExchangeConnectionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExchangeManagementUiState())
    val uiState: StateFlow<ExchangeManagementUiState> = _uiState.asStateFlow()

    init {
        // Reactive flow on connections so additions/deletions in detail screens reflect
        // here without an explicit reload.
        viewModelScope.launch {
            connectionRepository.observeAll().collect { connections ->
                _uiState.update { it.copy(connections = connections) }
            }
        }
        refreshFlags()
    }

    /**
     * Re-read non-reactive prefs (sandbox mode, experimental flag) on screen resume.
     * The connections list itself is reactive via [ExchangeConnectionRepository.observeAll].
     */
    fun refreshFlags() {
        viewModelScope.launch {
            val isSandbox = userPreferences.isSandboxMode()
            val showExperimental = userPreferences.areExperimentalExchangesEnabled()
            _uiState.update { it.copy(isSandboxMode = isSandbox, showExperimental = showExperimental) }
        }
    }

    fun setExperimentalExchangesEnabled(enabled: Boolean) {
        userPreferences.setExperimentalExchangesEnabled(enabled)
        _uiState.update { it.copy(showExperimental = enabled) }
    }

    /**
     * Display label for a connection — exchange display name plus optional custom name.
     * E.g. "Coinmate" or "Coinmate — Spoření".
     */
    fun displayLabel(connection: ExchangeConnectionEntity): String =
        connectionRepository.displayLabel(connection)
}
