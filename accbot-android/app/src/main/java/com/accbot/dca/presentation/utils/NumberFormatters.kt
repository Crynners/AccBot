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

    /** Format fiat amounts: 2 decimal places, with grouping (e.g. 1,235.00 or 1 235,00) */
    fun fiat(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        nf.isGroupingUsed = true
        return nf.format(value.setScale(2, RoundingMode.HALF_UP))
    }

    /** Format fee amounts: 2 decimal places, with grouping (e.g. 1.50 or 0.45) */
    fun fiatFee(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        nf.isGroupingUsed = true
        return nf.format(value.setScale(2, RoundingMode.HALF_UP))
    }

    /** Format fiat as whole number: no decimal places, with grouping (e.g. 1,235 or 1 235). Used for chart axis labels. */
    fun fiatWhole(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 0
        nf.isGroupingUsed = true
        return nf.format(value.setScale(0, RoundingMode.HALF_UP))
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
            else -> fiatWhole(value)
        }
    }

    private fun stripTrailingDecimalZero(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = 1
        nf.isGroupingUsed = false
        return nf.format(value)
    }

    /** Compact crypto for chart axis labels: strip trailing zeros, up to 8 decimals */
    fun cryptoCompact(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val scale = stripped.scale().coerceIn(0, 8)
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = scale.coerceAtLeast(2)
        nf.isGroupingUsed = true
        return nf.format(stripped)
    }

    /** Format crypto amounts: sub-1 values always show 8 decimals for satoshi readability, larger values preserve original precision */
    fun crypto(value: BigDecimal): String {
        val scale = if (value.abs() < BigDecimal.ONE) 8 else value.scale().coerceIn(2, 8)
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = scale
        nf.maximumFractionDigits = scale
        nf.isGroupingUsed = true
        return nf.format(value.setScale(scale, RoundingMode.HALF_UP))
    }

    /** Format percentages: 2 decimal places, no grouping */
    fun percent(value: BigDecimal): String {
        val nf = NumberFormat.getInstance(Locale.getDefault())
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        nf.isGroupingUsed = false
        return nf.format(value.setScale(2, RoundingMode.HALF_UP))
    }

    /** Returns "+" for non-negative values, "" for negative (BigDecimal already includes "-") */
    fun roiSign(value: BigDecimal): String = if (value >= BigDecimal.ZERO) "+" else ""

    /** Returns true if value is >= 0 */
    fun isPositiveRoi(value: BigDecimal): Boolean = value >= BigDecimal.ZERO

    /** Compute ROI absolute and percentage from invested and current value. Returns null if totalInvested is zero. */
    fun roiValues(totalInvested: BigDecimal, currentValue: BigDecimal): Pair<BigDecimal, BigDecimal>? {
        if (totalInvested <= BigDecimal.ZERO) return null
        val roiAbsolute = currentValue.subtract(totalInvested)
        val roiPercent = roiAbsolute.divide(totalInvested, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)
        return roiAbsolute to roiPercent
    }
}
