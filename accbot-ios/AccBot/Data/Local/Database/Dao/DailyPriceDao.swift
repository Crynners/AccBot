import Foundation
import GRDB

/// DAO for daily_prices table
final class DailyPriceDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func getPrice(crypto: String, fiat: String, dateEpochDay: Int64) throws -> Decimal? {
        try dbPool.read { db in
            let record = try DailyPriceRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("fiat") == fiat)
                .filter(Column("dateEpochDay") == dateEpochDay)
                .fetchOne(db)
            return record.flatMap { Decimal(string: $0.price) }
        }
    }

    func getPrices(crypto: String, fiat: String, fromDay: Int64, toDay: Int64) throws -> [(day: Int64, price: Decimal)] {
        try dbPool.read { db in
            try DailyPriceRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("fiat") == fiat)
                .filter(Column("dateEpochDay") >= fromDay)
                .filter(Column("dateEpochDay") <= toDay)
                .order(Column("dateEpochDay").asc)
                .fetchAll(db)
                .compactMap { record in
                    guard let price = Decimal(string: record.price) else { return nil }
                    return (day: record.dateEpochDay, price: price)
                }
        }
    }

    func getLatestDay(crypto: String, fiat: String) throws -> Int64? {
        try dbPool.read { db in
            let row = try Row.fetchOne(db, sql: """
                SELECT MAX(dateEpochDay) as maxDay
                FROM daily_prices
                WHERE crypto = ? AND fiat = ?
                """, arguments: [crypto, fiat])
            return row?["maxDay"] as? Int64
        }
    }

    func insertOrReplace(crypto: String, fiat: String, dateEpochDay: Int64, price: Decimal) throws {
        try dbPool.write { db in
            let record = DailyPriceRecord(
                crypto: crypto,
                fiat: fiat,
                dateEpochDay: dateEpochDay,
                price: "\(price)",
                fetchedAt: Date().timeIntervalSince1970
            )
            try record.save(db)
        }
    }

    func insertBatch(_ records: [(crypto: String, fiat: String, day: Int64, price: Decimal)]) throws {
        try dbPool.write { db in
            for r in records {
                let record = DailyPriceRecord(
                    crypto: r.crypto,
                    fiat: r.fiat,
                    dateEpochDay: r.day,
                    price: "\(r.price)",
                    fetchedAt: Date().timeIntervalSince1970
                )
                try record.save(db)
            }
        }
    }

    func getEarliestDay(crypto: String, fiat: String) throws -> Int64? {
        try dbPool.read { db in
            let row = try Row.fetchOne(db, sql: """
                SELECT MIN(dateEpochDay) as minDay
                FROM daily_prices
                WHERE crypto = ? AND fiat = ?
                """, arguments: [crypto, fiat])
            return row?["minDay"] as? Int64
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try DailyPriceRecord.deleteAll(db)
        }
    }
}
