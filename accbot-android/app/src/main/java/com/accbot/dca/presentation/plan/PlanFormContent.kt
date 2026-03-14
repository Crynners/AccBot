package com.accbot.dca.presentation.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.presentation.components.AmountInputWithPresets
import com.accbot.dca.presentation.components.ChipGroup
import com.accbot.dca.presentation.components.FrequencyDropdown
import com.accbot.dca.presentation.components.MonthlyCostEstimateCard
import com.accbot.dca.presentation.components.QrScannerButton
import com.accbot.dca.presentation.components.ScheduleBuilder
import com.accbot.dca.presentation.components.SectionTitle
import com.accbot.dca.presentation.components.StrategySelectionSection
import com.accbot.dca.presentation.components.getCryptoIconRes
import com.accbot.dca.presentation.components.getFiatIconRes

/**
 * Shared plan configuration form used by AddPlanScreen, FirstPlanScreen, and EditPlanScreen.
 * Renders: crypto/fiat selection, amount, frequency, strategy, monthly estimate,
 * auto-withdrawal, target amount, and error message.
 *
 * Does NOT include exchange selection, credentials, or the submit button —
 * those are screen-specific.
 */
@Composable
fun PlanFormContent(
    state: PlanFormState,
    availableCryptos: List<String>,
    availableFiats: List<String>,
    onCryptoSelected: (String) -> Unit,
    onFiatSelected: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onFrequencySelected: (DcaFrequency) -> Unit,
    onCronExpressionChanged: (String) -> Unit,
    onStrategySelected: (DcaStrategy) -> Unit,
    onWithdrawalEnabledChanged: (Boolean) -> Unit,
    onWithdrawalAddressChanged: (String) -> Unit,
    onTargetAmountChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    exchange: Exchange? = null,
    showCryptoFiatSelection: Boolean = true,
    errorMessage: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Crypto selection
        if (showCryptoFiatSelection) {
            Column {
                SectionTitle(stringResource(R.string.add_plan_cryptocurrency))
                ChipGroup(
                    options = availableCryptos,
                    selectedOption = state.selectedCrypto,
                    onOptionSelected = onCryptoSelected,
                    iconResolver = { getCryptoIconRes(it) }
                )
            }

            // Fiat selection
            Column {
                SectionTitle(stringResource(R.string.add_plan_fiat_currency))
                ChipGroup(
                    options = availableFiats,
                    selectedOption = state.selectedFiat,
                    onOptionSelected = onFiatSelected,
                    iconResolver = { getFiatIconRes(it) }
                )
            }
        }

        // Amount input
        Column {
            SectionTitle(stringResource(R.string.add_plan_amount_per_purchase))
            AmountInputWithPresets(
                amount = state.amount,
                onAmountChange = onAmountChanged,
                fiat = state.selectedFiat,
                minOrderSize = state.minOrderSize,
                amountBelowMinimum = state.amountBelowMinimum
            )
            if (exchange == Exchange.BINANCE) {
                val stepSize = Exchange.binanceLotStepSize[state.selectedCrypto]
                if (stepSize != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.binance_lot_size_note, state.selectedCrypto, stepSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Frequency selection
        Column {
            SectionTitle(stringResource(R.string.add_plan_purchase_frequency))
            FrequencyDropdown(
                selectedFrequency = state.selectedFrequency,
                onFrequencySelected = onFrequencySelected
            )

            if (state.selectedFrequency == DcaFrequency.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                ScheduleBuilder(
                    cronExpression = state.cronExpression,
                    cronDescription = state.cronDescription,
                    cronError = state.cronError,
                    onCronExpressionChange = onCronExpressionChanged
                )
            }
        }

        // Strategy selection
        Column {
            SectionTitle(stringResource(R.string.add_plan_dca_strategy))
            StrategySelectionSection(
                selectedStrategy = state.selectedStrategy,
                onStrategySelected = onStrategySelected
            )
        }

        // Monthly cost estimate
        if (state.monthlyCostEstimate != null && state.amount.toBigDecimalOrNull() != null) {
            MonthlyCostEstimateCard(
                estimate = state.monthlyCostEstimate,
                fiat = state.selectedFiat,
                isClassic = state.selectedStrategy is DcaStrategy.Classic
            )
        }

        // Auto-withdrawal toggle
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onWithdrawalEnabledChanged(!state.withdrawalEnabled) }
                    .semantics(mergeDescendants = true) { role = Role.Switch },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.add_plan_auto_withdrawal), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.add_plan_auto_withdrawal_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.withdrawalEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }

            if (state.withdrawalEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.withdrawalAddress,
                    onValueChange = onWithdrawalAddressChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.add_plan_wallet_address, state.selectedCrypto)) },
                    singleLine = true,
                    isError = state.addressError != null,
                    supportingText = if (state.addressError != null) {
                        { Text(state.addressError, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    trailingIcon = {
                        QrScannerButton(
                            onScanResult = onWithdrawalAddressChanged
                        )
                    }
                )
            }
        }

        // Target amount
        Column {
            SectionTitle(stringResource(R.string.plan_target_amount))
            OutlinedTextField(
                value = state.targetAmount,
                onValueChange = onTargetAmountChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.plan_target_amount)) },
                placeholder = { Text(stringResource(R.string.plan_target_amount_hint)) },
                suffix = { Text(state.selectedCrypto) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = {
                    Text(
                        text = stringResource(R.string.plan_target_amount_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
