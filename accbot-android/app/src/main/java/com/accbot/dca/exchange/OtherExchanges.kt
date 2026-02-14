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

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.KRAKEN, isSandbox)

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        withContext(Dispatchers.IO) {
            try {
                val pair = mapPair(crypto, fiat)
                val nonce = System.currentTimeMillis() * 1000

                val postData = "nonce=$nonce&ordertype=market&type=buy&pair=$pair&oflags=viqc&volume=${fiatAmount.toPlainString()}"
                val signature = createKrakenSignature("/0/private/AddOrder", nonce, postData)

                val request = Request.Builder()
                    .url("$baseUrl/0/private/AddOrder")
                    .header("API-Key", credentials.apiKey)
                    .header("API-Sign", signature)
                    .post(postData.toRequestBody())
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)

                val errors = json.optJSONArray("error")
                if (errors != null && errors.length() > 0) {
                    return@withContext DcaResult.Error(errors.getString(0), retryable = false)
                }

                val result = json.getJSONObject("result")
                val txIds = result.getJSONArray("txid")

                DcaResult.Success(
                    Transaction(
                        planId = 0,
                        exchange = Exchange.KRAKEN,
                        crypto = crypto,
                        fiat = fiat,
                        fiatAmount = fiatAmount,
                        cryptoAmount = BigDecimal.ZERO, // Will be updated via order status
                        price = BigDecimal.ZERO,
                        fee = BigDecimal.ZERO,
                        status = TransactionStatus.PENDING,
                        exchangeOrderId = if (txIds.length() > 0) txIds.getString(0) else null,
                        executedAt = Instant.now()
                    )
                )
            } catch (e: java.io.IOException) {
                DcaResult.Error(e.message ?: "Network error", retryable = true)
            } catch (e: Exception) {
                DcaResult.Error(e.message ?: "Unknown error", retryable = false)
            }
        }

    private fun mapPair(crypto: String, fiat: String): String {
        val krakenCrypto = when (crypto) {
            "BTC" -> "XBT"
            else -> crypto
        }
        return "$krakenCrypto$fiat"
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

    override suspend fun getBalance(currency: String): BigDecimal? = null // TODO: Implement
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null // TODO: Implement
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
    override suspend fun validateCredentials(): Boolean = true
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

    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.KUCOIN, isSandbox)

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        withContext(Dispatchers.IO) {
            try {
                val symbol = "$crypto-$fiat"
                val timestamp = System.currentTimeMillis().toString()
                val clientOid = java.util.UUID.randomUUID().toString()

                val body = JSONObject().apply {
                    put("clientOid", clientOid)
                    put("side", "buy")
                    put("symbol", symbol)
                    put("type", "market")
                    put("funds", fiatAmount.setScale(2, RoundingMode.DOWN).toPlainString())
                }.toString()

                val preSign = "${timestamp}POST/api/v1/orders$body"
                val signature = CryptoUtils.hmacSha256Base64Secret(preSign, credentials.apiSecret)
                val passphrase = credentials.passphrase?.let {
                    CryptoUtils.hmacSha256Base64Secret(it, credentials.apiSecret)
                } ?: ""

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/orders")
                    .header("KC-API-KEY", credentials.apiKey)
                    .header("KC-API-SIGN", signature)
                    .header("KC-API-TIMESTAMP", timestamp)
                    .header("KC-API-PASSPHRASE", passphrase)
                    .header("KC-API-KEY-VERSION", "2")
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody())
                    .build()

                val response = client.newCall(request).execute()
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
    override suspend fun validateCredentials(): Boolean = true
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
        DcaResult.Error("Not implemented", retryable = false)

    override suspend fun getBalance(currency: String): BigDecimal? = null
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
    override suspend fun validateCredentials(): Boolean = true
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
        DcaResult.Error("Not implemented", retryable = false)

    override suspend fun getBalance(currency: String): BigDecimal? = null
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
    override suspend fun validateCredentials(): Boolean = true
}

/**
 * Coinbase API implementation
 *
 * Supports sandbox mode via api-public.sandbox.exchange.coinbase.com
 */
class CoinbaseApi(
    private val credentials: ExchangeCredentials,
    private val isSandbox: Boolean = false
) : ExchangeApi {
    override val exchange = Exchange.COINBASE

    @Suppress("unused")
    private val baseUrl = ExchangeConfig.getBaseUrl(Exchange.COINBASE, isSandbox)

    override suspend fun marketBuy(crypto: String, fiat: String, fiatAmount: BigDecimal): DcaResult =
        DcaResult.Error("Not implemented", retryable = false)

    override suspend fun getBalance(currency: String): BigDecimal? = null
    override suspend fun getCurrentPrice(crypto: String, fiat: String): BigDecimal? = null
    override suspend fun withdraw(crypto: String, amount: BigDecimal, address: String): Result<String> =
        Result.failure(NotImplementedError())
    override suspend fun getWithdrawalFee(crypto: String): BigDecimal? = null
    override suspend fun validateCredentials(): Boolean = true
}
