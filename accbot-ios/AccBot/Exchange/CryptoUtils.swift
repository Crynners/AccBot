import Foundation
import CryptoKit

/// Cryptographic utilities for API authentication.
/// All outputs must be byte-identical to Android CryptoUtils for exchange auth to work.
enum CryptoUtils {

    // MARK: - HMAC-SHA256

    /// Generate HMAC-SHA256 with base64-encoded result
    static func hmacSha256(message: String, secret: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let signature = HMAC<SHA256>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).base64EncodedString()
    }

    /// Generate HMAC-SHA256 with hex-encoded result
    static func hmacSha256Hex(message: String, secret: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let signature = HMAC<SHA256>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).hexEncodedString()
    }

    /// Generate HMAC-SHA256 with base64-decoded secret key and base64-encoded result.
    /// Used by Coinbase and Kraken variants.
    static func hmacSha256Base64Secret(message: String, base64Secret: String) -> String {
        guard let secretData = Data(base64Encoded: base64Secret) else {
            return ""
        }
        let key = SymmetricKey(data: secretData)
        let signature = HMAC<SHA256>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).base64EncodedString()
    }

    // MARK: - HMAC-SHA384

    /// Generate HMAC-SHA384 with base64-encoded result
    static func hmacSha384(message: String, secret: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let signature = HMAC<SHA384>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).base64EncodedString()
    }

    /// Generate HMAC-SHA384 with hex-encoded result
    static func hmacSha384Hex(message: String, secret: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let signature = HMAC<SHA384>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).hexEncodedString()
    }

    // MARK: - HMAC-SHA512

    /// Generate HMAC-SHA512 with base64-encoded result
    static func hmacSha512(message: String, secret: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let signature = HMAC<SHA512>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).base64EncodedString()
    }

    /// Generate HMAC-SHA512 with hex-encoded result
    static func hmacSha512Hex(message: String, secret: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let signature = HMAC<SHA512>.authenticationCode(
            for: Data(message.utf8),
            using: key
        )
        return Data(signature).hexEncodedString()
    }

    /// Generate HMAC-SHA512 with base64-decoded secret key and base64-encoded result.
    /// Used by Kraken.
    static func hmacSha512Base64Secret(message: Data, base64Secret: String) -> String {
        guard let secretData = Data(base64Encoded: base64Secret) else {
            return ""
        }
        let key = SymmetricKey(data: secretData)
        let signature = HMAC<SHA512>.authenticationCode(
            for: message,
            using: key
        )
        return Data(signature).base64EncodedString()
    }

    // MARK: - SHA256

    /// SHA256 hash returning raw bytes
    static func sha256(_ data: Data) -> Data {
        let digest = SHA256.hash(data: data)
        return Data(digest)
    }

    // MARK: - Timestamps

    /// Current timestamp in milliseconds
    static func currentTimestampMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    /// Current timestamp in seconds
    static func currentTimestampSec() -> Int64 {
        Int64(Date().timeIntervalSince1970)
    }
}

// MARK: - Data Extensions

extension Data {
    func hexEncodedString() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}
