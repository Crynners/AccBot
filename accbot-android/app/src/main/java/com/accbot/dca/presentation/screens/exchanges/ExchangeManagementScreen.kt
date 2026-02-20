package com.accbot.dca.presentation.screens.exchanges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.ExchangeAvatar
import com.accbot.dca.presentation.components.SectionHeader
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddExchange: (String?) -> Unit,
    onNavigateToExchangeDetail: (String) -> Unit = {},
    viewModel: ExchangeManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Refresh when returning to screen
    LaunchedEffect(Unit) {
        viewModel.loadConnectedExchanges()
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.exchanges_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        val availableExchanges = Exchange.entries.filter { it !in uiState.connectedExchanges }

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
                        ExchangeTile(
                            exchange = exchange,
                            isConnected = true,
                            onClick = { onNavigateToExchangeDetail(exchange.name) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Available exchanges section
                if (availableExchanges.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = stringResource(R.string.exchanges_available))
                    }

                    items(availableExchanges, key = { it.name }) { exchange ->
                        ExchangeTile(
                            exchange = exchange,
                            isConnected = false,
                            onClick = { onNavigateToAddExchange(exchange.name) }
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ExchangeTile(
    exchange: Exchange,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val successCol = successColor()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExchangeAvatar(
                exchange = exchange,
                size = 48.dp,
                isConnected = isConnected
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = exchange.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isConnected) {
                    stringResource(R.string.common_connected)
                } else {
                    stringResource(R.string.add_exchange_cryptos, exchange.supportedCryptos.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected) successCol else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
