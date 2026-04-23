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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                Icon(Icons.Default.Close, contentDescription = "Zavrit")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Limit sell ${state.crypto}/${state.fiat}",
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

        // Info block
        InfoRow(
            "Aktualni cena:",
            state.spotPrice?.let { "${NumberFormatters.fiat(it)} ${state.fiat}" } ?: "-"
        )
        InfoRow(
            "Prum. nakup:",
            state.avgBuyPrice?.let { "${NumberFormatters.fiat(it)} ${state.fiat}" } ?: "-"
        )
        InfoRow(
            "K dispozici:",
            "${NumberFormatters.crypto(state.availableToSell)} ${state.crypto}"
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Mnozstvi",
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
            listOf(25 to "25%", 50 to "50%", 75 to "75%", 100 to "Vse").forEach { (pct, label) ->
                AssistChip(
                    onClick = { vm.setAmountPct(pct) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Limitni cena",
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
                label = { Text("Trzni") },
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
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Souhrn",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        val amountBD = state.amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val priceBD = state.priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val proceeds = (amountBD * priceBD).setScale(2, RoundingMode.HALF_UP)
        InfoRow("Ziskate:", "${NumberFormatters.fiat(proceeds)} ${state.fiat}")
        state.avgBuyPrice?.let { avg ->
            val profit = ((priceBD - avg) * amountBD).setScale(2, RoundingMode.HALF_UP)
            val profitPct = if (avg > BigDecimal.ZERO) {
                (priceBD - avg).divide(avg, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .setScale(1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            val sign = if (profit >= BigDecimal.ZERO) "+" else ""
            InfoRow(
                label = "Zisk vs prum:",
                value = "$sign${NumberFormatters.fiat(profit)} ${state.fiat} ($sign${profitPct.toPlainString()}%)",
                color = when {
                    profit > BigDecimal.ZERO -> successColor()
                    profit < BigDecimal.ZERO -> Error
                    else -> null
                }
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
                    "Prodej probehne okamzite. Limitni cena je pod aktualni trzni (${NumberFormatters.fiat(v.spot)} ${state.fiat}). Prikaz se zfilluje ihned za nejvyssi nabidku na burze (obvykle blizko trzni ceny minus spread). Neni to chyba."
                )
                is SellValidation.FarFromMarketWarning -> WarningBanner(
                    "Cena je vysoko nad trhem - prodej se nemusi zfillovat dlouho."
                )
                is SellValidation.Ok -> { /* no-op */ }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::proceedToConfirm,
            enabled = state.canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pokracovat")
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpet")
            }
            Text(
                "Potvrdit prodej",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        SummaryRow("Burza:", state.exchangeName)
        SummaryRow("Plan:", state.planName)
        SummaryRow("Smer:", "PRODEJ")
        SummaryRow("Mnozstvi:", "${NumberFormatters.crypto(amountBD)} ${state.crypto}")
        SummaryRow("Limitni cena:", "${NumberFormatters.fiat(priceBD)} ${state.fiat}")
        SummaryRow("Ziskate:", "${NumberFormatters.fiat(proceeds)} ${state.fiat}")

        Spacer(Modifier.height(16.dp))
        WarningBanner(
            "Tato akce odesle prikaz na ${state.exchangeName} a nelze ji vratit. Prikaz lze pote zrusit, dokud neni castecne nebo cele zfillovan."
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
                Text("Zpet")
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
                    Text("Odeslat")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (state.showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissTimeoutDialog,
            title = { Text("Nelze overit stav prikazu") },
            text = {
                Text(
                    "Spojeni s burzou selhalo nebo timeoutovalo. Prikaz mohl byt odeslan, ale nelze to potvrdit. Zkontroluj otevrene ordery na burze pres web a v pripade potreby zrus duplicitu."
                )
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
