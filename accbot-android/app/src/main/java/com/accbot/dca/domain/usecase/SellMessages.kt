package com.accbot.dca.domain.usecase

import android.content.Context
import com.accbot.dca.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * User-facing texts of the sell use cases. The use cases return these as reasons that the
 * UI shows directly, so production uses [AndroidSellMessages] (localized string resources)
 * and unit tests use [English].
 */
interface SellMessages {
    fun planNotFound(): String
    fun sellsDisabled(): String
    fun missingCredentials(): String
    fun limitSellsUnsupported(exchange: String): String
    fun planChanged(): String
    fun reviewPendingSell(): String
    fun responseUnreadable(): String
    fun missingOrderId(): String
    fun orderIdNotSaved(): String
    fun notConfirmedInTime(): String
    fun hardError(error: SellValidation.HardError): String
    fun transactionNotFound(): String
    fun orderAlreadyClosed(): String
    fun orderHasNoExchangeId(): String
    fun cancelSentUnverified(): String
    fun cancelNotConfirmed(): String

    object English : SellMessages {
        override fun planNotFound() = "Plan not found"
        override fun sellsDisabled() = "Sells are disabled for this plan"
        override fun missingCredentials() = "Missing credentials"
        override fun limitSellsUnsupported(exchange: String) = "$exchange does not support limit sells"
        override fun planChanged() = "The sell plan changed. Review it again."
        override fun reviewPendingSell() = "Review the pending sell in History before submitting a new one."
        override fun responseUnreadable() = "The exchange response could not be read."
        override fun missingOrderId() = "The exchange did not return an order ID."
        override fun orderIdNotSaved() = "The exchange accepted the order but AccBot could not save its ID."
        override fun notConfirmedInTime() = "The exchange did not confirm the sell in time."
        override fun hardError(error: SellValidation.HardError): String = when (error) {
            SellValidation.HardError.AmountMustBePositive -> "Amount must be greater than 0"
            SellValidation.HardError.PriceMustBePositive -> "Limit price must be greater than 0"
            is SellValidation.HardError.MinOrderTooLow -> "Order value is below minimum (${error.minOrderFiat})"
            is SellValidation.HardError.InsufficientInventory -> "Insufficient available inventory (${error.available})"
            is SellValidation.HardError.LadderCountOutOfRange -> "Order count must be between ${error.min} and ${error.max}"
            is SellValidation.HardError.LadderLegBelowMin -> "Smallest order (${error.smallestFiat}) is below minimum (${error.minOrderFiat})"
            SellValidation.HardError.LadderOrderInvalid -> "Invalid order parameters in series"
            SellValidation.HardError.SellsDisabled -> "Sells are disabled for this plan"
        }
        override fun transactionNotFound() = "Transaction not found"
        override fun orderAlreadyClosed() = "The order is already closed"
        override fun orderHasNoExchangeId() = "The order has no exchange ID"
        override fun cancelSentUnverified() = "The cancellation was sent, but the final fills could not be verified. Refresh the status."
        override fun cancelNotConfirmed() = "The exchange has not confirmed the cancelled order is closed yet. Refresh the status."
    }
}

class AndroidSellMessages @Inject constructor(
    @ApplicationContext private val context: Context
) : SellMessages {
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun amount(value: BigDecimal) = value.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()

    override fun planNotFound() = text(R.string.sell_error_plan_not_found)
    override fun sellsDisabled() = text(R.string.sell_error_sells_disabled)
    override fun missingCredentials() = text(R.string.sell_error_missing_credentials)
    override fun limitSellsUnsupported(exchange: String) = text(R.string.sell_error_limit_unsupported, exchange)
    override fun planChanged() = text(R.string.sell_error_plan_changed)
    override fun reviewPendingSell() = text(R.string.sell_error_review_pending)
    override fun responseUnreadable() = text(R.string.sell_uncertain_unreadable)
    override fun missingOrderId() = text(R.string.sell_uncertain_no_order_id)
    override fun orderIdNotSaved() = text(R.string.sell_uncertain_id_not_saved)
    override fun notConfirmedInTime() = text(R.string.sell_uncertain_timeout)
    override fun hardError(error: SellValidation.HardError): String = when (error) {
        SellValidation.HardError.AmountMustBePositive -> text(R.string.sell_validation_amount_must_be_positive)
        SellValidation.HardError.PriceMustBePositive -> text(R.string.sell_validation_price_must_be_positive)
        is SellValidation.HardError.MinOrderTooLow -> text(R.string.sell_validation_min_order_too_low, amount(error.minOrderFiat))
        is SellValidation.HardError.InsufficientInventory -> text(R.string.sell_validation_insufficient_inventory, amount(error.available))
        is SellValidation.HardError.LadderCountOutOfRange -> text(R.string.sell_error_ladder_count, error.min, error.max)
        is SellValidation.HardError.LadderLegBelowMin ->
            text(R.string.sell_error_ladder_leg_min, amount(error.smallestFiat), amount(error.minOrderFiat))
        SellValidation.HardError.LadderOrderInvalid -> text(R.string.sell_wizard_ladder_error_invalid_order)
        SellValidation.HardError.SellsDisabled -> text(R.string.sell_error_sells_disabled)
    }
    override fun transactionNotFound() = text(R.string.sell_error_transaction_not_found)
    override fun orderAlreadyClosed() = text(R.string.sell_error_order_closed)
    override fun orderHasNoExchangeId() = text(R.string.sell_error_no_exchange_id)
    override fun cancelSentUnverified() = text(R.string.sell_cancel_sent_unverified)
    override fun cancelNotConfirmed() = text(R.string.sell_cancel_not_confirmed)
}
