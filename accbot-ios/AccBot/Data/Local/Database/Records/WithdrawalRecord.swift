import Foundation
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "DataParsing")

/// GRDB Record for withdrawals table
struct WithdrawalRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "withdrawals"

    var id: Int64?
    var planId: Int64
    var exchange: String
    var crypto: String
    var amount: String
    var address: String
    var txHash: String?
    var fee: String
    var status: String
    var errorMessage: String?
    var createdAt: Double

    func toDomain() -> Withdrawal {
        if Exchange(rawValue: exchange) == nil {
            logger.warning("Unknown exchange rawValue '\(self.exchange)' in withdrawal \(self.id ?? 0), defaulting to coinmate")
        }
        if Decimal(string: amount) == nil {
            logger.error("Invalid amount '\(self.amount)' in withdrawal \(self.id ?? 0)")
        }
        if Decimal(string: fee) == nil {
            logger.error("Invalid fee '\(self.fee)' in withdrawal \(self.id ?? 0)")
        }
        return Withdrawal(
            id: id ?? 0,
            planId: planId,
            exchange: Exchange(rawValue: exchange) ?? .coinmate,
            crypto: crypto,
            amount: Decimal(string: amount) ?? 0,
            address: address,
            txHash: txHash,
            fee: Decimal(string: fee) ?? 0,
            status: WithdrawalStatus(rawValue: status) ?? .failed,
            errorMessage: errorMessage,
            createdAt: Date(timeIntervalSince1970: createdAt)
        )
    }

    static func fromDomain(_ w: Withdrawal) -> WithdrawalRecord {
        WithdrawalRecord(
            id: w.id == 0 ? nil : w.id,
            planId: w.planId,
            exchange: w.exchange.rawValue,
            crypto: w.crypto,
            amount: "\(w.amount)",
            address: w.address,
            txHash: w.txHash,
            fee: "\(w.fee)",
            status: w.status.rawValue,
            errorMessage: w.errorMessage,
            createdAt: w.createdAt.timeIntervalSince1970
        )
    }
}
