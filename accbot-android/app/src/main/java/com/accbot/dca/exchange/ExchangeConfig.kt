package com.accbot.dca.exchange

import com.accbot.dca.domain.model.Exchange

/**
 * Exchange URL configuration for production and sandbox environments.
 *
 * Binance provides the spot testnet supported by AccBot's trading flow.
 * KuCoin retired its sandbox and Coinbase Advanced Trade offers static mocks only.
 */
object ExchangeConfig {

    private val productionUrls = mapOf(
        Exchange.BINANCE to "https://api.binance.com",
        Exchange.KUCOIN to "https://api.kucoin.com",
        Exchange.COINBASE to "https://api.coinbase.com",
        Exchange.KRAKEN to "https://api.kraken.com",
        Exchange.BITFINEX to "https://api.bitfinex.com",
        Exchange.HUOBI to "https://api.huobi.pro",
        Exchange.COINMATE to "https://coinmate.io/api"
    )

    private val sandboxUrls = mapOf(
        Exchange.BINANCE to "https://testnet.binance.vision"
    )

    fun hasSandboxEndpoint(exchange: Exchange): Boolean = exchange in sandboxUrls

    /**
     * Get the base URL for an exchange based on sandbox mode.
     *
     * @param exchange The exchange to get URL for
     * @param isSandbox Whether sandbox/testnet mode is enabled
     * @return The appropriate base URL
     */
    fun getBaseUrl(exchange: Exchange, isSandbox: Boolean): String {
        return if (isSandbox) {
            requireNotNull(sandboxUrls[exchange]) { "${exchange.displayName} has no spot sandbox endpoint" }
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
            else -> null
        }
    }
}
