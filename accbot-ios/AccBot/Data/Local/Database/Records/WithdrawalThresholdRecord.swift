import Foundation
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "DataParsing")

/// GRDB Record for withdrawal_thresholds table
struct WithdrawalThresholdRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "withdrawal_thresholds"

    var crypto: String
    var exchange: String
    var thresholdAmount: String

    func toDomain() -> WithdrawalThreshold {
        if Exchange(rawValue: exchange) == nil {
            logger.warning("Unknown exchange rawValue '\(self.exchange)' in withdrawal threshold, defaulting to coinmate")
        }
        if Decimal(string: thresholdAmount) == nil {
            logger.error("Invalid thresholdAmount '\(self.thresholdAmount)' in withdrawal threshold")
        }
        return WithdrawalThreshold(
            crypto: crypto,
            exchange: Exchange(rawValue: exchange) ?? .coinmate,
            thresholdAmount: Decimal(string: thresholdAmount) ?? 0
        )
    }

    static func fromDomain(_ wt: WithdrawalThreshold) -> WithdrawalThresholdRecord {
        WithdrawalThresholdRecord(
            crypto: wt.crypto,
            exchange: wt.exchange.rawValue,
            thresholdAmount: "\(wt.thresholdAmount)"
        )
    }
}
