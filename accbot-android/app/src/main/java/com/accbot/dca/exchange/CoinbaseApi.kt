package com.accbot.dca.exchange

import com.accbot.dca.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Coinbase Advanced Trade API implementation
 *
 * Uses legacy API keys with HMAC-SHA256 authentication.
 * Production: https://api.coinbase.com
 * Sandbox: https://api-public.sandbox.exchange.coinbase.com
 *
 * Auth headers: CB-ACCESS-KEY, CB-ACCESS-SIGN, CB-ACCESS-TIMESTAMP, CB-ACCESS-PASSPHRASE
 * Prehash: timestamp + method + requestPath + body
 * Signature: HMAC-SHA256 with base64-decoded secret, result base64-encoded
 */
class CoinbaseApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false,
    private val client: OkHttpClient
) : ExchangeApi {

    override val exchange = Exchange.COINBASE

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.COINBASE, isSandbox)

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Create HMAC-SHA256 signature for Coinbase API authentication.
     * Prehash string: timestamp + method + requestPath + body
     */
    private fun sign(timestamp: String, method: String, path: String, body: String = ""): String {
        val prehash = "$timestamp$method$path$body"
        return CryptoUtils.hmacSha256Base64Secret(prehash, credentials.apiSecret)
    }

    /** Build an authenticated GET request */
    private fun buildGetRequest(path: String): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = sign(timestamp, "GET", path)

        return Request.Builder()
            .url("$baseUrl$path")
            .header("CB-ACCESS-KEY", credentials.apiKey)
            .header("CB-ACCESS-SIGN", signature)
            .header("CB-ACCESS-TIMESTAMP", timestamp)
            .header("CB-ACCESS-PASSPHRASE", credentials.passphrase ?: "")
            .get()
            .build()
    }

    /** Build an authenticated POST request */
    private fun buildPostRequest(path: String, body: String): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = sign(timestamp, "POST", path, body)

        return Request.Builder()
            .url("$baseUrl$path")
            .header("CB-ACCESS-KEY", credentials.apiKey)
            .header("CB-ACCESS-SIGN", signature)
            .header("CB-ACCESS-TIMESTAMP", timestamp)
            .header("CB-ACCESS-PASSPHRASE", credentials.passphrase ?: "")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    override suspend fun validateCredentials(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = buildGetRequest("/api/v3/brokerage/accounts")
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val msg = body?.let {
                    try { JSONObject(it).optString("message", "") .takeIf { s -> s.isNotEmpty() } } catch (_: Exception) { null }
                }
                throw Exception(msg ?: "HTTP ${response.code}")
            }
            true
        } catch (e: java.io.IOException) {
            throw Exception("Network error: ${e.message}")
        }
    }

    override suspend fun marketBuy(
        crypto: String,
        fiat: String,
        fiatAmount: BigDecimal
    ): DcaResult = withContext(Dispatchers.IO) {
        try {
            val productId = "$crypto-$fiat"
            val clientOrderId = java.util.UUID.randomUUID().toString()

            val body = JSONObject().apply {
                put("client_order_id", clientOrderId)
                put("product_id", productId)
                put("side", "BUY")
                put("order_configuration", JSONObject().apply {
                    put("market_market_ioc", JSONObject().apply {
                        put("quote_size", fiatAmount.setScale(2, RoundingMode.DOWN).toPlainString())
                    })
                })
            }.toString()

            val request = buildPostRequest("/api/v3/brokerage/orders", body)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                val isRetryable = response.code in 500..599 || response.code == 429
                val errorMsg = try {
                    JSONObject(responseBody).optString("message", "HTTP ${response.code}")
                } catch (_: Exception) { "HTTP ${response.code}" }
                return@withContext DcaResult.Error(errorMsg, retryable = isRetryable)
            }

            val json = JSONObject(responseBody)

            // Check for order placement failure
            val successResponse = json.optJSONObject("success_response")
            val errorResponse = json.optJSONObject("error_response")

            if (errorResponse != null) {
                val error = errorResponse.optString("error", "Order failed")
                val message = errorResponse.optString("message", error)
                return@withContext DcaResult.Error(message, retryable = false)
            }

            if (successResponse != null) {
                val orderId = successResponse.optString("order_id", "")

                // Try to get fill details by querying the order
                val fillDetails = queryOrderFill(orderId)
                if (fillDetails != null) {
                    return@withContext DcaResult.Success(
                        Transaction(
                            planId = 0,
                            exchange = Exchange.COINBASE,
                            crypto = crypto,
                            fiat = fiat,
                            fiatAmount = fillDetails.cost,
                            cryptoAmount = fillDetails.filledSize,
                            price = fillDetails.avgPrice,
                            fee = fillDetails.fee,
                            status = TransactionStatus.COMPLETED,
                            exchangeOrderId = orderId,
                            executedAt = Instant.now()
                        )
                    )
                }

                // Order placed but details not yet available
                return@withContext DcaResult.Success(
                    Transaction(
                        planId = 0,
                        exchange = Exchange.COINBASE,
                        crypto = crypto,
                        fiat = fiat,
                        fiatAmount = fiatAmount,
                        cryptoAmount = BigDecimal.ZERO,
                        price = BigDecimal.ZERO,
                        fee = BigDecimal.ZERO,
                        status = TransactionStatus.PENDING,
                        exchangeOrderId = orderId,
                        executedAt = Instant.now()
                    )
                )
            }

            DcaResult.Error("Unexpected response format", retryable = false)
        } catch (e: java.io.IOException) {
            DcaResult.Error(e.message ?: "Network error", retryable = true)
        } catch (e: Exception) {
            DcaResult.Error(e.message ?: "Unknown error", retryable = false)
        }
    }

    /** Query order to get fill details, retry up to 3 times */
    private suspend fun queryOrderFill(orderId: String): FillDetails? {
        repeat(3) { attempt ->
            try {
                if (attempt > 0) kotlinx.coroutines.delay(1000)

                val request = buildGetRequest("/api/v3/brokerage/orders/historical/$orderId")
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) return null

                val json = JSONObject(body)
                val order = json.optJSONObject("order") ?: return null
                val status = order.optString("status", "")

                if (status == "FILLED") {
                    val filledSize = BigDecimal(order.optString("filled_size", "0"))
                    val avgPrice = BigDecimal(order.optString("average_filled_price", "0"))
                    val totalFees = BigDecimal(order.optString("total_fees", "0"))
                    val cost = if (filledSize > BigDecimal.ZERO && avgPrice > BigDecimal.ZERO) {
                        filledSize.multiply(avgPrice).setScale(8, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO

                    return FillDetails(filledSize, cost, avgPrice, totalFees)
                }
            } catch (_: Exception) {
                // Continue retrying
            }
        }
        return null
    }

    private data class FillDetails(
        val filledSize: BigDecimal,
        val cost: BigDecimal,
        val avgPrice: BigDecimal,
        val fee: BigDecimal
    )

    override suspend fun getOrderStatus(orderId: String): Transaction? = withContext(Dispatchers.IO) {
        try {
            val request = buildGetRequest("/api/v3/brokerage/orders/historical/$orderId")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(body)
            val order = json.optJSONObject("order") ?: return@withContext null
            val status = order.optString("status", "")

            if (status == "FILLED") {
                val filledSize = BigDecimal(order.optString("filled_size", "0"))
                val avgPrice = BigDecimal(order.optString("average_filled_price", "0"))
                val totalFees = BigDecimal(order.optString("total_fees", "0"))
                val cost = if (filledSize > BigDecimal.ZERO && avgPrice > BigDecimal.ZERO) {
                    filledSize.multiply(avgPrice).setScale(8, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                Transaction(
                    planId = 0,
                    exchange = Exchange.COINBASE,
                    crypto = "",
                    fiat = "",
                    fiatAmount = cost,
                    cryptoAmount = filledSize,
                    price = avgPrice,
                    fee = totalFees,
                    status = TransactionStatus.COMPLETED,
                    exchangeOrderId = orderId,
                    executedAt = Instant.now()
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getBalance(currency: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val request = buildGetRequest("/api/v3/brokerage/accounts")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(body)
            val accounts = json.optJSONArray("accounts") ?: return@withContext null

            for (i in 0 until accounts.length()) {
                val account = accounts.getJSONObject(i)
                if (account.optString("currency") == currency) {
                    val availableBalance = account.optJSONObject("available_balance")
                    return@withContext availableBalance?.let {
                        BigDecimal(it.optString("value", "0"))
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val productId = "$crypto-$fiat"
            val request = buildGetRequest("/api/v3/brokerage/products/$productId")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(body)
            BigDecimal(json.getString("price"))
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getTradeHistory(
        crypto: String,
        fiat: String,
        sinceTimestamp: Instant?,
        limit: Int
    ): TradeHistoryPage = withContext(Dispatchers.IO) {
        val productId = "$crypto-$fiat"
        val path = buildString {
            append("/api/v3/brokerage/orders/historical/fills?product_id=$productId")
            append("&limit=$limit")
        }

        val request = buildGetRequest(path)
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(body).optString("message", "HTTP ${response.code}")
            } catch (_: Exception) { "HTTP ${response.code}" }
            throw Exception(errorMsg)
        }

        val json = JSONObject(body)
        val fills = json.optJSONArray("fills") ?: org.json.JSONArray()
        val cursor = json.optString("cursor", "")
        val trades = mutableListOf<HistoricalTrade>()

        for (i in 0 until fills.length()) {
            val fill = fills.getJSONObject(i)
            val side = fill.optString("side", "")
            if (side != "BUY") continue

            val size = BigDecimal(fill.optString("size", "0"))
            val price = BigDecimal(fill.optString("price", "0"))
            val commission = BigDecimal(fill.optString("commission", "0"))
            val tradeTime = fill.optString("trade_time", "")

            val timestamp = try {
                Instant.parse(tradeTime)
            } catch (_: Exception) {
                Instant.now()
            }

            // Filter locally by sinceTimestamp
            if (sinceTimestamp != null && !timestamp.isAfter(sinceTimestamp)) continue

            trades.add(
                HistoricalTrade(
                    orderId = fill.optString("trade_id", fill.optString("order_id", "")),
                    timestamp = timestamp,
                    crypto = crypto,
                    fiat = fiat,
                    cryptoAmount = size,
                    fiatAmount = size.multiply(price).setScale(8, RoundingMode.HALF_UP),
                    price = price,
                    fee = commission,
                    feeAsset = fiat,
                    side = "BUY"
                )
            )
        }

        TradeHistoryPage(
            trades = trades,
            hasMore = cursor.isNotBlank() && fills.length() >= limit
        )
    }

    override suspend fun withdraw(
        crypto: String,
        amount: BigDecimal,
        address: String
    ): Result<String> = Result.failure(
        NotImplementedError(
            "Coinbase Advanced Trade API does not support withdrawals. " +
                    "Please use the Coinbase app or website for withdrawals."
        )
    )

    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
}
