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
    @Published var showImportConfig = false
    @Published var sinceDate: Date?
    @Published var isPreviewing = false
    @Published var newTransactionCount = 0
    @Published var skippedTransactionCount = 0
    @Published var cachedPlan: DcaPlan?
    private var parsedTransactions: [Transaction] = []

    private let planId: Int64
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false
    private var importTask: Task<Void, Never>?

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }

    enum ImportMode: String, CaseIterable {
        case csv = "CSV File"
        case api = "Exchange API"

        var localizedName: String {
            String(localized: String.LocalizationValue(rawValue))
        }
    }

    init(planId: Int64) {
        self.planId = planId
    }

    deinit {
        importTask?.cancel()
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadPlan()
    }

    private func loadPlan() {
        cachedPlan = try? deps.activeDatabase.planDao.getById(planId)
    }

    var plan: DcaPlan? {
        cachedPlan
    }

    func importFromCsv(url: URL) {
        do {
            let data = try String(contentsOf: url, encoding: .utf8)
            importFromCsvData(data)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func importFromCsvData(_ data: String) {
        guard let plan = plan else { return }
        errorMessage = nil
        parsedTransactions = []

        Task {
            do {
                let lines = data.components(separatedBy: .newlines)
                    .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }

                // Skip header
                let dataLines = Array(lines.dropFirst())

                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"

                // Get existing order IDs to detect duplicates
                let existingOrderIds = Set(
                    try deps.activeDatabase.transactionDao.getExchangeOrderIdsByPlan(plan.id)
                )

                var newTxs: [Transaction] = []
                var skipped = 0

                for line in dataLines {
                    let columns = parseCsvLine(line)
                    guard columns.count >= 6 else { continue }

                    let orderId = columns[5]
                    if !orderId.isEmpty && existingOrderIds.contains(orderId) {
                        skipped += 1
                        continue
                    }

                    // Coinmate CSV format: Date, Type, Amount, Price, Fee, OrderId
                    let dateStr = columns[0]
                    let amount = Decimal(string: columns[2]) ?? 0
                    let price = Decimal(string: columns[3]) ?? 0
                    let fee = Decimal(string: columns[4]) ?? 0
                    guard let date = formatter.date(from: dateStr) else {
                        skipped += 1
                        continue
                    }

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
                        exchangeOrderId: orderId.isEmpty ? nil : orderId,
                        executedAt: date
                    )

                    newTxs.append(tx)
                }

                parsedTransactions = newTxs
                newTransactionCount = newTxs.count
                skippedTransactionCount = skipped
                totalCount = newTxs.count + skipped
                isPreviewing = true
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func confirmImport() {
        guard !parsedTransactions.isEmpty else { return }
        isImporting = true
        isPreviewing = false
        importedCount = 0

        Task {
            do {
                let count = try deps.activeDatabase.transactionDao.insertBatch(parsedTransactions)
                importedCount = count
                progress = 1.0
                parsedTransactions = []
                isComplete = true
            } catch {
                errorMessage = String(localized: "Import failed: \(error.localizedDescription). No transactions were imported.")
            }
            isImporting = false
        }
    }

    func importFromApi() {
        guard let plan = plan else { return }
        let isSandbox = deps.userPreferences.isSandboxMode()
        guard let credentials = deps.credentialsStore.get(for: plan.exchange, isSandbox: isSandbox) else {
            errorMessage = String(localized: "No credentials found for \(plan.exchange.displayName)")
            return
        }

        isImporting = true
        errorMessage = nil
        importedCount = 0

        importTask = Task {
            let api = deps.exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            let importUseCase = ImportTradeHistoryUseCase(
                transactionDao: deps.activeDatabase.transactionDao
            )

            let stream = importUseCase.importFromApi(
                api: api,
                planId: plan.id,
                crypto: plan.crypto,
                fiat: plan.fiat,
                exchange: plan.exchange,
                sinceDate: sinceDate
            )

            for await event in stream {
                switch event {
                case .fetching(_, let totalFetched):
                    // Approximate progress based on fetched count
                    progress = min(0.9, Double(totalFetched) / 500.0)
                case .complete(let imported, let skipped):
                    importedCount = imported
                    skippedTransactionCount = skipped
                    progress = 1.0
                    isComplete = true
                case .error(let message):
                    errorMessage = message
                default:
                    break
                }
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
