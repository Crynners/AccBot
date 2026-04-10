import Foundation
import GRDB

/// GRDB Record for exchange_balances table.
/// PK is (connectionId, currency). The exchange column is a redundant fallback
/// for display when the parent connection is deleted.
struct ExchangeBalanceRecord: FetchableRecord, PersistableRecord {
    static let databaseTableName = "exchange_balances"

    var connectionId: Int64
    var currency: String
    var exchange: String
    var balance: String
    var lastUpdated: Double

    // Composite PK
    init(row: Row) {
        connectionId = row["connectionId"]
        currency = row["currency"]
        exchange = row["exchange"]
        balance = row["balance"]
        lastUpdated = row["lastUpdated"]
    }

    func encode(to container: inout PersistenceContainer) {
        container["connectionId"] = connectionId
        container["currency"] = currency
        container["exchange"] = exchange
        container["balance"] = balance
        container["lastUpdated"] = lastUpdated
    }

    init(connectionId: Int64, currency: String, exchange: String, balance: String, lastUpdated: Double) {
        self.connectionId = connectionId
        self.currency = currency
        self.exchange = exchange
        self.balance = balance
        self.lastUpdated = lastUpdated
    }
}
