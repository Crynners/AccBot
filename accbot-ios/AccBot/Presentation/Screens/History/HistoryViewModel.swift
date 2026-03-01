import Foundation
import Combine

@MainActor
final class HistoryViewModel: ObservableObject {
    @Published var transactions: [Transaction] = []
    @Published var isLoading = true
    @Published var showFilterSheet = false
    @Published var showExportSheet = false
    @Published var csvFileUrl: URL?
    @Published var showDeleteConfirmation = false
    @Published var transactionToDelete: Transaction?
    @Published var errorMessage: String?
    @Published var searchText: String = ""

    // Undo support
    @Published var undoTransaction: Transaction?
    @Published var showUndoSnackbar = false
    private var undoTask: Task<Void, Never>?
    private var undoInsertIndex: Int?

    // Filters
    @Published var filterCrypto: String?
    @Published var filterFiat: String?
    @Published var filterExchange: Exchange?
    @Published var filterStatus: TransactionStatus?
    @Published var filterDateFrom: Date?
    @Published var filterDateTo: Date?

    // Dynamic crypto list
    @Published var availableCryptos: [String] = []

    // Sort
    @Published var sortOption: SortOption = .dateNewest

    // Pagination
    private var currentPage = 0
    private let pageSize = 50
    @Published var hasMore = true

    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }

    enum SortOption: String, CaseIterable {
        case dateNewest = "Date (Newest)"
        case dateOldest = "Date (Oldest)"
        case amountHighest = "Amount (Highest)"
        case amountLowest = "Amount (Lowest)"
        case priceHighest = "Price (Highest)"
        case priceLowest = "Price (Lowest)"

        var localizedName: String {
            String(localized: String.LocalizationValue(rawValue))
        }
    }

    init(filterCrypto: String? = nil, filterFiat: String? = nil) {
        self.filterCrypto = filterCrypto
        self.filterFiat = filterFiat
    }

    deinit {
        undoTask?.cancel()
        // Commit any pending soft-deleted transaction so it doesn't get lost
        MainActor.assumeIsolated {
            if let tx = undoTransaction, let deps = dependencies {
                try? deps.activeDatabase.transactionDao.delete(id: tx.id)
            }
        }
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadData()
    }

    func loadData() {
        currentPage = 0
        transactions = []
        hasMore = true
        loadAvailableCryptos()
        loadNextPage()
    }

    private func loadAvailableCryptos() {
        do {
            availableCryptos = try deps.activeDatabase.transactionDao.getDistinctCryptos()
        } catch {
            availableCryptos = []
        }
    }

    func loadNextPage() {
        guard hasMore else { return }
        isLoading = currentPage == 0

        do {
            let newTxs = try deps.activeDatabase.transactionDao.getFiltered(
                crypto: filterCrypto,
                fiat: filterFiat,
                exchange: filterExchange,
                status: filterStatus,
                from: filterDateFrom,
                to: filterDateTo,
                limit: pageSize,
                offset: currentPage * pageSize
            )

            let allTxs: [Transaction]
            if sortOption == .dateNewest {
                // DB already returns results sorted by executedAt desc — no re-sort needed
                allTxs = transactions + newTxs
            } else {
                // Non-default sort: merge and re-sort
                allTxs = applySorting(transactions + newTxs)
            }

            transactions = allTxs
            hasMore = newTxs.count >= pageSize
            currentPage += 1
        } catch {
            hasMore = false
        }
        isLoading = false
    }

    func confirmDeleteTransaction(_ transaction: Transaction) {
        transactionToDelete = transaction
        showDeleteConfirmation = true
    }

    func executeDelete() {
        guard let tx = transactionToDelete else { return }
        transactionToDelete = nil
        softDelete(tx)
    }

    // Legacy direct delete (still used by swipe)
    func deleteTransaction(_ transaction: Transaction) {
        softDelete(transaction)
    }

    private func softDelete(_ transaction: Transaction) {
        // Cancel any pending undo from a previous delete
        commitDelete()

        // Remove from UI and remember position
        if let index = transactions.firstIndex(where: { $0.id == transaction.id }) {
            undoInsertIndex = index
            transactions.remove(at: index)
        }
        undoTransaction = transaction
        showUndoSnackbar = true

        // Start 8-second timer for permanent delete
        undoTask?.cancel()
        undoTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(8))
            guard !Task.isCancelled else { return }
            self?.commitDelete()
        }
    }

    func undoDelete() {
        undoTask?.cancel()
        undoTask = nil
        guard let tx = undoTransaction else { return }

        // Re-insert at original position
        let index = min(undoInsertIndex ?? transactions.count, transactions.count)
        transactions.insert(tx, at: index)

        undoTransaction = nil
        undoInsertIndex = nil
        showUndoSnackbar = false
    }

    func commitDelete() {
        undoTask?.cancel()
        undoTask = nil
        if let tx = undoTransaction {
            try? deps.activeDatabase.transactionDao.delete(id: tx.id)
        }
        undoTransaction = nil
        undoInsertIndex = nil
        showUndoSnackbar = false
    }

    func clearFilter(_ filter: ActiveFilter) {
        switch filter {
        case .crypto: filterCrypto = nil; filterFiat = nil
        case .exchange: filterExchange = nil
        case .status: filterStatus = nil
        case .dateFrom: filterDateFrom = nil
        case .dateTo: filterDateTo = nil
        }
        loadData()
    }

    func clearFilters() {
        filterCrypto = nil
        filterFiat = nil
        filterExchange = nil
        filterStatus = nil
        filterDateFrom = nil
        filterDateTo = nil
        loadData()
    }

    var filteredTransactions: [Transaction] {
        guard !searchText.isEmpty else { return transactions }
        let query = searchText.lowercased()
        return transactions.filter { tx in
            tx.crypto.lowercased().contains(query) ||
            tx.fiat.lowercased().contains(query) ||
            tx.exchange.displayName.lowercased().contains(query) ||
            tx.exchange.rawValue.lowercased().contains(query) ||
            (tx.exchangeOrderId?.lowercased().contains(query) ?? false) ||
            "\(tx.fiatAmount)".contains(query) ||
            "\(tx.cryptoAmount)".contains(query) ||
            "\(tx.price)".contains(query)
        }
    }

    var hasActiveFilters: Bool {
        filterCrypto != nil || filterFiat != nil || filterExchange != nil ||
        filterStatus != nil || filterDateFrom != nil || filterDateTo != nil
    }

    var activeFilters: [ActiveFilter] {
        var filters = [ActiveFilter]()
        if let crypto = filterCrypto {
            filters.append(.crypto(crypto))
        }
        if let exchange = filterExchange {
            filters.append(.exchange(exchange))
        }
        if let status = filterStatus {
            filters.append(.status(status))
        }
        if let from = filterDateFrom {
            filters.append(.dateFrom(from))
        }
        if let to = filterDateTo {
            filters.append(.dateTo(to))
        }
        return filters
    }

    func setSortOption(_ option: SortOption) {
        sortOption = option
        // Re-sort existing data
        transactions = applySorting(transactions)
    }

    private func applySorting(_ txs: [Transaction]) -> [Transaction] {
        switch sortOption {
        case .dateNewest: return txs.sorted { $0.executedAt > $1.executedAt }
        case .dateOldest: return txs.sorted { $0.executedAt < $1.executedAt }
        case .amountHighest: return txs.sorted { $0.fiatAmount > $1.fiatAmount }
        case .amountLowest: return txs.sorted { $0.fiatAmount < $1.fiatAmount }
        case .priceHighest: return txs.sorted { $0.price > $1.price }
        case .priceLowest: return txs.sorted { $0.price < $1.price }
        }
    }

    func exportCsv() {
        let header = "Date,Exchange,Crypto,Fiat,Fiat Amount,Crypto Amount,Price,Fee,Fee Asset,Status,Order ID\n"
        let dateFormatter = ISO8601DateFormatter()

        let rows = transactions.map { tx in
            [
                dateFormatter.string(from: tx.executedAt),
                csvQuote(tx.exchange.displayName),
                tx.crypto,
                tx.fiat,
                "\(tx.fiatAmount)",
                "\(tx.cryptoAmount)",
                "\(tx.price)",
                "\(tx.fee)",
                csvQuote(tx.feeAsset),
                tx.status.rawValue,
                csvQuote(tx.exchangeOrderId ?? ""),
            ].joined(separator: ",")
        }.joined(separator: "\n")

        let csvContent = header + rows
        let tempDir = FileManager.default.temporaryDirectory
        let fileUrl = tempDir.appendingPathComponent("accbot_transactions.csv")

        do {
            try csvContent.write(to: fileUrl, atomically: true, encoding: .utf8)
            csvFileUrl = fileUrl
            showExportSheet = true
        } catch {
            errorMessage = String(localized: "Export failed: \(error.localizedDescription)")
        }
    }

    private func csvQuote(_ value: String) -> String {
        if value.contains(",") || value.contains("\"") || value.contains("\n") {
            return "\"\(value.replacingOccurrences(of: "\"", with: "\"\""))\""
        }
        return value
    }
}

// MARK: - Active Filter Type

enum ActiveFilter: Identifiable {
    case crypto(String)
    case exchange(Exchange)
    case status(TransactionStatus)
    case dateFrom(Date)
    case dateTo(Date)

    var id: String {
        switch self {
        case .crypto(let v): return "crypto_\(v)"
        case .exchange(let v): return "exchange_\(v.rawValue)"
        case .status(let v): return "status_\(v.rawValue)"
        case .dateFrom: return "dateFrom"
        case .dateTo: return "dateTo"
        }
    }

    var label: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        switch self {
        case .crypto(let v): return v
        case .exchange(let v): return v.displayName
        case .status(let v): return v.displayName
        case .dateFrom(let d): return String(localized: "From") + " \(formatter.string(from: d))"
        case .dateTo(let d): return String(localized: "To") + " \(formatter.string(from: d))"
        }
    }
}
