package com.accbot.dca.domain.util

/**
 * Basic validation for cryptocurrency wallet addresses.
 * Simplified validation — actual address format depends on the crypto.
 */
object CryptoAddressValidator {

    fun isValid(crypto: String, address: String): Boolean {
        if (address.isBlank()) return false
        val trimmed = address.trim()
        return when (crypto.uppercase()) {
            "BTC" -> isValidBtcAddress(trimmed)
            "ETH", "SOL", "ADA", "DOT" -> isValidGenericAddress(trimmed, minLength = 26, maxLength = 128)
            "LTC" -> isValidLtcAddress(trimmed)
            else -> isValidGenericAddress(trimmed, minLength = 20, maxLength = 128)
        }
    }

    private fun isValidBtcAddress(address: String): Boolean {
        return when {
            address.startsWith("1") || address.startsWith("3") ->
                address.length in 25..34 && address.all { it.isLetterOrDigit() }
            address.startsWith("bc1") ->
                address.length in 42..62 && address.all { it.isLetterOrDigit() }
            else -> false
        }
    }

    private fun isValidLtcAddress(address: String): Boolean {
        return when {
            address.startsWith("L") || address.startsWith("M") ->
                address.length in 25..34 && address.all { it.isLetterOrDigit() }
            address.startsWith("ltc1") ->
                address.length in 42..62 && address.all { it.isLetterOrDigit() }
            else -> false
        }
    }

    private fun isValidGenericAddress(address: String, minLength: Int, maxLength: Int): Boolean {
        return address.length in minLength..maxLength &&
                address.all { it.isLetterOrDigit() || it == '_' }
    }
}
