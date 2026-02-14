package com.accbot.dca.presentation.utils

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Centralized DateTimeFormatter instances for consistent date/time formatting.
 * These are thread-safe and should be reused instead of creating new instances.
 */
object DateFormatters {
    /**
     * Format: "Jan 15, 2024 14:30"
     * Used for transaction timestamps and general date-time display
     */
    val transactionDateTime: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM d, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

    /**
     * Format: "Jan 2024"
     * Used for monthly performance charts and summaries
     */
    val monthYear: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM yyyy")
        .withZone(ZoneId.systemDefault())

    /**
     * Format: "January 15, 2024"
     * Used for detailed transaction views
     */
    val fullDate: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMMM d, yyyy")
        .withZone(ZoneId.systemDefault())

    /**
     * Format: "14:30:45"
     * Used for time-only display
     */
    val timeOnly: DateTimeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Format: "2024-01-15"
     * ISO date format for data export and sorting
     */
    val isoDate: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())

    /**
     * Format: "2024-01-15 14:30:45"
     * ISO datetime format for CSV export
     */
    val isoDateTime: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
}
