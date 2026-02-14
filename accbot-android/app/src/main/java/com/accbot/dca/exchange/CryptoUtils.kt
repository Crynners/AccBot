package com.accbot.dca.exchange

import org.apache.commons.codec.binary.Hex
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities for API authentication
 * HMAC-SHA256, HMAC-SHA384, HMAC-SHA512 signatures
 */
object CryptoUtils {

    /**
     * Generate HMAC-SHA256 signature
     */
    fun hmacSha256(message: String, secret: String): String {
        return hmac("HmacSHA256", message, secret)
    }

    /**
     * Generate HMAC-SHA256 signature with hex-encoded result
     */
    fun hmacSha256Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return Hex.encodeHexString(hash)
    }

    /**
     * Generate HMAC-SHA384 signature
     */
    fun hmacSha384(message: String, secret: String): String {
        return hmac("HmacSHA384", message, secret)
    }

    /**
     * Generate HMAC-SHA384 signature with hex-encoded result
     */
    fun hmacSha384Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA384")
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA384")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return Hex.encodeHexString(hash)
    }

    /**
     * Generate HMAC-SHA512 signature
     */
    fun hmacSha512(message: String, secret: String): String {
        return hmac("HmacSHA512", message, secret)
    }

    /**
     * Generate HMAC-SHA512 signature with hex-encoded result
     */
    fun hmacSha512Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA512")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return Hex.encodeHexString(hash)
    }

    /**
     * Generate HMAC-SHA256 with base64 encoded secret (for some exchanges)
     */
    fun hmacSha256Base64Secret(message: String, base64Secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretBytes = android.util.Base64.decode(base64Secret, android.util.Base64.DEFAULT)
        val secretKey = SecretKeySpec(secretBytes, "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun hmac(algorithm: String, message: String, secret: String): String {
        val mac = Mac.getInstance(algorithm)
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), algorithm)
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    /**
     * Get current timestamp in milliseconds
     */
    fun currentTimestampMs(): Long = System.currentTimeMillis()

    /**
     * Get current timestamp in seconds
     */
    fun currentTimestampSec(): Long = System.currentTimeMillis() / 1000
}
