package com.accbot.dca.domain.model

import androidx.annotation.StringRes
import com.accbot.dca.R

/**
 * Instructions for setting up API keys on an exchange.
 * Contains step-by-step guide (as string resource IDs), URL, and whether passphrase/clientId is needed.
 * For sandbox mode, separate instructions and URLs are provided.
 */
data class ExchangeInstructions(
    val steps: List<Int>,
    val url: String,
    val needsPassphrase: Boolean,
    val needsClientId: Boolean = false,
    val sandboxSteps: List<Int>? = null,
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
                    R.string.exchange_instructions_coinmate_1,
                    R.string.exchange_instructions_coinmate_2,
                    R.string.exchange_instructions_coinmate_3,
                    R.string.exchange_instructions_coinmate_4,
                    R.string.exchange_instructions_coinmate_5,
                    R.string.exchange_instructions_coinmate_6
                ),
                url = "https://coinmate.io/apikeys",
                needsPassphrase = false,
                needsClientId = true
            )
            Exchange.BINANCE -> ExchangeInstructions(
                steps = listOf(
                    R.string.exchange_instructions_binance_1,
                    R.string.exchange_instructions_binance_2,
                    R.string.exchange_instructions_binance_3,
                    R.string.exchange_instructions_binance_4,
                    R.string.exchange_instructions_binance_5,
                    R.string.exchange_instructions_binance_6
                ),
                url = "https://www.binance.com/en/my/settings/api-management",
                needsPassphrase = false,
                sandboxSteps = listOf(
                    R.string.exchange_instructions_binance_sandbox_1,
                    R.string.exchange_instructions_binance_sandbox_2,
                    R.string.exchange_instructions_binance_sandbox_3,
                    R.string.exchange_instructions_binance_sandbox_4,
                    R.string.exchange_instructions_binance_sandbox_5
                ),
                sandboxUrl = "https://testnet.binance.vision/"
            )
            Exchange.KRAKEN -> ExchangeInstructions(
                steps = listOf(
                    R.string.exchange_instructions_kraken_1,
                    R.string.exchange_instructions_kraken_2,
                    R.string.exchange_instructions_kraken_3,
                    R.string.exchange_instructions_kraken_4,
                    R.string.exchange_instructions_kraken_5
                ),
                url = "https://www.kraken.com/u/security/api",
                needsPassphrase = false
            )
            Exchange.KUCOIN -> ExchangeInstructions(
                steps = listOf(
                    R.string.exchange_instructions_kucoin_1,
                    R.string.exchange_instructions_kucoin_2,
                    R.string.exchange_instructions_kucoin_3,
                    R.string.exchange_instructions_kucoin_4,
                    R.string.exchange_instructions_kucoin_5
                ),
                url = "https://www.kucoin.com/account/api",
                needsPassphrase = true,
                sandboxSteps = listOf(
                    R.string.exchange_instructions_kucoin_sandbox_1,
                    R.string.exchange_instructions_kucoin_sandbox_2,
                    R.string.exchange_instructions_kucoin_sandbox_3,
                    R.string.exchange_instructions_kucoin_sandbox_4,
                    R.string.exchange_instructions_kucoin_sandbox_5,
                    R.string.exchange_instructions_kucoin_sandbox_6
                ),
                sandboxUrl = "https://sandbox.kucoin.com/"
            )
            Exchange.BITFINEX -> ExchangeInstructions(
                steps = listOf(
                    R.string.exchange_instructions_bitfinex_1,
                    R.string.exchange_instructions_bitfinex_2,
                    R.string.exchange_instructions_bitfinex_3,
                    R.string.exchange_instructions_bitfinex_4,
                    R.string.exchange_instructions_bitfinex_5
                ),
                url = "https://setting.bitfinex.com/api",
                needsPassphrase = false
            )
            Exchange.HUOBI -> ExchangeInstructions(
                steps = listOf(
                    R.string.exchange_instructions_huobi_1,
                    R.string.exchange_instructions_huobi_2,
                    R.string.exchange_instructions_huobi_3,
                    R.string.exchange_instructions_huobi_4,
                    R.string.exchange_instructions_huobi_5
                ),
                url = "https://www.huobi.com/en-us/apikey/",
                needsPassphrase = false
            )
            Exchange.COINBASE -> ExchangeInstructions(
                steps = listOf(
                    R.string.exchange_instructions_coinbase_1,
                    R.string.exchange_instructions_coinbase_2,
                    R.string.exchange_instructions_coinbase_3,
                    R.string.exchange_instructions_coinbase_4,
                    R.string.exchange_instructions_coinbase_5
                ),
                url = "https://www.coinbase.com/settings/api",
                needsPassphrase = false,
                sandboxSteps = listOf(
                    R.string.exchange_instructions_coinbase_sandbox_1,
                    R.string.exchange_instructions_coinbase_sandbox_2,
                    R.string.exchange_instructions_coinbase_sandbox_3,
                    R.string.exchange_instructions_coinbase_sandbox_4,
                    R.string.exchange_instructions_coinbase_sandbox_5,
                    R.string.exchange_instructions_coinbase_sandbox_6
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
