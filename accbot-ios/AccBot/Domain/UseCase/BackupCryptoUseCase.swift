import Foundation
import CommonCrypto
import CryptoKit

/// AES-256-GCM encryption for backup data.
///
/// Binary format: salt(16B) || IV(12B) || ciphertext || GCM-tag(16B)
/// KDF: PBKDF2-HMAC-SHA256, 600 000 iterations, 256-bit key.
///
/// Cross-platform compatible with Android BackupCryptoUseCase.
final class BackupCryptoUseCase {
    private static let saltSize = 16
    private static let ivSize = 12
    private static let keySizeBits = 256
    private static let pbkdf2Iterations: UInt32 = 600_000

    private let bip39: Bip39WordList

    init(bip39: Bip39WordList) {
        self.bip39 = bip39
    }

    func generateSeed() -> [String] {
        bip39.generateSeed()
    }

    func encrypt(plaintext: Data, passphrase: String) throws -> String {
        var salt = Data(count: Self.saltSize)
        salt.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, Self.saltSize, $0.baseAddress!) }

        var iv = Data(count: Self.ivSize)
        iv.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, Self.ivSize, $0.baseAddress!) }

        let key = try deriveKey(passphrase: passphrase, salt: salt)

        let sealedBox = try AES.GCM.seal(plaintext, using: key, nonce: AES.GCM.Nonce(data: iv))

        // salt || IV || ciphertext || GCM-tag
        var combined = Data()
        combined.append(salt)
        combined.append(iv)
        combined.append(sealedBox.ciphertext)
        combined.append(sealedBox.tag)

        return combined.base64EncodedString()
    }

    func decrypt(base64Data: String, passphrase: String) throws -> Data {
        guard let combined = Data(base64Encoded: base64Data) else {
            throw BackupCryptoError.invalidData
        }
        guard combined.count > Self.saltSize + Self.ivSize else {
            throw BackupCryptoError.invalidData
        }

        let salt = combined[0..<Self.saltSize]
        let iv = combined[Self.saltSize..<(Self.saltSize + Self.ivSize)]
        let ciphertextAndTag = combined[(Self.saltSize + Self.ivSize)...]

        guard ciphertextAndTag.count > 16 else {
            throw BackupCryptoError.invalidData
        }

        let ciphertext = ciphertextAndTag[ciphertextAndTag.startIndex..<(ciphertextAndTag.endIndex - 16)]
        let tag = ciphertextAndTag[(ciphertextAndTag.endIndex - 16)...]

        let key = try deriveKey(passphrase: passphrase, salt: salt)
        let nonce = try AES.GCM.Nonce(data: iv)
        let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)

        return try AES.GCM.open(sealedBox, using: key)
    }

    func resolvePassphrase(mode: EncryptionMode, password: String, seed: String) -> String {
        switch mode {
        case .password: return password
        case .seed: return seed
        }
    }

    private func deriveKey(passphrase: String, salt: Data) throws -> SymmetricKey {
        let passphraseData = Data(passphrase.utf8)
        var derivedKey = Data(count: Self.keySizeBits / 8)

        let status = derivedKey.withUnsafeMutableBytes { derivedKeyPtr in
            salt.withUnsafeBytes { saltPtr in
                passphraseData.withUnsafeBytes { passwordPtr in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordPtr.baseAddress?.assumingMemoryBound(to: Int8.self),
                        passphraseData.count,
                        saltPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        Self.pbkdf2Iterations,
                        derivedKeyPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        Self.keySizeBits / 8
                    )
                }
            }
        }

        guard status == kCCSuccess else {
            throw BackupCryptoError.keyDerivationFailed
        }

        return SymmetricKey(data: derivedKey)
    }
}

enum BackupCryptoError: LocalizedError {
    case invalidData
    case keyDerivationFailed
    case wrongPassword

    var errorDescription: String? {
        switch self {
        case .invalidData: return "Invalid encrypted data"
        case .keyDerivationFailed: return "Key derivation failed"
        case .wrongPassword: return "Wrong password or seed"
        }
    }
}
