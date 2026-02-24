import Foundation
import GRDB

/// GRDB Record for withdrawal_thresholds table
struct WithdrawalThresholdRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "withdrawal_thresholds"

    var crypto: String
    var exchange: String
    var thresholdAmount: String

    func toDomain() -> WithdrawalThreshold {
        WithdrawalThreshold(
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
