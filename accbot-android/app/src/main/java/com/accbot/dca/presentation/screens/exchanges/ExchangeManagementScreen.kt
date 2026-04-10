package com.accbot.dca.presentation.screens.exchanges

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.ExchangeSelectionTile
import com.accbot.dca.presentation.components.ExperimentalExchangesToggle
import com.accbot.dca.presentation.components.ExperimentalToggleDisclaimer
import com.accbot.dca.presentation.components.RequestExchangeTile
import com.accbot.dca.presentation.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddExchange: (String?) -> Unit,
    onNavigateToExchangeDetail: (Long) -> Unit = {},
    viewModel: ExchangeManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExperimentalDisclaimer by remember { mutableStateOf(false) }

    if (showExperimentalDisclaimer) {
        ExperimentalToggleDisclaimer(
            onConfirm = {
                showExperimentalDisclaimer = false
                viewModel.setExperimentalExchangesEnabled(true)
            },
            onDismiss = { showExperimentalDisclaimer = false }
        )
    }

    // Refresh when returning to screen (e.g. after removing exchange in detail)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFlags()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.exchanges_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        // Group connections by exchange so the user can see envelopes per exchange.
        val groupedConnections = remember(uiState.connections) {
            uiState.connections.groupBy { it.exchange }
        }
        val availableExchanges = Exchange.entries
            .filter { uiState.showExperimental || it.isStable }

        if (uiState.connections.isEmpty() && availableExchanges.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.AccountBalance,
                    title = stringResource(R.string.exchanges_none_title),
                    description = stringResource(R.string.exchanges_none_desc)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Connected: render one tile per connection (envelope), grouped by exchange.
                // The tile subtitle shows the connection name when set, so users with multiple
                // envelopes ("Hlavní", "Spoření") can tell them apart.
                if (uiState.connections.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = stringResource(R.string.exchanges_connected))
                    }

                    items(uiState.connections, key = { it.id }) { connection ->
                        ExchangeSelectionTile(
                            exchange = connection.exchange,
                            isConnected = true,
                            subtitle = if (connection.name.isNotBlank()) connection.name
                                       else stringResource(R.string.exchanges_default_connection_label),
                            onClick = { onNavigateToExchangeDetail(connection.id) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Available exchanges section - show ALL exchanges (no longer filtered by
                // "has credentials"), so the user can add a second connection on Coinmate.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = stringResource(R.string.exchanges_available))
                }

                if (availableExchanges.isNotEmpty()) {
                    items(availableExchanges, key = { it.name }) { exchange ->
                        // Subtitle hint when there's already at least one connection on this exchange
                        val existingCount = groupedConnections[exchange]?.size ?: 0
                        val subtitle = if (existingCount > 0) {
                            stringResource(R.string.exchanges_add_another, existingCount)
                        } else null
                        ExchangeSelectionTile(
                            exchange = exchange,
                            subtitle = subtitle,
                            onClick = { onNavigateToAddExchange(exchange.name) }
                        )
                    }
                }

                // Request Exchange card
                item {
                    val context = LocalContext.current
                    RequestExchangeTile(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Crynners/AccBot/issues"))
                            context.startActivity(intent)
                        }
                    )
                }

                // Experimental exchanges toggle
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ExperimentalExchangesToggle(
                        isEnabled = uiState.showExperimental,
                        onToggle = { enabled ->
                            if (enabled) {
                                showExperimentalDisclaimer = true
                            } else {
                                viewModel.setExperimentalExchangesEnabled(false)
                            }
                        }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
