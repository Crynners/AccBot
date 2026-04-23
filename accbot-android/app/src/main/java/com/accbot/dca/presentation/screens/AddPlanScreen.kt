package com.accbot.dca.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
    onNavigateToExchangeManagement: (() -> Unit)? = null,
    viewModel: AddPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardAlert by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (uiState.hasChanges) {
            showDiscardAlert = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = uiState.hasChanges) {
        showDiscardAlert = true
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onPlanCreated()
        }
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text(stringResource(R.string.common_discard_changes_title)) },
            text = { Text(stringResource(R.string.common_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardAlert = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.common_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAlert = false }) {
                    Text(stringResource(R.string.common_keep_editing))
                }
            }
        )
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
                    // Phase 7+: navigate to Exchange Management list. User picks the
                    // specific connection envelope to import balances into. (Was previously
                    // a direct deep-link to ExchangeDetail with autoImport=true.)
                    onNavigateToExchangeManagement?.invoke()
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
                onNavigateBack = handleBack
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

            // Connection picker / credentials form
            val cred = uiState.credentialForm
            if (cred.selectedExchange != null) {
                val hasMultipleExisting = cred.existingConnections.size >= 2
                val isCreatingNew = cred.selectedConnectionId == null && cred.existingConnections.isNotEmpty()

                // Picker: shown when 2+ existing connections so the user can pick which
                // envelope this plan should target (and switch to "create new" if needed).
                if (hasMultipleExisting) {
                    SectionTitle(stringResource(R.string.add_plan_pick_connection))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cred.existingConnections.forEach { connection ->
                            ConnectionPickerRow(
                                label = if (connection.name.isNotBlank()) connection.name
                                        else stringResource(R.string.exchanges_default_connection_label),
                                selected = cred.selectedConnectionId == connection.id,
                                onClick = { viewModel.credentialForm.selectExistingConnection(connection.id) }
                            )
                        }
                        ConnectionPickerRow(
                            label = stringResource(R.string.add_plan_create_new_connection),
                            selected = cred.selectedConnectionId == null,
                            onClick = { viewModel.credentialForm.startNewConnection() }
                        )
                    }
                }

                // Credentials entry form: shown only when the user is creating a NEW connection
                // (no existing connection, or explicitly chose "Create new" from the picker).
                val showCredentialForm = cred.selectedConnectionId == null
                if (showCredentialForm) {
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

                    // Connection name input (required when there's already 1+ connections on this exchange)
                    if (cred.requireConnectionName || cred.existingConnections.isNotEmpty()) {
                        OutlinedTextField(
                            value = cred.connectionName,
                            onValueChange = viewModel.credentialForm::setConnectionName,
                            label = { Text(stringResource(R.string.exchanges_connection_name_label)) },
                            placeholder = { Text(stringResource(R.string.exchanges_connection_name_hint)) },
                            singleLine = true,
                            isError = cred.requireConnectionName && cred.connectionName.isBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (cred.requireConnectionName) {
                            Text(
                                text = stringResource(R.string.exchanges_connection_name_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                        errorMessage = uiState.errorMessage,
                        isValidating = uiState.isLoading
                    )
                }
            }

            // Multi-plan warning (when adding to a connection that already has plans)
            if (cred.selectedConnectionId != null && cred.existingPlansOnSelectedConnection > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.add_plan_multi_plan_warning,
                            cred.existingPlansOnSelectedConnection
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Plan form (crypto, fiat, amount, frequency, strategy, withdrawal, target)
            if (cred.selectedExchange != null) {
                PlanFormContent(
                    state = uiState.planForm,
                    availableCryptos = cred.selectedExchange!!.supportedCryptos,
                    availableFiats = cred.selectedExchange!!.supportedFiats,
                    onNameChanged = viewModel.planForm::setName,
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
                    errorMessage = uiState.errorMessage,
                    showSellSection = uiState.tradingEnabled,
                    onAllowSellsChanged = viewModel.planForm::setAllowSells,
                    onTargetProfitAmountChanged = viewModel.planForm::setTargetProfitAmount
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

/**
 * Single row in the connection picker (radio-like): a row with a leading RadioButton,
 * a label and a clickable surface. Uses the app's accent color (green / sandbox orange)
 * so it fits the AccBot palette instead of the Material3 default purple.
 */
@Composable
private fun ConnectionPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = com.accbot.dca.presentation.ui.theme.accentColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accent,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
