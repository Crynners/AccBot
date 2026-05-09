package com.accbot.dca.presentation.screens.plans.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.presentation.ui.theme.Error
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Card listing all open (PENDING / PARTIAL) sell orders for a plan with per-row
 * cancel action and a "Cancel all" header button. Hidden entirely when there are no
 * open sells.
 */
@Composable
fun OpenSellsList(
    openSells: List<Transaction>,
    onCancelClick: (Long) -> Unit,
    onCancelAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (openSells.isEmpty()) return

    var showCancelAllConfirm by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.open_sells_title, openSells.size),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                if (onCancelAllClick != null && openSells.size > 1) {
                    TextButton(onClick = { showCancelAllConfirm = true }) {
                        Text(
                            stringResource(R.string.open_sells_cancel_all),
                            color = Error
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            openSells.forEach { tx ->
                OpenSellRow(tx = tx, onCancelClick = onCancelClick)
            }
        }
    }

    if (showCancelAllConfirm && onCancelAllClick != null) {
        AlertDialog(
            onDismissRequest = { showCancelAllConfirm = false },
            title = { Text(stringResource(R.string.open_sells_cancel_all_confirm_title)) },
            text = {
                Text(stringResource(R.string.open_sells_cancel_all_confirm_text, openSells.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelAllConfirm = false
                    onCancelAllClick()
                }) {
                    Text(stringResource(R.string.open_sells_cancel_all), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelAllConfirm = false }) {
                    Text(stringResource(R.string.open_sells_cancel_back))
                }
            }
        )
    }
}

/**
 * Single row used by [OpenSellsList]. Exposed as `internal` so other screens
 * (e.g. the Pozice tab's collapsible section) can render the same row layout
 * with its built-in cancel-confirmation dialog without re-implementing it.
 */
@Composable
internal fun OpenSellRow(
    tx: Transaction,
    onCancelClick: (Long) -> Unit
) {
    val requested = tx.requestedCryptoAmount ?: BigDecimal.ZERO
    val filled = tx.cryptoAmount
    val progressPct = if (requested > BigDecimal.ZERO) {
        filled.divide(requested, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toInt()
    } else 0

    var showConfirm by remember(tx.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val priceText = tx.limitPrice?.let { NumberFormatters.fiat(it) } ?: "-"
            Text(
                text = "${NumberFormatters.crypto(requested)} ${tx.crypto} @ $priceText ${tx.fiat}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (tx.status == TransactionStatus.PARTIAL) {
                Text(
                    text = stringResource(
                        R.string.open_sells_status_partial,
                        progressPct.toString(),
                        NumberFormatters.crypto(filled),
                        NumberFormatters.crypto(requested)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Text(
                    text = stringResource(R.string.open_sells_status_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { showConfirm = true }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.open_sells_cancel_action),
                tint = Error
            )
        }
    }

    if (showConfirm) {
        val priceText = tx.limitPrice?.let { NumberFormatters.fiat(it) } ?: "-"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.open_sells_cancel_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.open_sells_cancel_confirm_text,
                        NumberFormatters.crypto(requested),
                        tx.crypto,
                        priceText,
                        tx.fiat
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onCancelClick(tx.id)
                }) {
                    Text(stringResource(R.string.open_sells_cancel_action), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.open_sells_cancel_back))
                }
            }
        )
    }
}
