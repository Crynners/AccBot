package com.accbot.dca.presentation.screens.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.presentation.components.ScheduleBuilder
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.LoadingState
import com.accbot.dca.presentation.components.ErrorState
import com.accbot.dca.presentation.components.MonthlyCostEstimateCard
import com.accbot.dca.presentation.components.QrScannerButton
import com.accbot.dca.presentation.components.StrategyInfoBottomSheet
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(
    planId: Long,
    onNavigateBack: () -> Unit,
    onPlanUpdated: () -> Unit,
    viewModel: EditPlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(planId) {
        viewModel.loadPlan(planId)
    }

    Scaffold(
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.edit_plan_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingState(message = stringResource(R.string.plan_details_loading))
                }
            }
            uiState.error != null && !uiState.isSaving -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.loadPlan(planId) }
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Plan info (read-only)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.edit_plan_details),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.edit_plan_pair_info, uiState.crypto, uiState.fiat, uiState.exchangeName),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.edit_plan_cannot_change),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Amount input
                    item {
                        Text(
                            text = stringResource(R.string.add_plan_amount_per_purchase),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.amount,
                            onValueChange = viewModel::setAmount,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.common_amount)) },
                            suffix = { Text(uiState.fiat) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = uiState.amountBelowMinimum || (uiState.error != null && uiState.isSaving),
                            supportingText = uiState.minOrderSize?.let { min ->
                                {
                                    Text(
                                        text = stringResource(R.string.min_order_size, min.toPlainString(), uiState.fiat),
                                        color = if (uiState.amountBelowMinimum) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }

                    // Frequency selection
                    item {
                        Text(
                            text = stringResource(R.string.add_plan_purchase_frequency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DcaFrequency.entries.forEach { frequency ->
                                FrequencyOption(
                                    frequency = frequency,
                                    isSelected = uiState.selectedFrequency == frequency,
                                    onClick = { viewModel.selectFrequency(frequency) }
                                )
                            }
                        }

                        // Schedule Builder (when Custom frequency is selected)
                        if (uiState.selectedFrequency == DcaFrequency.CUSTOM) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ScheduleBuilder(
                                cronExpression = uiState.cronExpression,
                                cronDescription = uiState.cronDescription,
                                cronError = uiState.cronError,
                                onCronExpressionChange = viewModel::setCronExpression
                            )
                        }
                    }

                    // Strategy selection
                    item {
                        var showStrategyInfo by remember { mutableStateOf<DcaStrategy?>(null) }

                        Text(
                            text = stringResource(R.string.add_plan_dca_strategy),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val strategies = remember {
                            listOf(
                                DcaStrategy.Classic,
                                DcaStrategy.AthBased(),
                                DcaStrategy.FearAndGreed()
                            )
                        }

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
                    }

                    // Monthly Cost Estimate
                    if (uiState.monthlyCostEstimate != null && uiState.amount.toBigDecimalOrNull() != null) {
                        item {
                            MonthlyCostEstimateCard(
                                estimate = uiState.monthlyCostEstimate!!,
                                fiat = uiState.fiat,
                                isClassic = uiState.selectedStrategy is DcaStrategy.Classic
                            )
                        }
                    }

                    // Auto-withdrawal toggle
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.add_plan_auto_withdrawal),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.add_plan_auto_withdrawal_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.withdrawalEnabled,
                                onCheckedChange = viewModel::setWithdrawalEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = successColor(),
                                    checkedTrackColor = successColor().copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (uiState.withdrawalEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.withdrawalAddress,
                                onValueChange = viewModel::setWithdrawalAddress,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.add_plan_wallet_address, uiState.crypto)) },
                                singleLine = true,
                                isError = uiState.addressError != null,
                                supportingText = if (uiState.addressError != null) {
                                    { Text(uiState.addressError!!, color = MaterialTheme.colorScheme.error) }
                                } else if (uiState.withdrawalAddress.isBlank()) {
                                    { Text(stringResource(R.string.edit_plan_enter_wallet, uiState.crypto)) }
                                } else null,
                                trailingIcon = {
                                    QrScannerButton(
                                        onScanResult = viewModel::setWithdrawalAddress
                                    )
                                }
                            )
                        }
                    }

                    // Error message
                    if (uiState.error != null && uiState.isSaving) {
                        item {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Save button
                    item {
                        Button(
                            onClick = { viewModel.savePlan(onPlanUpdated) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = uiState.isValid && !uiState.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.edit_plan_save),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FrequencyOption(
    frequency: DcaFrequency,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val successCol = successColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                successCol.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(frequency.displayNameRes),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) successCol else MaterialTheme.colorScheme.onSurface
            )
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = successCol
                )
            )
        }
    }
}

@Composable
private fun StrategyOption(
    strategy: DcaStrategy,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val accentCol = accentColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                accentCol.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(strategy.displayNameRes),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) accentCol else MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.add_plan_strategy_info),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(strategy.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentCol
                )
            )
        }
    }
}
