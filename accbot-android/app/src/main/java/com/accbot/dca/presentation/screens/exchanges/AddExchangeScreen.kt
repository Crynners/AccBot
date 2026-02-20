package com.accbot.dca.presentation.screens.exchanges

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.supportsApiImport
import com.accbot.dca.domain.usecase.ApiImportResultState
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.areCredentialsComplete
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExchangeScreen(
    onNavigateBack: () -> Unit,
    onExchangeAdded: () -> Unit,
    viewModel: AddExchangeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column {
                AccBotTopAppBar(
                    title = stringResource(uiState.currentStep.titleRes),
                    onNavigateBack = {
                        if (viewModel.goBack()) {
                            onNavigateBack()
                        }
                    }
                )
                // Step progress indicator
                if (uiState.currentStep != ExchangeSetupStep.SUCCESS) {
                    val progress = (uiState.currentStep.ordinal + 1) / 4f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = accentColor()
                    )
                }
            }
        }
    ) { paddingValues ->
        when (uiState.currentStep) {
            ExchangeSetupStep.SELECTION -> ExchangeSelectionStep(
                availableExchanges = viewModel.getAvailableExchanges(),
                onSelectExchange = { viewModel.selectExchange(it) },
                modifier = Modifier.padding(paddingValues)
            )
            ExchangeSetupStep.INSTRUCTIONS -> InstructionsStep(
                exchange = uiState.selectedExchange!!,
                instructions = viewModel.getInstructionsForExchange(uiState.selectedExchange!!),
                isSandboxMode = uiState.isSandboxMode,
                onContinue = { viewModel.proceedToCredentials() },
                onOpenUrl = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(paddingValues)
            )
            ExchangeSetupStep.CREDENTIALS -> CredentialsStep(
                exchange = uiState.selectedExchange!!,
                clientId = uiState.clientId,
                apiKey = uiState.apiKey,
                apiSecret = uiState.apiSecret,
                passphrase = uiState.passphrase,
                isValidating = uiState.isValidating,
                error = uiState.error,
                onClientIdChange = viewModel::setClientId,
                onApiKeyChange = viewModel::setApiKey,
                onApiSecretChange = viewModel::setApiSecret,
                onPassphraseChange = viewModel::setPassphrase,
                onValidate = { viewModel.validateAndSave(onExchangeAdded) },
                modifier = Modifier.padding(paddingValues)
            )
            ExchangeSetupStep.SUCCESS -> SuccessStep(
                exchange = uiState.selectedExchange!!,
                onFinish = onExchangeAdded,
                plansForExchange = uiState.plansForExchange,
                isApiImporting = uiState.isApiImporting,
                apiImportProgress = uiState.apiImportProgress,
                apiImportResult = uiState.apiImportResult,
                onImportViaApi = { viewModel.importViaApi() },
                onDismissImportResult = { viewModel.dismissImportResult() },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun ExchangeSelectionStep(
    availableExchanges: List<Exchange>,
    onSelectExchange: (Exchange) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.add_exchange_choose),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(availableExchanges, key = { it.name }) { exchange ->
                ExchangeSelectionCard(
                    exchange = exchange,
                    onClick = { onSelectExchange(exchange) }
                )
            }
        }

        if (availableExchanges.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = successColor()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.add_exchange_all_connected),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.add_exchange_all_connected_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExchangeSelectionCard(
    exchange: Exchange,
    onClick: () -> Unit
) {
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
            Image(
                painter = painterResource(exchange.logoRes),
                contentDescription = exchange.displayName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = exchange.displayName,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.add_exchange_cryptos, exchange.supportedCryptos.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InstructionsStep(
    exchange: Exchange,
    instructions: ExchangeInstructions,
    isSandboxMode: Boolean,
    onContinue: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use orange theme color in sandbox mode
    val accentCol = if (isSandboxMode) Warning else accentColor()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Sandbox mode indicator
        if (isSandboxMode) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Warning.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_exchange_sandbox_testnet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Warning,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Exchange header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(exchange.logoRes),
                contentDescription = exchange.displayName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = exchange.displayName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(if (isSandboxMode) R.string.add_exchange_testnet_setup else R.string.add_exchange_api_setup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                instructions.steps.forEachIndexed { index, stepResId ->
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentCol),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(stepResId),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Open website button
        OutlinedButton(
            onClick = { onOpenUrl(instructions.url) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = accentCol
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(if (isSandboxMode) R.string.add_exchange_open_testnet else R.string.add_exchange_open_api_page, exchange.displayName)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security warning / Sandbox info
        Card(
            colors = CardDefaults.cardColors(
                containerColor = accentCol.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (isSandboxMode) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    tint = accentCol,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(if (isSandboxMode) R.string.add_exchange_testnet_info else R.string.add_exchange_security_tip),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentCol)
        ) {
            Text(
                text = stringResource(R.string.add_exchange_i_have_keys),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CredentialsStep(
    exchange: Exchange,
    clientId: String,
    apiKey: String,
    apiSecret: String,
    passphrase: String,
    isValidating: Boolean,
    error: String?,
    onClientIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiSecretChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.add_exchange_enter_credentials, exchange.displayName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Use reusable CredentialsInputCard component
        CredentialsInputCard(
            exchange = exchange,
            clientId = clientId,
            apiKey = apiKey,
            apiSecret = apiSecret,
            passphrase = passphrase,
            onClientIdChange = onClientIdChange,
            onApiKeyChange = onApiKeyChange,
            onApiSecretChange = onApiSecretChange,
            onPassphraseChange = onPassphraseChange,
            errorMessage = error,
            isValidating = isValidating
        )

        Spacer(modifier = Modifier.weight(1f))

        // Validate button
        Button(
            onClick = onValidate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = areCredentialsComplete(exchange, clientId, apiKey, apiSecret, passphrase) &&
                    !isValidating,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor())
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = stringResource(R.string.add_exchange_connect),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SuccessStep(
    exchange: Exchange,
    onFinish: () -> Unit,
    plansForExchange: List<DcaPlanEntity>,
    isApiImporting: Boolean,
    apiImportProgress: String,
    apiImportResult: ApiImportResultState?,
    onImportViaApi: () -> Unit,
    onDismissImportResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val successCol = successColor()
    val accentCol = accentColor()

    // Import result dialog
    apiImportResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissImportResult,
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
                TextButton(onClick = onDismissImportResult) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(successCol.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = successCol,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.add_exchange_connected, exchange.displayName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.add_exchange_connected_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentCol)
        ) {
            Text(
                text = stringResource(R.string.common_done),
                fontWeight = FontWeight.SemiBold
            )
        }

        // Import via API section
        if (exchange.supportsApiImport) {
            if (plansForExchange.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onImportViaApi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isApiImporting && apiImportResult == null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentCol)
                ) {
                    if (isApiImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = accentCol
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = apiImportProgress,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.import_api_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.add_exchange_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
