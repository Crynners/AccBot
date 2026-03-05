package com.accbot.dca.presentation.screens.exchanges

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.EmptyState
import com.accbot.dca.presentation.components.ExchangeAvatar
import com.accbot.dca.presentation.components.SectionHeader
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor

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
        AlertDialog(
            onDismissRequest = { showExperimentalDisclaimer = false },
            title = { Text(stringResource(R.string.experimental_warning_title)) },
            text = { Text(stringResource(R.string.experimental_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showExperimentalDisclaimer = false
                    viewModel.setExperimentalExchangesEnabled(true)
                }) {
                    Text(stringResource(R.string.experimental_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExperimentalDisclaimer = false }) {
                    Text(stringResource(R.string.common_back))
                }
            }
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = stringResource(R.string.exchanges_available))
                }

                if (availableExchanges.isNotEmpty()) {
                    items(availableExchanges, key = { it.name }) { exchange ->
                        ExchangeTile(
                            exchange = exchange,
                            isConnected = false,
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
            if (!isConnected && !exchange.isStable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.experimental_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RequestExchangeTile(
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.add_exchange_request),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExperimentalExchangesToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isEnabled)
            }
            .semantics(mergeDescendants = true) { role = Role.Switch },
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Warning.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_experimental_exchanges),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEnabled) {
                        stringResource(R.string.settings_experimental_exchanges_enabled)
                    } else {
                        stringResource(R.string.settings_experimental_exchanges_disabled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Warning,
                    checkedTrackColor = Warning.copy(alpha = 0.5f)
                )
            )
        }
    }
}
