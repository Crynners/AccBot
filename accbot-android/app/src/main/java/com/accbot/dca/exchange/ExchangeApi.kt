package com.accbot.dca.exchange

import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TradeHistoryPage
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Common interface for all exchange API implementations
 * Allows AccBot to work with multiple exchanges through unified interface
 */
interface ExchangeApi {
    val exchange: Exchange

    /**
     * Execute a market buy order
     * @param crypto Cryptocurrency symbol (e.g., "BTC")
     * @param fiat Fiat currency (e.g., "EUR")
     * @param fiatAmount Amount in fiat to spend
     * @return DcaResult with transaction details or error
     */
    suspend fun marketBuy(
        crypto: String,
        fiat: String,
        fiatAmount: BigDecimal
    ): DcaResult

    /**
     * Get current balance for a currency
     */
    suspend fun getBalance(currency: String): BigDecimal?

    /**
     * Get current market price
     */
    suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal?

    /**
     * Withdraw crypto to external wallet
     */
    suspend fun withdraw(
        crypto: String,
        amount: BigDecimal,
        address: String
    ): Result<String> // Returns withdrawal ID or error

    /**
     * Get withdrawal fee
     */
    suspend fun getWithdrawalFee(crypto: String): BigDecimal?

    /**
     * Validate credentials (test API connection)
     */
    suspend fun validateCredentials(): Boolean

    /**
     * Get trade history for a currency pair.
     * Not all exchanges support this - default throws UnsupportedOperationException.
     * @param crypto Cryptocurrency symbol (e.g., "BTC")
     * @param fiat Fiat currency (e.g., "EUR")
     * @param sinceTimestamp Only return trades after this timestamp (for incremental import)
     * @param limit Maximum number of trades per page
     * @return A page of historical trades
     */
    suspend fun getTradeHistory(
        crypto: String,
        fiat: String,
        sinceTimestamp: Instant? = null,
        limit: Int = 100
    ): TradeHistoryPage = throw UnsupportedOperationException(
        "${exchange.displayName} does not support API trade history import"
    )
}

/**
 * Factory for creating exchange API instances.
 * Automatically configures APIs for sandbox or production mode based on user preferences.
 */
@Singleton
class ExchangeApiFactory @Inject constructor(
    private val userPreferences: UserPreferences,
    private val okHttpClient: OkHttpClient
) {
    /**
     * Create an exchange API instance.
     * @param credentials The credentials for the exchange
     * @param isSandbox Override sandbox mode (if null, uses user preference)
     */
    fun create(credentials: ExchangeCredentials, isSandbox: Boolean? = null): ExchangeApi {
        val sandboxMode = isSandbox ?: userPreferences.isSandboxMode()
        return when (credentials.exchange) {
            Exchange.COINMATE -> CoinmateApi(credentials, sandboxMode, okHttpClient)
            Exchange.BINANCE -> BinanceApi(credentials, sandboxMode, okHttpClient)
            Exchange.KRAKEN -> KrakenApi(credentials, sandboxMode, okHttpClient)
            Exchange.KUCOIN -> KuCoinApi(credentials, sandboxMode, okHttpClient)
            Exchange.BITFINEX -> BitfinexApi(credentials, sandboxMode)
            Exchange.HUOBI -> HuobiApi(credentials, sandboxMode)
            Exchange.COINBASE -> CoinbaseApi(credentials, sandboxMode)
        }
    }
}
