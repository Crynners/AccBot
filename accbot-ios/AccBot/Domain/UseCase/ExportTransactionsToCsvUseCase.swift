import Foundation

enum CsvExportResult {
    case success(csvContent: String, suggestedFileName: String)
    case error(String)
}

/// Exports confirmed fills to CSV.
final class ExportTransactionsToCsvUseCase {
    private let transactionDao: TransactionDao

    init(transactionDao: TransactionDao) {
        self.transactionDao = transactionDao
    }

    func execute(transactions: [Transaction]? = nil) throws -> CsvExportResult {
        let source = try transactions ?? transactionDao.getAllTransactionsOnce()
        let transactions = source.filter {
            ($0.status == .completed || $0.status == .partial) && $0.cryptoAmount > 0 && $0.fiatAmount > 0
        }

        guard !transactions.isEmpty else {
            return .error(String(localized: "No confirmed fills to export."))
        }

        let csvContent = generateCsv(transactions)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        let fileName = "accbot_transactions_\(formatter.string(from: Date())).csv"

        return .success(csvContent: csvContent, suggestedFileName: fileName)
    }

    private func generateCsv(_ transactions: [Transaction]) -> String {
        var csv = "Date,Exchange,Crypto,Fiat,Crypto Amount,Fiat Amount,Price,Fee,Fee Asset,Status,Order ID,Error,Warning\n"

        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime]

        for tx in transactions {
            csv += dateFormatter.string(from: tx.executedAt)
            csv += ","
            csv += escapeCsvField(tx.exchange.displayName)
            csv += ","
            csv += escapeCsvField(tx.crypto)
            csv += ","
            csv += escapeCsvField(tx.fiat)
            csv += ","
            csv += "\(tx.cryptoAmount)"
            csv += ","
            csv += "\(tx.fiatAmount)"
            csv += ","
            csv += "\(tx.price)"
            csv += ","
            csv += "\(tx.fee)"
            csv += ","
            csv += escapeCsvField(tx.feeAsset)
            csv += ","
            csv += escapeCsvField(tx.status.rawValue)
            csv += ","
            csv += escapeCsvField(tx.exchangeOrderId ?? "")
            csv += ","
            csv += escapeCsvField(tx.errorMessage ?? "")
            csv += ","
            csv += escapeCsvField(tx.warningMessage ?? "")
            csv += "\n"
        }

        return csv
    }

    private func escapeCsvField(_ value: String) -> String {
        let first = value.trimmingCharacters(in: .whitespacesAndNewlines).first
        let safe = first.map { "=+-@".contains($0) } == true ? "'" + value : value
        if safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r") {
            return "\"\(safe.replacingOccurrences(of: "\"", with: "\"\""))\""
        }
        return safe
    }
}
