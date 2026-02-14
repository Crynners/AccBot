package com.accbot.dca.exchange

import com.accbot.dca.domain.model.Exchange

/**
 * Exchange URL configuration for production and sandbox environments.
 *
 * Sandbox availability:
 * - Binance: Full testnet at testnet.binance.vision
 * - KuCoin: Full sandbox at openapi-sandbox.kucoin.com
 * - Coinbase: Full sandbox at api-public.sandbox.exchange.coinbase.com
 * - Kraken: Futures demo only (not spot trading)
 * - Bitfinex: Paper trading (same URL, different mode)
 * - Huobi: Testnet discontinued
 * - Coinmate: No sandbox available
 */
object ExchangeConfig {

    private val productionUrls = mapOf(
        Exchange.BINANCE to "https://api.binance.com",
        Exchange.KUCOIN to "https://api.kucoin.com",
        Exchange.COINBASE to "https://api.exchange.coinbase.com",
        Exchange.KRAKEN to "https://api.kraken.com",
        Exchange.BITFINEX to "https://api.bitfinex.com",
        Exchange.HUOBI to "https://api.huobi.pro",
        Exchange.COINMATE to "https://coinmate.io/api"
    )

    private val sandboxUrls = mapOf(
        Exchange.BINANCE to "https://testnet.binance.vision",
        Exchange.KUCOIN to "https://openapi-sandbox.kucoin.com",
        Exchange.COINBASE to "https://api-public.sandbox.exchange.coinbase.com",
        // Exchanges without full sandbox support use production URL
        Exchange.KRAKEN to "https://api.kraken.com",
        Exchange.BITFINEX to "https://api.bitfinex.com",
        Exchange.HUOBI to "https://api.huobi.pro",
        Exchange.COINMATE to "https://coinmate.io/api"
    )

    /**
     * Get the base URL for an exchange based on sandbox mode.
     *
     * @param exchange The exchange to get URL for
     * @param isSandbox Whether sandbox/testnet mode is enabled
     * @return The appropriate base URL
     */
    fun getBaseUrl(exchange: Exchange, isSandbox: Boolean): String {
        return if (isSandbox) {
            sandboxUrls[exchange] ?: productionUrls[exchange]
                ?: throw IllegalArgumentException("Unknown exchange: $exchange")
        } else {
            productionUrls[exchange]
                ?: throw IllegalArgumentException("Unknown exchange: $exchange")
        }
    }

    /**
     * Get registration URL for sandbox environment.
     * Returns null if exchange doesn't have a separate sandbox registration.
     */
    fun getSandboxRegistrationUrl(exchange: Exchange): String? {
        return when (exchange) {
            Exchange.BINANCE -> "https://testnet.binance.vision/"
            Exchange.KUCOIN -> "https://sandbox.kucoin.com/"
            Exchange.COINBASE -> "https://docs.cdp.coinbase.com/exchange/docs/sandbox"
            else -> null
        }
    }
}
