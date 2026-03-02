import Foundation

struct ParsedTransaction {
    let orderId: String
    let executedAt: Date
    let cryptoAmount: Decimal
    let crypto: String
    let price: Decimal
    let fiat: String
    let fee: Decimal
    let feeAsset: String
    let fiatAmount: Decimal
}

enum CsvImportResult {
    case success(importedCount: Int)
    case error(String)
}

/// Parses and imports Coinmate CSV transaction exports with deduplication.
final class ImportCoinmateCsvUseCase {
    private let transactionDao: TransactionDao

    init(transactionDao: TransactionDao) {
        self.transactionDao = transactionDao
    }

    /// Parse a Coinmate CSV export, filtering to MARKET_BUY with OK status for the given pair.
    func parseCsv(content: String, crypto: String, fiat: String) -> [ParsedTransaction] {
        let lines = content.components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard lines.count >= 2 else { return [] }

        return lines.dropFirst().compactMap { line in
            parseLine(line, crypto: crypto, fiat: fiat)
        }
    }

    /// Count how many parsed transactions are new (not yet in DB).
    func countNew(parsed: [ParsedTransaction], existingOrderIds: Set<String>) -> (new: Int, skipped: Int) {
        let newCount = parsed.filter { !existingOrderIds.contains($0.orderId) }.count
        return (new: newCount, skipped: parsed.count - newCount)
    }

    /// Convert parsed transactions to domain Transaction objects, skipping existing.
    func toTransactions(
        parsed: [ParsedTransaction],
        planId: Int64,
        exchange: Exchange,
        existingOrderIds: Set<String>
    ) -> [Transaction] {
        parsed
            .filter { !existingOrderIds.contains($0.orderId) }
            .map { tx in
                Transaction(
                    planId: planId,
                    exchange: exchange,
                    crypto: tx.crypto,
                    fiat: tx.fiat,
                    fiatAmount: tx.fiatAmount,
                    cryptoAmount: tx.cryptoAmount,
                    price: tx.price,
                    fee: tx.fee,
                    feeAsset: tx.feeAsset,
                    status: .completed,
                    exchangeOrderId: tx.orderId,
                    executedAt: tx.executedAt
                )
            }
    }

    func getExistingOrderIds(planId: Int64) throws -> Set<String> {
        Set(try transactionDao.getExchangeOrderIdsByPlan(planId))
    }

    func importTransactions(_ transactions: [Transaction]) throws -> CsvImportResult {
        let count = try transactionDao.insertBatch(transactions)
        return .success(importedCount: count)
    }

    // MARK: - Private

    private func parseLine(_ line: String, crypto: String, fiat: String) -> ParsedTransaction? {
        let fields = splitCsvLine(line, delimiter: Character(";"))
        guard fields.count >= 13 else { return nil }

        let orderId = fields[0].trimmingCharacters(in: .whitespaces)
        let dateStr = fields[1].trimmingCharacters(in: .whitespaces)
        let type = fields[2].trimmingCharacters(in: .whitespaces)
        let cryptoAmount = fields[3].trimmingCharacters(in: .whitespaces)
        let cryptoCurrency = fields[4].trimmingCharacters(in: .whitespaces)
        let price = fields[5].trimmingCharacters(in: .whitespaces)
        let priceCurrency = fields[6].trimmingCharacters(in: .whitespaces)
        let fee = fields[7].trimmingCharacters(in: .whitespaces)
        let feeCurrency = fields[8].trimmingCharacters(in: .whitespaces)
        let total = fields[9].trimmingCharacters(in: .whitespaces)
        let status = fields[12].trimmingCharacters(in: .whitespaces)

        guard type == "MARKET_BUY", status == "OK" else { return nil }
        guard cryptoCurrency.caseInsensitiveCompare(crypto) == .orderedSame else { return nil }
        guard priceCurrency.caseInsensitiveCompare(fiat) == .orderedSame else { return nil }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.timeZone = .current

        guard let date = formatter.date(from: dateStr),
              let cryptoAmt = Decimal(string: cryptoAmount),
              let priceVal = Decimal(string: price),
              let feeVal = Decimal(string: fee),
              let totalVal = Decimal(string: total)
        else { return nil }

        return ParsedTransaction(
            orderId: orderId,
            executedAt: date,
            cryptoAmount: cryptoAmt,
            crypto: cryptoCurrency.uppercased(),
            price: priceVal,
            fiat: priceCurrency.uppercased(),
            fee: feeVal,
            feeAsset: feeCurrency.uppercased(),
            fiatAmount: abs(totalVal)
        )
    }

    /// Split semicolon-delimited CSV line, respecting quoted fields.
    private func splitCsvLine(_ line: String, delimiter: Character) -> [String] {
        var fields: [String] = []
        var current = ""
        var inQuotes = false
        var chars = line.makeIterator()

        while let c = chars.next() {
            if c == "\"" {
                if inQuotes {
                    // Peek for escaped quote
                    // Can't peek with iterator, handle inline
                    inQuotes = false
                } else {
                    inQuotes = true
                }
            } else if c == delimiter && !inQuotes {
                fields.append(current)
                current = ""
            } else {
                current.append(c)
            }
        }
        fields.append(current)
        return fields
    }
}
