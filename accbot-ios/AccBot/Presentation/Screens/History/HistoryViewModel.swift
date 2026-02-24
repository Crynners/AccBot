import Foundation
import Combine

@MainActor
final class HistoryViewModel: ObservableObject {
    @Published var transactions: [Transaction] = []
    @Published var isLoading = true
    @Published var showFilterSheet = false
    @Published var showExportSheet = false
    @Published var csvFileUrl: URL?

    // Filters
    @Published var filterCrypto: String?
    @Published var filterFiat: String?
    @Published var filterExchange: Exchange?
    @Published var filterStatus: TransactionStatus?
    @Published var filterDateFrom: Date?
    @Published var filterDateTo: Date?

    // Sort
    @Published var sortBy: SortField = .date
    @Published var sortAscending = false

    // Pagination
    private var currentPage = 0
    private let pageSize = 50
    @Published var hasMore = true

    private let dependencies: AppDependencies

    enum SortField: String, CaseIterable {
        case date = "Date"
        case amount = "Amount"
        case price = "Price"
    }

    init(dependencies: AppDependencies, filterCrypto: String? = nil, filterFiat: String? = nil) {
        self.dependencies = dependencies
        self.filterCrypto = filterCrypto
        self.filterFiat = filterFiat
    }

    func loadData() {
        currentPage = 0
        transactions = []
        hasMore = true
        loadNextPage()
    }

    func loadNextPage() {
        guard hasMore else { return }
        isLoading = currentPage == 0

        do {
            let newTxs = try dependencies.activeDatabase.transactionDao.getFiltered(
                crypto: filterCrypto,
                fiat: filterFiat,
                exchange: filterExchange,
                status: filterStatus,
                from: filterDateFrom,
                to: filterDateTo,
                limit: pageSize,
                offset: currentPage * pageSize
            )

            transactions.append(contentsOf: newTxs)
            hasMore = newTxs.count >= pageSize
            currentPage += 1
        } catch {
            hasMore = false
        }
        isLoading = false
    }

    func deleteTransaction(_ transaction: Transaction) {
        try? dependencies.activeDatabase.transactionDao.delete(id: transaction.id)
        transactions.removeAll { $0.id == transaction.id }
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

    var hasActiveFilters: Bool {
        filterCrypto != nil || filterFiat != nil || filterExchange != nil ||
        filterStatus != nil || filterDateFrom != nil || filterDateTo != nil
    }

    func exportCsv() {
        let header = "Date,Exchange,Crypto,Fiat,Fiat Amount,Crypto Amount,Price,Fee,Fee Asset,Status,Order ID\n"
        let dateFormatter = ISO8601DateFormatter()

        let rows = transactions.map { tx in
            [
                dateFormatter.string(from: tx.executedAt),
                tx.exchange.displayName,
                tx.crypto,
                tx.fiat,
                "\(tx.fiatAmount)",
                "\(tx.cryptoAmount)",
                "\(tx.price)",
                "\(tx.fee)",
                tx.feeAsset,
                tx.status.rawValue,
                tx.exchangeOrderId ?? "",
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
            // Handle error
        }
    }
}
