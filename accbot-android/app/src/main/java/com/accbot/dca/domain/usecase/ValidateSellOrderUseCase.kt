package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Validation outcome for a prospective sell order. Multiple items may be returned
 * (e.g. InstantFillInfo and no hard error), so callers should render each item.
 * An empty list means "valid".
 */
sealed class SellValidation {
    /** Field this validation result attaches to (lets UI render it under the right input). */
    enum class Field { AMOUNT, PRICE, NET, GENERIC }

    data class HardError(val message: String, val field: Field = Field.GENERIC) : SellValidation()
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
    private val database: DcaDatabase
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
            result += SellValidation.HardError("Množství musí být větší než 0", SellValidation.Field.AMOUNT)
            return result
        }
        if (limitPrice <= BigDecimal.ZERO) {
            result += SellValidation.HardError("Limitní cena musí být větší než 0", SellValidation.Field.PRICE)
            return result
        }
        if (minOrderFiat > BigDecimal.ZERO && cryptoAmount * limitPrice < minOrderFiat) {
            result += SellValidation.HardError(
                "Minimální hodnota orderu je $minOrderFiat (zvyš množství nebo cenu)",
                SellValidation.Field.AMOUNT
            )
        }

        val tx = database.transactionDao().getTransactionsByPlanSync(planId)
        val completedOrPartial = tx.filter {
            it.status == TransactionStatus.COMPLETED || it.status == TransactionStatus.PARTIAL
        }
        val heldBought = completedOrPartial
            .filter { it.side == TransactionSide.BUY }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.cryptoAmount }
        val heldSold = completedOrPartial
            .filter { it.side == TransactionSide.SELL }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.cryptoAmount }
        val held = heldBought - heldSold

        // Unfilled crypto reserved by other open sells (PENDING or PARTIAL).
        val openSellsRequested = tx
            .filter {
                it.side == TransactionSide.SELL &&
                    it.status in setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL)
            }
            .fold(BigDecimal.ZERO) { acc, t ->
                acc + ((t.requestedCryptoAmount ?: BigDecimal.ZERO) - t.cryptoAmount)
            }

        val available = held - openSellsRequested
        if (cryptoAmount > available) {
            result += SellValidation.HardError(
                "Nemáš tolik k dispozici (k dispozici $available)",
                SellValidation.Field.AMOUNT
            )
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
