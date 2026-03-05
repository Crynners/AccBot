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
    onNavigateToExchangeDetail: (String) -> Unit = {},
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
                viewModel.loadConnectedExchanges()
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
        val availableExchanges = Exchange.entries
            .filter { it !in uiState.connectedExchanges }
            .filter { uiState.showExperimental || it.isStable }

        if (uiState.connectedExchanges.isEmpty() && availableExchanges.isEmpty()) {
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
                // Connected exchanges section
                if (uiState.connectedExchanges.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = stringResource(R.string.exchanges_connected))
                    }

                    items(uiState.connectedExchanges, key = { it.name }) { exchange ->
                        ExchangeSelectionTile(
                            exchange = exchange,
                            isConnected = true,
                            subtitle = stringResource(R.string.common_connected),
                            onClick = { onNavigateToExchangeDetail(exchange.name) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Available exchanges section
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = stringResource(R.string.exchanges_available))
                }

                if (availableExchanges.isNotEmpty()) {
                    items(availableExchanges, key = { it.name }) { exchange ->
                        ExchangeSelectionTile(
                            exchange = exchange,
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
