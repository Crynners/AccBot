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
 * Kraken API implementation
 *
 * Note: Kraken only has futures demo, no spot sandbox
 */
class KrakenApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false,
    private val client: OkHttpClient
) : ExchangeApi {
    override val exchange = Exchange.KRAKEN

    override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.0026")

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.KRAKEN, isSandbox)

    private val formMediaType = "application/x-www-form-urlencoded".toMediaType()

    /** Map standard crypto/fiat codes to Kraken pair format */
    private fun mapPair(crypto: String, fiat: String): String {
        val krakenCrypto = when (crypto) {
            "BTC" -> "XBT"
            else -> crypto
        }
        return "$krakenCrypto$fiat"
    }

    /** Map Kraken asset codes back to standard codes */
    private fun mapAssetCode(krakenCode: String): String {
        return when (krakenCode) {
            "ZEUR" -> "EUR"
            "ZUSD" -> "USD"
            "ZGBP" -> "GBP"
            "ZCAD" -> "CAD"
            "ZJPY" -> "JPY"
            "XXBT" -> "BTC"
            "XETH" -> "ETH"
            "XLTC" -> "LTC"
            "XXRP" -> "XRP"
            "XXLM" -> "XLM"
            else -> krakenCode
        }
    }

    /** Map standard crypto code to Kraken asset code for withdrawals */
    private fun mapToKrakenAsset(crypto: String): String {
        return when (crypto) {
            "BTC" -> "XBT"
            else -> crypto
        }
    }

    private fun createKrakenSignature(path: String, nonce: Long, postData: String): String {
        val message = "$nonce$postData"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(message.toByteArray())
        val secretDecoded = android.util.Base64.decode(credentials.apiSecret, android.util.Base64.DEFAULT)
        val mac = javax.crypto.Mac.getInstance("HmacSHA512")
        mac.init(javax.crypto.spec.SecretKeySpec(secretDecoded, "HmacSHA512"))
        mac.update(path.toByteArray())
        val signed = mac.doFinal(hash)
        return android.util.Base64.encodeToString(signed, android.util.Base64.NO_WRAP)
    }

    private data class ApiResponse(val isSuccessful: Boolean, val code: Int, val body: String)

    /** Execute an authenticated POST request to Kraken private API */
    private fun executePrivateRequest(path: String, extraParams: String = ""): ApiResponse {
        val nonce = System.currentTimeMillis() * 1000
        val postData = if (extraParams.isEmpty()) "nonce=$nonce" else "nonce=$nonce&$extraParams"
        val signature = createKrakenSignature(path, nonce, postData)

        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("API-Key", credentials.apiKey)
            .header("API-Sign", signature)
            .post(postData.toRequestBody(formMediaType))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            ApiResponse(response.isSuccessful, response.code, body)
        }
    }

    /** Check Kraken JSON response for errors. Returns error string or null. */
    private fun checkKrakenErrors(json: JSONObject): String? {
        val errors = json.optJSONArray("error")
        if (errors != null && errors.length() > 0) {
            return errors.getString(0)
        }
        return null
    }

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        withContext(Dispatchers.IO) {
            try {
                val pair = mapPair(crypto, fiat)
                // Kraken's AddOrder takes volume in BASE currency - the 'viqc'
                // (volume-in-quote) flag was removed from the spot API, so we size the
                // order from the current price and reserve the taker fee so that
                // cost + fee stays within the plan's fiat amount.
                val price = getCurrentPrice(crypto, fiat)
                    ?: return@withContext DcaResult.Error(
                        "Could not fetch $pair price to size the order",
                        retryable = true
                    )
                val volume = fiatAmount.divide(
                    price.multiply(BigDecimal.ONE.plus(estimatedTakerFeeRate)),
                    8,
                    RoundingMode.DOWN
                )
                val params = "ordertype=market&type=buy&pair=$pair&volume=${volume.toPlainString()}"

                val (isSuccessful, code, body) = executePrivateRequest("/0/private/AddOrder", params)

                if (!isSuccessful) {
                    val isRetryable = code in 500..599 || code == 429
                    return@withContext DcaResult.Error("HTTP $code", retryable = isRetryable)
                }

                val json = JSONObject(body)
                checkKrakenErrors(json)?.let { error ->
                    val isRetryable = error.contains("EService:Unavailable") || error.contains("EGeneral:Temporary")
                    return@withContext DcaResult.Error(error, retryable = isRetryable)
                }

                val result = json.getJSONObject("result")
                val txIds = result.getJSONArray("txid")
                val txId = if (txIds.length() > 0) txIds.getString(0) else null

                // Query order to get fill details
                if (txId != null) {
                    val fillDetails = queryOrderFill(txId)
                    if (fillDetails != null) {
                        return@withContext DcaResult.Success(
                            Transaction(
                                planId = 0,
                                exchange = Exchange.KRAKEN,
                                crypto = crypto,
                                fiat = fiat,
                                fiatAmount = fillDetails.cost,
                                cryptoAmount = fillDetails.volume,
                                price = fillDetails.price,
                                fee = fillDetails.fee,
                                status = TransactionStatus.COMPLETED,
                                exchangeOrderId = txId,
                                executedAt = Instant.now()
                            )
                        )
                    }
                }

                // Fallback: order placed but fill details not yet available
                DcaResult.Success(
                    Transaction(
                        planId = 0,
                        exchange = Exchange.KRAKEN,
                        crypto = crypto,
                        fiat = fiat,
                        fiatAmount = fiatAmount,
                        cryptoAmount = BigDecimal.ZERO,
                        price = BigDecimal.ZERO,
                        fee = BigDecimal.ZERO,
                        status = TransactionStatus.PENDING,
                        exchangeOrderId = txId,
                        executedAt = Instant.now()
                    )
                )
            } catch (e: java.io.IOException) {
                DcaResult.Error(e.message ?: "Network error", retryable = true)
            } catch (e: Exception) {
                DcaResult.Error(e.message ?: "Unknown error", retryable = false)
            }
        }

    /** Query order fill details with retry (market orders fill quickly) */
    private suspend fun queryOrderFill(txId: String): OrderFillDetails? {
        // Try up to 3 times with 1s delay for order to fill
        repeat(3) { attempt ->
            try {
                if (attempt > 0) kotlinx.coroutines.delay(1000)

                val (_, _, body) = executePrivateRequest("/0/private/QueryOrders", "txid=$txId&trades=true")
                val json = JSONObject(body)

                if (checkKrakenErrors(json) != null) return null

                val result = json.getJSONObject("result")
                val order = result.optJSONObject(txId) ?: return null
                val status = order.optString("status")

                if (status == "closed") {
                    val volExec = BigDecimal(order.optString("vol_exec", "0"))
                    val cost = BigDecimal(order.optString("cost", "0"))
                    val fee = BigDecimal(order.optString("fee", "0"))
                    val price = if (volExec > BigDecimal.ZERO) {
                        cost.divide(volExec, 8, RoundingMode.HALF_UP)
                    } else BigDecimal.ZERO

                    return OrderFillDetails(volExec, cost, fee, price)
                }
            } catch (_: Exception) {
                // Continue retrying
            }
        }
        return null
    }

    private data class OrderFillDetails(
        val volume: BigDecimal,
        val cost: BigDecimal,
        val fee: BigDecimal,
        val price: BigDecimal
    )

    override suspend fun getOrderStatus(
        orderId: String,
        crypto: String,
        fiat: String
    ): OrderStatusResult? = withContext(Dispatchers.IO) {
        try {
            val (_, _, body) = executePrivateRequest("/0/private/QueryOrders", "txid=$orderId&trades=true")
            val json = JSONObject(body)

            if (checkKrakenErrors(json) != null) return@withContext null

            val result = json.getJSONObject("result")
            val order = result.optJSONObject(orderId) ?: return@withContext null
            val status = order.optString("status")

            val volExec = BigDecimal(order.optString("vol_exec", "0"))
            val cost = BigDecimal(order.optString("cost", "0"))
            val fee = BigDecimal(order.optString("fee", "0"))
            val price = if (volExec > BigDecimal.ZERO) {
                cost.divide(volExec, 8, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            val mappedStatus = when (status) {
                "closed" -> TransactionStatus.COMPLETED
                "open", "pending" -> if (volExec > BigDecimal.ZERO) {
                    TransactionStatus.PARTIAL
                } else TransactionStatus.PENDING
                "canceled", "cancelled", "expired" ->
                    if (volExec > BigDecimal.ZERO) TransactionStatus.PARTIAL
                    else TransactionStatus.FAILED
                else -> return@withContext null
            }

            OrderStatusResult(
                status = mappedStatus,
                filledCryptoAmount = volExec,
                filledFiatAmount = cost,
                avgFillPrice = if (volExec > BigDecimal.ZERO) price else null,
                fee = fee,
                feeAsset = fiat
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getBalance(currency: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val (isSuccessful, _, body) = executePrivateRequest("/0/private/Balance")
            if (!isSuccessful) return@withContext null

            val json = JSONObject(body)
            if (checkKrakenErrors(json) != null) return@withContext null

            val result = json.getJSONObject("result")

            // Try direct match first, then try Kraken-prefixed codes
            val keys = result.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (mapAssetCode(key) == currency || key == currency) {
                    return@withContext BigDecimal(result.getString(key))
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = withContext(Dispatchers.IO) {
        try {
            val pair = mapPair(crypto, fiat)
            val request = Request.Builder()
                .url("$baseUrl/0/public/Ticker?pair=$pair")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(body)
                if (checkKrakenErrors(json) != null) return@withContext null

                val result = json.getJSONObject("result")
                // Kraken returns the pair key which may differ from input
                if (!result.keys().hasNext()) return@withContext null
                val pairKey = result.keys().next()
                val ticker = result.getJSONObject(pairKey)
                // c = last trade closed [price, lot-volume]
                val lastPrice = ticker.getJSONArray("c").getString(0)
                BigDecimal(lastPrice)
            }
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
        val pair = mapPair(crypto, fiat)
        val params = buildString {
            append("pair=$pair")
            if (sinceTimestamp != null) {
                append("&start=${sinceTimestamp.plusSeconds(1).epochSecond}")
            }
        }

        val (isSuccessful, code, body) = executePrivateRequest("/0/private/TradesHistory", params)
        if (!isSuccessful) throw Exception("HTTP $code")

        val json = JSONObject(body)
        checkKrakenErrors(json)?.let { throw Exception(it) }

        val result = json.getJSONObject("result")
        val tradesObj = result.getJSONObject("trades")
        val trades = mutableListOf<HistoricalTrade>()

        val keys = tradesObj.keys()
        while (keys.hasNext()) {
            val tradeId = keys.next()
            val trade = tradesObj.getJSONObject(tradeId)

            val type = trade.optString("type", "")
            if (type != "buy") continue

            val tradePair = trade.optString("pair", "")
            val vol = BigDecimal(trade.optString("vol", "0"))
            val cost = BigDecimal(trade.optString("cost", "0"))
            val fee = BigDecimal(trade.optString("fee", "0"))
            val price = BigDecimal(trade.optString("price", "0"))
            val time = trade.optDouble("time", 0.0)

            trades.add(
                HistoricalTrade(
                    orderId = trade.optString("ordertxid", tradeId),
                    timestamp = Instant.ofEpochSecond(time.toLong()),
                    crypto = crypto,
                    fiat = fiat,
                    cryptoAmount = vol,
                    fiatAmount = cost,
                    price = price,
                    fee = fee,
                    feeAsset = fiat,
                    side = "BUY"
                )
            )
        }

        // Sort by timestamp ascending
        trades.sortBy { it.timestamp }

        TradeHistoryPage(
            trades = trades,
            hasMore = trades.size >= limit
        )
    }

    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val asset = mapToKrakenAsset(crypto)
                val params = "asset=$asset&key=$address&amount=${amount.toPlainString()}"

                val (isSuccessful, code, body) = executePrivateRequest("/0/private/Withdraw", params)

                if (!isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP $code"))
                }

                val json = JSONObject(body)
                checkKrakenErrors(json)?.let { error ->
                    // Provide helpful message if Kraken rejects raw address
                    val message = if (error.contains("EFunding:Unknown withdraw key")) {
                        "Kraken requires a pre-configured withdrawal address. " +
                                "Please add this address in your Kraken account settings first."
                    } else error
                    return@withContext Result.failure(Exception(message))
                }

                val result = json.getJSONObject("result")
                Result.success(result.optString("refid", ""))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null

    override suspend fun validateCredentials(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (isSuccessful, code, body) = executePrivateRequest("/0/private/Balance")
            if (!isSuccessful) {
                throw Exception("HTTP $code")
            }

            val json = JSONObject(body)
            checkKrakenErrors(json)?.let { error ->
                throw Exception(error)
            }
            true
        } catch (e: java.io.IOException) {
            throw Exception("Network error: ${e.message}")
        }
    }
}

/**
 * KuCoin API implementation
 *
 * Supports sandbox mode via openapi-sandbox.kucoin.com
 */
class KuCoinApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false,
    private val client: OkHttpClient
) : ExchangeApi {
    override val exchange = Exchange.KUCOIN

    override val estimatedTakerFeeRate: BigDecimal = BigDecimal("0.001")

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.KUCOIN, isSandbox)

    private fun signedRequest(method: String, endpoint: String, body: String? = null): Request.Builder {
        val timestamp = System.currentTimeMillis().toString()
        val preSign = "$timestamp$method$endpoint${body ?: ""}"
        val signature = CryptoUtils.hmacSha256Base64Secret(preSign, credentials.apiSecret)
        val passphrase = credentials.passphrase?.let {
            CryptoUtils.hmacSha256Base64Secret(it, credentials.apiSecret)
        } ?: ""

        return Request.Builder()
            .url("$baseUrl$endpoint")
            .header("KC-API-KEY", credentials.apiKey)
            .header("KC-API-SIGN", signature)
            .header("KC-API-TIMESTAMP", timestamp)
            .header("KC-API-PASSPHRASE", passphrase)
            .header("KC-API-KEY-VERSION", "2")
    }

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        withContext(Dispatchers.IO) {
            try {
                val symbol = "$crypto-$fiat"
                val clientOid = java.util.UUID.randomUUID().toString()

                val body = JSONObject().apply {
                    put("clientOid", clientOid)
                    put("side", "buy")
                    put("symbol", symbol)
                    put("type", "market")
                    put("funds", fiatAmount.setScale(2, RoundingMode.DOWN).toPlainString())
                }.toString()

                val request = signedRequest("POST", "/api/v1/orders", body)
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody())
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: throw Exception("Empty response")
                    val json = JSONObject(responseBody)

                    if (json.optString("code") != "200000") {
                        val errorMessage = json.optString("msg", "Unknown error")
                        return@withContext DcaResult.Error(errorMessage, retryable = false)
                    }

                    val data = json.getJSONObject("data")

                    DcaResult.Success(
                        Transaction(
                            planId = 0,
                            exchange = Exchange.KUCOIN,
                            crypto = crypto,
                            fiat = fiat,
                            fiatAmount = fiatAmount,
                            cryptoAmount = BigDecimal.ZERO,
                            price = BigDecimal.ZERO,
                            fee = BigDecimal.ZERO,
                            status = TransactionStatus.PENDING,
                            exchangeOrderId = data.optString("orderId"),
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

    override suspend fun getBalance(currency: String): BigDecimal? = null
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null

    override suspend fun validateCredentials(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = signedRequest("GET", "/api/v1/accounts")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext false
                val json = JSONObject(body)
                json.optString("code") == "200000"
            }
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Bitfinex API implementation
 *
 * Note: Bitfinex uses paper trading mode (same URL)
 */
class BitfinexApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false
) : ExchangeApi {
    override val exchange = Exchange.BITFINEX

    @Suppress("unused")
    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.BITFINEX, isSandbox)

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        DcaResult.Error("Bitfinex trading is not yet implemented", retryable = false)

    override suspend fun getBalance(currency: String): BigDecimal? = null
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
    override suspend fun validateCredentials(): Boolean = false
}

/**
 * Huobi API implementation
 *
 * Note: Huobi testnet has been discontinued
 */
class HuobiApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false
) : ExchangeApi {
    override val exchange = Exchange.HUOBI

    @Suppress("unused")
    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.HUOBI, isSandbox)

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        DcaResult.Error("Huobi trading is not yet implemented", retryable = false)

    override suspend fun getBalance(currency: String): BigDecimal? = null
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
    override suspend fun validateCredentials(): Boolean = false
}

