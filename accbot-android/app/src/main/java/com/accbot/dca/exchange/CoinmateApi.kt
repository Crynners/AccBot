package com.accbot.dca.exchange

import com.accbot.dca.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Coinmate API implementation
 * Czech exchange supporting CZK and EUR
 *
 * Note: Coinmate does not have a sandbox environment
 */
class CoinmateApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false,
    private val client: OkHttpClient
) : ExchangeApi {

    override val exchange = Exchange.COINMATE

    // Coinmate taker fee: 0.35% (same as .NET CoinmateAPI.getTakerFee())
    private val takerFeeRate = BigDecimal("0.0035")

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.COINMATE, isSandbox)

    private val clientId: String = credentials.clientId
        ?: throw IllegalArgumentException("Coinmate requires clientId in credentials")


    override suspend fun marketBuy(
        crypto: String,
        fiat: String,
        fiatAmount: BigDecimal
    ): DcaResult = withContext(Dispatchers.IO) {
        try {
            val pair = "${crypto}_${fiat}"
            val nonce = System.currentTimeMillis()
            val signature = createSignature(nonce)

            val formBody = FormBody.Builder()
                .add("clientId", clientId)
                .add("publicKey", credentials.apiKey)
                .add("nonce", nonce.toString())
                .add("signature", signature)
                .add("currencyPair", pair)
                .add("total", fiatAmount.setScale(2, RoundingMode.DOWN).toPlainString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/buyInstant")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)

            if (json.optBoolean("error", true)) {
                val errorMessage = json.optString("errorMessage", "Unknown error")
                return@withContext DcaResult.Error(errorMessage, retryable = false)
            }

            // buyInstant returns just the order ID
            val orderId = json.get("data").toString()

            // Query tradeHistory for real fill details (amount, price, fee)
            val tradeDetails = getTradeDetailsByOrderId(orderId, pair)

            val fee: BigDecimal
            val cryptoAmount: BigDecimal
            val fillingPrice: BigDecimal

            if (tradeDetails != null) {
                // Real values from trade history
                cryptoAmount = tradeDetails.totalAmount
                fee = tradeDetails.totalFee
                fillingPrice = tradeDetails.weightedAvgPrice
            } else {
                // Fallback: estimate from current price if tradeHistory call failed
                fee = fiatAmount.multiply(takerFeeRate).setScale(2, RoundingMode.HALF_UP)
                val currentPrice = getCurrentPrice(crypto, fiat) ?: BigDecimal.ONE
                val netFiatAmount = fiatAmount - fee
                cryptoAmount = if (currentPrice > BigDecimal.ZERO)
                    netFiatAmount.divide(currentPrice, 8, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
                fillingPrice = currentPrice
            }

            DcaResult.Success(
                Transaction(
                    planId = 0, // Will be set by caller
                    exchange = Exchange.COINMATE,
                    crypto = crypto,
                    fiat = fiat,
                    fiatAmount = fiatAmount,
                    cryptoAmount = cryptoAmount,
                    price = fillingPrice,
                    fee = fee,
                    feeAsset = fiat,
                    status = TransactionStatus.COMPLETED,
                    exchangeOrderId = orderId,
                    executedAt = Instant.now()
                )
            )
        } catch (e: java.io.IOException) {
            DcaResult.Error(e.message ?: "Network error", retryable = true)
        } catch (e: Exception) {
            DcaResult.Error(e.message ?: "Unknown error", retryable = false)
        }
    }

    /**
     * Aggregated trade fill details for a single order.
     */
    private data class TradeDetails(
        val totalAmount: BigDecimal,   // total crypto bought
        val totalFee: BigDecimal,      // total fee in fiat
        val weightedAvgPrice: BigDecimal // volume-weighted average execution price
    )

    /**
     * Queries /tradeHistory to find fills for a specific orderId.
     * Instant orders fill immediately, but we retry once after a short delay
     * in case the exchange needs a moment to settle.
     */
    private suspend fun getTradeDetailsByOrderId(orderId: String, currencyPair: String): TradeDetails? {
        // Try immediately, then retry once after 1s if no fills found
        for (attempt in 0..1) {
            if (attempt > 0) delay(1000)

            try {
                val nonce = System.currentTimeMillis()
                val signature = createSignature(nonce)

                val formBody = FormBody.Builder()
                    .add("clientId", clientId)
                    .add("publicKey", credentials.apiKey)
                    .add("nonce", nonce.toString())
                    .add("signature", signature)
                    .add("currencyPair", currencyPair)
                    .add("limit", "20")
                    .add("sort", "DESC")
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/tradeHistory")
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                val json = JSONObject(body)

                if (json.optBoolean("error", true)) continue

                val dataArray = json.optJSONArray("data") ?: continue

                // Filter fills matching our orderId
                var totalAmount = BigDecimal.ZERO
                var totalFee = BigDecimal.ZERO
                var totalCost = BigDecimal.ZERO // amount × price per fill, for weighted avg
                var found = false

                for (i in 0 until dataArray.length()) {
                    val trade = dataArray.getJSONObject(i)
                    val tradeOrderId = trade.optString("orderId", "")
                    if (tradeOrderId == orderId) {
                        found = true
                        val amount = BigDecimal(trade.getString("amount"))
                        val price = BigDecimal(trade.getString("price"))
                        val fee = BigDecimal(trade.getString("fee"))
                        totalAmount = totalAmount.add(amount)
                        totalFee = totalFee.add(fee)
                        totalCost = totalCost.add(amount.multiply(price))
                    }
                }

                if (found && totalAmount > BigDecimal.ZERO) {
                    val weightedAvgPrice = totalCost.divide(totalAmount, 2, RoundingMode.HALF_UP)
                    return TradeDetails(
                        totalAmount = totalAmount,
                        totalFee = totalFee.setScale(2, RoundingMode.HALF_UP),
                        weightedAvgPrice = weightedAvgPrice
                    )
                }
            } catch (_: Exception) {
                // Continue to retry
            }
        }
        return null
    }

    override suspend fun getBalance(currency: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val nonce = System.currentTimeMillis()
            val signature = createSignature(nonce)

            val formBody = FormBody.Builder()
                .add("clientId", clientId)
                .add("publicKey", credentials.apiKey)
                .add("nonce", nonce.toString())
                .add("signature", signature)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/balances")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            if (json.optBoolean("error", true)) return@withContext null

            val data = json.getJSONObject("data")
            val currencyData = data.optJSONObject(currency) ?: return@withContext null
            BigDecimal(currencyData.getString("available"))
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val pair = "${crypto}_${fiat}"
            val request = Request.Builder()
                .url("$baseUrl/ticker?currencyPair=$pair")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            if (json.optBoolean("error", true)) return@withContext null

            val data = json.getJSONObject("data")
            BigDecimal(data.getString("last"))
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
            val nonce = System.currentTimeMillis()
            val signature = createSignature(nonce)

            val formBody = FormBody.Builder()
                .add("clientId", clientId)
                .add("publicKey", credentials.apiKey)
                .add("nonce", nonce.toString())
                .add("signature", signature)
                .add("coinName", crypto)
                .add("amount", amount.toPlainString())
                .add("address", address)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/${crypto.lowercase()}Withdrawal")
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)

            if (json.optBoolean("error", true)) {
                val errorMessage = json.optString("errorMessage", "Withdrawal failed")
                return@withContext Result.failure(Exception(errorMessage))
            }

            val data = json.getJSONObject("data")
            Result.success(data.optString("id", ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = withContext(Dispatchers.IO) {
        // Coinmate has fixed fees - return approximate
        when (crypto) {
            "BTC" -> BigDecimal("0.0001")
            "ETH" -> BigDecimal("0.001")
            "LTC" -> BigDecimal("0.001")
            else -> null
        }
    }

    override suspend fun validateCredentials(): Boolean = withContext(Dispatchers.IO) {
        try {
            getBalance("BTC") != null
        } catch (e: Exception) {
            false
        }
    }

    private fun createSignature(nonce: Long): String {
        val message = "$nonce$clientId${credentials.apiKey}"
        return CryptoUtils.hmacSha256Hex(message, credentials.apiSecret).uppercase()
    }
}
