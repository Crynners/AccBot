package com.accbot.dca.exchange

import android.util.Log
import com.accbot.dca.domain.model.Exchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching minimum order sizes from exchange APIs.
 * Caches results for 1 hour, falls back to hardcoded Exchange.minOrderSize on failure.
 */
@Singleton
class MinOrderSizeRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private data class CachedValue(val minOrderSize: BigDecimal, val timestamp: Long)

    private val cache = ConcurrentHashMap<String, CachedValue>()
    private val cacheTtlMs = 3_600_000L // 1 hour

    /**
     * Get the minimum order size in fiat for a given exchange/crypto/fiat combination.
     * Returns the API-fetched value (cached), or falls back to Exchange.minOrderSize.
     */
    suspend fun getMinOrderSize(exchange: Exchange, crypto: String, fiat: String): BigDecimal {
        val cacheKey = "${exchange}_${crypto}_${fiat}"
        val now = System.currentTimeMillis()

        // Check cache
        cache[cacheKey]?.let { cached ->
            if (now - cached.timestamp < cacheTtlMs) {
                return cached.minOrderSize
            }
        }

        // Fetch from API
        val fetched = try {
            when (exchange) {
                Exchange.COINMATE -> fetchCoinmateMinOrder(crypto, fiat)
                Exchange.BINANCE -> fetchBinanceMinOrder(crypto, fiat)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch min order size for $cacheKey", e)
            null
        }

        val result = fetched ?: exchange.minOrderSize[fiat] ?: BigDecimal.ONE

        // Cache the result
        cache[cacheKey] = CachedValue(result, now)

        return result
    }

    /**
     * Coinmate: GET /tradingPairs → minAmount (crypto), then multiply by current price.
     */
    private suspend fun fetchCoinmateMinOrder(crypto: String, fiat: String): BigDecimal? =
        withContext(Dispatchers.IO) {
            val baseUrl = ExchangeConfig.getBaseUrl(Exchange.COINMATE, false)
            val pair = "${crypto}_${fiat}"

            // 1. Get minAmount from trading pairs
            val pairsRequest = Request.Builder()
                .url("$baseUrl/tradingPairs")
                .get()
                .build()

            val pairsResponse = okHttpClient.newCall(pairsRequest).execute()
            if (!pairsResponse.isSuccessful) return@withContext null

            val pairsBody = pairsResponse.body.string()
            val pairsJson = JSONObject(pairsBody)
            val pairsData = pairsJson.optJSONArray("data") ?: return@withContext null

            var minAmount: BigDecimal? = null
            for (i in 0 until pairsData.length()) {
                val pairObj = pairsData.getJSONObject(i)
                if (pairObj.getString("name") == pair) {
                    minAmount = BigDecimal(pairObj.getString("minAmount"))
                    break
                }
            }
            if (minAmount == null) return@withContext null

            // 2. Get current price from ticker
            val tickerRequest = Request.Builder()
                .url("$baseUrl/ticker?currencyPair=$pair")
                .get()
                .build()

            val tickerResponse = okHttpClient.newCall(tickerRequest).execute()
            if (!tickerResponse.isSuccessful) return@withContext null

            val tickerBody = tickerResponse.body.string()
            val tickerJson = JSONObject(tickerBody)
            val tickerData = tickerJson.optJSONObject("data") ?: return@withContext null
            val lastPrice = BigDecimal(tickerData.getString("last"))

            // 3. minAmount (crypto) × price = minimum fiat amount, rounded up
            val minFiat = minAmount.multiply(lastPrice).setScale(2, RoundingMode.UP)
            // buyInstant enforces a separate minimum fiat total, not exposed via API.
            val instantBuyFloor = COINMATE_INSTANT_BUY_MIN[fiat]
            val result = if (instantBuyFloor != null) maxOf(minFiat, instantBuyFloor) else minFiat
            Log.d(TAG, "Coinmate $pair: minAmount=$minAmount, price=$lastPrice, minFiat=$minFiat, floor=$instantBuyFloor, result=$result")
            result
        }

    /**
     * Binance: GET /api/v3/exchangeInfo → NOTIONAL filter → minNotional (fiat).
     */
    private suspend fun fetchBinanceMinOrder(crypto: String, fiat: String): BigDecimal? =
        withContext(Dispatchers.IO) {
            val baseUrl = ExchangeConfig.getBaseUrl(Exchange.BINANCE, false)
            val symbol = "${crypto}${fiat}" // Binance uses no underscore

            val request = Request.Builder()
                .url("$baseUrl/api/v3/exchangeInfo?symbol=$symbol")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body.string()
            val json = JSONObject(body)
            val symbols = json.optJSONArray("symbols")
            if (symbols == null || symbols.length() == 0) return@withContext null

            val symbolObj = symbols.getJSONObject(0)
            val filters = symbolObj.getJSONArray("filters")

            for (i in 0 until filters.length()) {
                val filter = filters.getJSONObject(i)
                if (filter.getString("filterType") == "NOTIONAL") {
                    val minNotional = BigDecimal(filter.getString("minNotional"))
                    Log.d(TAG, "Binance $symbol: minNotional=$minNotional")
                    return@withContext minNotional
                }
            }

            null
        }

    companion object {
        private const val TAG = "MinOrderSizeRepo"

        // Coinmate buyInstant minimum fiat total (not exposed via /tradingPairs API).
        // Only CZK is confirmed; EUR is left to the dynamic calculation.
        private val COINMATE_INSTANT_BUY_MIN = mapOf(
            "CZK" to BigDecimal("50")
        )
    }
}
