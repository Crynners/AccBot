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
     * Generate a human-readable description of a CRON expression.
     * Returns null if the expression is invalid.
     */
    fun describeCron(expression: String): String? {
        val cron = parse(expression) ?: return null
        return try {
            // Use cron-utils descriptor
            val descriptor = com.cronutils.descriptor.CronDescriptor.instance(java.util.Locale.ENGLISH)
            descriptor.describe(cron)
        } catch (e: Exception) {
            Log.w(TAG, "CronDescriptor failed for '$expression', using fallback", e)
            // Fallback: just show the expression itself
            expression
        }
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
