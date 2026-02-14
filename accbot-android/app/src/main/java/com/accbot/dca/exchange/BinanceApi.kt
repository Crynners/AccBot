package com.accbot.dca.exchange

import com.accbot.dca.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    private fun ensureTimeSynced() {
        if (!timeSynced) {
            syncServerTime()
            timeSynced = true
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
            val response = client.newCall(request).execute()
            val localAfter = System.currentTimeMillis()
            val body = response.body?.string() ?: return

            val json = JSONObject(body)
            val serverTime = json.getLong("serverTime")
            val localTime = (localBefore + localAfter) / 2
            timeOffset = serverTime - localTime
            android.util.Log.d("BinanceApi", "Time synced: offset=${timeOffset}ms, url=$baseUrl")
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

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)

            if (json.has("code")) {
                val errorMessage = json.optString("msg", "Unknown error")
                return@withContext DcaResult.Error(errorMessage, retryable = false)
            }

            val executedQty = BigDecimal(json.getString("executedQty"))
            val cummulativeQuoteQty = BigDecimal(json.getString("cummulativeQuoteQty"))
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
                    status = TransactionStatus.COMPLETED,
                    exchangeOrderId = json.optString("orderId"),
                    executedAt = Instant.now()
                )
            )
        } catch (e: java.io.IOException) {
            DcaResult.Error(e.message ?: "Network error", retryable = true)
        } catch (e: Exception) {
            DcaResult.Error(e.message ?: "Unknown error", retryable = false)
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

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
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

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            BigDecimal(json.getString("price"))
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

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)

            if (json.has("code")) {
                val errorMessage = json.optString("msg", "Withdrawal failed")
                return@withContext Result.failure(Exception(errorMessage))
            }

            Result.success(json.optString("id", ""))
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

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
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
        } catch (e: Exception) {
            null
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

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (body == null) {
                android.util.Log.e("BinanceApi", "validateCredentials: Empty response")
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
        } catch (e: Exception) {
            android.util.Log.e("BinanceApi", "validateCredentials exception: ${e.message}, url=$baseUrl")
            throw e
        }
    }
}
