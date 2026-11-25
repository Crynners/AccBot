import Foundation
import zlib

enum RestoreBackupResult {
    case previewReady(preview: BackupPreview, payload: BackupPayload)
    case restoreComplete(String)
    case error(String)
}

final class RestoreBackupUseCase {
    private let restorer: BackupDataRestorer
    private let crypto: BackupCryptoUseCase
    private let userPreferences: UserPreferences
    private let targetEnvironment: String

    init(restorer: BackupDataRestorer, crypto: BackupCryptoUseCase, userPreferences: UserPreferences) {
        self.restorer = restorer
        self.crypto = crypto
        self.userPreferences = userPreferences
        targetEnvironment = userPreferences.sandboxMode ? "sandbox" : "prod"
    }

    func parseAndPreview(envelopeJson: String, passphrase: String = "") -> RestoreBackupResult {
        do {
            guard envelopeJson.utf8.count <= BackupEnvelope.maxEnvelopeBytes else {
                return .error(String(localized: "This backup exceeds the supported size limit."))
            }
            guard let jsonData = envelopeJson.data(using: .utf8) else {
                return .error("Invalid backup file")
            }

            let envelope = try JSONDecoder().decode(BackupEnvelope.self, from: jsonData)

            guard envelope.format == BackupEnvelope.formatIdentifier,
                  (1...BackupEnvelope.currentVersion).contains(envelope.version) else {
                return .error("Invalid backup format")
            }
            guard envelope.environment == targetEnvironment else {
                return .error(String(localized: "This backup belongs to a different environment. Switch to that environment before restoring it."))
            }

            // Decrypt or decode
            let compressed: Data
            if envelope.encrypted {
                if passphrase.isEmpty {
                    return .error("Password required")
                }
                do {
                    compressed = try crypto.decrypt(base64Data: envelope.data, passphrase: passphrase)
                } catch {
                    return .error("Wrong password or seed")
                }
            } else {
                guard let decoded = Data(base64Encoded: envelope.data) else {
                    return .error("Invalid backup data")
                }
                compressed = decoded
            }

            // Decompress
            let payloadData: Data
            if envelope.compressed {
                payloadData = try gzipDecompress(compressed)
            } else {
                payloadData = compressed
            }

            let payload = try JSONDecoder().decode(BackupPayload.self, from: payloadData)
            guard envelope.encrypted || payload.credentials.isEmpty else {
                return .error(String(localized: "A backup that includes API credentials must be encrypted."))
            }
            if let incompatibility = incompatibility(in: payload) { return .error(incompatibility) }

            let preview = BackupPreview(
                createdAt: envelope.createdAt,
                appVersion: envelope.appVersion,
                environment: envelope.environment,
                planCount: payload.plans.count,
                hasSettings: payload.settings != nil,
                thresholdCount: payload.withdrawalThresholds.count,
                credentialCount: payload.credentials.count,
                transactionCount: payload.transactions.count,
                notificationCount: payload.notifications.count,
                withdrawalCount: payload.withdrawals.count,
                sections: envelope.sections
            )

            return .previewReady(preview: preview, payload: payload)
        } catch {
            return .error(error.localizedDescription)
        }
    }

    func restore(payload: BackupPayload, environment: String, restoreMode: RestoreMode = .merge) -> RestoreBackupResult {
        let currentEnvironment = userPreferences.sandboxMode ? "sandbox" : "prod"
        guard environment == targetEnvironment, environment == currentEnvironment else {
            return .error(String(localized: "This backup belongs to a different environment. Switch to that environment before restoring it."))
        }
        if let incompatibility = incompatibility(in: payload) { return .error(incompatibility) }
        let result = restorer.restore(payload: payload, restoreMode: restoreMode)
        switch result {
        case .success(let msg): return .restoreComplete(msg)
        case .error(let msg): return .error(msg)
        }
    }

    private func incompatibility(in payload: BackupPayload) -> String? {
        let connections = payload.connections ?? []
        let multipleAccounts = Set(connections.map(\.exchange)).count != connections.count
            || Set(payload.credentials.map(\.exchange)).count != payload.credentials.count
        let hasSellData = payload.transactions.contains { ($0.side ?? "BUY") != "BUY" }
            || payload.plans.contains { $0.allowSells == true || $0.targetProfitAmount != nil }
        if multipleAccounts || hasSellData {
            return String(localized: "This backup contains multiple accounts per exchange or sell data that iOS cannot restore.")
        }
        let hasUnsupportedOrder = payload.transactions.contains {
            ($0.side != nil && $0.status == TransactionStatus.partial.rawValue) ||
                (TransactionStatus(rawValue: $0.status) == nil && !["CANCELLED", "PARTIAL_CANCELLED"].contains($0.status))
        }
        if hasUnsupportedOrder {
            return String(localized: "The backup contains orders in states that iOS cannot restore.")
        }
        let planIds = Set(payload.plans.map(\.id))
        if planIds.count != payload.plans.count ||
            !payload.transactions.allSatisfy({ planIds.contains($0.planId) }) ||
            !payload.withdrawals.allSatisfy({ planIds.contains($0.planId) }) {
            return String(localized: "The backup contains transactions or withdrawals without their plans.")
        }
        return nil
    }

    private func gzipDecompress(_ data: Data) throws -> Data {
        guard !data.isEmpty else { return data }
        let nsData = data as NSData

        var stream = z_stream()
        stream.next_in = UnsafeMutablePointer<Bytef>(mutating: nsData.bytes.assumingMemoryBound(to: Bytef.self))
        stream.avail_in = uInt(nsData.length)

        let initResult = inflateInit2_(&stream, MAX_WBITS + 16, // +16 for gzip
                                        ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard initResult == Z_OK else {
            throw NSError(domain: "gunzip", code: Int(initResult), userInfo: [NSLocalizedDescriptionKey: "gunzip init failed"])
        }

        var decompressed = Data()
        let bufferSize = 65536
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer {
            buffer.deallocate()
            inflateEnd(&stream)
        }

        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufferSize)
            let result = inflate(&stream, Z_NO_FLUSH)
            let count = bufferSize - Int(stream.avail_out)
            guard count <= BackupEnvelope.maxPayloadBytes - decompressed.count else {
                throw NSError(domain: "gunzip", code: 1, userInfo: [NSLocalizedDescriptionKey: String(localized: "This backup exceeds the supported size limit.")])
            }
            if count > 0 { decompressed.append(buffer, count: count) }
            if result == Z_STREAM_END { break }
            guard result == Z_OK else {
                throw NSError(domain: "gunzip", code: Int(result), userInfo: [NSLocalizedDescriptionKey: "gunzip decompress failed"])
            }
        } while true

        return decompressed
    }
}
