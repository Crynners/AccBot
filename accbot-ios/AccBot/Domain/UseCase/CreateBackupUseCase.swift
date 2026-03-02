import Foundation
import Compression
import zlib

enum CreateBackupResult {
    case success(envelopeJson: String, suggestedFileName: String, payloadSizeBytes: Int)
    case error(String)
}

final class CreateBackupUseCase {
    private static let qrMaxBytes = 1850

    private let collector: BackupDataCollector
    private let crypto: BackupCryptoUseCase

    init(collector: BackupDataCollector, crypto: BackupCryptoUseCase) {
        self.collector = collector
        self.crypto = crypto
    }

    func execute(options: BackupExportOptions) throws -> CreateBackupResult {
        do {
            // Validate
            if options.includeCredentials && options.password.isEmpty && options.seed.isEmpty {
                return .error("Encryption is required when including credentials")
            }

            // Collect data
            let payload = try collector.collect(options: options)
            let payloadData = try JSONEncoder().encode(payload)

            // Compress
            let compressed = try gzipCompress(payloadData)

            // Determine encryption
            let passphrase = crypto.resolvePassphrase(mode: options.encryptionMode, password: options.password, seed: options.seed)
            let hasPassphrase = !passphrase.isEmpty

            // Encrypt or encode
            let data: String
            if hasPassphrase {
                data = try crypto.encrypt(plaintext: compressed, passphrase: passphrase)
            } else {
                data = compressed.base64EncodedString()
            }

            // Build sections list
            var sections = ["plans", "settings"]
            if options.includeCredentials { sections.append("credentials") }
            if options.includeTransactions { sections.append("transactions") }
            if options.includeNotifications { sections.append("notifications") }
            if options.includeWithdrawals { sections.append("withdrawals") }

            // Build envelope
            let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
            let envelope = BackupEnvelope(
                appVersion: appVersion,
                environment: collector.isSandbox() ? "sandbox" : "prod",
                encrypted: hasPassphrase,
                compressed: true,
                sections: sections,
                data: data
            )

            let envelopeJson = try JSONEncoder().encode(envelope)
            let jsonString = String(data: envelopeJson, encoding: .utf8) ?? ""

            // Generate filename
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyyMMdd_HHmmss"
            let timestamp = formatter.string(from: Date())
            let fileName = "accbot_backup_\(timestamp).json"

            return .success(
                envelopeJson: jsonString,
                suggestedFileName: fileName,
                payloadSizeBytes: envelopeJson.count
            )
        } catch {
            return .error(error.localizedDescription)
        }
    }

    func isQrFeasible(payloadSizeBytes: Int) -> Bool {
        payloadSizeBytes <= Self.qrMaxBytes
    }

    private func gzipCompress(_ data: Data) throws -> Data {
        guard !data.isEmpty else { return data }
        var compressed = Data()
        let bufferSize = 65536
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        let outputFilter = try OutputFilter(.compress, using: .zlib) { outputData in
            if let outputData = outputData { compressed.append(outputData) }
        }

        // Write gzip header manually and use zlib for raw deflate,
        // OR use simpler approach with NSData
        // Use simpler gzip via zlib for cross-platform compat
        return try gzipCompressNSData(data)
    }

    private func gzipCompressNSData(_ data: Data) throws -> Data {
        guard !data.isEmpty else { return data }
        // Use zlib directly for gzip compression
        let nsData = data as NSData
        var stream = z_stream()

        stream.next_in = UnsafeMutablePointer<Bytef>(mutating: nsData.bytes.assumingMemoryBound(to: Bytef.self))
        stream.avail_in = uInt(nsData.length)

        let initResult = deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED,
                                        MAX_WBITS + 16, // +16 for gzip format
                                        8, Z_DEFAULT_STRATEGY,
                                        ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard initResult == Z_OK else {
            throw NSError(domain: "gzip", code: Int(initResult), userInfo: [NSLocalizedDescriptionKey: "gzip init failed"])
        }

        var compressed = Data()
        let bufferSize = 65536
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer {
            buffer.deallocate()
            deflateEnd(&stream)
        }

        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufferSize)
            let result = deflate(&stream, Z_FINISH)
            let count = bufferSize - Int(stream.avail_out)
            if count > 0 { compressed.append(buffer, count: count) }
            if result == Z_STREAM_END { break }
            guard result == Z_OK || result == Z_BUF_ERROR else {
                throw NSError(domain: "gzip", code: Int(result), userInfo: [NSLocalizedDescriptionKey: "gzip compress failed"])
            }
        } while true

        return compressed
    }
}
