package com.accbot.dca.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyErrorsTest {

    private fun assertCategory(expected: ErrorCategory, raw: String) =
        assertEquals(raw, expected, FriendlyErrors.categorize(raw))

    @Test
    fun `transport failures are network problems`() {
        assertCategory(ErrorCategory.NETWORK, "Unable to resolve host \"api.binance.com\": No address associated with hostname")
        assertCategory(ErrorCategory.NETWORK, "java.net.SocketTimeoutException: timeout")
        assertCategory(ErrorCategory.NETWORK, "Failed to connect to coinmate.io/1.2.3.4:443")
        // A DNS failure while fetching a balance must not look like a funds problem.
        assertCategory(ErrorCategory.NETWORK, "Unable to resolve host while checking insufficient balance")
    }

    @Test
    fun `rejected keys are auth problems`() {
        assertCategory(ErrorCategory.AUTH, "Invalid API-key, IP, or permissions for action.")
        assertCategory(ErrorCategory.AUTH, "EAPI:Invalid key")
        assertCategory(ErrorCategory.AUTH, "Signature for this request is not valid.")
        assertCategory(ErrorCategory.AUTH, "HTTP 401 Unauthorized")
    }

    @Test
    fun `exchange specific funds and limit errors`() {
        assertCategory(ErrorCategory.INSUFFICIENT_FUNDS, "Account has insufficient balance for requested action.")
        assertCategory(ErrorCategory.INSUFFICIENT_FUNDS, "EOrder:Insufficient funds")
        assertCategory(ErrorCategory.EXCHANGE_LIMIT, "Filter failure: MIN_NOTIONAL")
        assertCategory(ErrorCategory.EXCHANGE_LIMIT, "EOrder:Order minimum not met")
        assertCategory(ErrorCategory.EXCHANGE_LIMIT, "Amount 20 CZK below minimum 50 CZK")
    }

    @Test
    fun `throttling and outages`() {
        assertCategory(ErrorCategory.RATE_LIMIT, "EAPI:Rate limit exceeded")
        assertCategory(ErrorCategory.RATE_LIMIT, "HTTP 429 Too Many Requests")
        assertCategory(ErrorCategory.EXCHANGE_UNAVAILABLE, "EService:Unavailable")
        assertCategory(ErrorCategory.EXCHANGE_UNAVAILABLE, "HTTP 503 Service Unavailable")
    }

    @Test
    fun `unknown and empty messages stay unknown`() {
        assertCategory(ErrorCategory.UNKNOWN, "Something odd happened")
        assertEquals(ErrorCategory.UNKNOWN, FriendlyErrors.categorize(null))
        assertEquals(ErrorCategory.UNKNOWN, FriendlyErrors.categorize("  "))
    }
}
