package com.accbot.dca.testing

import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.exchange.ExchangeApi
import com.accbot.dca.exchange.OrderStatusResult
import java.math.BigDecimal
import java.time.Instant

/**
 * Configurable fake [ExchangeApi] for JVM unit tests.
 *
 * Each behaviour is a swappable lambda so tests can simulate timeouts (throw),
 * successful buys, and trade-history reconciliation scenarios deterministically.
 */
class FakeExchangeApi(
    override val exchange: Exchange = Exchange.COINMATE,
    override val supportsLimitSell: Boolean = true,
    var marketBuyHandler: suspend (crypto: String, fiat: String, fiatAmount: BigDecimal) -> DcaResult =
        { _, _, _ -> DcaResult.Error("not configured", retryable = false) },
    var tradeHistoryHandler: suspend (crypto: String, fiat: String, since: Instant?, limit: Int) -> TradeHistoryPage =
        { _, _, _, _ -> TradeHistoryPage(emptyList(), hasMore = false) },
    var limitSellHandler: suspend (crypto: String, fiat: String, cryptoAmount: BigDecimal, limitPrice: BigDecimal) -> DcaResult =
        { _, _, _, _ -> DcaResult.Error("not configured", retryable = false) },
    var balance: BigDecimal? = BigDecimal("1000"),
    var price: BigDecimal? = BigDecimal("1500000")
) : ExchangeApi {

    /** Number of times [marketBuy] was invoked - lets tests assert "no duplicate order". */
    var marketBuyCallCount: Int = 0
        private set

    /** Number of times [limitSell] was invoked. */
    var limitSellCallCount: Int = 0
        private set

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult {
        marketBuyCallCount++
        return marketBuyHandler(crypto, fiat, fiatAmount)
    }

    override suspend fun limitSell(
        crypto: String,
        fiat: String,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal
    ): DcaResult {
        limitSellCallCount++
        return limitSellHandler(crypto, fiat, cryptoAmount, limitPrice)
    }

    override suspend fun getTradeHistory(
        crypto: String,
        fiat: String,
        sinceTimestamp: Instant?,
        limit: Int
    ): TradeHistoryPage = tradeHistoryHandler(crypto, fiat, sinceTimestamp, limit)

    override suspend fun getBalance(currency: String): BigDecimal? = balance
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = price
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.success("fake-withdrawal")
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = BigDecimal.ZERO
    override suspend fun validateCredentials(): Boolean = true
    override suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = null
}
