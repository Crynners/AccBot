package com.accbot.dca.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.accbot.dca.R
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.domain.util.ScheduleBuilderState
import com.accbot.dca.domain.util.ScheduleType
import com.accbot.dca.presentation.ui.theme.successColor
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Visual schedule builder — drop-in replacement for CronExpressionInput.
 * Same signature: takes a CRON string in, emits a CRON string out.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleBuilder(
    cronExpression: String,
    cronDescription: String?,
    cronError: String?,
    onCronExpressionChange: (String) -> Unit
) {
    var state by remember(cronExpression) {
        mutableStateOf(ScheduleBuilderState.fromCronExpression(cronExpression))
    }

    // Emit CRON whenever state changes
    fun emitCron(newState: ScheduleBuilderState) {
        state = newState
        val cron = newState.toCronExpression()
        if (cron != null) {
            onCronExpressionChange(cron)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Schedule Type Selector ──
        if (!state.useAdvancedMode) {
            Text(
                text = stringResource(R.string.schedule_type_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val types = listOf(
                    ScheduleType.DAILY to stringResource(R.string.schedule_type_daily),
                    ScheduleType.DAYS_OF_WEEK to stringResource(R.string.schedule_type_days_of_week),
                    ScheduleType.DAYS_OF_MONTH to stringResource(R.string.schedule_type_days_of_month)
                )
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.scheduleType == type,
                        onClick = {
                            emitCron(state.copy(scheduleType = type))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // ── Time Selection ──
            Text(
                text = stringResource(R.string.schedule_time_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Minute selector
            Text(
                text = stringResource(R.string.schedule_minute_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 45).forEach { minute ->
                    SelectableChip(
                        text = ":%02d".format(minute),
                        selected = state.selectedMinute == minute,
                        onClick = { emitCron(state.copy(selectedMinute = minute)) }
                    )
                }
            }

            // Hour selector
            Text(
                text = stringResource(R.string.schedule_hour_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (0..23).forEach { hour ->
                    SelectableChip(
                        text = "%d:00".format(hour),
                        selected = hour in state.selectedHours,
                        onClick = {
                            val newHours = if (hour in state.selectedHours) {
                                state.selectedHours - hour
                            } else {
                                state.selectedHours + hour
                            }
                            emitCron(state.copy(selectedHours = newHours))
                        }
                    )
                }
            }

            // Time summary
            if (state.selectedTimes.isNotEmpty()) {
                Text(
                    text = state.selectedTimes.joinToString(", ") { it.format() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = successColor(),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Day of Week Selector ──
            if (state.scheduleType == ScheduleType.DAYS_OF_WEEK) {
                Text(
                    text = stringResource(R.string.schedule_select_days),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Sunday (0) then Monday-Saturday (1-6)
                    val dayOrder = listOf(1, 2, 3, 4, 5, 6, 0) // Mon..Sat, Sun
                    val locale = Locale.getDefault()
                    dayOrder.forEach { cronDow ->
                        val javaDow = when (cronDow) {
                            0 -> DayOfWeek.SUNDAY
                            1 -> DayOfWeek.MONDAY
                            2 -> DayOfWeek.TUESDAY
                            3 -> DayOfWeek.WEDNESDAY
                            4 -> DayOfWeek.THURSDAY
                            5 -> DayOfWeek.FRIDAY
                            6 -> DayOfWeek.SATURDAY
                            else -> DayOfWeek.MONDAY
                        }
                        val dayName = javaDow.getDisplayName(TextStyle.SHORT, locale)
                        SelectableChip(
                            text = dayName,
                            selected = cronDow in state.selectedDaysOfWeek,
                            onClick = {
                                val newDays = if (cronDow in state.selectedDaysOfWeek) {
                                    state.selectedDaysOfWeek - cronDow
                                } else {
                                    state.selectedDaysOfWeek + cronDow
                                }
                                emitCron(state.copy(selectedDaysOfWeek = newDays))
                            }
                        )
                    }
                }
            }

            // ── Day of Month Selector ──
            if (state.scheduleType == ScheduleType.DAYS_OF_MONTH) {
                Text(
                    text = stringResource(R.string.schedule_select_days),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..31).forEach { day ->
                        SelectableChip(
                            text = day.toString(),
                            selected = day in state.selectedDaysOfMonth,
                            onClick = {
                                val newDays = if (day in state.selectedDaysOfMonth) {
                                    state.selectedDaysOfMonth - day
                                } else {
                                    state.selectedDaysOfMonth + day
                                }
                                emitCron(state.copy(selectedDaysOfMonth = newDays))
                            }
                        )
                    }
                }
            }

            // ── CRON Preview ──
            val generatedCron = state.toCronExpression()
            if (generatedCron != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_preview_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = generatedCron,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val description = cronDescription ?: CronUtils.describeCron(generatedCron)
                        if (description != null) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = successColor()
                            )
                        }
                        if (cronError != null) {
                            Text(
                                text = cronError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // ── Advanced Mode Section ──
        AdvancedModeSection(
            isAdvancedMode = state.useAdvancedMode,
            rawCronExpression = if (state.useAdvancedMode) state.rawCronExpression else (state.toCronExpression() ?: ""),
            cronDescription = cronDescription,
            cronError = cronError,
            onToggle = {
                if (state.useAdvancedMode) {
                    // Switching from advanced → visual: try to parse current raw CRON
                    val parsed = ScheduleBuilderState.fromCronExpression(state.rawCronExpression)
                    if (!parsed.useAdvancedMode) {
                        emitCron(parsed)
                    } else {
                        // Can't parse back to visual, stay in advanced
                        emitCron(state)
                    }
                } else {
                    // Switching from visual → advanced: keep current CRON in raw field
                    val cron = state.toCronExpression() ?: ""
                    emitCron(state.copy(useAdvancedMode = true, rawCronExpression = cron))
                }
            },
            onRawCronChange = { newCron ->
                emitCron(state.copy(rawCronExpression = newCron))
            }
        )
    }
}

@Composable
private fun AdvancedModeSection(
    isAdvancedMode: Boolean,
    rawCronExpression: String,
    cronDescription: String?,
    cronError: String?,
    onToggle: () -> Unit,
    onRawCronChange: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.schedule_advanced_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (isAdvancedMode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = isAdvancedMode) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rawCronExpression,
                    onValueChange = onRawCronChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cron_input_label)) },
                    placeholder = { Text(stringResource(R.string.cron_input_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = cronError != null,
                    supportingText = when {
                        cronError != null -> {
                            { Text(cronError, color = MaterialTheme.colorScheme.error) }
                        }
                        cronDescription != null -> {
                            { Text(cronDescription, color = successColor()) }
                        }
                        else -> null
                    }
                )

                // CRON examples card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cron_examples_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.cron_example_daily),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.cron_example_weekly),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.cron_example_monthly),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.cron_example_twice_daily),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
