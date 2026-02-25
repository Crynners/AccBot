package com.accbot.dca.domain.usecase

import android.util.Base64
import com.accbot.dca.data.local.Bip39WordList
import com.accbot.dca.domain.model.EncryptionMode
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * AES-256-GCM encryption for backup data.
 *
 * Binary format: salt(16B) || IV(12B) || ciphertext || GCM-tag(16B)
 * KDF: PBKDF2-HMAC-SHA256, 600 000 iterations, 256-bit key.
 */
class BackupCryptoUseCase @Inject constructor(
    private val bip39WordList: Bip39WordList
) {
    companion object {
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 600_000
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    fun generateSeed(): List<String> = bip39WordList.generateSeed()

    fun encrypt(plaintext: ByteArray, passphrase: String): String {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // salt || IV || ciphertext+tag
        val combined = salt + iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(base64Data: String, passphrase: String): ByteArray {
        val combined = Base64.decode(base64Data, Base64.NO_WRAP)
        require(combined.size > SALT_SIZE + IV_SIZE) { "Invalid encrypted data" }

        val salt = combined.copyOfRange(0, SALT_SIZE)
        val iv = combined.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val ciphertext = combined.copyOfRange(SALT_SIZE + IV_SIZE, combined.size)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext) // throws AEADBadTagException on wrong password
    }

    fun resolvePassphrase(mode: EncryptionMode, password: String, seed: String): String {
        return when (mode) {
            EncryptionMode.Password -> password
            EncryptionMode.Seed -> seed
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
