package com.accbot.dca.exchange

import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TradeHistoryPage
import com.accbot.dca.domain.model.supportsApiImport
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/**
 * The only exchange access used for plans shared with this user.
 *
 * It wraps an [ExchangeApi] privately and exposes nothing but reads (key permissions,
 * trade history, current price), so shared credentials can never reach `marketBuy`,
 * `limitSell`, `cancelOrder` or `withdraw` through this code path. The real protection is
 * still the exchange-side permission of the key, which [verifyKey] checks where possible.
 */
class ReadOnlyExchangeClient private constructor(private val api: ExchangeApi) {

    enum class KeyCheck {
        /** The exchange confirmed the key cannot trade or move funds. */
        READ_ONLY,
        /** The exchange reports trading or withdrawal permission; the key must not be shared. */
        NOT_READ_ONLY,
        /** The exchange cannot report key permissions; the user has to confirm it. */
        UNVERIFIABLE
    }

    /** @throws Exception when the exchange supports the check but it failed. */
    suspend fun verifyKey(): KeyCheck {
        val permissions = api.getKeyPermissions() ?: return KeyCheck.UNVERIFIABLE
        return if (permissions.isReadOnly) KeyCheck.READ_ONLY else KeyCheck.NOT_READ_ONLY
    }

    /** @throws Exception when credentials are rejected or the network fails. */
    suspend fun validateCredentials(): Boolean = api.validateCredentials()

    suspend fun tradeHistory(crypto: String, fiat: String, since: Instant?, limit: Int = 100): TradeHistoryPage =
        api.getTradeHistory(crypto, fiat, since, limit)

    suspend fun currentPrice(crypto: String, fiat: String): BigDecimal? = api.getCurrentPrice(crypto, fiat)

    /** [createApi] is replaceable only for tests; production always uses [ExchangeApiFactory]. */
    class Factory internal constructor(private val createApi: (ExchangeCredentials, Boolean) -> ExchangeApi) {
        @Inject constructor(exchangeApiFactory: ExchangeApiFactory) : this(
            { credentials, isSandbox -> exchangeApiFactory.create(credentials, isSandbox) }
        )

        /** Null when the exchange has no trade history API, so live updates are impossible. */
        fun create(credentials: ExchangeCredentials, isSandbox: Boolean): ReadOnlyExchangeClient? {
            if (!credentials.exchange.supportsApiImport) return null
            return ReadOnlyExchangeClient(createApi(credentials, isSandbox))
        }
    }
}
