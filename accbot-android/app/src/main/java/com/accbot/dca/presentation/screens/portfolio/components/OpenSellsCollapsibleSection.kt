package com.accbot.dca.presentation.screens.portfolio.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.presentation.screens.plans.components.OpenSellRow

/**
 * Collapsible section showing open (PENDING / PARTIAL) sell orders for the
 * currently selected plan on the Pozice (Portfolio) screen. Header is always
 * visible; the order list is hidden by default and toggled by tapping the
 * header. Hidden entirely when there are no open orders for the plan.
 *
 * Reuses [OpenSellRow] from the plan-detail screen so the cancel-confirmation
 * dialog is shared between the two surfaces.
 */
@Composable
fun OpenSellsCollapsibleSection(
    openSells: List<Transaction>,
    onCancelClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (openSells.isEmpty()) return

    // Persist expand state across configuration changes (rotation) but reset on
    // process death - matches the "transient UI" feel.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tappable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.portfolio_open_sells_section_title,
                        openSells.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    HorizontalDivider()
                    openSells.forEach { tx ->
                        OpenSellRow(tx = tx, onCancelClick = onCancelClick)
                    }
                }
            }
        }
    }
}
