package com.accbot.dca.presentation.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware number formatters for consistent display across the app.
 * Automatically uses the correct grouping/decimal separators for the active locale
 * (e.g. EN: 1,234.56 / CS: 1 234,56).
 */
object NumberFormatters {

    /** Format fiat amounts: whole numbers, with grouping (e.g. 1,235 or 1 235) */
    fun fiat(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 0
        nf.isGroupingUsed = true
        return nf.format(value.setScale(0, RoundingMode.HALF_UP))
    }

    /** Format fee amounts: 2 decimal places, with grouping (e.g. 1.50 or 0.45) */
    fun fiatFee(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        nf.isGroupingUsed = true
        return nf.format(value.setScale(2, RoundingMode.HALF_UP))
    }

    /** Compact fiat for Y-axis labels: 1800000 → "1.8M", 50000 → "50K", 800 → "800" */
    fun compactFiat(value: BigDecimal): String {
        val abs = value.abs()
        return when {
            abs >= BigDecimal(1_000_000) -> {
                val m = value.divide(BigDecimal(1_000_000), 1, RoundingMode.HALF_UP)
                "${stripTrailingDecimalZero(m)}M"
            }
            abs >= BigDecimal(10_000) -> {
                val k = value.divide(BigDecimal(1_000), 1, RoundingMode.HALF_UP)
                "${stripTrailingDecimalZero(k)}K"
            }
            else -> fiat(value)
        }
    }

    private fun stripTrailingDecimalZero(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 1
        nf.isGroupingUsed = false
        return nf.format(value)
    }

    /** Format crypto amounts: strip trailing zeros, up to 8 decimals, with grouping */
    fun crypto(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val scale = stripped.scale().coerceIn(0, 8)
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = scale.coerceAtLeast(2)
        nf.isGroupingUsed = true
        return nf.format(stripped)
    }

    /** Format percentages: 2 decimal places, no grouping */
    fun percent(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        nf.isGroupingUsed = false
        return nf.format(value.setScale(2, RoundingMode.HALF_UP))
    }
}
