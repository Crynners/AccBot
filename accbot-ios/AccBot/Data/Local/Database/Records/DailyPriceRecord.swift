import Foundation
import GRDB

/// GRDB Record for daily_prices table
struct DailyPriceRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "daily_prices"

    var crypto: String
    var fiat: String
    var dateEpochDay: Int64  // days since epoch
    var price: String
    var fetchedAt: Double
}
