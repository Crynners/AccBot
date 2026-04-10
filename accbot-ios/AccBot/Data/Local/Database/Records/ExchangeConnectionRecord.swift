import Foundation
import GRDB

/// GRDB Record for exchange_connections table.
/// Represents one set of API credentials for one exchange.
/// Multiple connections can target the same exchange (e.g. two Coinmate sub-accounts).
struct ExchangeConnectionRecord: Codable, FetchableRecord, MutablePersistableRecord, Identifiable {
    static let databaseTableName = "exchange_connections"

    var id: Int64?
    var exchange: String
    var name: String
    var createdAt: Double    // timeIntervalSince1970
    var displayOrder: Int

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }

    // MARK: - Domain Mapping

    func toDomain() -> ExchangeConnection {
        ExchangeConnection(
            id: id ?? 0,
            exchange: Exchange(rawValue: exchange) ?? .coinmate,
            name: name,
            createdAt: Date(timeIntervalSince1970: createdAt),
            displayOrder: displayOrder
        )
    }

    static func fromDomain(_ c: ExchangeConnection) -> ExchangeConnectionRecord {
        ExchangeConnectionRecord(
            id: c.id == 0 ? nil : c.id,
            exchange: c.exchange.rawValue,
            name: c.name,
            createdAt: c.createdAt.timeIntervalSince1970,
            displayOrder: c.displayOrder
        )
    }
}
