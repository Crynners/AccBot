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
    @Published var chartData: [ChartPoint] = [] {
        didSet {
            chartDateCount = Set(chartData.map(\.date)).count
        }
    }
    /// Number of unique dates in chart data (avoids O(n) recomputation in View body).
    var chartDateCount: Int = 0
    @Published var selectedChartSeries: ChartSeries = .portfolioValue
    @Published var denomination: Denomination = .fiat
    @Published var exchangeFilter: Exchange?
    @Published var zoomLevel: ChartZoomLevel = .overview
    @Published var visibleSeries: Set<ChartSeries> = [.portfolioValue, .costBasis]
    @Published var availableExchanges: [Exchange] = []
    @Published var availableYears: [Int] = []
    @Published var availableMonths: [Int] = []
    @Published var kpiSnapshots: [KpiSnapshot] = []
    @Published var periodRoiPercent: Decimal?
    @Published var periodRoiLabel: String?
    @Published var accumulatedScaleMin: Decimal = 0
    @Published var accumulatedScaleMax: Decimal = 0
    /// Fiat Y-axis range used for normalizing accumulated crypto; exposed for reverse-mapping on trailing axis.
    var fiatScaleMin: Double = 0
    var fiatScaleMax: Double = 0

    struct KpiSnapshot {
        let date: Date
        let portfolioValue: Decimal
        let totalInvested: Decimal
        let roiPercent: Decimal?
        let avgBuyPrice: Decimal
        let cumulativeCrypto: Decimal
        let price: Decimal
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
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
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

    /// Cancel any in-flight page load and start a new one after a short debounce.
    private var reloadWorkItem: DispatchWorkItem?

    private func reloadPage() {
        activeTask?.cancel()
        reloadWorkItem?.cancel()
        let item = DispatchWorkItem { [weak self] in
            self?.activeTask = Task { await self?.loadPageData() }
        }
        reloadWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1, execute: item)
    }

    // MARK: - Private

    /// Maximum chart points per series. Matches Android (90 points).
    /// With 5 series × 90 = 450 LineMarks — smooth on A12+.
    private static let maxPointsPerSeries = 90

    private func adaptiveAggregate(_ points: [ChartPoint]) -> [ChartPoint] {
        guard !points.isEmpty else { return points }

        let dates = points.map(\.date)
        guard let minDate = dates.min(), let maxDate = dates.max() else { return points }
        let spanDays = Int64(maxDate.timeIntervalSince(minDate) / 86400)

        let mode = CalculateChartDataUseCase.aggregationMode(zoomLevel: zoomLevel, spanDays: spanDays)

        // Group by series, aggregate each independently — last point per bucket wins
        let grouped = Dictionary(grouping: points) { $0.series }
        return grouped.values.flatMap { seriesPoints -> [ChartPoint] in
            let sorted = seriesPoints.sorted { $0.date < $1.date }

            var result: [ChartPoint]
            if mode == .daily {
                result = sorted
            } else {
                result = []
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
            }

            // Hard cap: downsample with every-Nth if still too many points
            let maxPts = Self.maxPointsPerSeries
            if result.count > maxPts {
                let step = Double(result.count - 1) / Double(maxPts - 1)
                var downsampled = [ChartPoint]()
                downsampled.reserveCapacity(maxPts)
                for i in 0..<maxPts {
                    let idx = min(Int(Double(i) * step), result.count - 1)
                    downsampled.append(result[idx])
                }
                result = downsampled
            }

            return result
        }
    }

    private func loadPageData() async {
        guard let page = currentPage else { return }

        do {
            var allTransactions: [Transaction]

            switch page {
            case .singlePair(let crypto, let fiat):
                allTransactions = try deps.activeDatabase.transactionDao.getCompletedTransactions(
                    crypto: crypto, fiat: fiat
                )
            case .aggregate(let fiat):
                allTransactions = try deps.activeDatabase.transactionDao.getAllTransactionsOnce()
                    .filter { $0.fiat == fiat && ($0.status == .completed || $0.status == .partial) }
                    .sorted { $0.executedAt < $1.executedAt }
            }

            // Apply exchange filter
            if let filter = exchangeFilter {
                allTransactions = allTransactions.filter { $0.exchange == filter }
            }

            // Collect available exchanges
            availableExchanges = Array(Set(allTransactions.map { $0.exchange })).sorted { $0.rawValue < $1.rawValue }

            // Compute available years/months from ALL transactions (not zoom-filtered)
            computeAvailableTimeRanges(from: allTransactions)

            // Zoom-filtered subset for period-specific KPIs
            let zoomTransactions = filterByZoom(allTransactions)

            guard !zoomTransactions.isEmpty else {
                resetStats()
                return
            }

            // Period-specific KPIs from zoom window only
            totalCrypto = zoomTransactions.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
            totalInvested = zoomTransactions.reduce(Decimal.zero) { $0 + $1.fiatAmount }
            avgBuyPrice = totalCrypto > 0 ? totalInvested / totalCrypto : 0
            transactionCount = zoomTransactions.count

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

            // Pre-compute cumulative running totals from ALL transactions
            var runningCostBasis: Decimal = 0
            var runningAccumulated: Decimal = 0
            // Map transaction executedAt timestamp to cumulative values
            struct CumulativeEntry {
                let costBasis: Decimal
                let accumulated: Decimal
                let avgBuyPrice: Decimal
            }
            var cumulativeByTimestamp: [TimeInterval: CumulativeEntry] = [:]
            for tx in allTransactions {
                runningCostBasis += tx.fiatAmount
                runningAccumulated += tx.cryptoAmount
                let avg = runningAccumulated > 0 ? runningCostBasis / runningAccumulated : Decimal.zero
                cumulativeByTimestamp[tx.executedAt.timeIntervalSince1970] = CumulativeEntry(
                    costBasis: runningCostBasis,
                    accumulated: runningAccumulated,
                    avgBuyPrice: avg
                )
            }

            // Build KPI snapshots using cumulative values, but only for zoom-window transactions
            var snapshots = [KpiSnapshot]()
            for (index, tx) in zoomTransactions.enumerated() {
                let entry = cumulativeByTimestamp[tx.executedAt.timeIntervalSince1970]!
                let pv = entry.accumulated * tx.price
                let roi: Decimal? = entry.costBasis > 0
                    ? ((pv - entry.costBasis) / entry.costBasis) * 100
                    : nil
                snapshots.append(KpiSnapshot(
                    date: tx.executedAt,
                    portfolioValue: pv,
                    totalInvested: entry.costBasis,
                    roiPercent: roi,
                    avgBuyPrice: entry.avgBuyPrice,
                    cumulativeCrypto: entry.accumulated,
                    price: tx.price,
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

            // Build chart data — cumulative values, but only emit points for zoom window
            var allChartData = [ChartPoint]()
            var costBasisByIndex = [Decimal]()
            var accumulatedByIndex = [Decimal]()
            var avgBuyPriceByIndex = [Decimal]()
            for tx in zoomTransactions {
                let entry = cumulativeByTimestamp[tx.executedAt.timeIntervalSince1970]!
                costBasisByIndex.append(entry.costBasis)
                accumulatedByIndex.append(entry.accumulated)
                avgBuyPriceByIndex.append(entry.avgBuyPrice)
            }

            // Compute fiat range for normalizing accumulated crypto onto the same Y axis
            var fiatMin: Decimal = Decimal.greatestFiniteMagnitude
            var fiatMax: Decimal = -Decimal.greatestFiniteMagnitude
            for series in visibleSeries where series != .accumulatedCrypto {
                for (index, tx) in zoomTransactions.enumerated() {
                    let value: Decimal
                    switch series {
                    case .portfolioValue: value = accumulatedByIndex[index] * tx.price
                    case .costBasis: value = costBasisByIndex[index]
                    case .cryptoPrice: value = tx.price
                    case .avgBuyPrice: value = avgBuyPriceByIndex[index]
                    case .accumulatedCrypto: continue
                    }
                    if value < fiatMin { fiatMin = value }
                    if value > fiatMax { fiatMax = value }
                }
            }
            // Fallback if only accumulated is visible
            if fiatMin == Decimal.greatestFiniteMagnitude {
                fiatMin = 0
                fiatMax = 1
            }

            let cryptoMin: Decimal = 0
            let cryptoMax = accumulatedByIndex.max() ?? 1
            let cryptoRange = cryptoMax - cryptoMin
            let fiatRange = fiatMax - fiatMin
            accumulatedScaleMin = cryptoMin
            accumulatedScaleMax = cryptoMax
            fiatScaleMin = NSDecimalNumber(decimal: fiatMin).doubleValue
            fiatScaleMax = NSDecimalNumber(decimal: fiatMax).doubleValue

            for series in visibleSeries {
                for (index, tx) in zoomTransactions.enumerated() {
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
                        let original = accumulatedByIndex[index]
                        // Normalize to fiat range for visual alignment
                        if cryptoRange > 0 && fiatRange > 0 {
                            value = fiatMin + (original - cryptoMin) / cryptoRange * fiatRange
                        } else {
                            value = fiatMin
                        }
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
        accumulatedScaleMin = 0
        accumulatedScaleMax = 0
        fiatScaleMin = 0
        fiatScaleMax = 0
    }

    private func announceForVoiceOver(_ message: String) {
        UIAccessibility.post(notification: .announcement, argument: message)
    }
}
