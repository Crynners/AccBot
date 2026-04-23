package com.accbot.dca.presentation.screens.plans.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accbot.dca.domain.model.PlanPnL
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal

/**
 * Plan-level profit & loss card with realized / unrealized / net breakdown and
 * optional target-progress bar when the plan has `targetProfitAmount` configured.
 *
 * Null-valued PnL fields render as "-" (no spot price available / no buys yet).
 */
@Composable
fun PnLCard(
    pnl: PlanPnL,
    fiat: String,
    crypto: String,
    targetAmount: BigDecimal?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "P&L",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))

            val heldValue = if (pnl.currentValueFiat != null) {
                "${NumberFormatters.crypto(pnl.currentCryptoHeld)} $crypto (${NumberFormatters.fiat(pnl.currentValueFiat)} $fiat)"
            } else {
                "${NumberFormatters.crypto(pnl.currentCryptoHeld)} $crypto"
            }
            PnLRow(label = "Drzeno:", value = heldValue)

            PnLRow(
                label = "Prum. nakup:",
                value = pnl.avgBuyPrice?.let { "${NumberFormatters.fiat(it)} $fiat" } ?: "-"
            )

            PnLRow(
                label = "Realizovany:",
                value = formatOptionalPnL(pnl.realizedPnL, fiat),
                color = colorForPnL(pnl.realizedPnL)
            )
            PnLRow(
                label = "Nerealizovany:",
                value = formatOptionalPnL(pnl.unrealizedPnL, fiat),
                color = colorForPnL(pnl.unrealizedPnL)
            )
            PnLRow(
                label = "Net:",
                value = formatOptionalPnL(pnl.netPnL, fiat),
                color = colorForPnL(pnl.netPnL),
                bold = true
            )

            if (targetAmount != null && pnl.targetProgressPct != null) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { pnl.targetProgressPct.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Cil: ${NumberFormatters.fiat(targetAmount)} $fiat (${(pnl.targetProgressPct * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PnLRow(
    label: String,
    value: String,
    color: Color? = null,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
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
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun colorForPnL(value: BigDecimal?): Color? = when {
    value == null -> null
    value > BigDecimal.ZERO -> successColor()
    value < BigDecimal.ZERO -> Error
    else -> null
}

private fun formatOptionalPnL(value: BigDecimal?, fiat: String): String {
    if (value == null) return "-"
    val prefix = if (value >= BigDecimal.ZERO) "+" else ""
    return "$prefix${NumberFormatters.fiat(value)} $fiat"
}
