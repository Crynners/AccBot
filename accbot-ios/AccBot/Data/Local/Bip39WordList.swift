import Foundation
import CommonCrypto

/// BIP39 wordlist loaded from bundle + 12-word seed generator.
///
/// Uses 128-bit entropy -> SHA-256 checksum (4 bits) -> 132 bits -> 12 x 11-bit indices.
final class Bip39WordList {
    private lazy var words: [String] = {
        guard let url = Bundle.main.url(forResource: "bip39_english", withExtension: "txt"),
              let content = try? String(contentsOf: url, encoding: .utf8) else {
            return []
        }
        return content.components(separatedBy: .newlines).filter { !$0.isEmpty }
    }()

    func generateSeed() -> [String] {
        var entropy = Data(count: 16) // 128 bits
        entropy.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        return entropyToWords(entropy)
    }

    func isValidWord(_ word: String) -> Bool {
        words.contains(word.lowercased())
    }

    func isValidSeed(_ seedWords: [String]) -> Bool {
        guard seedWords.count == 12 else { return false }
        guard seedWords.allSatisfy({ isValidWord($0) }) else { return false }

        let indices = seedWords.map { w in words.firstIndex(of: w.lowercased()) ?? -1 }
        guard indices.allSatisfy({ $0 >= 0 }) else { return false }

        // 12 words x 11 bits = 132 bits = 128 entropy + 4 checksum
        var bits = [Bool](repeating: false, count: 132)
        for (i, idx) in indices.enumerated() {
            for b in 0..<11 {
                bits[i * 11 + b] = (idx >> (10 - b)) & 1 == 1
            }
        }

        var entropyBytes = Data(count: 16)
        for i in 0..<128 {
            if bits[i] {
                entropyBytes[i / 8] |= UInt8(1 << (7 - i % 8))
            }
        }

        var hash = Data(count: Int(CC_SHA256_DIGEST_LENGTH))
        entropyBytes.withUnsafeBytes { entropyPtr in
            hash.withUnsafeMutableBytes { hashPtr in
                _ = CC_SHA256(entropyPtr.baseAddress, CC_LONG(entropyBytes.count), hashPtr.baseAddress?.assumingMemoryBound(to: UInt8.self))
            }
        }

        let checksumBits = Int(hash[0]) >> 4 // top 4 bits
        var actualChecksum = 0
        for b in 0..<4 {
            if bits[128 + b] { actualChecksum |= 1 << (3 - b) }
        }

        return checksumBits == actualChecksum
    }

    func getSuggestions(prefix: String, limit: Int = 5) -> [String] {
        guard !prefix.isEmpty else { return [] }
        let lower = prefix.lowercased()
        return Array(words.filter { $0.hasPrefix(lower) }.prefix(limit))
    }

    func entropyToWords(_ entropy: Data) -> [String] {
        precondition(entropy.count == 16, "Entropy must be 16 bytes (128 bits)")

        var hash = Data(count: Int(CC_SHA256_DIGEST_LENGTH))
        entropy.withUnsafeBytes { entropyPtr in
            hash.withUnsafeMutableBytes { hashPtr in
                _ = CC_SHA256(entropyPtr.baseAddress, CC_LONG(entropy.count), hashPtr.baseAddress?.assumingMemoryBound(to: UInt8.self))
            }
        }
        let checksumByte = hash[0]

        // 128 entropy bits + 4 checksum bits = 132 bits
        var bits = [Bool](repeating: false, count: 132)
        for i in 0..<128 {
            bits[i] = (Int(entropy[i / 8]) >> (7 - i % 8)) & 1 == 1
        }
        for b in 0..<4 {
            bits[128 + b] = (Int(checksumByte) >> (7 - b)) & 1 == 1
        }

        // 132 bits / 11 = 12 words
        return (0..<12).map { wordIndex in
            var idx = 0
            for b in 0..<11 {
                if bits[wordIndex * 11 + b] { idx |= 1 << (10 - b) }
            }
            return words[idx]
        }
    }
}
