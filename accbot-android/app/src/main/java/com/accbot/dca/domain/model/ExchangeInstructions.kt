package com.accbot.dca.domain.model

/**
 * Instructions for setting up API keys on an exchange.
 * Contains step-by-step guide, URL, and whether passphrase/clientId is needed.
 * For sandbox mode, separate instructions and URLs are provided.
 */
data class ExchangeInstructions(
    val steps: List<String>,
    val url: String,
    val needsPassphrase: Boolean,
    val needsClientId: Boolean = false,
    val sandboxSteps: List<String>? = null,
    val sandboxUrl: String? = null
)

/**
 * Provider for exchange-specific API setup instructions.
 * Extracted from ViewModel for reusability and Single Responsibility Principle.
 */
object ExchangeInstructionsProvider {

    /**
     * Get API setup instructions for a specific exchange.
     *
     * @param exchange The exchange to get instructions for
     * @param isSandbox Whether to return sandbox-specific instructions (default: false)
     * @return Instructions with steps and URL, using sandbox-specific values when available and isSandbox is true
     */
    fun getInstructions(exchange: Exchange, isSandbox: Boolean = false): ExchangeInstructions {
        val instructions = getBaseInstructions(exchange)

        // If sandbox mode and sandbox instructions exist, return a modified copy
        return if (isSandbox && instructions.sandboxSteps != null && instructions.sandboxUrl != null) {
            instructions.copy(
                steps = instructions.sandboxSteps,
                url = instructions.sandboxUrl
            )
        } else {
            instructions
        }
    }

    /**
     * Get base instructions including sandbox-specific information.
     */
    private fun getBaseInstructions(exchange: Exchange): ExchangeInstructions {
        return when (exchange) {
            Exchange.COINMATE -> ExchangeInstructions(
                steps = listOf(
                    "Go to coinmate.io and log in",
                    "Navigate to Settings > API",
                    "Note your Client ID (shown at the top)",
                    "Click 'Create new API key'",
                    "Enable 'Trade' permission only",
                    "Copy Client ID, Public Key, and Private Key"
                ),
                url = "https://coinmate.io/apikeys",
                needsPassphrase = false,
                needsClientId = true
            )
            Exchange.BINANCE -> ExchangeInstructions(
                steps = listOf(
                    "Go to binance.com and log in",
                    "Navigate to API Management",
                    "Create a new API key",
                    "Enable 'Spot & Margin Trading' only",
                    "Restrict to your IP if possible",
                    "Copy the API Key and Secret"
                ),
                url = "https://www.binance.com/en/my/settings/api-management",
                needsPassphrase = false,
                sandboxSteps = listOf(
                    "Open testnet.binance.vision",
                    "Log in with your GitHub account",
                    "Click 'Generate HMAC_SHA256 Key'",
                    "Copy the API Key and Secret Key",
                    "Test funds are provided automatically"
                ),
                sandboxUrl = "https://testnet.binance.vision/"
            )
            Exchange.KRAKEN -> ExchangeInstructions(
                steps = listOf(
                    "Go to kraken.com and log in",
                    "Navigate to Settings > API",
                    "Create a new API key",
                    "Enable 'Create & Modify Orders' only",
                    "Copy the API Key and Private Key"
                ),
                url = "https://www.kraken.com/u/security/api",
                needsPassphrase = false
            )
            Exchange.KUCOIN -> ExchangeInstructions(
                steps = listOf(
                    "Go to kucoin.com and log in",
                    "Navigate to API Management",
                    "Create a new API key with passphrase",
                    "Enable 'Trade' permission only",
                    "Copy the API Key, Secret, and Passphrase"
                ),
                url = "https://www.kucoin.com/account/api",
                needsPassphrase = true,
                sandboxSteps = listOf(
                    "Open sandbox.kucoin.com",
                    "Register a new account (separate from production)",
                    "Go to API Management",
                    "Create a new API key with passphrase",
                    "Enable 'Trade' permission",
                    "Copy API Key, Secret, and Passphrase"
                ),
                sandboxUrl = "https://sandbox.kucoin.com/"
            )
            Exchange.BITFINEX -> ExchangeInstructions(
                steps = listOf(
                    "Go to bitfinex.com and log in",
                    "Navigate to Account > API Keys",
                    "Create a new API key",
                    "Enable 'Orders' permission only",
                    "Copy the API Key and Secret"
                ),
                url = "https://setting.bitfinex.com/api",
                needsPassphrase = false
            )
            Exchange.HUOBI -> ExchangeInstructions(
                steps = listOf(
                    "Go to huobi.com and log in",
                    "Navigate to API Management",
                    "Create a new API key",
                    "Enable 'Trade' permission only",
                    "Copy the Access Key and Secret Key"
                ),
                url = "https://www.huobi.com/en-us/apikey/",
                needsPassphrase = false
            )
            Exchange.COINBASE -> ExchangeInstructions(
                steps = listOf(
                    "Go to coinbase.com and log in",
                    "Navigate to Settings > API",
                    "Create a new API key",
                    "Enable 'Trade' permission only",
                    "Copy the API Key and Secret"
                ),
                url = "https://www.coinbase.com/settings/api",
                needsPassphrase = false,
                sandboxSteps = listOf(
                    "Open the Coinbase Exchange Sandbox",
                    "Register a sandbox account",
                    "Go to Settings > API",
                    "Create a new API key",
                    "Select 'View' and 'Trade' permissions",
                    "Copy the API Key and Secret"
                ),
                sandboxUrl = "https://public.sandbox.exchange.coinbase.com/"
            )
        }
    }

    /**
     * Check if an exchange requires a passphrase for API authentication.
     */
    fun needsPassphrase(exchange: Exchange): Boolean {
        return exchange == Exchange.KUCOIN
    }

    /**
     * Check if an exchange requires a separate Client ID.
     */
    fun needsClientId(exchange: Exchange): Boolean {
        return exchange == Exchange.COINMATE
    }
}
