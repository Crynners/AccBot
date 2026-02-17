package com.accbot.dca.presentation.screens.exchanges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.ExchangeCard
import com.accbot.dca.presentation.components.SectionHeader
import com.accbot.dca.presentation.ui.theme.accentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddExchange: () -> Unit,
    viewModel: ExchangeManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var exchangeToRemove by remember { mutableStateOf<Exchange?>(null) }

    // Refresh when returning to screen
    LaunchedEffect(Unit) {
        viewModel.loadConnectedExchanges()
    }

    // Confirmation dialog for removing exchange
    if (exchangeToRemove != null) {
        AlertDialog(
            onDismissRequest = { exchangeToRemove = null },
            title = { Text(stringResource(R.string.exchanges_remove_title)) },
            text = {
                Text(stringResource(R.string.exchanges_remove_text, exchangeToRemove?.displayName ?: ""))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exchangeToRemove?.let { viewModel.removeExchange(it) }
                        exchangeToRemove = null
                    }
                ) {
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { exchangeToRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.exchanges_title),
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddExchange,
                containerColor = accentColor()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.exchanges_add))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Connected exchanges section
            if (uiState.connectedExchanges.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.exchanges_connected))
                }

                items(uiState.connectedExchanges, key = { it.name }) { exchange ->
                    ExchangeCard(
                        exchange = exchange,
                        isConnected = true,
                        onClick = { /* Could navigate to exchange details */ },
                        onRemove = { exchangeToRemove = exchange }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Available exchanges section
            val availableExchanges = Exchange.entries.filter { it !in uiState.connectedExchanges }

            if (availableExchanges.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.exchanges_available))
                }

                items(availableExchanges, key = { it.name }) { exchange ->
                    ExchangeCard(
                        exchange = exchange,
                        isConnected = false,
                        onClick = onNavigateToAddExchange
                    )
                }
            }

            // Empty state if no exchanges at all
            if (uiState.connectedExchanges.isEmpty() && availableExchanges.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AccountBalance,
                        title = stringResource(R.string.exchanges_none_title),
                        description = stringResource(R.string.exchanges_none_desc)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}
