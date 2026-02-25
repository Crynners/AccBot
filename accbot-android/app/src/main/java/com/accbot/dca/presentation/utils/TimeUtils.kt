package com.accbot.dca.presentation.utils

import android.content.Context
import com.accbot.dca.R
import java.time.Duration
import java.time.Instant

object TimeUtils {
    fun formatTimeUntil(nextExecution: Instant?, context: Context): String {
        if (nextExecution == null) return context.getString(R.string.time_not_scheduled)
        val now = Instant.now()
        if (nextExecution.isBefore(now)) return context.getString(R.string.time_due_now)
        val duration = Duration.between(now, nextExecution)
        return when {
            duration.toHours() >= 24 -> {
                val days = duration.toDays()
                val remainingHours = (duration.toHours() % 24).toInt()
                if (remainingHours == 0) {
                    if (days == 1L) context.getString(R.string.time_in_1_day_exact) else context.getString(R.string.time_in_days_exact, days.toInt())
                } else {
                    if (days == 1L) context.getString(R.string.time_in_1_day, remainingHours) else context.getString(R.string.time_in_days, days.toInt(), remainingHours)
                }
            }
            duration.toHours() >= 1 -> {
                val hours = duration.toHours()
                val remainingMinutes = (duration.toMinutes() % 60).toInt()
                if (remainingMinutes == 0) {
                    if (hours == 1L) context.getString(R.string.time_in_1_hour_exact) else context.getString(R.string.time_in_hours_exact, hours.toInt())
                } else {
                    if (hours == 1L) context.getString(R.string.time_in_1_hour, remainingMinutes) else context.getString(R.string.time_in_hours, hours.toInt(), remainingMinutes)
                }
            }
            else -> {
                val minutes = duration.toMinutes()
                if (minutes <= 1) context.getString(R.string.time_in_less_1_min) else context.getString(R.string.time_in_minutes, minutes.toInt())
            }
        }
    }
}
