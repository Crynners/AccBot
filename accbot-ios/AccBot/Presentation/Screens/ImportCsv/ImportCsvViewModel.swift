import Foundation

@MainActor
final class ImportCsvViewModel: ObservableObject {
    @Published var isImporting = false
    @Published var progress: Double = 0
    @Published var importedCount = 0
    @Published var totalCount = 0
    @Published var errorMessage: String?
    @Published var isComplete = false
    @Published var importMode: ImportMode = .csv

    private let planId: Int64
    private let dependencies: AppDependencies

    enum ImportMode: String, CaseIterable {
        case csv = "CSV File"
        case api = "Exchange API"
    }

    init(planId: Int64, dependencies: AppDependencies) {
        self.planId = planId
        self.dependencies = dependencies
    }

    var plan: DcaPlan? {
        try? dependencies.activeDatabase.planDao.getById(planId)
    }

    func importFromCsv(url: URL) {
        guard let plan = plan else { return }
        isImporting = true
        errorMessage = nil
        importedCount = 0

        Task {
            do {
                let data = try String(contentsOf: url, encoding: .utf8)
                let lines = data.components(separatedBy: .newlines)
                    .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }

                // Skip header
                let dataLines = Array(lines.dropFirst())
                totalCount = dataLines.count

                for (index, line) in dataLines.enumerated() {
                    let columns = parseCsvLine(line)
                    guard columns.count >= 6 else { continue }

                    // Coinmate CSV format: Date, Type, Amount, Price, Fee, OrderId
                    let dateStr = columns[0]
                    let amount = Decimal(string: columns[2]) ?? 0
                    let price = Decimal(string: columns[3]) ?? 0
                    let fee = Decimal(string: columns[4]) ?? 0

                    let formatter = DateFormatter()
                    formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
                    let date = formatter.date(from: dateStr) ?? Date()

                    let tx = Transaction(
                        planId: plan.id,
                        exchange: plan.exchange,
                        crypto: plan.crypto,
                        fiat: plan.fiat,
                        fiatAmount: amount * price,
                        cryptoAmount: amount,
                        price: price,
                        fee: fee,
                        feeAsset: plan.fiat,
                        status: .completed,
                        exchangeOrderId: columns.count > 5 ? columns[5] : nil,
                        executedAt: date
                    )

                    try dependencies.activeDatabase.transactionDao.insert(tx)
                    importedCount += 1
                    progress = Double(index + 1) / Double(totalCount)
                }

                isComplete = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isImporting = false
        }
    }

    func importFromApi() {
        guard let plan = plan else { return }
        let isSandbox = dependencies.userPreferences.isSandboxMode()
        guard let credentials = dependencies.credentialsStore.get(for: plan.exchange, isSandbox: isSandbox) else {
            errorMessage = "No credentials found for \(plan.exchange.displayName)"
            return
        }

        isImporting = true
        errorMessage = nil
        importedCount = 0

        Task {
            do {
                let api = dependencies.exchangeApiFactory.create(credentials: credentials)
                var since: Date? = nil
                var hasMore = true

                while hasMore {
                    let page = try await api.getTradeHistory(
                        crypto: plan.crypto,
                        fiat: plan.fiat,
                        since: since,
                        limit: 100
                    )

                    for trade in page.trades where trade.side == "BUY" {
                        let tx = Transaction(
                            planId: plan.id,
                            exchange: plan.exchange,
                            crypto: plan.crypto,
                            fiat: plan.fiat,
                            fiatAmount: trade.fiatAmount,
                            cryptoAmount: trade.cryptoAmount,
                            price: trade.price,
                            fee: trade.fee,
                            feeAsset: trade.feeAsset,
                            status: .completed,
                            exchangeOrderId: trade.orderId,
                            executedAt: trade.timestamp
                        )
                        try dependencies.activeDatabase.transactionDao.insert(tx)
                        importedCount += 1
                    }

                    hasMore = page.hasMore
                    since = page.trades.last?.timestamp
                }

                isComplete = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isImporting = false
        }
    }

    private func parseCsvLine(_ line: String) -> [String] {
        var result: [String] = []
        var current = ""
        var inQuotes = false

        for char in line {
            if char == "\"" {
                inQuotes.toggle()
            } else if char == "," && !inQuotes {
                result.append(current.trimmingCharacters(in: .whitespaces))
                current = ""
            } else {
                current.append(char)
            }
        }
        result.append(current.trimmingCharacters(in: .whitespaces))
        return result
    }
}
