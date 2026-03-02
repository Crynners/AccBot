import Foundation
import GRDB

/// GRDB Record for monthly_summaries table
struct MonthlySummaryRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "monthly_summaries"

    var id: String  // "YYYY-MM"
    var year: Int
    var month: Int
    var totalInvestedEur: String
    var totalBtcAccumulated: String
    var transactionCount: Int
    var averageBtcPrice: String
    var lastUpdated: Double
}
