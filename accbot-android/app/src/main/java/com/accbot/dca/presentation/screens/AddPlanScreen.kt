package com.accbot.dca.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.CredentialsInputCard
import com.accbot.dca.presentation.components.MonthlyCostEstimateCard
import com.accbot.dca.presentation.components.QrScannerButton
import com.accbot.dca.presentation.components.SandboxCredentialsInfoCard
import com.accbot.dca.presentation.components.SandboxModeIndicator
import com.accbot.dca.presentation.components.ScheduleBuilder
import com.accbot.dca.presentation.components.SelectableChip
import com.accbot.dca.presentation.components.StrategyInfoBottomSheet
import com.accbot.dca.presentation.components.StrategyOption
import com.accbot.dca.presentation.components.getCryptoIconRes
import com.accbot.dca.presentation.components.getFiatIconRes
import com.accbot.dca.presentation.ui.theme.successColor

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
        val exchangeName = uiState.selectedExchange?.displayName ?: ""
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportDialog() },
            title = { Text(stringResource(R.string.import_api_offer_title)) },
            text = { Text(stringResource(R.string.import_api_offer_text, exchangeName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissImportDialog()
                    uiState.selectedExchange?.let { exchange ->
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
            TopAppBar(
                title = { Text(stringResource(R.string.add_plan_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            if (uiState.isSandboxMode) {
                SandboxModeIndicator()
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.availableExchanges.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { exchange ->
                            ExchangeCard(
                                exchange = exchange,
                                isSelected = uiState.selectedExchange == exchange,
                                onClick = { viewModel.selectExchange(exchange) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining slots for incomplete rows
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // API Credentials
            if (uiState.selectedExchange != null && !uiState.hasCredentials) {
                // Sandbox credentials info card
                if (uiState.isSandboxMode && uiState.selectedExchangeInstructions != null) {
                    SandboxCredentialsInfoCard(
                        exchange = uiState.selectedExchange!!,
                        instructions = uiState.selectedExchangeInstructions!!
                    )
                }

                SectionTitle(stringResource(R.string.add_plan_api_credentials))
                // Use reusable CredentialsInputCard component
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
                    errorMessage = uiState.errorMessage,
                    isValidating = uiState.isLoading
                )
            }

            // Cryptocurrency Selection
            if (uiState.selectedExchange != null) {
                SectionTitle(stringResource(R.string.add_plan_cryptocurrency))
                ChipGroup(
                    options = uiState.selectedExchange!!.supportedCryptos,
                    selectedOption = uiState.selectedCrypto,
                    onOptionSelected = viewModel::selectCrypto,
                    iconResolver = { getCryptoIconRes(it) }
                )

                // Fiat Currency Selection
                SectionTitle(stringResource(R.string.add_plan_fiat_currency))
                ChipGroup(
                    options = uiState.selectedExchange!!.supportedFiats,
                    selectedOption = uiState.selectedFiat,
                    onOptionSelected = viewModel::selectFiat,
                    iconResolver = { getFiatIconRes(it) }
                )

                // Amount Input
                SectionTitle(stringResource(R.string.add_plan_amount_per_purchase))
                OutlinedTextField(
                    value = uiState.amount,
                    onValueChange = viewModel::setAmount,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.common_amount)) },
                    suffix = { Text(uiState.selectedFiat) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = uiState.amountBelowMinimum,
                    supportingText = uiState.minOrderSize?.let { min ->
                        {
                            Text(
                                text = stringResource(R.string.min_order_size, min.toPlainString(), uiState.selectedFiat),
                                color = if (uiState.amountBelowMinimum) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                // Preset amount buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickAmounts = remember(uiState.minOrderSize) {
                        val allAmounts = listOf("25", "50", "100", "250", "500")
                        val min = uiState.minOrderSize
                        if (min != null) allAmounts.filter { it.toBigDecimal() >= min }
                        else allAmounts
                    }
                    quickAmounts.forEach { preset ->
                        OutlinedButton(
                            onClick = { viewModel.setAmount(preset) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = if (uiState.amount == preset) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = successColor().copy(alpha = 0.15f),
                                    contentColor = successColor()
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(text = preset, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Frequency Selection - Dropdown
                SectionTitle(stringResource(R.string.add_plan_purchase_frequency))
                FrequencyDropdown(
                    selectedFrequency = uiState.selectedFrequency,
                    onFrequencySelected = viewModel::selectFrequency
                )

                // Schedule Builder (when Custom frequency is selected)
                if (uiState.selectedFrequency == DcaFrequency.CUSTOM) {
                    ScheduleBuilder(
                        cronExpression = uiState.cronExpression,
                        cronDescription = uiState.cronDescription,
                        cronError = uiState.cronError,
                        onCronExpressionChange = viewModel::setCronExpression
                    )
                }

                // Strategy Selection
                SectionTitle(stringResource(R.string.add_plan_dca_strategy))
                val strategies = remember {
                    listOf(
                        DcaStrategy.Classic,
                        DcaStrategy.AthBased(),
                        DcaStrategy.FearAndGreed()
                    )
                }
                var showStrategyInfo by remember { mutableStateOf<DcaStrategy?>(null) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strategies.forEach { strategy ->
                        val isSelected = when {
                            uiState.selectedStrategy is DcaStrategy.Classic && strategy is DcaStrategy.Classic -> true
                            uiState.selectedStrategy is DcaStrategy.AthBased && strategy is DcaStrategy.AthBased -> true
                            uiState.selectedStrategy is DcaStrategy.FearAndGreed && strategy is DcaStrategy.FearAndGreed -> true
                            else -> false
                        }
                        StrategyOption(
                            strategy = strategy,
                            isSelected = isSelected,
                            onClick = { viewModel.selectStrategy(strategy) },
                            onInfoClick = { showStrategyInfo = strategy }
                        )
                    }
                }

                // Strategy Info Bottom Sheet
                showStrategyInfo?.let { strategy ->
                    StrategyInfoBottomSheet(
                        strategy = strategy,
                        onDismiss = { showStrategyInfo = null }
                    )
                }

                // Monthly Cost Estimate
                if (uiState.monthlyCostEstimate != null && uiState.amount.toBigDecimalOrNull() != null) {
                    MonthlyCostEstimateCard(
                        estimate = uiState.monthlyCostEstimate!!,
                        fiat = uiState.selectedFiat,
                        isClassic = uiState.selectedStrategy is DcaStrategy.Classic
                    )
                }

                // Auto-withdrawal Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) { viewModel.setWithdrawalEnabled(!uiState.withdrawalEnabled) }
                        .semantics(mergeDescendants = true) { role = Role.Switch },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.add_plan_auto_withdrawal), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.add_plan_auto_withdrawal_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.withdrawalEnabled,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                }

                if (uiState.withdrawalEnabled) {
                    OutlinedTextField(
                        value = uiState.withdrawalAddress,
                        onValueChange = viewModel::setWithdrawalAddress,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_plan_wallet_address, uiState.selectedCrypto)) },
                        singleLine = true,
                        trailingIcon = {
                            QrScannerButton(
                                onScanResult = viewModel::setWithdrawalAddress
                            )
                        }
                    )
                }

                // Target Amount (optional goal)
                SectionTitle(stringResource(R.string.plan_target_amount))
                OutlinedTextField(
                    value = uiState.targetAmount,
                    onValueChange = viewModel::setTargetAmount,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plan_target_amount)) },
                    placeholder = { Text(stringResource(R.string.plan_target_amount_hint)) },
                    suffix = { Text(uiState.selectedCrypto) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = stringResource(R.string.plan_target_amount_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // Error Message
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun ExchangeCard(
    exchange: Exchange,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val successCol = successColor()
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                successCol.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(successCol)
            )
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(exchange.logoRes),
                contentDescription = exchange.displayName,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = exchange.displayName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) successCol else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ChipGroup(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    iconResolver: ((String) -> Int?)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            val iconRes = iconResolver?.invoke(option)
            SelectableChip(
                text = option,
                selected = option == selectedOption,
                onClick = { onOptionSelected(option) },
                leadingIcon = if (iconRes != null) {
                    {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyDropdown(
    selectedFrequency: DcaFrequency,
    onFrequencySelected: (DcaFrequency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = stringResource(selectedFrequency.displayNameRes),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DcaFrequency.entries.forEach { frequency ->
                DropdownMenuItem(
                    text = { Text(stringResource(frequency.displayNameRes)) },
                    onClick = {
                        onFrequencySelected(frequency)
                        expanded = false
                    },
                    leadingIcon = if (frequency == selectedFrequency) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = successColor()) }
                    } else null
                )
            }
        }
    }
}


