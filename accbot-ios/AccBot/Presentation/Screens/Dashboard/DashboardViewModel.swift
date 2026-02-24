import Foundation
import Combine

@MainActor
final class DashboardViewModel: ObservableObject {
    @Published var plans: [DcaPlan] = []
    @Published var holdings: [HoldingInfo] = []
    @Published var isLoading = true
    @Published var showRunNowSheet = false
    @Published var selectedPlanIds: Set<Int64> = []
    @Published var isRunning = false

    private var cancellables = Set<AnyCancellable>()
    private let dependencies: AppDependencies

    struct HoldingInfo: Identifiable {
        let id: String  // "BTC/EUR"
        let crypto: String
        let fiat: String
        let totalCrypto: Decimal
        let totalInvested: Decimal
        let avgPrice: Decimal
        let transactionCount: Int
        let roi: Decimal?  // Percentage, nil if no current price
        let currentValue: Decimal?
    }

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
        loadData()
        observePlans()
    }

    func loadData() {
        Task {
            isLoading = true
            await loadPlans()
            await loadHoldings()
            isLoading = false
        }
    }

    private func loadPlans() async {
        do {
            plans = try dependencies.activeDatabase.planDao.getAll()
        } catch {
            plans = []
        }
    }

    private func loadHoldings() async {
        do {
            let db = dependencies.activeDatabase
            let pairs = try db.transactionDao.getDistinctPairs()
            var holdingsResult: [HoldingInfo] = []

            for pair in pairs {
                let transactions = try db.transactionDao.getCompletedTransactions(
                    crypto: pair.crypto, fiat: pair.fiat
                )
                guard !transactions.isEmpty else { continue }

                let totalCrypto = transactions.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
                let totalInvested = transactions.reduce(Decimal.zero) { $0 + $1.fiatAmount }
                let avgPrice = totalCrypto > 0
                    ? totalInvested / totalCrypto
                    : Decimal.zero

                // Try to get current price for ROI
                let currentPrice = await dependencies.marketDataService.getCurrentPrice(
                    crypto: pair.crypto, fiat: pair.fiat
                )
                let currentValue = currentPrice.map { totalCrypto * $0 }
                let roi: Decimal? = if let cv = currentValue, totalInvested > 0 {
                    ((cv - totalInvested) / totalInvested) * 100
                } else {
                    nil
                }

                holdingsResult.append(HoldingInfo(
                    id: "\(pair.crypto)/\(pair.fiat)",
                    crypto: pair.crypto,
                    fiat: pair.fiat,
                    totalCrypto: totalCrypto,
                    totalInvested: totalInvested,
                    avgPrice: avgPrice,
                    transactionCount: transactions.count,
                    roi: roi,
                    currentValue: currentValue
                ))
            }

            holdings = holdingsResult
        } catch {
            holdings = []
        }
    }

    private func observePlans() {
        dependencies.activeDatabase.planDao.observeAll()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { [weak self] plans in
                    self?.plans = plans
                }
            )
            .store(in: &cancellables)
    }

    func togglePlan(_ plan: DcaPlan, enabled: Bool) {
        Task {
            try? dependencies.activeDatabase.planDao.setEnabled(id: plan.id, enabled: enabled)
        }
    }

    func runSelectedPlans() {
        guard !selectedPlanIds.isEmpty else { return }
        isRunning = true
        let planIds = Array(selectedPlanIds)

        Task {
            await dependencies.dcaExecutionEngine.executePlans(planIds)
            isRunning = false
            showRunNowSheet = false
            selectedPlanIds.removeAll()
            loadData()
        }
    }

    func runAllDuePlans() {
        isRunning = true
        Task {
            await dependencies.dcaExecutionEngine.executeDuePlans()
            isRunning = false
            loadData()
        }
    }
}
