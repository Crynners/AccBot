import Foundation
import GRDB

/// GRDB Record for transactions table
struct TransactionRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "transactions"

    var id: Int64?
    var planId: Int64
    var exchange: String
    var crypto: String
    var fiat: String
    var fiatAmount: String
    var cryptoAmount: String
    var price: String
    var fee: String
    var feeAsset: String
    var status: String
    var exchangeOrderId: String?
    var errorMessage: String?
    var warningMessage: String?
    var executedAt: Double

    func toDomain() -> Transaction {
        Transaction(
            id: id ?? 0,
            planId: planId,
            exchange: Exchange(rawValue: exchange) ?? .coinmate,
            crypto: crypto,
            fiat: fiat,
            fiatAmount: Decimal(string: fiatAmount) ?? 0,
            cryptoAmount: Decimal(string: cryptoAmount) ?? 0,
            price: Decimal(string: price) ?? 0,
            fee: Decimal(string: fee) ?? 0,
            feeAsset: feeAsset,
            status: TransactionStatus(rawValue: status) ?? .failed,
            exchangeOrderId: exchangeOrderId,
            errorMessage: errorMessage,
            warningMessage: warningMessage,
            executedAt: Date(timeIntervalSince1970: executedAt)
        )
    }

    static func fromDomain(_ tx: Transaction) -> TransactionRecord {
        TransactionRecord(
            id: tx.id == 0 ? nil : tx.id,
            planId: tx.planId,
            exchange: tx.exchange.rawValue,
            crypto: tx.crypto,
            fiat: tx.fiat,
            fiatAmount: "\(tx.fiatAmount)",
            cryptoAmount: "\(tx.cryptoAmount)",
            price: "\(tx.price)",
            fee: "\(tx.fee)",
            feeAsset: tx.feeAsset,
            status: tx.status.rawValue,
            exchangeOrderId: tx.exchangeOrderId,
            errorMessage: tx.errorMessage,
            warningMessage: tx.warningMessage,
            executedAt: tx.executedAt.timeIntervalSince1970
        )
    }
}
