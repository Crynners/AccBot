package com.accbot.dca.presentation.screens.plans.sell

import com.accbot.dca.domain.usecase.SellLadderLimits
import com.accbot.dca.domain.usecase.LadderOrder
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Generates N limit-sell orders linearly distributed across a price range.
 *
 * Two amount distribution modes:
 * - [AmountMode.EQUAL_CRYPTO]: each order sells `total / N` BTC. Simplest and most
 *   predictable for min-order-size validation.
 * - [AmountMode.EQUAL_FIAT]: each order generates the same gross fiat. Cheaper orders
 *   sell larger crypto amounts. After per-order rounding the sum is rescaled to match
 *   `total` so the user actually sells the requested total.
 */
object LadderGenerator {

    const val MIN_ORDER_COUNT = SellLadderLimits.MIN_ORDER_COUNT
    const val MAX_ORDER_COUNT = SellLadderLimits.MAX_ORDER_COUNT

    enum class AmountMode { EQUAL_CRYPTO, EQUAL_FIAT }

    /** `pct = (price - avg) / avg * 100`. Returns null when avg is missing or zero. */
    fun priceToProfitPct(price: BigDecimal, avg: BigDecimal?): BigDecimal? {
        if (avg == null || avg <= BigDecimal.ZERO) return null
        return (price - avg).divide(avg, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(2, RoundingMode.HALF_UP)
    }

    /** `price = avg * (1 + pct/100)`. Returns null when avg is missing or zero. */
    fun profitPctToPrice(pct: BigDecimal, avg: BigDecimal?): BigDecimal? {
        if (avg == null || avg <= BigDecimal.ZERO) return null
        return (avg * (BigDecimal.ONE + pct.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)))
            .setScale(2, RoundingMode.HALF_UP)
    }

    fun generate(
        totalAmount: BigDecimal,
        from: BigDecimal,
        to: BigDecimal,
        count: Int,
        mode: AmountMode
    ): List<LadderOrder> {
        require(count in MIN_ORDER_COUNT..MAX_ORDER_COUNT) {
            "count must be in $MIN_ORDER_COUNT..$MAX_ORDER_COUNT"
        }
        require(totalAmount > BigDecimal.ZERO) { "totalAmount > 0" }
        require(from > BigDecimal.ZERO && to > from) { "to must exceed from (both positive)" }

        val n = BigDecimal(count)
        val prices = (0 until count).map { i ->
            val step = (to - from).multiply(BigDecimal(i))
                .divide(BigDecimal(count - 1), 8, RoundingMode.HALF_UP)
            (from + step).setScale(2, RoundingMode.HALF_UP)
        }

        return when (mode) {
            AmountMode.EQUAL_CRYPTO -> {
                val per = totalAmount.divide(n, 8, RoundingMode.DOWN)
                val remainder = totalAmount - per * n
                prices.mapIndexed { i, p ->
                    val a = if (i == count - 1) per + remainder else per
                    LadderOrder(a, p)
                }
            }
            AmountMode.EQUAL_FIAT -> {
                // Exact harmonic allocation: each order targets equal gross fiat G = totalAmount / sum(1 / p_i),
                // so a_i = G / p_i. Floored to 8 decimals so sum never exceeds totalAmount; the tiny
                // sub-satoshi rounding remainder is assigned to the first (cheapest) order.
                val invSum = prices.fold(BigDecimal.ZERO) { acc, p ->
                    acc + BigDecimal.ONE.divide(p, 18, RoundingMode.HALF_UP)
                }
                val perOrderGross = totalAmount.divide(invSum, 12, RoundingMode.HALF_UP)
                val baseAmounts = prices.map { p ->
                    perOrderGross.divide(p, 8, RoundingMode.DOWN)
                }
                val sumBase = baseAmounts.fold(BigDecimal.ZERO) { acc, a -> acc + a }
                val remainder = (totalAmount - sumBase).max(BigDecimal.ZERO)
                baseAmounts.mapIndexed { i, a ->
                    val adjusted = if (i == 0) a + remainder else a
                    LadderOrder(adjusted.setScale(8, RoundingMode.DOWN), prices[i])
                }
            }
        }
    }

    /** Total net fiat proceeds across all orders after applying [feeRate]. */
    fun totalNetProceeds(orders: List<LadderOrder>, feeRate: BigDecimal): BigDecimal {
        val factor = (BigDecimal.ONE - feeRate).max(BigDecimal.ZERO)
        return orders.fold(BigDecimal.ZERO) { acc, o ->
            acc + o.cryptoAmount * o.limitPrice * factor
        }.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Exclusive theoretical upper bound of net proceeds for [AmountMode.EQUAL_FIAT].
     * Because the harmonic mean H(p_0..p_{N-1}) satisfies H < N * p_0 for all finite `to > from`,
     * total net proceeds can never reach or exceed `totalAmount * count * from * (1 - feeRate)`.
     * Returns null for [AmountMode.EQUAL_CRYPTO] (unbounded as `to` grows).
     */
    fun maxTheoreticalNetProceeds(
        totalAmount: BigDecimal,
        from: BigDecimal,
        count: Int,
        mode: AmountMode,
        feeRate: BigDecimal
    ): BigDecimal? {
        if (mode != AmountMode.EQUAL_FIAT) return null
        val factor = BigDecimal.ONE - feeRate
        if (totalAmount <= BigDecimal.ZERO || from <= BigDecimal.ZERO || count < MIN_ORDER_COUNT || factor <= BigDecimal.ZERO) {
            return null
        }
        return (totalAmount * BigDecimal(count) * from * factor).setScale(2, RoundingMode.DOWN)
    }

    /**
     * Solve the upper limit price `to` (> [from]) such that a ladder of [count] orders across
     * `[from, to]` selling [totalAmount] in [mode] yields [targetNet] after [feeRate].
     *
     * Returns null when [targetNet] is unreachable (at or below the `from`-price floor, or for
     * [AmountMode.EQUAL_FIAT] at or above the harmonic-mean asymptote `N * from * totalAmount * (1 - feeRate)`).
     */
    fun solveToPriceForNetTarget(
        totalAmount: BigDecimal,
        from: BigDecimal,
        count: Int,
        mode: AmountMode,
        feeRate: BigDecimal,
        targetNet: BigDecimal
    ): BigDecimal? {
        if (count !in MIN_ORDER_COUNT..MAX_ORDER_COUNT) return null
        if (totalAmount <= BigDecimal.ZERO || from <= BigDecimal.ZERO || targetNet <= BigDecimal.ZERO) return null
        val factor = BigDecimal.ONE - feeRate
        if (factor <= BigDecimal.ZERO) return null

        val minNet = totalAmount * from * factor
        if (targetNet <= minNet) return null

        return when (mode) {
            AmountMode.EQUAL_CRYPTO -> {
                val toPrice = (BigDecimal(2) * targetNet)
                    .divide(totalAmount * factor, 8, RoundingMode.HALF_UP) - from
                val rounded = toPrice.setScale(2, RoundingMode.HALF_UP)
                if (rounded > from) rounded else null
            }
            AmountMode.EQUAL_FIAT -> {
                val asymptote = totalAmount * BigDecimal(count) * from * factor
                if (targetNet >= asymptote) return null

                fun continuousNet(candidateTo: BigDecimal): BigDecimal {
                    var invSum = BigDecimal.ZERO
                    val denom = BigDecimal(count - 1)
                    val span = candidateTo - from
                    for (i in 0 until count) {
                        val p = from + span.multiply(BigDecimal(i)).divide(denom, 12, RoundingMode.HALF_UP)
                        invSum += BigDecimal.ONE.divide(p, 18, RoundingMode.HALF_UP)
                    }
                    val harmonicMean = BigDecimal(count).divide(invSum, 12, RoundingMode.HALF_UP)
                    return totalAmount * harmonicMean * factor
                }

                var lo = from
                var hi = from * BigDecimal(2)
                var doublings = 0
                while (continuousNet(hi) < targetNet && doublings < 40) {
                    lo = hi
                    hi *= BigDecimal(2)
                    doublings++
                }
                if (continuousNet(hi) < targetNet) return null

                val tolerance = BigDecimal("0.001")
                repeat(64) {
                    val mid = (lo + hi).divide(BigDecimal(2), 8, RoundingMode.HALF_UP)
                    if (continuousNet(mid) < targetNet) {
                        lo = mid
                    } else {
                        hi = mid
                    }
                    if (hi - lo < tolerance) return@repeat
                }
                val rounded = (lo + hi).divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
                if (rounded > from) rounded else null
            }
        }
    }
}
