package com.accbot.dca.presentation.utils

import android.content.Context
import com.accbot.dca.R

/**
 * What kind of problem a raw exchange / transport error is, so the UI can say what
 * happened and what to do instead of showing "Unable to resolve host api.binance.com".
 */
enum class ErrorCategory { NETWORK, AUTH, INSUFFICIENT_FUNDS, EXCHANGE_LIMIT, RATE_LIMIT, EXCHANGE_UNAVAILABLE, UNKNOWN }

object FriendlyErrors {

    // Order matters: a DNS failure while "fetching balance" is a network problem,
    // not a funds problem, so transport patterns are checked first.
    private val rules: List<Pair<ErrorCategory, List<Regex>>> = listOf(
        ErrorCategory.NETWORK to patterns(
            "unable to resolve host", "unknownhost", "no address associated", "failed to connect",
            "timeout", "timed out", "connection reset", "connection refused", "connection abort",
            "network is unreachable", "sslhandshake", "ssl handshake", "socket", "no internet"
        ),
        ErrorCategory.RATE_LIMIT to patterns(
            "rate limit", "too many requests", "\\b429\\b", "-1003\\b", "eapi:rate", "request weight"
        ),
        ErrorCategory.EXCHANGE_UNAVAILABLE to patterns(
            "maintenance", "service unavailable", "eservice", "\\b50[234]\\b", "temporarily unavailable",
            "system busy", "server busy"
        ),
        ErrorCategory.AUTH to patterns(
            "api[- _]?key", "invalid key", "signature", "unauthori[sz]ed", "\\b401\\b", "\\b403\\b",
            "permission", "access denied", "invalid nonce", "nonce", "kc-api", "authenticat", "credential",
            "-2014\\b", "-2015\\b", "-1022\\b", "ip not allowed", "whitelist"
        ),
        ErrorCategory.INSUFFICIENT_FUNDS to patterns(
            "insufficient", "not enough", "balance too low", "-2010\\b", "exceeds available"
        ),
        ErrorCategory.EXCHANGE_LIMIT to patterns(
            "minimum", "min_notional", "notional", "lot_size", "too small", "below min", "min order",
            "order size", "precision", "-1013\\b", "price_filter", "max order"
        )
    )

    private fun patterns(vararg p: String) = p.map { Regex(it, RegexOption.IGNORE_CASE) }

    fun categorize(raw: String?): ErrorCategory {
        if (raw.isNullOrBlank()) return ErrorCategory.UNKNOWN
        return rules.firstOrNull { (_, regexes) -> regexes.any { it.containsMatchIn(raw) } }?.first
            ?: ErrorCategory.UNKNOWN
    }

    fun title(context: Context, category: ErrorCategory): String? = when (category) {
        ErrorCategory.NETWORK -> context.getString(R.string.error_network_title)
        ErrorCategory.AUTH -> context.getString(R.string.error_auth_title)
        ErrorCategory.INSUFFICIENT_FUNDS -> context.getString(R.string.error_funds_title)
        ErrorCategory.EXCHANGE_LIMIT -> context.getString(R.string.error_limit_title)
        ErrorCategory.RATE_LIMIT -> context.getString(R.string.error_rate_limit_title)
        ErrorCategory.EXCHANGE_UNAVAILABLE -> context.getString(R.string.error_unavailable_title)
        ErrorCategory.UNKNOWN -> null
    }

    fun advice(context: Context, category: ErrorCategory): String? = when (category) {
        ErrorCategory.NETWORK -> context.getString(R.string.error_network_advice)
        ErrorCategory.AUTH -> context.getString(R.string.error_auth_advice)
        ErrorCategory.INSUFFICIENT_FUNDS -> context.getString(R.string.error_funds_advice)
        ErrorCategory.EXCHANGE_LIMIT -> context.getString(R.string.error_limit_advice)
        ErrorCategory.RATE_LIMIT -> context.getString(R.string.error_rate_limit_advice)
        ErrorCategory.EXCHANGE_UNAVAILABLE -> context.getString(R.string.error_unavailable_advice)
        ErrorCategory.UNKNOWN -> null
    }

    /** One short line for lists: the category title, or the raw text when unknown. */
    fun shortText(context: Context, raw: String): String = title(context, categorize(raw)) ?: raw

    /** "Title. Advice" for notifications; the raw text when the error is not recognized. */
    fun summary(context: Context, raw: String): String {
        val category = categorize(raw)
        val title = title(context, category) ?: return raw
        return "$title. ${advice(context, category)}"
    }
}
