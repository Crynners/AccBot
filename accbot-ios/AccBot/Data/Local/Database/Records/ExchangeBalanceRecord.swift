import Foundation
import GRDB

/// GRDB Record for exchange_balances table
struct ExchangeBalanceRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "exchange_balances"

    var id: String  // "${exchange}_${currency}"
    var exchange: String
    var currency: String
    var balance: String
    var lastUpdated: Double
}
