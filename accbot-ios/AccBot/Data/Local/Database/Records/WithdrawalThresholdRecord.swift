import Foundation
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "DataParsing")

/// GRDB Record for withdrawal_thresholds table.
/// PK is (crypto, connectionId) - one threshold per crypto per connection.
struct WithdrawalThresholdRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "withdrawal_thresholds"

    var crypto: String
    var connectionId: Int64
    var thresholdAmount: String

    func toDomain() -> WithdrawalThreshold {
        if Decimal(string: thresholdAmount) == nil {
            logger.error("Invalid thresholdAmount '\(self.thresholdAmount)' in withdrawal threshold")
        }
        return WithdrawalThreshold(
            crypto: crypto,
            connectionId: connectionId,
            thresholdAmount: Decimal(string: thresholdAmount) ?? 0
        )
    }

    static func fromDomain(_ wt: WithdrawalThreshold) -> WithdrawalThresholdRecord {
        WithdrawalThresholdRecord(
            crypto: wt.crypto,
            connectionId: wt.connectionId,
            thresholdAmount: "\(wt.thresholdAmount)"
        )
    }
}
