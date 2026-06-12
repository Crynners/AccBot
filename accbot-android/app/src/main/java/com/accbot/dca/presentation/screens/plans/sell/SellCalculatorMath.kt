package com.accbot.dca.presentation.screens.plans.sell

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure logic for the three-field sell calculator (Amount / Price / Net).
 *
 * Relationship: `N = A * P * (1 - feeRate)`
 *
 * The wizard ViewModel records the two most-recently edited fields. When the user types in
 * the third field it becomes the "newest"; the field that drops out of the recent-edits pair
 * is the one we recompute. [recompute] does this in one shot.
 */
object SellCalculatorMath {

    enum class Field { AMOUNT, PRICE, NET }

    /**
     * @param a current amount value (parsed from input, null when blank)
     * @param p current price value
     * @param n current net value
     * @param feeRate exchange fee, e.g. 0.0035 for Coinmate taker
     * @param lastTwoEdited fields most recently edited in newest-first order
     * @return updated triple with the third field recomputed when possible
     */
    fun recompute(
        a: BigDecimal?,
        p: BigDecimal?,
        n: BigDecimal?,
        feeRate: BigDecimal,
        lastTwoEdited: List<Field>
    ): Triple<BigDecimal?, BigDecimal?, BigDecimal?> {
        if (lastTwoEdited.size < 2) return Triple(a, p, n)
        val factor = BigDecimal.ONE - feeRate
        val toCompute = Field.values().firstOrNull { it !in lastTwoEdited }
            ?: return Triple(a, p, n)

        return when (toCompute) {
            Field.NET -> {
                val newN = if (a != null && p != null && a > BigDecimal.ZERO && p > BigDecimal.ZERO)
                    (a * p * factor).setScale(2, RoundingMode.HALF_UP)
                else null
                Triple(a, p, newN)
            }
            Field.PRICE -> {
                val newP = if (a != null && n != null && a > BigDecimal.ZERO && factor > BigDecimal.ZERO)
                    n.divide(a * factor, 2, RoundingMode.HALF_UP)
                else null
                Triple(a, newP, n)
            }
            Field.AMOUNT -> {
                val newA = if (p != null && n != null && p > BigDecimal.ZERO && factor > BigDecimal.ZERO)
                    n.divide(p * factor, 8, RoundingMode.HALF_UP)
                else null
                Triple(newA, p, n)
            }
        }
    }
}
