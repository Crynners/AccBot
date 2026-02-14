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
                if (days == 1L) context.getString(R.string.time_in_1_day) else context.getString(R.string.time_in_days, days.toInt())
            }
            duration.toHours() >= 1 -> {
                val hours = duration.toHours()
                if (hours == 1L) context.getString(R.string.time_in_1_hour) else context.getString(R.string.time_in_hours, hours.toInt())
            }
            else -> {
                val minutes = duration.toMinutes()
                if (minutes <= 1) context.getString(R.string.time_in_less_1_min) else context.getString(R.string.time_in_minutes, minutes.toInt())
            }
        }
    }
}
