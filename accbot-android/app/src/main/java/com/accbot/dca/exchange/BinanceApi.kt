package com.accbot.dca.exchange

import com.accbot.dca.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Binance API implementation
 * World's largest crypto exchange
 *
 * Supports sandbox mode via testnet.binance.vision
 */
class BinanceApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false,
    private val client: OkHttpClient
) : ExchangeApi {

    override val exchange = Exchange.BINANCE

    override val supportsLimitSell: Boolean = true

    override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.001")

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.BINANCE, isSandbox)

    /** Offset in ms: serverTime - localTime. Add to System.currentTimeMillis() to get server time. */
    @Volatile
    private var timeOffset: Long = 0

    @Volatile
    private var timeSynced: Boolean = false

    /**
     * Ensure time is synced once per instance lifetime.
     * Called before every signed API request.
     */
    private val timeSyncMutex = Mutex()

    private suspend fun ensureTimeSynced() {
        if (!timeSynced) {
            timeSyncMutex.withLock {
                if (!timeSynced) {
                    syncServerTime()
                    if (timeOffset == 0L) {
                        // Retry once if initial sync failed
                        delay(500)
                        syncServerTime()
                    }
                    timeSynced = true
                }
            }
        }
    }

    /**
     * Sync local clock with Binance server time.
     * Binance rejects timestamps >1s ahead of server time regardless of recvWindow.
     */
    private fun syncServerTime() {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/v3/time")
                .get()
                .build()

            val localBefore = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                val localAfter = System.currentTimeMillis()
                val body = response.body?.string() ?: return

                val json = JSONObject(body)
                val serverTime = json.getLong("serverTime")
                val localTime = (localBefore + localAfter) / 2
                timeOffset = serverTime - localTime
                android.util.Log.d("BinanceApi", "Time synced: offset=${timeOffset}ms, url=$baseUrl")
            }
        } catch (e: Exception) {
            android.util.Log.w("BinanceApi", "Time sync failed: ${e.message}")
        }
    }

    /** Get current timestamp adjusted for server time. */
    private fun serverTimestamp(): Long = System.currentTimeMillis() + timeOffset

    override suspend fun marketBuy(
        crypto: String,
        fiat: String,
        fiatAmount: BigDecimal
    ): DcaResult = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val symbol = "$crypto$fiat"
            val timestamp = serverTimestamp()

            val params = buildString {
                append("symbol=$symbol")
                append("&side=BUY")
                append("&type=MARKET")
                append("&quoteOrderQty=${fiatAmount.setScale(2, RoundingMode.DOWN).toPlainString()}")
                append("&timestamp=$timestamp")
                append("&recvWindow=60000")
            }

            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)
            val signedParams = "$params&signature=$signature"

            val request = Request.Builder()
                .url("$baseUrl/api/v3/order?$signedParams")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .post("".toRequestBody())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val isRetryable = response.code in 500..599 || response.code == 429
                    val error = runCatching { JSONObject(body) }.getOrNull()
                    val confirmedRejection = response.code in 400..499 &&
                        error?.optInt("code")?.let { it in setOf(-1013, -1021, -1022, -1121, -2010, -2014, -2015) } == true
                    return@withContext DcaResult.Error(
                        error?.optString("msg") ?: "HTTP ${response.code}",
                        retryable = isRetryable,
                        confirmedRejection = confirmedRejection
                    )
                }

                val json = JSONObject(body)

                if (json.has("code")) {
                    val errorMessage = json.optString("msg", "Unknown error")
                    return@withContext DcaResult.Error(errorMessage, retryable = false)
                }

                val executedQty = BigDecimal(json.getString("executedQty"))
                val cummulativeQuoteQty = BigDecimal(json.getString("cummulativeQuoteQty"))
                val orderId = json.optString("orderId").takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
                    ?: return@withContext DcaResult.Error("Missing order ID in Binance purchase response")
                val filled = executedQty.signum() > 0 && cummulativeQuoteQty.signum() > 0
                val status = when (json.optString("status")) {
                    "FILLED" -> if (filled) TransactionStatus.COMPLETED else TransactionStatus.PENDING
                    "PARTIALLY_FILLED" -> if (filled) TransactionStatus.PARTIAL else TransactionStatus.PENDING
                    "CANCELED", "EXPIRED", "EXPIRED_IN_MATCH", "REJECTED" ->
                        if (filled) TransactionStatus.PARTIAL_CANCELLED else TransactionStatus.CANCELLED
                    else -> TransactionStatus.PENDING
                }
                val avgPrice = if (executedQty > BigDecimal.ZERO) {
                    cummulativeQuoteQty.divide(executedQty, 8, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // Calculate fee from fills
                var totalFee = BigDecimal.ZERO
                var feeAsset = ""
                val fills = json.optJSONArray("fills")
                if (fills != null) {
                    for (i in 0 until fills.length()) {
                        val fill = fills.getJSONObject(i)
                        totalFee += BigDecimal(fill.getString("commission"))
                        if (feeAsset.isEmpty()) {
                            feeAsset = fill.optString("commissionAsset", "")
                        }
                    }
                }

                DcaResult.Success(
                    Transaction(
                        planId = 0,
                        exchange = Exchange.BINANCE,
                        crypto = crypto,
                        fiat = fiat,
                        fiatAmount = cummulativeQuoteQty,
                        cryptoAmount = executedQty,
                        price = avgPrice,
                        fee = totalFee,
                        feeAsset = feeAsset,
                        status = status,
                        exchangeOrderId = orderId,
                        executedAt = Instant.now()
                    )
                )
            }
        } catch (e: java.io.IOException) {
            DcaResult.Error(e.message ?: "Network error", retryable = true)
        } catch (e: Exception) {
            DcaResult.Error(e.message ?: "Unknown error", retryable = false)
        }
    }

    override suspend fun limitSell(
        crypto: String,
        fiat: String,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal
    ): DcaResult = withContext(Dispatchers.IO) {
        try {
            val roundedAmount = cryptoAmount.setScale(8, RoundingMode.DOWN)
            val roundedPrice = limitPrice.setScale(2, RoundingMode.HALF_UP)
            if (roundedAmount <= BigDecimal.ZERO || roundedPrice <= BigDecimal.ZERO) {
                return@withContext DcaResult.Error(
                    "Invalid limit sell amount or price",
                    retryable = false,
                    confirmedRejection = true
                )
            }

            ensureTimeSynced()
            val symbol = "$crypto$fiat"
            val timestamp = serverTimestamp()

            val params = buildString {
                append("symbol=$symbol")
                append("&side=SELL")
                append("&type=LIMIT")
                append("&timeInForce=GTC")
                append("&quantity=${roundedAmount.toPlainString()}")
                append("&price=${roundedPrice.toPlainString()}")
                append("&timestamp=$timestamp")
                append("&recvWindow=60000")
            }

            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)
            val signedParams = "$params&signature=$signature"

            val request = Request.Builder()
                .url("$baseUrl/api/v3/order?$signedParams")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .post("".toRequestBody())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val isRetryable = response.code in 500..599 || response.code == 429 || response.code == 408
                    val error = runCatching { JSONObject(body) }.getOrNull()
                    val confirmedRejection = response.code in 400..499 &&
                        error?.optInt("code") in setOf(-1013, -1021, -1022, -1121, -2010, -2014, -2015)
                    return@withContext DcaResult.Error(
                        error?.optString("msg")?.ifBlank { null } ?: "HTTP ${response.code}",
                        retryable = isRetryable,
                        confirmedRejection = confirmedRejection
                    )
                }

                val json = JSONObject(body)

                if (json.has("code")) {
                    val errorMessage = json.optString("msg", "Unknown error")
                    return@withContext DcaResult.Error(
                        errorMessage,
                        retryable = false,
                        confirmedRejection = true
                    )
                }

                // Binance returns orderId as a Long; convert to String for our domain
                val orderId = json.get("orderId").toString()

                DcaResult.Success(
                    Transaction(
                        planId = 0, // caller fills in
                        connectionId = null, // caller fills in
                        exchange = Exchange.BINANCE,
                        crypto = crypto,
                        fiat = fiat,
                        fiatAmount = BigDecimal.ZERO,
                        cryptoAmount = BigDecimal.ZERO,
                        price = roundedPrice,
                        fee = BigDecimal.ZERO,
                        feeAsset = "",
                        status = TransactionStatus.PENDING,
                        exchangeOrderId = orderId,
                        executedAt = Instant.now(),
                        side = TransactionSide.SELL,
                        limitPrice = roundedPrice,
                        requestedCryptoAmount = roundedAmount
                    )
                )
            }
        } catch (e: java.io.IOException) {
            DcaResult.Error(e.message ?: "Network error", retryable = true, confirmedRejection = false)
        } catch (e: Exception) {
            DcaResult.Error(e.message ?: "Unknown error", retryable = false, confirmedRejection = false)
        }
    }

    override suspend fun cancelOrder(
        orderId: String,
        crypto: String,
        fiat: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val symbol = "$crypto$fiat"
            val timestamp = serverTimestamp()

            val params = buildString {
                append("symbol=$symbol")
                append("&orderId=$orderId")
                append("&timestamp=$timestamp")
                append("&recvWindow=60000")
            }

            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)
            val signedParams = "$params&signature=$signature"

            val request = Request.Builder()
                .url("$baseUrl/api/v3/order?$signedParams")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: return@withContext Result.failure(java.io.IOException("Empty response"))

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(body).optString("msg", "HTTP ${response.code}")
                    } catch (_: Exception) { "HTTP ${response.code}" }
                    return@withContext Result.failure(java.io.IOException(errorMsg))
                }

                val json = JSONObject(body)
                if (json.has("code")) {
                    val errorMessage = json.optString("msg", "Cancel failed")
                    return@withContext Result.failure(java.io.IOException(errorMessage))
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getOrderStatus(
        orderId: String,
        crypto: String,
        fiat: String
    ): OrderStatusResult? = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val symbol = "$crypto$fiat"
            val timestamp = serverTimestamp()

            val params = buildString {
                append("symbol=$symbol")
                append("&orderId=$orderId")
                append("&timestamp=$timestamp")
                append("&recvWindow=60000")
            }

            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)

            val request = Request.Builder()
                .url("$baseUrl/api/v3/order?$params&signature=$signature")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                parseOrderStatusJson(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getBalance(currency: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val timestamp = serverTimestamp()
            val params = "timestamp=$timestamp&recvWindow=60000"
            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)

            val request = Request.Builder()
                .url("$baseUrl/api/v3/account?$params&signature=$signature")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(body)

                if (json.has("code")) return@withContext null

                val balances = json.getJSONArray("balances")
                for (i in 0 until balances.length()) {
                    val balance = balances.getJSONObject(i)
                    if (balance.getString("asset") == currency) {
                        return@withContext BigDecimal(balance.getString("free"))
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val symbol = "$crypto$fiat"
            val request = Request.Builder()
                .url("$baseUrl/api/v3/ticker/price?symbol=$symbol")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(body)

                BigDecimal(json.getString("price"))
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun withdraw(
        crypto: String,
        amount: BigDecimal,
        address: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val timestamp = serverTimestamp()
            val params = buildString {
                append("coin=$crypto")
                append("&address=$address")
                append("&amount=${amount.toPlainString()}")
                append("&timestamp=$timestamp")
                append("&recvWindow=60000")
            }

            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)

            val request = Request.Builder()
                .url("$baseUrl/sapi/v1/capital/withdraw/apply?$params&signature=$signature")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .post("".toRequestBody())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(body).optString("msg", "HTTP ${response.code}")
                    } catch (_: Exception) { "HTTP ${response.code}" }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val json = JSONObject(body)

                if (json.has("code")) {
                    val errorMessage = json.optString("msg", "Withdrawal failed")
                    return@withContext Result.failure(Exception(errorMessage))
                }

                Result.success(json.optString("id", ""))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val timestamp = serverTimestamp()
            val params = "timestamp=$timestamp&recvWindow=60000"
            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)

            val request = Request.Builder()
                .url("$baseUrl/sapi/v1/capital/config/getall?$params&signature=$signature")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val json = org.json.JSONArray(body)

                for (i in 0 until json.length()) {
                    val coin = json.getJSONObject(i)
                    if (coin.getString("coin") == crypto) {
                        val networks = coin.getJSONArray("networkList")
                        if (networks.length() > 0) {
                            val network = networks.getJSONObject(0)
                            return@withContext BigDecimal(network.getString("withdrawFee"))
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getTradeHistory(
        crypto: String,
        fiat: String,
        sinceTimestamp: Instant?,
        limit: Int
    ): TradeHistoryPage = withContext(Dispatchers.IO) {
        ensureTimeSynced()
        val symbol = "$crypto$fiat"
        val timestamp = serverTimestamp()

        val params = buildString {
            append("symbol=$symbol")
            append("&limit=$limit")
            if (sinceTimestamp != null) {
                // Add 1ms to avoid re-fetching the exact same trade
                append("&startTime=${sinceTimestamp.toEpochMilli() + 1}")
            }
            append("&timestamp=$timestamp")
            append("&recvWindow=60000")
        }

        val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)

        val request = Request.Builder()
            .url("$baseUrl/api/v3/myTrades?$params&signature=$signature")
            .header("X-MBX-APIKEY", credentials.apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(body).optString("msg", "HTTP ${response.code}")
                } catch (_: Exception) { "HTTP ${response.code}" }
                throw Exception(errorMsg)
            }

            // Check for error response (JSON object with "code" field)
            if (body.trimStart().startsWith("{")) {
                val json = JSONObject(body)
                if (json.has("code")) {
                    throw Exception(json.optString("msg", "Failed to fetch trade history"))
                }
            }

            val jsonArray = org.json.JSONArray(body)
            val trades = mutableListOf<HistoricalTrade>()

            for (i in 0 until jsonArray.length()) {
                val trade = jsonArray.getJSONObject(i)
                val isBuyer = trade.optBoolean("isBuyer", false)
                val qty = BigDecimal(trade.getString("qty"))
                val price = BigDecimal(trade.getString("price"))
                val quoteQty = BigDecimal(trade.getString("quoteQty"))
                val commission = BigDecimal(trade.getString("commission"))
                val commissionAsset = trade.optString("commissionAsset", "")
                val tradeTime = trade.getLong("time")

                trades.add(
                    HistoricalTrade(
                        // Use trade id for dedup since one market order can produce multiple fills
                        orderId = trade.get("id").toString(),
                        timestamp = Instant.ofEpochMilli(tradeTime),
                        crypto = crypto,
                        fiat = fiat,
                        cryptoAmount = qty,
                        fiatAmount = quoteQty,
                        price = price,
                        fee = commission,
                        feeAsset = commissionAsset,
                        side = if (isBuyer) "BUY" else "SELL"
                    )
                )
            }

            TradeHistoryPage(
                trades = trades,
                hasMore = trades.size >= limit
            )
        }
    }

    override suspend fun validateCredentials(): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureTimeSynced()
            val timestamp = serverTimestamp()
            val params = "timestamp=$timestamp&recvWindow=60000"

            val signature = CryptoUtils.hmacSha256Hex(params, credentials.apiSecret)

            val request = Request.Builder()
                .url("$baseUrl/api/v3/account?$params&signature=$signature")
                .header("X-MBX-APIKEY", credentials.apiKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (body == null) {
                    android.util.Log.e("BinanceApi", "validateCredentials: Empty response")
                    return@withContext false
                }

                if (!response.isSuccessful) {
                    android.util.Log.e("BinanceApi", "validateCredentials: HTTP ${response.code}")
                    return@withContext false
                }

                val json = JSONObject(body)

                if (json.has("code")) {
                    val code = json.optInt("code")
                    val msg = json.optString("msg", "Unknown error")
                    android.util.Log.e("BinanceApi", "validateCredentials failed: code=$code, msg=$msg, url=$baseUrl")
                    throw Exception(msg)
                }

                // Success - account info returned
                android.util.Log.d("BinanceApi", "validateCredentials success, url=$baseUrl")
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("BinanceApi", "validateCredentials exception: ${e.message}, url=$baseUrl")
            throw e
        }
    }

    companion object {
        internal fun parseOrderStatusJson(body: String): OrderStatusResult? {
            return try {
                val json = JSONObject(body)
                if (json.has("code")) return null

                val status = json.optString("status", "")
                val executedQty = BigDecimal(json.optString("executedQty", "0"))
                val cummulativeQuoteQty = BigDecimal(json.optString("cummulativeQuoteQty", "0"))

                val avgFillPrice = if (executedQty > BigDecimal.ZERO) {
                    cummulativeQuoteQty.divide(executedQty, 8, RoundingMode.HALF_UP)
                } else null

                val mappedStatus = when (status.uppercase()) {
                    "FILLED" -> TransactionStatus.COMPLETED
                    "NEW", "PENDING_NEW", "PENDING_CANCEL" -> if (executedQty > BigDecimal.ZERO) {
                        TransactionStatus.PARTIAL
                    } else TransactionStatus.PENDING
                    "PARTIALLY_FILLED" -> TransactionStatus.PARTIAL
                    "CANCELED", "CANCELLED", "EXPIRED", "EXPIRED_IN_MATCH" ->
                        if (executedQty > BigDecimal.ZERO) TransactionStatus.PARTIAL_CANCELLED
                        else TransactionStatus.CANCELLED
                    "REJECTED" ->
                        if (executedQty > BigDecimal.ZERO) TransactionStatus.PARTIAL_CANCELLED
                        else TransactionStatus.FAILED
                    else -> return null
                }

                val identity = runCatching {
                    OrderIdentity(
                        id = json.getString("orderId"),
                        side = TransactionSide.valueOf(json.getString("side")),
                        pair = json.getString("symbol"),
                        createdAt = Instant.ofEpochMilli(json.getLong("time")),
                        amount = json.getString("origQty").toBigDecimal(),
                        limitPrice = json.getString("price").toBigDecimal()
                    )
                }.getOrNull()
                OrderStatusResult(
                    status = mappedStatus,
                    filledCryptoAmount = executedQty,
                    filledFiatAmount = cummulativeQuoteQty,
                    avgFillPrice = avgFillPrice,
                    // Binance fees are per-trade (need /api/v3/myTrades). MVP: leave null.
                    fee = null,
                    feeAsset = null,
                    identity = identity
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
