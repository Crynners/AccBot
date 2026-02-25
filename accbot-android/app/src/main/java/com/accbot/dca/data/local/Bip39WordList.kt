package com.accbot.dca.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BIP39 wordlist loaded from assets + 12-word seed generator.
 *
 * Uses 128-bit entropy → SHA-256 checksum (4 bits) → 132 bits → 12 × 11-bit indices.
 */
@Singleton
class Bip39WordList @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val words: List<String> by lazy {
        context.assets.open("bip39_english.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toList()
        }
    }

    fun generateSeed(): List<String> {
        val entropy = ByteArray(16) // 128 bits
        SecureRandom().nextBytes(entropy)
        return entropyToWords(entropy)
    }

    fun isValidWord(word: String): Boolean =
        words.contains(word.lowercase())

    fun isValidSeed(seedWords: List<String>): Boolean {
        if (seedWords.size != 12) return false
        if (seedWords.any { !isValidWord(it) }) return false

        // Verify checksum: convert words back to bits, check last 4 bits match SHA-256
        val indices = seedWords.map { w -> words.indexOf(w.lowercase()) }
        if (indices.any { it < 0 }) return false

        // 12 words × 11 bits = 132 bits = 128 entropy + 4 checksum
        val bits = BooleanArray(132)
        for (i in indices.indices) {
            val idx = indices[i]
            for (b in 0 until 11) {
                bits[i * 11 + b] = (idx shr (10 - b)) and 1 == 1
            }
        }

        val entropyBytes = ByteArray(16)
        for (i in 0 until 128) {
            if (bits[i]) entropyBytes[i / 8] = (entropyBytes[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
        }

        val hash = MessageDigest.getInstance("SHA-256").digest(entropyBytes)
        val checksumBits = (hash[0].toInt() and 0xFF) shr 4 // top 4 bits
        var actualChecksum = 0
        for (b in 0 until 4) {
            if (bits[128 + b]) actualChecksum = actualChecksum or (1 shl (3 - b))
        }

        return checksumBits == actualChecksum
    }

    fun getSuggestions(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        return words.filter { it.startsWith(lower) }.take(limit)
    }

    internal fun entropyToWords(entropy: ByteArray): List<String> {
        require(entropy.size == 16) { "Entropy must be 16 bytes (128 bits)" }

        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        // checksum = top 4 bits of SHA-256(entropy)
        val checksumByte = hash[0]

        // 128 entropy bits + 4 checksum bits = 132 bits
        val bits = BooleanArray(132)
        for (i in 0 until 128) {
            bits[i] = (entropy[i / 8].toInt() shr (7 - i % 8)) and 1 == 1
        }
        for (b in 0 until 4) {
            bits[128 + b] = (checksumByte.toInt() shr (7 - b)) and 1 == 1
        }

        // 132 bits / 11 = 12 words
        return List(12) { wordIndex ->
            var idx = 0
            for (b in 0 until 11) {
                if (bits[wordIndex * 11 + b]) idx = idx or (1 shl (10 - b))
            }
            words[idx]
        }
    }
}
