package com.accbot.dca.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.presentation.components.AccBotTopAppBar
import com.accbot.dca.presentation.components.MonthlyCostEstimateCard
import com.accbot.dca.presentation.screens.plans.FrequencyOption
import com.accbot.dca.presentation.screens.plans.StrategyOption
import com.accbot.dca.presentation.ui.theme.AccBotTheme
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlanPreviewContent() {
    val state = SampleData.editPlanUiState

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AccBotTopAppBar(
                title = stringResource(R.string.edit_plan_title),
                onNavigateBack = {}
            )
        }
    ) { paddingValues ->
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
                            text = stringResource(R.string.edit_plan_pair_info, state.crypto, state.fiat, state.exchangeName),
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
                    value = state.amount,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.common_amount)) },
                    suffix = { Text(state.fiat) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = state.minOrderSize?.let { min ->
                        {
                            Text(
                                text = stringResource(R.string.min_order_size, min.toPlainString(), state.fiat),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            isSelected = state.selectedFrequency == frequency,
                            onClick = {}
                        )
                    }
                }
            }

            // Strategy selection
            item {
                Text(
                    text = stringResource(R.string.add_plan_dca_strategy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                val strategies = listOf(
                    DcaStrategy.Classic,
                    DcaStrategy.AthBased(),
                    DcaStrategy.FearAndGreed()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strategies.forEach { strategy ->
                        val isSelected = when {
                            state.selectedStrategy is DcaStrategy.Classic && strategy is DcaStrategy.Classic -> true
                            state.selectedStrategy is DcaStrategy.AthBased && strategy is DcaStrategy.AthBased -> true
                            state.selectedStrategy is DcaStrategy.FearAndGreed && strategy is DcaStrategy.FearAndGreed -> true
                            else -> false
                        }
                        StrategyOption(
                            strategy = strategy,
                            isSelected = isSelected,
                            onClick = {},
                            onInfoClick = {}
                        )
                    }
                }
            }

            // Monthly Cost Estimate
            item {
                MonthlyCostEstimateCard(
                    estimate = state.monthlyCostEstimate!!,
                    fiat = state.fiat,
                    isClassic = state.selectedStrategy is DcaStrategy.Classic
                )
            }

            // Auto-withdrawal toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                        checked = state.withdrawalEnabled,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = successColor(),
                            checkedTrackColor = successColor().copy(alpha = 0.5f)
                        )
                    )
                }

                if (state.withdrawalEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.withdrawalAddress,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_plan_wallet_address, state.crypto)) },
                        singleLine = true
                    )
                }
            }

            // Save button
            item {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                ) {
                    Text(
                        text = stringResource(R.string.edit_plan_save),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ── Phone 6.5" ──

@PreviewTest
@Preview(name = "EditPlan_Phone_EN", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EditPlanPhoneEn() {
    AccBotTheme(darkTheme = true) { EditPlanPreviewContent() }
}

@PreviewTest
@Preview(name = "EditPlan_Phone_CS", widthDp = 412, heightDp = 892,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun EditPlanPhoneCs() {
    AccBotTheme(darkTheme = true) { EditPlanPreviewContent() }
}

// ── 7" Tablet ──

@PreviewTest
@Preview(name = "EditPlan_7inch_EN", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EditPlanTablet7En() {
    AccBotTheme(darkTheme = true) { EditPlanPreviewContent() }
}

@PreviewTest
@Preview(name = "EditPlan_7inch_CS", widthDp = 600, heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun EditPlanTablet7Cs() {
    AccBotTheme(darkTheme = true) { EditPlanPreviewContent() }
}

// ── 10" Tablet ──

@PreviewTest
@Preview(name = "EditPlan_10inch_EN", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EditPlanTablet10En() {
    AccBotTheme(darkTheme = true) { EditPlanPreviewContent() }
}

@PreviewTest
@Preview(name = "EditPlan_10inch_CS", widthDp = 800, heightDp = 1280,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "cs")
@Composable
fun EditPlanTablet10Cs() {
    AccBotTheme(darkTheme = true) { EditPlanPreviewContent() }
}
