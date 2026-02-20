package com.accbot.dca.domain.util

import android.util.Log
import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Utility wrapper around cron-utils for CRON expression parsing,
 * validation, description, and next-execution calculation.
 */
object CronUtils {
    private const val TAG = "CronUtils"

    private val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    private val parser = CronParser(cronDefinition)

    /**
     * Parse a CRON expression. Returns null if invalid.
     */
    private fun parse(expression: String): Cron? {
        return try {
            parser.parse(expression).validate()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a CRON expression is valid (5-field UNIX CRON).
     */
    fun isValidCron(expression: String): Boolean {
        return parse(expression) != null
    }

    /**
     * Generate a human-readable, localized description of a CRON expression.
     * Handles common DCA patterns with clean output, falls back to CronDescriptor.
     * Returns null if the expression is invalid.
     */
    fun describeCron(expression: String): String? {
        parse(expression) ?: return null
        // Try custom human-readable description first
        describeCronHumanReadable(expression)?.let { return it }
        // Fallback to cron-utils descriptor with current locale
        return try {
            val cron = parse(expression)!!
            val descriptor = com.cronutils.descriptor.CronDescriptor.instance(Locale.getDefault())
            descriptor.describe(cron)
        } catch (e: Exception) {
            Log.w(TAG, "CronDescriptor failed for '$expression', using fallback", e)
            expression
        }
    }

    /**
     * Custom human-readable description for common DCA cron patterns.
     * Returns null for patterns it cannot handle (caller falls back).
     */
    private fun describeCronHumanReadable(expression: String): String? {
        val parts = expression.trim().split("\\s+".toRegex())
        if (parts.size != 5) return null

        val (minuteField, hourField, domField, monthField, dowField) = parts

        // Only handle patterns where month is * (every month)
        if (monthField != "*") return null

        val isCzech = Locale.getDefault().language == "cs"

        // Pattern: */N * * * * → "Every N minutes"
        if (minuteField.startsWith("*/") && hourField == "*" && domField == "*" && dowField == "*") {
            val n = minuteField.removePrefix("*/").toIntOrNull() ?: return null
            return if (isCzech) "Každých $n minut" else "Every $n minutes"
        }

        // Pattern: M */N * * * → "Every N hours"
        if (hourField.startsWith("*/") && domField == "*" && dowField == "*") {
            val n = hourField.removePrefix("*/").toIntOrNull() ?: return null
            return if (isCzech) "Každých $n hodin" else "Every $n hours"
        }

        // Parse specific minutes
        val minutes = parseFieldValues(minuteField) ?: return null
        if (minutes.isEmpty()) return null
        val minute = minutes.first() // use first minute for formatting times

        // Parse specific hours
        val hours = parseFieldValues(hourField) ?: return null
        if (hours.isEmpty()) return null

        // Format time list: "1:30, 13:30"
        val timeStrings = hours.map { h -> "%d:%02d".format(h, minute) }
        val timePart = if (isCzech) {
            timeStrings.joinWithLastSeparator(" a ")
        } else {
            timeStrings.joinWithLastSeparator(" and ")
        }

        // Pattern: M H * * * → "Every day at H:MM"
        if (domField == "*" && dowField == "*") {
            return if (isCzech) "Každý den v $timePart" else "Every day at $timePart"
        }

        // Pattern: M H * * D → "Mon, Wed at H:MM"
        if (domField == "*") {
            val days = parseFieldValues(dowField) ?: return null
            if (days.isEmpty()) return null
            val dayNames = days.map { dayName(it, isCzech) }
            val dayPart = dayNames.joinToString(", ")
            return if (isCzech) "$dayPart v $timePart" else "$dayPart at $timePart"
        }

        // Pattern: M H D * * → "1st, 15th of month at H:MM"
        if (dowField == "*") {
            val doms = parseFieldValues(domField) ?: return null
            if (doms.isEmpty()) return null
            val domPart = if (isCzech) {
                doms.joinToString(", ") { "${it}." }
            } else {
                doms.joinToString(", ") { ordinal(it) }
            }
            return if (isCzech) {
                "$domPart den v měsíci v $timePart"
            } else {
                "$domPart of month at $timePart"
            }
        }

        return null
    }

    /**
     * Parse a cron field into a list of specific integer values.
     * Supports: single values (5), lists (1,13), ranges (1-5).
     * Returns null for *, step patterns, or invalid input.
     */
    private fun parseFieldValues(field: String): List<Int>? {
        if (field == "*" || field.contains("/")) return null
        val values = mutableListOf<Int>()
        for (part in field.split(",")) {
            if (part.contains("-")) {
                val range = part.split("-")
                if (range.size != 2) return null
                val from = range[0].toIntOrNull() ?: return null
                val to = range[1].toIntOrNull() ?: return null
                values.addAll(from..to)
            } else {
                values.add(part.toIntOrNull() ?: return null)
            }
        }
        return values.sorted()
    }

    private fun dayName(dow: Int, czech: Boolean): String {
        // cron: 0=Sun, 1=Mon, ..., 6=Sat  (7=Sun also)
        val normalized = if (dow == 7) 0 else dow
        return if (czech) {
            when (normalized) {
                0 -> "Ne"; 1 -> "Po"; 2 -> "Út"; 3 -> "St"
                4 -> "Čt"; 5 -> "Pá"; 6 -> "So"; else -> "?"
            }
        } else {
            when (normalized) {
                0 -> "Sun"; 1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"
                4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; else -> "?"
            }
        }
    }

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    private fun List<String>.joinWithLastSeparator(lastSep: String): String = when (size) {
        0 -> ""
        1 -> first()
        else -> dropLast(1).joinToString(", ") + lastSep + last()
    }

    /**
     * Calculate the next execution time after the given instant.
     * Returns null if the expression is invalid.
     */
    fun getNextExecution(expression: String, after: Instant): Instant? {
        val cron = parse(expression) ?: return null
        return try {
            val executionTime = ExecutionTime.forCron(cron)
            val zdt = ZonedDateTime.ofInstant(after, ZoneId.systemDefault())
            executionTime.nextExecution(zdt)
                .orElse(null)
                ?.toInstant()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate next execution for '$expression'", e)
            null
        }
    }

    /**
     * Estimate the average interval in minutes for a CRON expression.
     * Used for monthly cost estimates. Returns null if invalid.
     */
    fun getIntervalMinutesEstimate(expression: String): Long? {
        val cron = parse(expression) ?: return null
        return try {
            val executionTime = ExecutionTime.forCron(cron)
            val now = ZonedDateTime.now()

            // Calculate next 5 executions and average the intervals
            val executions = mutableListOf<ZonedDateTime>()
            var current = now
            repeat(6) {
                val next = executionTime.nextExecution(current).orElse(null) ?: return@repeat
                executions.add(next)
                current = next
            }

            if (executions.size < 2) return 1440L // Default to daily

            val totalMinutes = java.time.Duration.between(executions.first(), executions.last()).toMinutes()
            val intervals = executions.size - 1
            maxOf(totalMinutes / intervals, 1L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to estimate interval for '$expression'", e)
            1440L // Default to daily
        }
    }
}
