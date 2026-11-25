package com.accbot.dca.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Validation outcome for a prospective sell order or ladder series. Multiple items may be
 * returned (e.g. InstantFillInfo and no hard error), so callers should render each item.
 * An empty list means "valid".
 *
 * Hard errors are locale-free: each subtype maps to a string resource the UI resolves.
 */
sealed class SellValidation {
    /** Field this validation result attaches to (lets UI render it under the right input). */
    enum class Field { AMOUNT, PRICE, NET, COUNT, RANGE, GENERIC }

    sealed class HardError(val field: Field) : SellValidation() {
        object AmountMustBePositive : HardError(Field.AMOUNT)
        object PriceMustBePositive : HardError(Field.PRICE)
        data class MinOrderTooLow(val minOrderFiat: BigDecimal) : HardError(Field.AMOUNT)
        data class InsufficientInventory(val available: BigDecimal) : HardError(Field.AMOUNT)
        data class LadderCountOutOfRange(val min: Int, val max: Int) : HardError(Field.COUNT)
        data class LadderLegBelowMin(
            val smallestFiat: BigDecimal,
            val minOrderFiat: BigDecimal
        ) : HardError(Field.RANGE)
        object LadderOrderInvalid : HardError(Field.RANGE)
        object SellsDisabled : HardError(Field.GENERIC)
    }

    data class InstantFillInfo(val spot: BigDecimal, val affectedCount: Int = 1) : SellValidation()
    data class FarFromMarketWarning(val spot: BigDecimal, val affectedCount: Int = 1) : SellValidation()
    /**
     * Net profit after exchange fee would be negative for this order (or for one or more
     * orders in a ladder). Triggered both when the limit price is below avg buy and when
     * the price is just above avg buy but fee pushes the result into negative territory.
     * Caller (UI) shows a warning banner; the wizard still allows the user to proceed.
     */
    data class LossWarning(
        val lossFiat: BigDecimal,
        val lossPct: BigDecimal,
        val affectedCount: Int = 1
    ) : SellValidation()
}

/**
 * Shared validation for prospective single limit sell orders and ladder sell series
 * against plan state (held crypto minus reservations from open sells) and optional spot price.
 *
 * Used by both [com.accbot.dca.presentation.screens.plans.sell.SellWizardViewModel] and
 * the submission use cases ([PlaceLimitSellUseCase], [PlaceLadderSellUseCase]) so UI and
 * execution enforce identical invariants.
 */
class ValidateSellOrderUseCase @Inject constructor(
    private val calculatePlanCostBasisUseCase: CalculatePlanCostBasisUseCase
) {
    suspend operator fun invoke(
        planId: Long,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal,
        /** Minimum order size **in fiat** (Coinmate ~50 CZK, Binance NOTIONAL filter, etc.). */
        minOrderFiat: BigDecimal,
        currentSpot: BigDecimal?,
        avgBuyPrice: BigDecimal? = null,
        feeRate: BigDecimal = BigDecimal.ZERO
    ): List<SellValidation> {
        val available = calculatePlanCostBasisUseCase(planId).available
        return validateSingleAgainstAvailable(
            cryptoAmount = cryptoAmount,
            limitPrice = limitPrice,
            available = available,
            minOrderFiat = minOrderFiat,
            currentSpot = currentSpot,
            avgBuyPrice = avgBuyPrice,
            feeRate = feeRate
        )
    }

    suspend fun validateLadder(
        planId: Long,
        orders: List<LadderOrder>,
        minOrderFiat: BigDecimal,
        currentSpot: BigDecimal?,
        avgBuyPrice: BigDecimal? = null,
        feeRate: BigDecimal = BigDecimal.ZERO
    ): List<SellValidation> {
        val available = calculatePlanCostBasisUseCase(planId).available
        return validateLadderAgainstAvailable(
            orders = orders,
            available = available,
            minOrderFiat = minOrderFiat,
            currentSpot = currentSpot,
            avgBuyPrice = avgBuyPrice,
            feeRate = feeRate
        )
    }

    companion object {
        internal fun validateSingleAgainstAvailable(
            cryptoAmount: BigDecimal,
            limitPrice: BigDecimal,
            available: BigDecimal,
            minOrderFiat: BigDecimal,
            currentSpot: BigDecimal?,
            avgBuyPrice: BigDecimal? = null,
            feeRate: BigDecimal = BigDecimal.ZERO
        ): List<SellValidation> {
            val result = mutableListOf<SellValidation>()

            if (cryptoAmount <= BigDecimal.ZERO) {
                result += SellValidation.HardError.AmountMustBePositive
                return result
            }
            if (limitPrice <= BigDecimal.ZERO) {
                result += SellValidation.HardError.PriceMustBePositive
                return result
            }
            if (minOrderFiat > BigDecimal.ZERO && cryptoAmount * limitPrice < minOrderFiat) {
                result += SellValidation.HardError.MinOrderTooLow(minOrderFiat)
            }
            if (cryptoAmount > available) {
                result += SellValidation.HardError.InsufficientInventory(available)
            }

            if (currentSpot != null && currentSpot > BigDecimal.ZERO) {
                if (limitPrice <= currentSpot) {
                    result += SellValidation.InstantFillInfo(currentSpot, affectedCount = 1)
                }
                if (limitPrice > currentSpot.multiply(BigDecimal("3"))) {
                    result += SellValidation.FarFromMarketWarning(currentSpot, affectedCount = 1)
                }
            }

            checkLoss(cryptoAmount, limitPrice, avgBuyPrice, feeRate)?.let { result += it }

            return result
        }

        internal fun validateLadderAgainstAvailable(
            orders: List<LadderOrder>,
            available: BigDecimal,
            minOrderFiat: BigDecimal,
            currentSpot: BigDecimal?,
            avgBuyPrice: BigDecimal? = null,
            feeRate: BigDecimal = BigDecimal.ZERO
        ): List<SellValidation> {
            val result = mutableListOf<SellValidation>()

            if (orders.size !in SellLadderLimits.MIN_ORDER_COUNT..SellLadderLimits.MAX_ORDER_COUNT) {
                result += SellValidation.HardError.LadderCountOutOfRange(
                    min = SellLadderLimits.MIN_ORDER_COUNT,
                    max = SellLadderLimits.MAX_ORDER_COUNT
                )
                return result
            }

            if (orders.any { it.cryptoAmount <= BigDecimal.ZERO || it.limitPrice <= BigDecimal.ZERO }) {
                result += SellValidation.HardError.LadderOrderInvalid
                return result
            }

            val totalCrypto = orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.cryptoAmount }
            if (totalCrypto <= BigDecimal.ZERO) {
                result += SellValidation.HardError.AmountMustBePositive
                return result
            }
            if (totalCrypto > available) {
                result += SellValidation.HardError.InsufficientInventory(available)
            }

            val smallestFiat = orders.minOf { it.cryptoAmount * it.limitPrice }
            if (minOrderFiat > BigDecimal.ZERO && smallestFiat < minOrderFiat) {
                result += SellValidation.HardError.LadderLegBelowMin(
                    smallestFiat = smallestFiat.setScale(2, RoundingMode.HALF_UP),
                    minOrderFiat = minOrderFiat
                )
            }

            if (currentSpot != null && currentSpot > BigDecimal.ZERO) {
                val instantCount = orders.count { it.limitPrice <= currentSpot }
                if (instantCount > 0) {
                    result += SellValidation.InstantFillInfo(currentSpot, affectedCount = instantCount)
                }
                val farThreshold = currentSpot.multiply(BigDecimal("3"))
                val farCount = orders.count { it.limitPrice > farThreshold }
                if (farCount > 0) {
                    result += SellValidation.FarFromMarketWarning(currentSpot, affectedCount = farCount)
                }
            }

            if (avgBuyPrice != null && avgBuyPrice > BigDecimal.ZERO) {
                var totalLossFiat = BigDecimal.ZERO
                var totalLosingCostBasis = BigDecimal.ZERO
                var losingCount = 0
                for (o in orders) {
                    val w = checkLoss(o.cryptoAmount, o.limitPrice, avgBuyPrice, feeRate)
                    if (w != null) {
                        losingCount++
                        totalLossFiat += w.lossFiat
                        totalLosingCostBasis += o.cryptoAmount * avgBuyPrice
                    }
                }
                if (losingCount > 0) {
                    val lossPct = if (totalLosingCostBasis > BigDecimal.ZERO) {
                        totalLossFiat.divide(totalLosingCostBasis, 4, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO
                    result += SellValidation.LossWarning(
                        lossFiat = totalLossFiat.setScale(2, RoundingMode.HALF_UP),
                        lossPct = lossPct,
                        affectedCount = losingCount
                    )
                }
            }

            return result
        }

        /**
         * Pure helper: returns LossWarning when the net-of-fee profit would be negative.
         * Triggers also when [limitPrice] is just above [avgBuyPrice] but fee pushes the
         * result negative. Returns null when [avgBuyPrice] is unknown.
         */
        internal fun checkLoss(
            cryptoAmount: BigDecimal,
            limitPrice: BigDecimal,
            avgBuyPrice: BigDecimal?,
            feeRate: BigDecimal
        ): SellValidation.LossWarning? {
            if (avgBuyPrice == null || avgBuyPrice <= BigDecimal.ZERO) return null
            if (cryptoAmount <= BigDecimal.ZERO || limitPrice <= BigDecimal.ZERO) return null
            val grossFiat = cryptoAmount * limitPrice
            val netFiat = grossFiat * (BigDecimal.ONE - feeRate)
            val costBasis = cryptoAmount * avgBuyPrice
            val netProfit = netFiat - costBasis
            if (netProfit >= BigDecimal.ZERO) return null
            val lossFiat = netProfit.negate()
            val lossPct = if (costBasis > BigDecimal.ZERO) {
                lossFiat.divide(costBasis, 4, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            return SellValidation.LossWarning(lossFiat = lossFiat, lossPct = lossPct, affectedCount = 1)
        }
    }
}
