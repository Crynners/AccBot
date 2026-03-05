package com.accbot.dca.presentation.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeInstructions
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.ExchangeSelectionGrid
import com.accbot.dca.presentation.components.ExperimentalExchangeDisclaimer
import com.accbot.dca.presentation.components.ExperimentalExchangesToggle
import com.accbot.dca.presentation.components.ExperimentalToggleDisclaimer
import com.accbot.dca.presentation.components.SandboxCredentialsInfoCard
import com.accbot.dca.presentation.components.SandboxModeIndicator
import com.accbot.dca.R
import com.accbot.dca.presentation.ui.theme.accentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeSetupScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exchange_setup_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.common_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val context = LocalContext.current

        // Experimental exchange disclaimer (selecting an experimental exchange)
        var experimentalExchangePending by remember { mutableStateOf<Exchange?>(null) }
        experimentalExchangePending?.let { exchange ->
            ExperimentalExchangeDisclaimer(
                onConfirm = {
                    experimentalExchangePending = null
                    viewModel.selectExchange(exchange)
                },
                onDismiss = { experimentalExchangePending = null }
            )
        }

        // Experimental toggle disclaimer (enabling the toggle)
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.exchange_setup_choose),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.exchange_setup_choose_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sandbox mode indicator
            if (uiState.isSandboxMode) {
                SandboxModeIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Exchange selection grid with request card
            ExchangeSelectionGrid(
                exchanges = uiState.availableExchanges,
                onExchangeClick = { exchange ->
                    if (exchange.isStable) {
                        viewModel.selectExchange(exchange)
                    } else {
                        experimentalExchangePending = exchange
                    }
                },
                selectedExchange = uiState.selectedExchange,
                onRequestExchangeClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Crynners/AccBot/issues"))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Experimental exchanges toggle
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

            // Instructions + credentials (only show when exchange is selected)
            if (uiState.selectedExchange != null) {
                Spacer(modifier = Modifier.height(24.dp))

                // Exchange setup instructions card
                if (uiState.selectedExchangeInstructions != null) {
                    if (uiState.isSandboxMode) {
                        SandboxCredentialsInfoCard(
                            exchange = uiState.selectedExchange!!,
                            instructions = uiState.selectedExchangeInstructions!!
                        )
                    } else {
                        ExchangeInstructionsCard(
                            exchange = uiState.selectedExchange!!,
                            instructions = uiState.selectedExchangeInstructions!!
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                CredentialsInputCard(
                    exchange = uiState.selectedExchange!!,
                    clientId = uiState.clientId,
                    apiKey = uiState.apiKey,
                    apiSecret = uiState.apiSecret,
                    passphrase = uiState.passphrase,
                    onClientIdChange = viewModel::setClientId,
                    onApiKeyChange = viewModel::setApiKey,
                    onApiSecretChange = viewModel::setApiSecret,
                    onPassphraseChange = viewModel::setPassphrase,
                    errorMessage = uiState.credentialsError,
                    isValidating = uiState.isValidatingCredentials
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button
            Button(
                onClick = {
                    if (uiState.selectedExchange != null) {
                        viewModel.validateAndSaveCredentials(onSuccess = onContinue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.selectedExchange != null &&
                        uiState.apiKey.isNotBlank() &&
                        uiState.apiSecret.isNotBlank() &&
                        (uiState.selectedExchange != Exchange.COINMATE || uiState.clientId.isNotBlank()) &&
                        !uiState.isValidatingCredentials,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor()
                )
            ) {
                if (uiState.isValidatingCredentials) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.exchange_setup_connect),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExchangeInstructionsCard(
    exchange: Exchange,
    instructions: ExchangeInstructions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedUrl = instructions.urlRes?.let { stringResource(it) } ?: instructions.url
    val accentCol = accentColor()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.add_exchange_api_setup),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )

            instructions.steps.forEachIndexed { index, stepResId ->
                Row {
                    Text(
                        "${index + 1}.",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(stringResource(stepResId), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (resolvedUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentCol)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_exchange_open_api_page, exchange.displayName))
                }
            }

            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = accentCol,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_exchange_security_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
