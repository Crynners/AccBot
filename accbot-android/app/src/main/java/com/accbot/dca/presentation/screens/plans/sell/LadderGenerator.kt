package com.accbot.dca.presentation.screens.plans.sell

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

    enum class AmountMode { EQUAL_CRYPTO, EQUAL_FIAT }

    fun generate(
        totalAmount: BigDecimal,
        from: BigDecimal,
        to: BigDecimal,
        count: Int,
        mode: AmountMode
    ): List<LadderOrder> {
        require(count >= 2) { "count >= 2" }
        require(totalAmount > BigDecimal.ZERO) { "totalAmount > 0" }
        require(from > BigDecimal.ZERO && to > BigDecimal.ZERO) { "prices > 0" }

        val n = BigDecimal(count)
        val prices = (0 until count).map { i ->
            val step = (to - from) * BigDecimal(i) / BigDecimal(count - 1)
            (from + step).setScale(2, RoundingMode.HALF_UP)
        }

        return when (mode) {
            AmountMode.EQUAL_CRYPTO -> {
                val per = totalAmount.divide(n, 8, RoundingMode.DOWN)
                val drobky = totalAmount - per * n
                prices.mapIndexed { i, p ->
                    val a = if (i == count - 1) per + drobky else per
                    LadderOrder(a, p)
                }
            }
            AmountMode.EQUAL_FIAT -> {
                // Target gross per order = totalAmount * avgPrice / N. Use the simple
                // arithmetic mean of prices as the fair "expected price" so the resulting
                // crypto amounts sum close to total before rescaling.
                val avgPrice = prices.fold(BigDecimal.ZERO) { acc, x -> acc + x }
                    .divide(n, 8, RoundingMode.HALF_UP)
                val perOrderGross = (totalAmount * avgPrice).divide(n, 8, RoundingMode.HALF_UP)
                val rawAmounts = prices.map { p ->
                    perOrderGross.divide(p, 8, RoundingMode.DOWN)
                }
                val sumRaw = rawAmounts.fold(BigDecimal.ZERO) { acc, x -> acc + x }
                val scale = if (sumRaw > BigDecimal.ZERO)
                    totalAmount.divide(sumRaw, 12, RoundingMode.HALF_UP)
                else BigDecimal.ONE
                rawAmounts.mapIndexed { i, a ->
                    LadderOrder((a * scale).setScale(8, RoundingMode.DOWN), prices[i])
                }
            }
        }
    }
}
