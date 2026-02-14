package com.accbot.dca.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching market data from public APIs
 * Used for DCA strategy calculations
 */
@Singleton
class MarketDataService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private data class PriceCache(
        val price: BigDecimal,
        val fetchedAt: Long
    )

    private val priceCache = ConcurrentHashMap<String, PriceCache>()
    private val fetchMutexes = ConcurrentHashMap<String, Mutex>()
    private val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    companion object {
        private const val TAG = "MarketDataService"

        // CoinGecko API (free, no auth required)
        private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3"

        // CryptoCompare API (free, no auth required, full historical data)
        private const val CRYPTOCOMPARE_BASE_URL = "https://min-api.cryptocompare.com/data/v2"

        // Alternative.me Fear & Greed API (free, no auth required)
        private const val FEAR_GREED_URL = "https://api.alternative.me/fng/"

        // Crypto ID mapping for CoinGecko
        private val CRYPTO_IDS = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "SOL" to "solana",
            "ADA" to "cardano",
            "DOT" to "polkadot",
            "LTC" to "litecoin",
            "XRP" to "ripple",
            "DOGE" to "dogecoin"
        )

        // Fiat currency mapping for CoinGecko
        private val FIAT_IDS = mapOf(
            "USD" to "usd",
            "EUR" to "eur",
            "GBP" to "gbp",
            "CZK" to "czk",
            "USDT" to "usd"  // Treat USDT as USD
        )
    }

    /**
     * Get current price and ATH for a cryptocurrency
     */
    suspend fun getCryptoData(crypto: String, fiat: String): CryptoData? = withContext(Dispatchers.IO) {
        val cryptoId = CRYPTO_IDS[crypto.uppercase()] ?: run {
            Log.w(TAG, "Unknown crypto: $crypto")
            return@withContext null
        }
        val fiatId = FIAT_IDS[fiat.uppercase()] ?: "usd"

        try {
            val url = "$COINGECKO_BASE_URL/coins/$cryptoId?localization=false&tickers=false&community_data=false&developer_data=false"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "CoinGecko API error: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val coinData = gson.fromJson(body, CoinGeckoResponse::class.java)

                val currentPrice = coinData.marketData?.currentPrice?.get(fiatId)
                val ath = coinData.marketData?.ath?.get(fiatId)

                if (currentPrice != null && ath != null) {
                    CryptoData(
                        crypto = crypto,
                        fiat = fiat,
                        currentPrice = BigDecimal(currentPrice.toString()),
                        allTimeHigh = BigDecimal(ath.toString()),
                        athDate = coinData.marketData.athDate?.get(fiatId)
                    )
                } else {
                    Log.w(TAG, "Missing price data for $crypto/$fiat")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching crypto data", e)
            null
        }
    }

    /**
     * Get cached current price for a crypto/fiat pair.
     * Returns from 1-hour in-memory cache if available, otherwise fetches fresh.
     * Uses per-key Mutex to prevent cache stampede (duplicate concurrent API calls).
     */
    suspend fun getCachedPrice(crypto: String, fiat: String): BigDecimal? {
        val key = "${crypto}_${fiat}"
        val cached = priceCache[key]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
            return cached.price
        }
        val mutex = fetchMutexes.getOrPut(key) { Mutex() }
        return mutex.withLock {
            // Double-check after acquiring lock
            val rechecked = priceCache[key]
            if (rechecked != null && System.currentTimeMillis() - rechecked.fetchedAt < CACHE_TTL_MS) {
                return@withLock rechecked.price
            }
            val data = getCryptoData(crypto, fiat)
            if (data != null) {
                priceCache[key] = PriceCache(data.currentPrice, System.currentTimeMillis())
            }
            data?.currentPrice
        }
    }

    fun invalidateCache() {
        priceCache.clear()
        fetchMutexes.clear()
    }

    /**
     * Get daily price history from CoinGecko.
     * Returns list of (LocalDate, BigDecimal) pairs ordered by date ascending.
     * Uses /coins/{id}/market_chart endpoint with daily interval.
     */
    suspend fun getDailyPriceHistory(
        crypto: String,
        fiat: String,
        days: Int
    ): List<Pair<LocalDate, BigDecimal>>? = withContext(Dispatchers.IO) {
        val cryptoId = CRYPTO_IDS[crypto.uppercase()] ?: run {
            Log.w(TAG, "Unknown crypto for history: $crypto")
            return@withContext null
        }
        val fiatId = FIAT_IDS[fiat.uppercase()] ?: "usd"

        try {
            val url = "$COINGECKO_BASE_URL/coins/$cryptoId/market_chart?vs_currency=$fiatId&days=$days"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "CoinGecko market_chart error: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val chartResponse = gson.fromJson(body, MarketChartResponse::class.java)

                chartResponse.prices?.mapNotNull { point ->
                    if (point.size >= 2) {
                        val timestampMs = point[0].toLong()
                        val price = BigDecimal(point[1].toString())
                        val date = Instant.ofEpochMilli(timestampMs)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        date to price
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching price history for $crypto/$fiat", e)
            null
        }
    }

    /**
     * Get daily price history for a date range using CryptoCompare histoday endpoint.
     * Free tier supports full historical data (up to 2000 data points per call).
     * Used for historical backfill — fetches data ending at [toDate] going back [limit] days.
     * Returns list of (LocalDate, BigDecimal) pairs ordered by date ascending.
     */
    suspend fun getDailyPriceHistoryRange(
        crypto: String,
        fiat: String,
        toDate: LocalDate,
        limit: Int
    ): List<Pair<LocalDate, BigDecimal>>? = withContext(Dispatchers.IO) {
        val fsym = crypto.uppercase()
        val tsym = if (fiat.uppercase() == "USDT") "USD" else fiat.uppercase()

        try {
            val toUnix = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

            val url = "$CRYPTOCOMPARE_BASE_URL/histoday?fsym=$fsym&tsym=$tsym&limit=$limit&toTs=$toUnix"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "CryptoCompare histoday error: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val ccResponse = gson.fromJson(body, CryptoCompareHistodayResponse::class.java)

                if (ccResponse.response != "Success") {
                    Log.e(TAG, "CryptoCompare error: ${ccResponse.message}")
                    return@withContext null
                }

                ccResponse.data?.data?.mapNotNull { point ->
                    val close = point.close ?: return@mapNotNull null
                    if (close <= 0.0) return@mapNotNull null
                    val date = Instant.ofEpochSecond(point.time)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    date to BigDecimal(close.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching CryptoCompare history for $crypto/$fiat (to=$toDate, limit=$limit)", e)
            null
        }
    }

    /**
     * Get current Fear & Greed Index
     * Returns value 0-100 (0 = Extreme Fear, 100 = Extreme Greed)
     */
    suspend fun getFearGreedIndex(): FearGreedData? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(FEAR_GREED_URL)
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Fear & Greed API error: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val fngResponse = gson.fromJson(body, FearGreedResponse::class.java)

                val latestData = fngResponse.data?.firstOrNull()
                if (latestData != null) {
                    FearGreedData(
                        value = latestData.value.toIntOrNull() ?: 50,
                        classification = latestData.valueClassification ?: "Neutral",
                        timestamp = latestData.timestamp?.toLongOrNull() ?: System.currentTimeMillis() / 1000
                    )
                } else {
                    Log.w(TAG, "No Fear & Greed data available")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Fear & Greed index", e)
            null
        }
    }

    /**
     * Calculate distance from ATH as percentage (0.0 to 1.0)
     */
    fun calculateAthDistance(currentPrice: BigDecimal, ath: BigDecimal): Float {
        if (ath <= BigDecimal.ZERO) return 0f
        val distance = (ath - currentPrice) / ath
        return distance.toFloat().coerceIn(0f, 1f)
    }
}

// Response models

data class CryptoData(
    val crypto: String,
    val fiat: String,
    val currentPrice: BigDecimal,
    val allTimeHigh: BigDecimal,
    val athDate: String? = null
) {
    val athDistance: Float
        get() = if (allTimeHigh > BigDecimal.ZERO) {
            ((allTimeHigh - currentPrice) / allTimeHigh).toFloat().coerceIn(0f, 1f)
        } else 0f

    val athDistancePercent: Int
        get() = (athDistance * 100).toInt()
}

data class FearGreedData(
    val value: Int,           // 0-100
    val classification: String, // "Extreme Fear", "Fear", "Neutral", "Greed", "Extreme Greed"
    val timestamp: Long
)

// CoinGecko API response models

data class CoinGeckoResponse(
    @SerializedName("market_data")
    val marketData: MarketDataResponse?
)

data class MarketDataResponse(
    @SerializedName("current_price")
    val currentPrice: Map<String, Double>?,
    @SerializedName("ath")
    val ath: Map<String, Double>?,
    @SerializedName("ath_date")
    val athDate: Map<String, String>?
)

// Fear & Greed API response models

data class FearGreedResponse(
    val data: List<FearGreedItem>?
)

data class FearGreedItem(
    val value: String,
    @SerializedName("value_classification")
    val valueClassification: String?,
    val timestamp: String?
)

// CoinGecko market_chart response
data class MarketChartResponse(
    val prices: List<List<Double>>?
)

// CryptoCompare histoday response
data class CryptoCompareHistodayResponse(
    @SerializedName("Response")
    val response: String?,
    @SerializedName("Message")
    val message: String?,
    @SerializedName("Data")
    val data: CryptoCompareHistodayData?
)

data class CryptoCompareHistodayData(
    @SerializedName("Data")
    val data: List<CryptoCompareHistodayPoint>?
)

data class CryptoCompareHistodayPoint(
    val time: Long,
    val close: Double?
)
