package com.accbot.dca.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.presentation.model.MonthlyCostEstimate
import com.accbot.dca.presentation.ui.theme.accentColor
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal

@Composable
fun MonthlyCostEstimateCard(
    estimate: MonthlyCostEstimate,
    fiat: String,
    isClassic: Boolean
) {
    val accentCol = accentColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accentCol.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentCol
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_plan_monthly_estimate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentCol
                )
            }

            if (isClassic) {
                Text(
                    text = stringResource(R.string.add_plan_monthly_approx, formatAmount(estimate.minMonthly), fiat),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = stringResource(R.string.add_plan_monthly_range, formatAmount(estimate.minMonthly), formatAmount(estimate.maxMonthly), fiat),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (estimate.currentMonthly != null) {
                    Text(
                        text = stringResource(R.string.add_plan_monthly_current, formatAmount(estimate.currentMonthly), fiat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentCol
                    )
                }
                if (estimate.currentInfo != null) {
                    Text(
                        text = estimate.currentInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatAmount(amount: BigDecimal): String {
    return NumberFormatters.fiat(amount)
}
