package com.accbot.dca.presentation.screens.exchanges

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.ExchangeAvatar
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExchangeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val exchange = uiState.exchange ?: return
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRemoveDialog by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.exchange_detail_saved)

    // Import result dialog
    uiState.apiImportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportResult() },
            title = {
                Text(
                    when (result) {
                        is ApiImportResultState.Success -> stringResource(R.string.import_api_success_title)
                        is ApiImportResultState.Error -> stringResource(R.string.import_api_error_title)
                    }
                )
            },
            text = {
                Text(
                    when (result) {
                        is ApiImportResultState.Success -> {
                            if (result.imported == 0) {
                                stringResource(R.string.import_api_no_new)
                            } else {
                                stringResource(R.string.import_api_success_message, result.imported, result.skipped)
                            }
                        }
                        is ApiImportResultState.Error -> result.message
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportResult() }) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
    }

    // Remove confirmation dialog
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.exchanges_remove_title)) },
            text = {
                Text(stringResource(R.string.exchanges_remove_text, exchange.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        viewModel.removeExchange(onRemoved = onNavigateBack)
                    }
                ) {
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AccBotTopAppBar(
                title = exchange.displayName,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Exchange avatar + connected badge
            ExchangeAvatar(
                exchange = exchange,
                size = 64.dp,
                isConnected = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.common_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = successColor(),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info card: supported cryptos, fiats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.exchanges_detail_cryptos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = exchange.supportedCryptos.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.exchanges_detail_fiats),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = exchange.supportedFiats.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Collapsible credentials section
            val chevronRotation by animateFloatAsState(
                targetValue = if (uiState.credentialsExpanded) 180f else 0f,
                label = "chevron"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    // Header - always visible, clickable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleCredentials() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.exchange_detail_credentials_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.rotate(chevronRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Expandable content
                    AnimatedVisibility(visible = uiState.credentialsExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                            CredentialsInputCard(
                                exchange = exchange,
                                clientId = uiState.clientId,
                                apiKey = uiState.apiKey,
                                apiSecret = uiState.apiSecret,
                                passphrase = uiState.passphrase,
                                onClientIdChange = viewModel::setClientId,
                                onApiKeyChange = viewModel::setApiKey,
                                onApiSecretChange = viewModel::setApiSecret,
                                onPassphraseChange = viewModel::setPassphrase,
                                errorMessage = uiState.error,
                                isValidating = uiState.isValidating
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Save button
                            Button(
                                onClick = {
                                    viewModel.saveCredentials {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(savedMessage)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isValidating
                            ) {
                                if (uiState.isValidating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.exchange_detail_save))
                            }
                        }
                    }
                }
            }

            // Import via API card
            if (exchange.supportsApiImport) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    onClick = { viewModel.importViaApi() },
                    enabled = !uiState.isApiImporting && uiState.plans.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (uiState.plans.isNotEmpty()) accentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.import_api_title),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.isApiImporting) {
                                Text(
                                    text = uiState.apiImportProgress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentColor()
                                )
                            } else if (uiState.plans.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.exchange_detail_import_no_plans),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.exchange_detail_import_subtitle_plans, uiState.plans.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (uiState.isApiImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Remove exchange button
            OutlinedButton(
                onClick = { showRemoveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                border = BorderStroke(1.dp, Error)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.exchanges_remove_exchange))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        } // Box
    }
}
