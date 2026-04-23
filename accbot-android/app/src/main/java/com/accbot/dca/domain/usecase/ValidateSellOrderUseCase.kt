package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Validation outcome for a prospective sell order. Multiple items may be returned
 * (e.g. InstantFillInfo and no hard error), so callers should render each item.
 * [Ok] is only emitted when the list would otherwise be empty.
 */
sealed class SellValidation {
    object Ok : SellValidation()
    data class HardError(val message: String) : SellValidation()
    data class InstantFillInfo(val spot: BigDecimal) : SellValidation()
    data class FarFromMarketWarning(val spot: BigDecimal) : SellValidation()
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
        minOrderSize: BigDecimal,
        currentSpot: BigDecimal?
    ): List<SellValidation> {
        val result = mutableListOf<SellValidation>()

        if (cryptoAmount <= BigDecimal.ZERO) {
            result += SellValidation.HardError("Mnozstvi musi byt vetsi nez 0")
            return result
        }
        if (limitPrice <= BigDecimal.ZERO) {
            result += SellValidation.HardError("Limitni cena musi byt vetsi nez 0")
            return result
        }
        if (cryptoAmount < minOrderSize) {
            result += SellValidation.HardError("Minimalni order je $minOrderSize")
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
                "Nemas tolik k dispozici (k dispozici $available)"
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

        if (result.isEmpty()) result += SellValidation.Ok
        return result
    }
}
