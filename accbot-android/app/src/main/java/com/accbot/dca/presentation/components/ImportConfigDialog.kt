package com.accbot.dca.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.usecase.ImportRange
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfigDialog(
    planCount: Int? = null,
    range: ImportRange,
    onRangeChange: (ImportRange) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Offer [ImportRange.SinceLastPurchase]; false when no plan has a recorded purchase yet. */
    canImportSinceLastPurchase: Boolean = false,
    /** Shown next to the "since last purchase" option; null for several plans (each uses its own). */
    lastPurchaseMillis: Long? = null,
    /** When > 0, shows a warning that this connection has other plans and the import
     *  will fetch ALL trades from the exchange account, not just this plan's trades. */
    otherPlansOnSameConnection: Int = 0,
    /** Earliest date the date picker accepts (epoch milliseconds); null for no lower bound. */
    minSelectableDateMillis: Long? = null,
    /** Replaces the title and hint of the "all history" option. */
    allHistoryTitle: String? = null,
    allHistoryHint: String? = null,
    /** Text of the warning card, used instead of the multi-plan warning when set. */
    warning: String? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (range as? ImportRange.SinceDate)?.millis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis() &&
                        (minSelectableDateMillis == null ||
                            // The picker reports midnight UTC, so the start day itself stays selectable.
                            utcTimeMillis >= minSelectableDateMillis - minSelectableDateMillis % DAY_MILLIS)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onRangeChange(ImportRange.SinceDate(it)) }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.common_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_api_title)) },
        text = {
            Column {
                if (planCount != null) {
                    Text(pluralStringResource(R.plurals.import_api_dialog_text, planCount, planCount))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                val warningText = warning
                    ?: if (otherPlansOnSameConnection > 0) {
                        stringResource(R.string.import_api_multi_plan_warning, otherPlansOnSameConnection)
                    } else null
                if (warningText != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = stringResource(R.string.import_api_from_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (canImportSinceLastPurchase) {
                    RangeOption(
                        selected = range == ImportRange.SinceLastPurchase,
                        title = stringResource(R.string.import_api_since_last_purchase),
                        subtitle = lastPurchaseMillis
                            ?.let { dateTimeFormatter.format(Instant.ofEpochMilli(it)) }
                            ?: stringResource(R.string.import_api_since_last_purchase_each_plan),
                        onClick = { onRangeChange(ImportRange.SinceLastPurchase) }
                    )
                }
                RangeOption(
                    selected = range is ImportRange.SinceDate,
                    title = stringResource(R.string.import_api_since_date),
                    subtitle = (range as? ImportRange.SinceDate)
                        ?.let { dateFormatter.format(Instant.ofEpochMilli(it.millis)) }
                        ?: stringResource(R.string.import_api_pick_date),
                    onClick = { showDatePicker = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                RangeOption(
                    selected = range == ImportRange.AllHistory,
                    title = allHistoryTitle ?: stringResource(R.string.import_api_all_history),
                    subtitle = allHistoryHint ?: stringResource(R.string.import_api_all_history_hint),
                    onClick = { onRangeChange(ImportRange.AllHistory) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.import_api_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun RangeOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

private const val DAY_MILLIS = 86_400_000L
