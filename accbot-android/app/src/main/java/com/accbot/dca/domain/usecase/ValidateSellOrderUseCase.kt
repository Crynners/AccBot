package com.accbot.dca.domain.usecase

import java.math.BigDecimal
import javax.inject.Inject

/**
 * Validation outcome for a prospective sell order. Multiple items may be returned
 * (e.g. InstantFillInfo and no hard error), so callers should render each item.
 * An empty list means "valid".
 *
 * Hard errors are locale-free: each subtype maps to a string resource the UI resolves.
 */
sealed class SellValidation {
    /** Field this validation result attaches to (lets UI render it under the right input). */
    enum class Field { AMOUNT, PRICE, NET, GENERIC }

    sealed class HardError(val field: Field) : SellValidation() {
        object AmountMustBePositive : HardError(Field.AMOUNT)
        object PriceMustBePositive : HardError(Field.PRICE)
        data class MinOrderTooLow(val minOrderFiat: BigDecimal) : HardError(Field.AMOUNT)
        data class InsufficientInventory(val available: BigDecimal) : HardError(Field.AMOUNT)
    }

    data class InstantFillInfo(val spot: BigDecimal) : SellValidation()
    data class FarFromMarketWarning(val spot: BigDecimal) : SellValidation()
    /**
     * Net profit after exchange fee would be negative for this order. Triggered both when
     * the limit price is below avg buy and when the price is just above avg buy but fee
     * pushes the result into negative territory. Caller (UI) shows a warning banner;
     * the wizard still allows the user to proceed - the user makes the final decision.
     */
    data class LossWarning(val lossFiat: BigDecimal, val lossPct: BigDecimal) : SellValidation()
}

/**
 * Validate a prospective limit sell order against plan state (held crypto minus
 * reservations from open sells) and optional spot price.
 *
 * Checks:
 *  - amount > 0, limitPrice > 0
 *  - amount >= minOrderSize (exchange-specific, passed by caller)
 *  - amount <= available crypto (held - unfilled reservations on other open sells)
 *  - limitPrice <= spot -> InstantFillInfo (UI shows warning; order will fill immediately)
 *  - limitPrice > 3x spot -> FarFromMarketWarning (typo protection)
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

        // Single source of truth for "available to sell" - the cost basis use case already
        // accounts for filled buys, filled sells, and full reservation of PENDING/PARTIAL sells.
        val available = calculatePlanCostBasisUseCase(planId).available
        if (cryptoAmount > available) {
            result += SellValidation.HardError.InsufficientInventory(available)
        }

        if (currentSpot != null) {
            if (limitPrice <= currentSpot) {
                result += SellValidation.InstantFillInfo(currentSpot)
            }
            if (limitPrice > currentSpot.multiply(BigDecimal("3"))) {
                result += SellValidation.FarFromMarketWarning(currentSpot)
            }
        }

        checkLoss(cryptoAmount, limitPrice, avgBuyPrice, feeRate)?.let { result += it }

        return result
    }

    companion object {
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
                lossFiat.divide(costBasis, 4, java.math.RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            return SellValidation.LossWarning(lossFiat = lossFiat, lossPct = lossPct)
        }
    }
}
