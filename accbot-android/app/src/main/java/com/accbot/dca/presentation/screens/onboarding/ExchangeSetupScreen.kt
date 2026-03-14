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
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.credentials.resolvedCredentialsError
import com.accbot.dca.presentation.components.ExchangeSelectionGrid
import com.accbot.dca.presentation.components.ExchangeInstructionsCard
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
            AccBotTopAppBar(
                title = stringResource(R.string.exchange_setup_title),
                onNavigateBack = onBack,
                actions = {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.common_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
                    viewModel.credentialForm.setExperimentalExchangesEnabled(true)
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
            if (uiState.credentialForm.isSandboxMode) {
                SandboxModeIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Exchange selection grid with request card
            ExchangeSelectionGrid(
                exchanges = uiState.credentialForm.availableExchanges,
                onExchangeClick = { exchange ->
                    if (exchange.isStable) {
                        viewModel.selectExchange(exchange)
                    } else {
                        experimentalExchangePending = exchange
                    }
                },
                selectedExchange = uiState.credentialForm.selectedExchange,
                onRequestExchangeClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Crynners/AccBot/issues"))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Experimental exchanges toggle
            ExperimentalExchangesToggle(
                isEnabled = uiState.credentialForm.showExperimental,
                onToggle = { enabled ->
                    if (enabled) {
                        showExperimentalDisclaimer = true
                    } else {
                        viewModel.credentialForm.setExperimentalExchangesEnabled(false)
                    }
                }
            )

            // Instructions + credentials (only show when exchange is selected)
            val cred = uiState.credentialForm
            if (cred.selectedExchange != null) {
                Spacer(modifier = Modifier.height(24.dp))

                // Exchange setup instructions card
                if (cred.selectedExchangeInstructions != null) {
                    if (cred.isSandboxMode) {
                        SandboxCredentialsInfoCard(
                            exchange = cred.selectedExchange!!,
                            instructions = cred.selectedExchangeInstructions!!
                        )
                    } else {
                        ExchangeInstructionsCard(
                            exchange = cred.selectedExchange!!,
                            instructions = cred.selectedExchangeInstructions!!
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                CredentialsInputCard(
                    exchange = cred.selectedExchange!!,
                    clientId = cred.clientId,
                    apiKey = cred.apiKey,
                    apiSecret = cred.apiSecret,
                    passphrase = cred.passphrase,
                    onClientIdChange = viewModel.credentialForm::setClientId,
                    onApiKeyChange = viewModel.credentialForm::setApiKey,
                    onApiSecretChange = viewModel.credentialForm::setApiSecret,
                    onPassphraseChange = viewModel.credentialForm::setPassphrase,
                    errorMessage = cred.resolvedCredentialsError,
                    isValidating = cred.isValidatingCredentials
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button
            Button(
                onClick = {
                    if (cred.selectedExchange != null) {
                        viewModel.credentialForm.validateAndSaveCredentials(onSuccess = onContinue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = cred.selectedExchange != null &&
                        cred.apiKey.isNotBlank() &&
                        cred.apiSecret.isNotBlank() &&
                        (cred.selectedExchange != Exchange.COINMATE || cred.clientId.isNotBlank()) &&
                        !cred.isValidatingCredentials,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor()
                )
            ) {
                if (cred.isValidatingCredentials) {
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

