package com.accbot.dca.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.presentation.components.CryptoIcon
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.ui.theme.Warning
import com.accbot.dca.presentation.ui.theme.successColor
import com.accbot.dca.presentation.utils.DateFormatters
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal

/** Read-only card of a received plan: no toggle, countdown or actions; a click opens the detail. */
@Composable
internal fun SharedPlanCard(info: SharedPlanCardInfo, onClick: () -> Unit) {
    val plan = info.plan
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.shared_plan_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CryptoIcon(crypto = plan.crypto, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = plan.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${plan.crypto}/${plan.fiat} · ${plan.exchange?.displayName ?: plan.exchangeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                info.performancePercent?.let {
                    Text(
                        text = "${NumberFormatters.roiSign(it)}${NumberFormatters.percent(it)} %",
                        fontWeight = FontWeight.SemiBold,
                        color = if (it >= BigDecimal.ZERO) successColor() else Error
                    )
                }
            }
            plan.summary.lastTradeAt?.let {
                Text(
                    text = stringResource(R.string.shared_plan_card_last_trade, DateFormatters.shortDateTime.format(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            plan.summary.heldCrypto?.let {
                Text(
                    text = stringResource(R.string.shared_plan_card_held, NumberFormatters.crypto(it), plan.crypto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val refreshedAt = plan.refreshedAt
            if (plan.liveUpdates && refreshedAt != null) {
                Text(
                    text = stringResource(R.string.shared_plan_live_text, DateFormatters.shortDateTime.format(refreshedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (info.keyRemoved) {
                Text(
                    text = stringResource(R.string.shared_plan_key_revoked_not_read_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning
                )
            } else if (info.refreshFailed) {
                Text(
                    text = stringResource(R.string.shared_plan_refresh_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning
                )
            }
        }
    }
}
