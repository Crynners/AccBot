import Foundation
import Combine

@MainActor
final class PortfolioViewModel: ObservableObject {
    @Published var pairs: [(crypto: String, fiat: String)] = []
    @Published var selectedPairIndex = 0
    @Published var isLoading = true
    @Published var portfolioValue: Decimal?
    @Published var totalInvested: Decimal = 0
    @Published var totalCrypto: Decimal = 0
    @Published var avgBuyPrice: Decimal = 0
    @Published var transactionCount = 0
    @Published var roiPercent: Decimal?
    @Published var chartData: [ChartPoint] = []
    @Published var selectedChartSeries: ChartSeries = .portfolioValue
    @Published var denomination: Denomination = .fiat
    @Published var exchangeFilter: Exchange?

    private let dependencies: AppDependencies

    struct ChartPoint: Identifiable {
        let id = UUID()
        let date: Date
        let value: Decimal
    }

    enum ChartSeries: String, CaseIterable {
        case portfolioValue = "Portfolio Value"
        case costBasis = "Cost Basis"
        case cryptoPrice = "Price"
        case accumulatedCrypto = "Accumulated"
    }

    enum Denomination {
        case fiat, crypto
    }

    var currentPair: (crypto: String, fiat: String)? {
        guard selectedPairIndex < pairs.count else { return nil }
        return pairs[selectedPairIndex]
    }

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
    }

    func loadData() async {
        isLoading = true
        do {
            pairs = try dependencies.activeDatabase.transactionDao.getDistinctPairs()
            if !pairs.isEmpty {
                await loadPairData()
            }
        } catch {
            pairs = []
        }
        isLoading = false
    }

    func selectPair(at index: Int) {
        guard index < pairs.count else { return }
        selectedPairIndex = index
        Task { await loadPairData() }
    }

    private func loadPairData() async {
        guard let pair = currentPair else { return }

        do {
            var transactions = try dependencies.activeDatabase.transactionDao.getCompletedTransactions(
                crypto: pair.crypto, fiat: pair.fiat
            )

            // Apply exchange filter
            if let filter = exchangeFilter {
                transactions = transactions.filter { $0.exchange == filter }
            }

            guard !transactions.isEmpty else {
                resetStats()
                return
            }

            totalCrypto = transactions.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
            totalInvested = transactions.reduce(Decimal.zero) { $0 + $1.fiatAmount }
            avgBuyPrice = totalCrypto > 0 ? totalInvested / totalCrypto : 0
            transactionCount = transactions.count

            // Current value
            let currentPrice = await dependencies.marketDataService.getCurrentPrice(
                crypto: pair.crypto, fiat: pair.fiat
            )
            portfolioValue = currentPrice.map { totalCrypto * $0 }
            roiPercent = if let pv = portfolioValue, totalInvested > 0 {
                ((pv - totalInvested) / totalInvested) * 100
            } else {
                nil
            }

            // Build chart data (simple accumulated value over time)
            var accumulated: Decimal = 0
            chartData = transactions.map { tx in
                accumulated += tx.cryptoAmount
                let value: Decimal
                switch selectedChartSeries {
                case .portfolioValue:
                    value = accumulated * (currentPrice ?? tx.price)
                case .costBasis:
                    value = transactions.filter { $0.executedAt <= tx.executedAt }
                        .reduce(Decimal.zero) { $0 + $1.fiatAmount }
                case .cryptoPrice:
                    value = tx.price
                case .accumulatedCrypto:
                    value = accumulated
                }
                return ChartPoint(date: tx.executedAt, value: value)
            }
        } catch {
            resetStats()
        }
    }

    private func resetStats() {
        totalCrypto = 0
        totalInvested = 0
        avgBuyPrice = 0
        transactionCount = 0
        portfolioValue = nil
        roiPercent = nil
        chartData = []
    }
}
