import Foundation
import Combine
import UIKit

enum ChartZoomLevel: Equatable, Hashable {
    case overview
    case year(Int)
    case month(Int, Int) // year, month

    var title: String {
        switch self {
        case .overview: return String(localized: "All Time")
        case .year(let y): return "\(y)"
        case .month(let y, let m):
            return AccBotFormatters.monthYearLabel(month: m, year: y)
        }
    }
}

enum PairPage: Equatable, Hashable {
    case aggregate(fiat: String)
    case singlePair(crypto: String, fiat: String)

    var label: String {
        switch self {
        case .aggregate(let fiat): return String(localized: "All Crypto/\(fiat)")
        case .singlePair(let crypto, let fiat): return "\(crypto)/\(fiat)"
        }
    }
}

@MainActor
final class PortfolioViewModel: ObservableObject {
    @Published var pages: [PairPage] = []
    @Published var selectedPageIndex = 0
    @Published var isLoading = true
    @Published var isRefreshing = false
    @Published var portfolioValue: Decimal?
    @Published var totalInvested: Decimal = 0
    @Published var totalCrypto: Decimal = 0
    @Published var avgBuyPrice: Decimal = 0
    @Published var transactionCount = 0
    @Published var roiPercent: Decimal?
    @Published var currentPrice: Decimal?
    @Published var chartData: [ChartPoint] = []
    @Published var selectedChartSeries: ChartSeries = .portfolioValue
    @Published var denomination: Denomination = .fiat
    @Published var exchangeFilter: Exchange?
    @Published var zoomLevel: ChartZoomLevel = .overview
    @Published var visibleSeries: Set<ChartSeries> = [.portfolioValue]
    @Published var availableExchanges: [Exchange] = []
    @Published var availableYears: [Int] = []
    @Published var availableMonths: [Int] = []
    @Published var kpiSnapshots: [KpiSnapshot] = []
    @Published var periodRoiPercent: Decimal?
    @Published var periodRoiLabel: String?

    struct KpiSnapshot {
        let date: Date
        let portfolioValue: Decimal
        let totalInvested: Decimal
        let roiPercent: Decimal?
        let avgBuyPrice: Decimal
        let cumulativeCrypto: Decimal
        let transactionCount: Int
    }

    // Legacy compatibility
    var pairs: [(crypto: String, fiat: String)] {
        pages.compactMap {
            if case .singlePair(let c, let f) = $0 { return (c, f) }
            return nil
        }
    }
    var selectedPairIndex: Int { selectedPageIndex }

    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }
    private var cancellables = Set<AnyCancellable>()
    private var activeTask: Task<Void, Never>?
    var lastLoadedAt: Date?
    private var priceCache: [String: (price: Decimal, fetchedAt: Date)] = [:]
    private let priceCacheTTL: TimeInterval = 60

    deinit {
        activeTask?.cancel()
    }

    struct ChartPoint: Identifiable {
        var id: String { "\(series.rawValue)-\(date.timeIntervalSince1970)" }
        let date: Date
        let value: Double   // pre-converted from Decimal to avoid repeated conversion during Chart rendering
        let series: ChartSeries
    }

    enum ChartSeries: String, CaseIterable {
        case portfolioValue = "Portfolio Value"
        case costBasis = "Cost Basis"
        case cryptoPrice = "Price"
        case avgBuyPrice = "Avg Buy Price"
        case accumulatedCrypto = "Accumulated"

        var localizedName: String {
            String(localized: String.LocalizationValue(rawValue))
        }
    }

    enum Denomination: String, CaseIterable {
        case fiat = "FIAT"
        case crypto = "CRYPTO"
    }

    var currentPage: PairPage? {
        guard selectedPageIndex < pages.count else { return nil }
        return pages[selectedPageIndex]
    }

    var currentPair: (crypto: String, fiat: String)? {
        guard let page = currentPage else { return nil }
        switch page {
        case .singlePair(let c, let f): return (c, f)
        case .aggregate(let f): return (crypto: "ALL", fiat: f)
        }
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        observeTransactions()
    }

    private func observeTransactions() {
        deps.activeDatabase.transactionDao.observeCount()
            .removeDuplicates()
            .dropFirst()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { [weak self] _ in
                    self?.activeTask?.cancel()
                    self?.activeTask = Task { await self?.loadData() }
                }
            )
            .store(in: &cancellables)
    }

    func loadData() async {
        isLoading = true
        do {
            let rawPairs = try deps.activeDatabase.transactionDao.getDistinctPairs()

            // Build pages: aggregate views + individual pairs
            var newPages = [PairPage]()
            let fiatGroups = Dictionary(grouping: rawPairs, by: { $0.fiat })
            for (fiat, group) in fiatGroups.sorted(by: { $0.key < $1.key }) {
                if group.count > 1 {
                    newPages.append(.aggregate(fiat: fiat))
                }
                for pair in group {
                    newPages.append(.singlePair(crypto: pair.crypto, fiat: pair.fiat))
                }
            }
            pages = newPages

            if !pages.isEmpty {
                await loadPageData()
            }
        } catch {
            pages = []
        }
        isLoading = false
        announceForVoiceOver(String(localized: "Portfolio loaded"))
    }

    func refresh() async {
        isRefreshing = true
        await loadPageData()
        isRefreshing = false
        announceForVoiceOver(String(localized: "Portfolio refreshed"))
    }

    func refreshIfStale() async {
        guard let lastLoaded = lastLoadedAt else { return }
        if Date().timeIntervalSince(lastLoaded) > 300 { // 5 minutes
            await refresh()
        }
    }

    func selectPage(at index: Int) {
        guard index < pages.count else { return }
        selectedPageIndex = index
        zoomLevel = .overview
        reloadPage()
    }

    func selectPair(crypto: String, fiat: String) {
        if let index = pages.firstIndex(where: {
            if case .singlePair(let c, let f) = $0 { return c == crypto && f == fiat }
            return false
        }) {
            selectPage(at: index)
        }
    }

    func setZoomLevel(_ level: ChartZoomLevel) {
        zoomLevel = level
        reloadPage()
    }

    func toggleSeries(_ series: ChartSeries) {
        if visibleSeries.contains(series) {
            if visibleSeries.count > 1 { visibleSeries.remove(series) }
        } else {
            visibleSeries.insert(series)
        }
        reloadPage()
    }

    func setExchangeFilter(_ exchange: Exchange?) {
        exchangeFilter = exchange
        reloadPage()
    }

    func setDenomination(_ denom: Denomination) {
        denomination = denom
        reloadPage()
    }

    func navigatePrev() {
        switch zoomLevel {
        case .year(let y):
            if availableYears.contains(y - 1) { zoomLevel = .year(y - 1); reloadPage() }
        case .month(let y, let m):
            let prev = m == 1 ? (y - 1, 12) : (y, m - 1)
            zoomLevel = .month(prev.0, prev.1)
            reloadPage()
        default: break
        }
    }

    func navigateNext() {
        switch zoomLevel {
        case .year(let y):
            if availableYears.contains(y + 1) { zoomLevel = .year(y + 1); reloadPage() }
        case .month(let y, let m):
            let next = m == 12 ? (y + 1, 1) : (y, m + 1)
            zoomLevel = .month(next.0, next.1)
            reloadPage()
        default: break
        }
    }

    func zoomOut() {
        switch zoomLevel {
        case .month(let y, _): zoomLevel = .year(y)
        case .year: zoomLevel = .overview
        default: return
        }
        reloadPage()
    }

    func drillDown(year: Int? = nil, month: Int? = nil) {
        if let month = month, case .year(let y) = zoomLevel {
            zoomLevel = .month(y, month)
        } else if let year = year {
            zoomLevel = .year(year)
        }
        reloadPage()
    }

    /// Cancel any in-flight page load and start a new one.
    private func reloadPage() {
        activeTask?.cancel()
        activeTask = Task { await loadPageData() }
    }

    // MARK: - Private

    private func adaptiveAggregate(_ points: [ChartPoint]) -> [ChartPoint] {
        guard !points.isEmpty else { return points }

        let dates = points.map(\.date)
        guard let minDate = dates.min(), let maxDate = dates.max() else { return points }
        let spanDays = Int64(maxDate.timeIntervalSince(minDate) / 86400)

        let mode = CalculateChartDataUseCase.aggregationMode(zoomLevel: zoomLevel, spanDays: spanDays)
        if mode == .daily { return points }

        // Group by series, aggregate each independently — last point per bucket wins
        let grouped = Dictionary(grouping: points) { $0.series }
        return grouped.values.flatMap { seriesPoints -> [ChartPoint] in
            let sorted = seriesPoints.sorted { $0.date < $1.date }
            var result: [ChartPoint] = []
            var currentBucketKey = -1

            for point in sorted {
                let key = CalculateChartDataUseCase.bucketKey(for: point.date, mode: mode)
                if key != currentBucketKey {
                    currentBucketKey = key
                    result.append(point)
                } else {
                    result[result.count - 1] = point
                }
            }
            return result
        }
    }

    private func loadPageData() async {
        guard let page = currentPage else { return }

        do {
            var transactions: [Transaction]

            switch page {
            case .singlePair(let crypto, let fiat):
                transactions = try deps.activeDatabase.transactionDao.getCompletedTransactions(
                    crypto: crypto, fiat: fiat
                )
            case .aggregate(let fiat):
                transactions = try deps.activeDatabase.transactionDao.getAllTransactionsOnce()
                    .filter { $0.fiat == fiat && ($0.status == .completed || $0.status == .partial) }
                    .sorted { $0.executedAt < $1.executedAt }
            }

            // Apply exchange filter
            if let filter = exchangeFilter {
                transactions = transactions.filter { $0.exchange == filter }
            }

            // Collect available exchanges
            availableExchanges = Array(Set(transactions.map { $0.exchange })).sorted { $0.rawValue < $1.rawValue }

            // Apply zoom level filter
            transactions = filterByZoom(transactions)

            // Compute available years/months for drill-down
            computeAvailableTimeRanges(from: transactions)

            guard !transactions.isEmpty else {
                resetStats()
                return
            }

            totalCrypto = transactions.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
            totalInvested = transactions.reduce(Decimal.zero) { $0 + $1.fiatAmount }
            avgBuyPrice = totalCrypto > 0 ? totalInvested / totalCrypto : 0
            transactionCount = transactions.count

            // Current price (with 60s cache)
            if case .singlePair(let crypto, let fiat) = page {
                let cacheKey = "\(crypto)/\(fiat)"
                if let cached = priceCache[cacheKey],
                   Date().timeIntervalSince(cached.fetchedAt) < priceCacheTTL {
                    currentPrice = cached.price
                } else {
                    let fetched = await withTimeoutOrNil(seconds: 10) {
                        await self.deps.marketDataService.getCurrentPrice(crypto: crypto, fiat: fiat)
                    }
                    currentPrice = fetched
                    if let fetched {
                        priceCache[cacheKey] = (price: fetched, fetchedAt: Date())
                    }
                }
            } else {
                currentPrice = nil
            }

            portfolioValue = currentPrice.map { totalCrypto * $0 }
            roiPercent = if let pv = portfolioValue, totalInvested > 0 {
                ((pv - totalInvested) / totalInvested) * 100
            } else {
                nil
            }

            // Build KPI snapshots by walking transactions chronologically
            var snapshots = [KpiSnapshot]()
            var runningCrypto: Decimal = 0
            var runningInvested: Decimal = 0
            for (index, tx) in transactions.enumerated() {
                runningCrypto += tx.cryptoAmount
                runningInvested += tx.fiatAmount
                let pv = runningCrypto * (currentPrice ?? tx.price)
                let roi: Decimal? = runningInvested > 0
                    ? ((pv - runningInvested) / runningInvested) * 100
                    : nil
                let avg = runningCrypto > 0 ? runningInvested / runningCrypto : 0
                snapshots.append(KpiSnapshot(
                    date: tx.executedAt,
                    portfolioValue: pv,
                    totalInvested: runningInvested,
                    roiPercent: roi,
                    avgBuyPrice: avg,
                    cumulativeCrypto: runningCrypto,
                    transactionCount: index + 1
                ))
            }
            kpiSnapshots = snapshots

            // Period ROI: compare first and last snapshot values in current zoom window
            if let first = snapshots.first, let last = snapshots.last,
               snapshots.count >= 2, zoomLevel != .overview, first.portfolioValue > 0 {
                let change = ((last.portfolioValue - first.portfolioValue) / first.portfolioValue) * 100
                periodRoiPercent = change
                periodRoiLabel = zoomLevel.title
            } else {
                periodRoiPercent = nil
                periodRoiLabel = nil
            }

            // Build chart data for all visible series (O(n) per series)
            var allChartData = [ChartPoint]()
            // Pre-compute running totals once to avoid O(n²) refiltering
            var runningCostBasis: Decimal = 0
            var runningAccumulated: Decimal = 0
            var costBasisByIndex = [Decimal]()
            var accumulatedByIndex = [Decimal]()
            var avgBuyPriceByIndex = [Decimal]()
            for tx in transactions {
                runningCostBasis += tx.fiatAmount
                runningAccumulated += tx.cryptoAmount
                costBasisByIndex.append(runningCostBasis)
                accumulatedByIndex.append(runningAccumulated)
                avgBuyPriceByIndex.append(runningAccumulated > 0 ? runningCostBasis / runningAccumulated : 0)
            }
            for series in visibleSeries {
                for (index, tx) in transactions.enumerated() {
                    let value: Decimal
                    switch series {
                    case .portfolioValue:
                        value = accumulatedByIndex[index] * (currentPrice ?? tx.price)
                    case .costBasis:
                        value = costBasisByIndex[index]
                    case .cryptoPrice:
                        value = tx.price
                    case .avgBuyPrice:
                        value = avgBuyPriceByIndex[index]
                    case .accumulatedCrypto:
                        value = accumulatedByIndex[index]
                    }
                    allChartData.append(ChartPoint(date: tx.executedAt, value: NSDecimalNumber(decimal: value).doubleValue, series: series))
                }
            }
            chartData = adaptiveAggregate(allChartData)
            lastLoadedAt = Date()
        } catch {
            resetStats()
        }
    }

    private func filterByZoom(_ transactions: [Transaction]) -> [Transaction] {
        let calendar = Calendar.current
        switch zoomLevel {
        case .overview:
            return transactions
        case .year(let year):
            return transactions.filter { calendar.component(.year, from: $0.executedAt) == year }
        case .month(let year, let month):
            return transactions.filter {
                calendar.component(.year, from: $0.executedAt) == year &&
                calendar.component(.month, from: $0.executedAt) == month
            }
        }
    }

    private func computeAvailableTimeRanges(from transactions: [Transaction]) {
        let calendar = Calendar.current
        let years = Set(transactions.map { calendar.component(.year, from: $0.executedAt) })
        availableYears = years.sorted()

        if case .year(let y) = zoomLevel {
            let months = Set(
                transactions
                    .filter { calendar.component(.year, from: $0.executedAt) == y }
                    .map { calendar.component(.month, from: $0.executedAt) }
            )
            availableMonths = months.sorted()
        } else {
            availableMonths = []
        }
    }

    private func resetStats() {
        totalCrypto = 0
        totalInvested = 0
        avgBuyPrice = 0
        transactionCount = 0
        portfolioValue = nil
        roiPercent = nil
        currentPrice = nil
        chartData = []
        kpiSnapshots = []
        periodRoiPercent = nil
        periodRoiLabel = nil
    }

    private func announceForVoiceOver(_ message: String) {
        UIAccessibility.post(notification: .announcement, argument: message)
    }
}
