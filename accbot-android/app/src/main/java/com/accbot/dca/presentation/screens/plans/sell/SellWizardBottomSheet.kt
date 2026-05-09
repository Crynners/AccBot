package com.accbot.dca.presentation.screens.plans.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accbot.dca.R
import com.accbot.dca.domain.usecase.SellValidation
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Two-step bottom sheet for placing a limit sell order:
 *  1. INPUT - amount + price with quick-set chips, live validations and summary
 *  2. CONFIRM - read-only summary + warning + submit (added in Task 25)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellWizardBottomSheet(
    planId: Long,
    onDismiss: () -> Unit,
    viewModel: SellWizardViewModel = hiltViewModel()
) {
    LaunchedEffect(planId) { viewModel.init(planId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.dismissRequested) {
        if (state.dismissRequested) {
            viewModel.consumeDismiss()
            onDismiss()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        when (state.step) {
            SellWizardViewModel.Step.INPUT -> SellInputStep(state, viewModel, onDismiss)
            SellWizardViewModel.Step.CONFIRM -> SellConfirmStep(state, viewModel)
        }
    }

    state.ladderOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::consumeLadderOutcome,
            title = { Text(stringResource(R.string.sell_wizard_ladder_enable)) },
            text = {
                Text(
                    if (outcome.reason == null) {
                        stringResource(R.string.sell_wizard_ladder_outcome_all, outcome.placed)
                    } else {
                        stringResource(
                            R.string.sell_wizard_ladder_outcome_partial,
                            outcome.placed, outcome.total, outcome.reason
                        )
                    }
                )
            },
            confirmButton = {
                Button(onClick = viewModel::consumeLadderOutcome) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SellInputStep(
    state: SellWizardViewModel.UiState,
    vm: SellWizardViewModel,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sell_wizard_title, state.crypto, state.fiat),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.exchangeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Info block (read-only context)
        InfoRow(
            stringResource(R.string.sell_wizard_spot_price),
            state.spotPrice?.let { "${NumberFormatters.fiat(it)} ${state.fiat}" } ?: "-"
        )
        InfoRow(
            stringResource(R.string.sell_wizard_available),
            "${NumberFormatters.crypto(state.availableToSell)} ${state.crypto}"
        )

        if (state.inventoryDeficit > BigDecimal.ZERO) {
            Spacer(Modifier.height(4.dp))
            WarningBanner(
                stringResource(
                    R.string.sell_wizard_inventory_deficit,
                    "${NumberFormatters.crypto(state.inventoryDeficit)} ${state.crypto}"
                )
            )
        }

        // Editable avg buy price
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.sell_wizard_avg_buy),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.avgBuyPriceInput,
            onValueChange = vm::setAvgBuyPrice,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.avgBuyPriceManual && state.avgBuyPriceAuto != null) {
                        AssistChip(
                            onClick = vm::resetAvgBuyPrice,
                            label = { Text(stringResource(R.string.sell_wizard_avg_buy_reset)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = state.fiat,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            supportingText = {
                Text(
                    stringResource(
                        when {
                            state.avgBuyPriceAuto == null && state.avgBuyPriceInput.isBlank() ->
                                R.string.sell_wizard_avg_buy_helper_required
                            state.avgBuyPriceManual ->
                                R.string.sell_wizard_avg_buy_helper_manual
                            else -> R.string.sell_wizard_avg_buy_helper_auto
                        }
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.sell_wizard_amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = vm::setAmount,
            trailingIcon = {
                Text(
                    text = state.crypto,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allLabel = stringResource(R.string.sell_wizard_amount_all)
            listOf(25 to "25%", 50 to "50%", 75 to "75%", 100 to allLabel).forEach { (pct, label) ->
                AssistChip(
                    onClick = { vm.setAmountPct(pct) },
                    label = { Text(label) }
                )
            }
        }

        // Ladder mode toggle
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = state.ladderEnabled,
                onCheckedChange = vm::setLadderEnabled
            )
            Text(stringResource(R.string.sell_wizard_ladder_enable))
        }

        if (state.ladderEnabled) {
            LadderControls(state = state, vm = vm)
        }

        if (!state.ladderEnabled) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.sell_wizard_limit_price),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.priceInput,
            onValueChange = vm::setPrice,
            trailingIcon = {
                Text(
                    text = state.fiat,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = vm::setPriceSpot,
                label = { Text(stringResource(R.string.sell_wizard_chip_spot)) },
                enabled = state.spotPrice != null
            )
            AssistChip(
                onClick = vm::setPriceBreakeven,
                label = { Text("Breakeven") },
                enabled = state.avgBuyPrice != null
            )
            AssistChip(
                onClick = { vm.setPriceAvgPlus(10) },
                label = { Text("+10%") },
                enabled = state.avgBuyPrice != null
            )
            AssistChip(
                onClick = { vm.setPriceAvgPlus(25) },
                label = { Text("+25%") },
                enabled = state.avgBuyPrice != null
            )
            AssistChip(
                onClick = { vm.setPriceAvgPlus(50) },
                label = { Text("+50%") },
                enabled = state.avgBuyPrice != null
            )
        }

        // Net fiat field (3rd of the calculator triple)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.sell_wizard_net_fiat),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.netInput,
            onValueChange = vm::setNetFiat,
            trailingIcon = {
                Text(
                    text = state.fiat,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text(
            stringResource(R.string.sell_wizard_net_preset_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.10 to "+10%", 0.20 to "+20%", 0.50 to "+50%", 1.00 to "+100%").forEach { (factor, label) ->
                AssistChip(
                    onClick = { vm.applyNetProfitPreset(factor) },
                    label = { Text(label) },
                    enabled = state.avgBuyPrice != null && state.amountInput.toBigDecimalOrNull() != null
                )
            }
        }
        } // end if !ladderEnabled

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.sell_wizard_summary),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        val amountBD = state.amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val priceBD = state.priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val proceeds = (amountBD * priceBD).setScale(2, RoundingMode.HALF_UP)
        val feeAmount = (proceeds * state.feeRate).setScale(2, RoundingMode.HALF_UP)
        val netProceeds = (proceeds - feeAmount).setScale(2, RoundingMode.HALF_UP)
        InfoRow(
            stringResource(R.string.sell_wizard_proceeds),
            "${NumberFormatters.fiat(proceeds)} ${state.fiat}"
        )
        if (state.feeRate > BigDecimal.ZERO && proceeds > BigDecimal.ZERO) {
            InfoRow(
                stringResource(R.string.sell_wizard_summary_fee),
                "-${NumberFormatters.fiat(feeAmount)} ${state.fiat} (${state.feeRate.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString()}%)"
            )
        }
        state.avgBuyPrice?.let { avg ->
            val costBasis = amountBD * avg
            val netProfit = (netProceeds - costBasis).setScale(2, RoundingMode.HALF_UP)
            val netProfitPct = if (costBasis > BigDecimal.ZERO) {
                netProfit.divide(costBasis, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            val sign = if (netProfit >= BigDecimal.ZERO) "+" else ""
            InfoRow(
                label = stringResource(R.string.sell_wizard_summary_net_profit),
                value = "$sign${NumberFormatters.fiat(netProfit)} ${state.fiat} ($sign${netProfitPct.toPlainString()}%)",
                color = when {
                    netProfit > BigDecimal.ZERO -> successColor()
                    netProfit < BigDecimal.ZERO -> Error
                    else -> null
                }
            )

            // Target progress
            state.targetProfitAmount?.takeIf { it > BigDecimal.ZERO }?.let { target ->
                val totalProgress = state.realizedPnLSoFar + netProfit
                val pct = (totalProgress.toDouble() / target.toDouble()).coerceAtLeast(0.0)
                InfoRow(
                    stringResource(R.string.sell_wizard_summary_target_progress),
                    "${NumberFormatters.fiat(totalProgress)} / ${NumberFormatters.fiat(target)} ${state.fiat} (${"%.0f".format(pct * 100)}%)"
                )
            }
        }

        // Loss banner from validations
        state.validations.filterIsInstance<SellValidation.LossWarning>().firstOrNull()?.let { loss ->
            Spacer(Modifier.height(8.dp))
            val isPriceBelowAvg = state.priceInput.toBigDecimalOrNull()?.let { p ->
                state.avgBuyPrice?.let { avg -> p < avg }
            } ?: false
            LossBanner(
                stringResource(
                    if (isPriceBelowAvg) R.string.sell_wizard_loss_below_buy
                    else R.string.sell_wizard_loss_after_fee,
                    NumberFormatters.fiat(loss.lossFiat),
                    state.fiat
                )
            )
        }

        // Validations
        Spacer(Modifier.height(8.dp))
        state.validations.forEach { v ->
            when (v) {
                is SellValidation.HardError -> Text(
                    text = v.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                is SellValidation.InstantFillInfo -> InfoBanner(
                    stringResource(R.string.sell_wizard_instant_fill_warning, NumberFormatters.fiat(v.spot), state.fiat)
                )
                is SellValidation.FarFromMarketWarning -> WarningBanner(
                    stringResource(R.string.sell_wizard_far_from_market_warning)
                )
                is SellValidation.LossWarning -> { /* shown via LossBanner in summary section */ }
                is SellValidation.Ok -> { /* no-op */ }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::proceedToConfirm,
            enabled = state.canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.sell_wizard_proceed))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SellConfirmStep(
    state: SellWizardViewModel.UiState,
    vm: SellWizardViewModel
) {
    val amountBD = state.amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val priceBD = state.priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val proceeds = (amountBD * priceBD).setScale(2, RoundingMode.HALF_UP)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                stringResource(R.string.sell_wizard_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        SummaryRow(stringResource(R.string.sell_wizard_confirm_exchange), state.exchangeName)
        SummaryRow(stringResource(R.string.sell_wizard_confirm_plan), state.planName)
        SummaryRow(stringResource(R.string.sell_wizard_confirm_side), stringResource(R.string.sell_wizard_confirm_side_sell))
        SummaryRow(stringResource(R.string.sell_wizard_confirm_amount), "${NumberFormatters.crypto(amountBD)} ${state.crypto}")
        SummaryRow(stringResource(R.string.sell_wizard_confirm_limit_price), "${NumberFormatters.fiat(priceBD)} ${state.fiat}")
        SummaryRow(stringResource(R.string.sell_wizard_confirm_proceeds), "${NumberFormatters.fiat(proceeds)} ${state.fiat}")

        Spacer(Modifier.height(16.dp))
        WarningBanner(
            stringResource(R.string.sell_wizard_confirm_warning, state.exchangeName)
        )

        state.submitError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = vm::back,
                enabled = !state.submitting,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.sell_wizard_back))
            }
            Button(
                onClick = { vm.submit() },
                enabled = !state.submitting,
                modifier = Modifier.weight(1f)
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text(stringResource(R.string.sell_wizard_submit))
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (state.showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissTimeoutDialog,
            title = { Text(stringResource(R.string.sell_wizard_timeout_title)) },
            text = {
                Text(stringResource(R.string.sell_wizard_timeout_text))
            },
            confirmButton = {
                Button(onClick = vm::dismissTimeoutDialog) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun InfoRow(
    label: String,
    value: String,
    color: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color ?: LocalContentColor.current,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun InfoBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LadderControls(state: SellWizardViewModel.UiState, vm: SellWizardViewModel) {
    val avg = state.avgBuyPrice
    Spacer(Modifier.height(12.dp))

    // Range mode toggle
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SellWizardViewModel.LadderRangeMode.PROFIT_PCT to stringResource(R.string.sell_wizard_ladder_range_profit),
            SellWizardViewModel.LadderRangeMode.PRICE to stringResource(R.string.sell_wizard_ladder_range_price)
        ).forEach { (mode, label) ->
            AssistChip(
                onClick = { vm.setLadderRangeMode(mode) },
                label = { Text(label) },
                colors = if (state.ladderRangeMode == mode) {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else androidx.compose.material3.AssistChipDefaults.assistChipColors()
            )
        }
    }

    // From / To
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.ladderFromInput,
            onValueChange = vm::setLadderFrom,
            label = { Text(stringResource(R.string.sell_wizard_ladder_from)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = state.ladderToInput,
            onValueChange = vm::setLadderTo,
            label = { Text(stringResource(R.string.sell_wizard_ladder_to)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }

    // Count
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.ladderCountInput,
        onValueChange = vm::setLadderCount,
        label = { Text(stringResource(R.string.sell_wizard_ladder_count)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    // Amount mode toggle
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            LadderGenerator.AmountMode.EQUAL_CRYPTO to stringResource(R.string.sell_wizard_ladder_amount_equal_crypto),
            LadderGenerator.AmountMode.EQUAL_FIAT to stringResource(R.string.sell_wizard_ladder_amount_equal_fiat)
        ).forEach { (mode, label) ->
            AssistChip(
                onClick = { vm.setLadderAmountMode(mode) },
                label = { Text(label) },
                colors = if (state.ladderAmountMode == mode) {
                    androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else androidx.compose.material3.AssistChipDefaults.assistChipColors()
            )
        }
    }

    state.ladderHardError?.let { err ->
        Spacer(Modifier.height(8.dp))
        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (state.ladderPreview.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("#", modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.sell_wizard_amount), modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.sell_wizard_limit_price), modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.labelSmall)
                Text("Profit", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Net", modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.labelSmall)
            }
            androidx.compose.material3.HorizontalDivider()
            var totalNet = BigDecimal.ZERO
            state.ladderPreview.forEachIndexed { i, o ->
                val gross = o.cryptoAmount * o.limitPrice
                val net = gross * (BigDecimal.ONE - state.feeRate)
                val profitPctText = if (avg != null && avg > BigDecimal.ZERO) {
                    val pct = (o.limitPrice - avg).divide(avg, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)
                    "${if (pct.signum() >= 0) "+" else ""}${pct.toPlainString()}%"
                } else "-"
                totalNet += if (avg != null) net - o.cryptoAmount * avg else net
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${i + 1}", modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
                    Text(NumberFormatters.crypto(o.cryptoAmount), modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                    Text(NumberFormatters.fiat(o.limitPrice), modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
                    Text(profitPctText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(NumberFormatters.fiat(net.setScale(2, RoundingMode.HALF_UP)), modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
                }
            }
            androidx.compose.material3.HorizontalDivider()
            Text(
                "${stringResource(R.string.sell_wizard_ladder_preview_total)}: ${NumberFormatters.fiat(totalNet.setScale(2, RoundingMode.HALF_UP))} ${state.fiat}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
internal fun LossBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
internal fun WarningBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
