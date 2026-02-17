package com.accbot.dca.domain.util

/**
 * Schedule type for the visual schedule builder.
 */
enum class ScheduleType {
    DAILY,
    DAYS_OF_WEEK,
    DAYS_OF_MONTH
}

/**
 * Represents a time-of-day with hour and minute.
 */
data class TimeOfDay(val hour: Int, val minute: Int) : Comparable<TimeOfDay> {
    override fun compareTo(other: TimeOfDay): Int {
        val hourCmp = hour.compareTo(other.hour)
        return if (hourCmp != 0) hourCmp else minute.compareTo(other.minute)
    }

    fun format(): String = "%d:%02d".format(hour, minute)
}

/**
 * Pure state model for the visual schedule builder.
 * Maps bidirectionally to/from 5-field UNIX CRON expressions.
 *
 * Design: single-minute model — minute selector is shared across all selected hours.
 * Hours are multi-selectable. This maps cleanly to CRON fields.
 */
data class ScheduleBuilderState(
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val selectedMinute: Int = 0,
    val selectedHours: Set<Int> = setOf(9),
    val selectedDaysOfWeek: Set<Int> = emptySet(),  // 0=Sun, 1=Mon..6=Sat (CRON convention)
    val selectedDaysOfMonth: Set<Int> = emptySet(),  // 1-31
    val useAdvancedMode: Boolean = false,
    val rawCronExpression: String = ""
) {
    /**
     * Generate a 5-field UNIX CRON expression from the visual state.
     * Returns null if the state is incomplete.
     */
    fun toCronExpression(): String? {
        if (useAdvancedMode) {
            return rawCronExpression.ifBlank { null }
        }

        if (selectedHours.isEmpty()) return null

        val minuteField = selectedMinute.toString()
        val hourField = selectedHours.sorted().joinToString(",")

        return when (scheduleType) {
            ScheduleType.DAILY -> "$minuteField $hourField * * *"
            ScheduleType.DAYS_OF_WEEK -> {
                if (selectedDaysOfWeek.isEmpty()) return null
                val dowField = selectedDaysOfWeek.sorted().joinToString(",")
                "$minuteField $hourField * * $dowField"
            }
            ScheduleType.DAYS_OF_MONTH -> {
                if (selectedDaysOfMonth.isEmpty()) return null
                val domField = selectedDaysOfMonth.sorted().joinToString(",")
                "$minuteField $hourField $domField * *"
            }
        }
    }

    /**
     * Whether the current state produces a valid CRON expression.
     */
    val isValid: Boolean
        get() {
            val cron = toCronExpression() ?: return false
            return CronUtils.isValidCron(cron)
        }

    /**
     * The list of formatted times based on selected hours and minute.
     */
    val selectedTimes: List<TimeOfDay>
        get() = selectedHours.map { TimeOfDay(it, selectedMinute) }.sorted()

    companion object {
        /**
         * Parse a CRON expression back into visual builder state.
         * Falls back to advanced mode for complex expressions (ranges, steps, etc.).
         */
        fun fromCronExpression(cron: String): ScheduleBuilderState {
            if (cron.isBlank()) return ScheduleBuilderState()

            val parts = cron.trim().split("\\s+".toRegex())
            if (parts.size != 5) {
                return ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)
            }

            val (minutePart, hourPart, domPart, monthPart, dowPart) = parts

            // If month field isn't wildcard, fall back to advanced
            if (monthPart != "*") {
                return ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)
            }

            // Parse minute — must be a single number (no lists, ranges, steps)
            val minute = minutePart.toIntOrNull()
            if (minute == null || minute !in 0..59) {
                return ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)
            }

            // Parse hours — must be a comma-separated list of numbers
            val hours = parseNumberList(hourPart, 0..23)
                ?: return ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)

            // Determine schedule type based on DOM and DOW fields
            val isDomWild = domPart == "*"
            val isDowWild = dowPart == "*"

            return when {
                // "m h * * *" → daily
                isDomWild && isDowWild -> ScheduleBuilderState(
                    scheduleType = ScheduleType.DAILY,
                    selectedMinute = minute,
                    selectedHours = hours
                )
                // "m h * * d,d,d" → days of week
                isDomWild && !isDowWild -> {
                    val dows = parseNumberList(dowPart, 0..7)
                        ?: return ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)
                    // Normalize DOW 7 (some crons use 7=Sun) to 0
                    val normalizedDows = dows.map { if (it == 7) 0 else it }.toSet()
                    ScheduleBuilderState(
                        scheduleType = ScheduleType.DAYS_OF_WEEK,
                        selectedMinute = minute,
                        selectedHours = hours,
                        selectedDaysOfWeek = normalizedDows
                    )
                }
                // "m h d,d * *" → days of month
                !isDomWild && isDowWild -> {
                    val doms = parseNumberList(domPart, 1..31)
                        ?: return ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)
                    ScheduleBuilderState(
                        scheduleType = ScheduleType.DAYS_OF_MONTH,
                        selectedMinute = minute,
                        selectedHours = hours,
                        selectedDaysOfMonth = doms
                    )
                }
                // Both DOM and DOW specified — too complex
                else -> ScheduleBuilderState(useAdvancedMode = true, rawCronExpression = cron)
            }
        }

        /**
         * Parse a CRON field as a comma-separated list of plain integers within [range].
         * Returns null if the field contains ranges, steps, or wildcards.
         */
        private fun parseNumberList(field: String, range: IntRange): Set<Int>? {
            if (field.contains('-') || field.contains('/') || field.contains('*')) return null
            val numbers = field.split(',').map { it.trim() }
            val result = mutableSetOf<Int>()
            for (num in numbers) {
                val n = num.toIntOrNull() ?: return null
                if (n !in range) return null
                result.add(n)
            }
            return if (result.isEmpty()) null else result
        }
    }
}
