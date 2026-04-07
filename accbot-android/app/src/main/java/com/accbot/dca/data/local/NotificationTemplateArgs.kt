package com.accbot.dca.data.local

import org.json.JSONObject

/**
 * Structured arguments for notification templates.
 * Stored as JSON in the `templateArgs` column – text is rendered at display time
 * using the current locale, so language switches re-render old notifications.
 *
 * Numbers are stored as raw strings (BigDecimal.toPlainString()) and formatted
 * via NumberFormatters at render time.
 */
sealed class NotificationTemplateArgs {

    abstract fun toJson(): String

    /** Successful purchase (filled immediately). */
    data class Purchase(
        val cryptoAmount: String,
        val crypto: String,
        val fiatAmount: String,
        val fiat: String,
        val price: String,
        val scheduledAtEpochMs: Long? = null,
        val executedAtEpochMs: Long? = null
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_PURCHASE)
            put("cryptoAmount", cryptoAmount)
            put("crypto", crypto)
            put("fiatAmount", fiatAmount)
            put("fiat", fiat)
            put("price", price)
            if (scheduledAtEpochMs != null) put("scheduledAtEpochMs", scheduledAtEpochMs)
            if (executedAtEpochMs != null) put("executedAtEpochMs", executedAtEpochMs)
        }.toString()
    }

    /** Purchase submitted but awaiting confirmation (PENDING status). */
    data class PurchasePending(
        val fiatAmount: String,
        val fiat: String,
        val crypto: String,
        val price: String,
        val scheduledAtEpochMs: Long? = null,
        val executedAtEpochMs: Long? = null
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_PURCHASE_PENDING)
            put("fiatAmount", fiatAmount)
            put("fiat", fiat)
            put("crypto", crypto)
            put("price", price)
            if (scheduledAtEpochMs != null) put("scheduledAtEpochMs", scheduledAtEpochMs)
            if (executedAtEpochMs != null) put("executedAtEpochMs", executedAtEpochMs)
        }.toString()
    }

    /** DCA error with structured crypto + errorMessage. */
    data class Error(
        val crypto: String,
        val errorMessage: String
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_ERROR)
            put("crypto", crypto)
            put("errorMessage", errorMessage)
        }.toString()
    }

    /** Low fiat balance warning. */
    data class LowBalance(
        val exchangeName: String,
        val fiat: String,
        val remainingDays: Double
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_LOW_BALANCE)
            put("exchangeName", exchangeName)
            put("fiat", fiat)
            put("remainingDays", remainingDays)
        }.toString()
    }

    /** Withdrawal threshold reached. */
    data class WithdrawalThreshold(
        val amount: String,
        val crypto: String,
        val exchangeName: String
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_WITHDRAWAL_THRESHOLD)
            put("amount", amount)
            put("crypto", crypto)
            put("exchangeName", exchangeName)
        }.toString()
    }

    /** Target accumulation reached – plan auto-disabled. */
    data class TargetReached(
        val targetAmount: String,
        val crypto: String
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_TARGET_REACHED)
            put("targetAmount", targetAmount)
            put("crypto", crypto)
        }.toString()
    }

    /** Purchase amount below exchange minimum order size. */
    data class BelowMinimum(
        val crypto: String,
        val purchaseAmount: String,
        val fiat: String,
        val minOrderSize: String
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_BELOW_MINIMUM)
            put("crypto", crypto)
            put("purchaseAmount", purchaseAmount)
            put("fiat", fiat)
            put("minOrderSize", minOrderSize)
        }.toString()
    }

    /** Network error – purchase will be retried. */
    data class NetworkRetry(
        val crypto: String,
        val exchangeName: String,
        val errorMessage: String,
        val nextRetryAtEpochMs: Long,
        val attemptCount: Int,
        val planId: Long
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_NETWORK_RETRY)
            put("crypto", crypto)
            put("exchangeName", exchangeName)
            put("errorMessage", errorMessage)
            put("nextRetryAtEpochMs", nextRetryAtEpochMs)
            put("attemptCount", attemptCount)
            put("planId", planId)
        }.toString()
    }

    /** Missed purchases due to prolonged offline period. */
    data class MissedPurchases(
        val crypto: String,
        val exchangeName: String,
        val missedCount: Int,
        val planId: Long
    ) : NotificationTemplateArgs() {
        override fun toJson(): String = JSONObject().apply {
            put(KEY_TYPE, TYPE_MISSED_PURCHASES)
            put("crypto", crypto)
            put("exchangeName", exchangeName)
            put("missedCount", missedCount)
            put("planId", planId)
        }.toString()
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val TYPE_PURCHASE = "purchase"
        private const val TYPE_PURCHASE_PENDING = "purchase_pending"
        private const val TYPE_ERROR = "error"
        private const val TYPE_LOW_BALANCE = "low_balance"
        private const val TYPE_WITHDRAWAL_THRESHOLD = "withdrawal_threshold"
        private const val TYPE_TARGET_REACHED = "target_reached"
        private const val TYPE_BELOW_MINIMUM = "below_minimum"
        private const val TYPE_NETWORK_RETRY = "network_retry"
        private const val TYPE_MISSED_PURCHASES = "missed_purchases"

        fun fromJson(json: String): NotificationTemplateArgs? = try {
            val obj = JSONObject(json)
            when (obj.getString(KEY_TYPE)) {
                TYPE_PURCHASE -> Purchase(
                    cryptoAmount = obj.getString("cryptoAmount"),
                    crypto = obj.getString("crypto"),
                    fiatAmount = obj.getString("fiatAmount"),
                    fiat = obj.getString("fiat"),
                    price = obj.getString("price"),
                    scheduledAtEpochMs = obj.optLong("scheduledAtEpochMs", 0L).takeIf { it > 0 },
                    executedAtEpochMs = obj.optLong("executedAtEpochMs", 0L).takeIf { it > 0 }
                )
                TYPE_PURCHASE_PENDING -> PurchasePending(
                    fiatAmount = obj.getString("fiatAmount"),
                    fiat = obj.getString("fiat"),
                    crypto = obj.getString("crypto"),
                    price = obj.getString("price"),
                    scheduledAtEpochMs = obj.optLong("scheduledAtEpochMs", 0L).takeIf { it > 0 },
                    executedAtEpochMs = obj.optLong("executedAtEpochMs", 0L).takeIf { it > 0 }
                )
                TYPE_ERROR -> Error(
                    crypto = obj.getString("crypto"),
                    errorMessage = obj.getString("errorMessage")
                )
                TYPE_LOW_BALANCE -> LowBalance(
                    exchangeName = obj.getString("exchangeName"),
                    fiat = obj.getString("fiat"),
                    remainingDays = obj.getDouble("remainingDays")
                )
                TYPE_WITHDRAWAL_THRESHOLD -> WithdrawalThreshold(
                    amount = obj.getString("amount"),
                    crypto = obj.getString("crypto"),
                    exchangeName = obj.getString("exchangeName")
                )
                TYPE_TARGET_REACHED -> TargetReached(
                    targetAmount = obj.getString("targetAmount"),
                    crypto = obj.getString("crypto")
                )
                TYPE_BELOW_MINIMUM -> BelowMinimum(
                    crypto = obj.getString("crypto"),
                    purchaseAmount = obj.getString("purchaseAmount"),
                    fiat = obj.getString("fiat"),
                    minOrderSize = obj.getString("minOrderSize")
                )
                TYPE_MISSED_PURCHASES -> MissedPurchases(
                    crypto = obj.getString("crypto"),
                    exchangeName = obj.getString("exchangeName"),
                    missedCount = obj.getInt("missedCount"),
                    planId = obj.optLong("planId", 0)
                )
                TYPE_NETWORK_RETRY -> NetworkRetry(
                    crypto = obj.getString("crypto"),
                    exchangeName = obj.getString("exchangeName"),
                    errorMessage = obj.getString("errorMessage"),
                    nextRetryAtEpochMs = obj.getLong("nextRetryAtEpochMs"),
                    attemptCount = obj.optInt("attemptCount", 1),
                    planId = obj.optLong("planId", 0)
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
