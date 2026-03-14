package com.accbot.dca.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.isStable
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.ExchangeInstructionsCard
import com.accbot.dca.presentation.components.ExchangeSelectionGrid
import com.accbot.dca.presentation.components.ExperimentalExchangeDisclaimer
import com.accbot.dca.presentation.components.ExperimentalExchangesToggle
import com.accbot.dca.presentation.components.ExperimentalToggleDisclaimer
import com.accbot.dca.presentation.components.SandboxCredentialsInfoCard
import com.accbot.dca.presentation.components.SandboxModeIndicator
import com.accbot.dca.presentation.components.SectionTitle
import com.accbot.dca.presentation.plan.PlanFormContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlanScreen(
    onNavigateBack: () -> Unit,
    onPlanCreated: () -> Unit,
    onNavigateToExchangeDetail: ((String) -> Unit)? = null,
    viewModel: AddPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onPlanCreated()
        }
    }

    // Import offer dialog after creating plan with new credentials
    if (uiState.showImportDialog) {
        val exchangeName = uiState.credentialForm.selectedExchange?.displayName ?: ""
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportDialog() },
            title = { Text(stringResource(R.string.import_api_offer_title)) },
            text = { Text(stringResource(R.string.import_api_offer_text, exchangeName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissImportDialog()
                    uiState.credentialForm.selectedExchange?.let { exchange ->
                        onNavigateToExchangeDetail?.invoke(exchange.name)
                    }
                }) {
                    Text(stringResource(R.string.import_api_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissImportDialog() }) {
                    Text(stringResource(R.string.common_skip))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.add_plan_title),
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Exchange Selection
            SectionTitle(stringResource(R.string.add_plan_select_exchange))

            // Sandbox mode indicator
            if (uiState.credentialForm.isSandboxMode) {
                SandboxModeIndicator()
            }

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

            val addPlanContext = LocalContext.current

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        addPlanContext.startActivity(intent)
                    }
                )

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
            }

            // API Credentials
            val cred = uiState.credentialForm
            if (cred.selectedExchange != null && !cred.hasCredentials) {
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
                }

                SectionTitle(stringResource(R.string.add_plan_api_credentials))
                // Use reusable CredentialsInputCard component
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
                    errorMessage = uiState.errorMessage,
                    isValidating = uiState.isLoading
                )
            }

            // Plan form (crypto, fiat, amount, frequency, strategy, withdrawal, target)
            if (cred.selectedExchange != null) {
                PlanFormContent(
                    state = uiState.planForm,
                    availableCryptos = cred.selectedExchange!!.supportedCryptos,
                    availableFiats = cred.selectedExchange!!.supportedFiats,
                    onCryptoSelected = viewModel.planForm::selectCrypto,
                    onFiatSelected = viewModel.planForm::selectFiat,
                    onAmountChanged = viewModel.planForm::setAmount,
                    onFrequencySelected = viewModel.planForm::selectFrequency,
                    onCronExpressionChanged = viewModel.planForm::setCronExpression,
                    onStrategySelected = viewModel.planForm::selectStrategy,
                    onWithdrawalEnabledChanged = viewModel.planForm::setWithdrawalEnabled,
                    onWithdrawalAddressChanged = viewModel.planForm::setWithdrawalAddress,
                    onTargetAmountChanged = viewModel.planForm::setTargetAmount,
                    exchange = cred.selectedExchange,
                    errorMessage = uiState.errorMessage
                )

                // Create Button
                Button(
                    onClick = viewModel::createPlan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = uiState.isValid && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.add_plan_create))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        } // Box
    }
}
