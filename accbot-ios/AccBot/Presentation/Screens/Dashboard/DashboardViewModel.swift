import Foundation
import Combine
import UIKit

@MainActor
final class DashboardViewModel: ObservableObject {
    @Published var plans: [DcaPlan] = []
    @Published var plansWithBalance: [PlanWithBalance] = []
    @Published var holdings: [HoldingInfo] = []
    @Published var isLoading = true
    @Published var showRunNowSheet = false
    @Published var selectedPlanIds: Set<Int64> = []
    @Published var isRunning = false
    @Published var isRefreshingPrices = false
    @Published var errorMessage: String?
    @Published var runResultMessage: String?

    private var cancellables = Set<AnyCancellable>()
    private var balanceTask: Task<Void, Never>?
    private var loadTask: Task<Void, Never>?
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            assertionFailure("ViewModel used before setup() — call setup() in onAppear")
            return dependencies!
        }
        return d
    }

    deinit {
        loadTask?.cancel()
        balanceTask?.cancel()
    }

    struct PlanWithBalance: Identifiable {
        var id: Int64 { plan.id }
        let plan: DcaPlan
        let fiatBalance: Decimal?
        let remainingExecutions: Int?
        let remainingDays: Double?
        let isLowBalance: Bool
        let isOverWithdrawalThreshold: Bool
        let exchangeCryptoBalance: Decimal?

        init(plan: DcaPlan,
             fiatBalance: Decimal? = nil,
             remainingExecutions: Int? = nil,
             remainingDays: Double? = nil,
             isLowBalance: Bool = false,
             isOverWithdrawalThreshold: Bool = false,
             exchangeCryptoBalance: Decimal? = nil) {
            self.plan = plan
            self.fiatBalance = fiatBalance
            self.remainingExecutions = remainingExecutions
            self.remainingDays = remainingDays
            self.isLowBalance = isLowBalance
            self.isOverWithdrawalThreshold = isOverWithdrawalThreshold
            self.exchangeCryptoBalance = exchangeCryptoBalance
        }
    }

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
        let currentPrice: Decimal?
        let fiatGainLoss: Decimal?
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadData()
        observePlans()
    }

    func loadData() {
        loadTask?.cancel()
        loadTask = Task {
            isLoading = true
            await loadPlans()
            await loadHoldings()
            isLoading = false
            announceForVoiceOver(String(localized: "Dashboard loaded"))
            // Fetch balances in background after initial load
            await fetchBalancesForPlans()
        }
    }

    func loadDataAsync() async {
        await loadPlans()
        await loadHoldings()
        announceForVoiceOver(String(localized: "Dashboard loaded"))
        await fetchBalancesForPlans()
    }

    func loadPlans() async {
        do {
            plans = try deps.activeDatabase.planDao.getAll()
            plansWithBalance = plans.map { PlanWithBalance(plan: $0) }
        } catch {
            plans = []
            plansWithBalance = []
            errorMessage = String(localized: "Failed to load plans: \(error.localizedDescription)")
        }
    }

    func loadHoldings() async {
        do {
            let db = deps.activeDatabase
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
                let currentPrice = await withTimeoutOrNil(seconds: 10) {
                    await self.deps.marketDataService.getCurrentPrice(
                        crypto: pair.crypto, fiat: pair.fiat
                    )
                }
                let currentValue = currentPrice.map { totalCrypto * $0 }
                let roi: Decimal? = if let cv = currentValue, totalInvested > 0 {
                    ((cv - totalInvested) / totalInvested) * 100
                } else {
                    nil
                }

                let fiatGainLoss: Decimal? = if let cv = currentValue, totalInvested > 0 {
                    cv - totalInvested
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
                    currentValue: currentValue,
                    currentPrice: currentPrice,
                    fiatGainLoss: fiatGainLoss
                ))
            }

            holdings = holdingsResult
        } catch {
            holdings = []
            errorMessage = String(localized: "Failed to load holdings: \(error.localizedDescription)")
        }
    }

    private func observePlans() {
        deps.activeDatabase.planDao.observeAll()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        #if DEBUG
                        print("[DashboardVM] Observation error: \(error.localizedDescription)")
                        #endif
                    }
                },
                receiveValue: { [weak self] plans in
                    self?.plans = plans
                    self?.plansWithBalance = plans.map { PlanWithBalance(plan: $0) }
                    self?.balanceTask?.cancel()
                    self?.balanceTask = Task { [weak self] in
                        await self?.fetchBalancesForPlans()
                    }
                }
            )
            .store(in: &cancellables)
    }

    func fetchBalancesForPlans() async {
        let enabledPlans = plans.filter { $0.isEnabled }
        guard !enabledPlans.isEmpty else { return }

        let isSandbox = deps.userPreferences.sandboxMode
        let thresholdDays = deps.userPreferences.lowBalanceThresholdDays

        // Cache fetched balances to avoid duplicate API calls for same exchange+currency
        var balanceCache: [String: Decimal?] = [:]

        var result: [PlanWithBalance] = []

        for plan in plans {
            guard !Task.isCancelled else { return }
            guard plan.isEnabled else {
                result.append(PlanWithBalance(plan: plan))
                continue
            }

            // Fetch fiat balance
            let fiatKey = "\(plan.exchange.rawValue)_\(plan.fiat)"
            if balanceCache[fiatKey] == nil {
                balanceCache[fiatKey] = await fetchBalance(
                    exchange: plan.exchange,
                    currency: plan.fiat,
                    isSandbox: isSandbox
                )
            }
            let fiatBalance = balanceCache[fiatKey] ?? nil

            // Fetch crypto balance for withdrawal threshold check
            let cryptoKey = "\(plan.exchange.rawValue)_\(plan.crypto)"
            if balanceCache[cryptoKey] == nil {
                balanceCache[cryptoKey] = await fetchBalance(
                    exchange: plan.exchange,
                    currency: plan.crypto,
                    isSandbox: isSandbox
                )
            }
            let cryptoBalance = balanceCache[cryptoKey] ?? nil

            // Check withdrawal threshold
            let withdrawalThreshold = try? deps.activeDatabase.withdrawalThresholdDao
                .get(crypto: plan.crypto, exchange: plan.exchange)
            let isOverThreshold: Bool
            if let threshold = withdrawalThreshold, let balance = cryptoBalance {
                isOverThreshold = balance >= threshold.thresholdAmount
            } else {
                isOverThreshold = false
            }

            // Calculate remaining executions and days
            if let balance = fiatBalance, plan.amount > 0 {
                let remainingExec = NSDecimalNumber(decimal: balance)
                    .dividing(by: NSDecimalNumber(decimal: plan.amount),
                              withBehavior: NSDecimalNumberHandler(
                                roundingMode: .down, scale: 0,
                                raiseOnExactness: false, raiseOnOverflow: false,
                                raiseOnUnderflow: false, raiseOnDivideByZero: false))
                    .intValue

                let rawInterval: Int
                if let cron = plan.cronExpression {
                    rawInterval = CronUtils.getIntervalMinutesEstimate(cron: cron) ?? 1440
                } else {
                    rawInterval = plan.frequency.intervalMinutes
                }
                let effectiveInterval = rawInterval > 0 ? rawInterval : 1440
                let remainingMinutes = remainingExec * effectiveInterval
                let remainingDaysVal = Double(remainingMinutes) / 1440.0

                result.append(PlanWithBalance(
                    plan: plan,
                    fiatBalance: balance,
                    remainingExecutions: remainingExec,
                    remainingDays: remainingDaysVal,
                    isLowBalance: remainingDaysVal < Double(thresholdDays),
                    isOverWithdrawalThreshold: isOverThreshold,
                    exchangeCryptoBalance: cryptoBalance
                ))
            } else {
                result.append(PlanWithBalance(
                    plan: plan,
                    isOverWithdrawalThreshold: isOverThreshold,
                    exchangeCryptoBalance: cryptoBalance
                ))
            }
        }

        // Only update if plan set hasn't changed
        let currentIds = Set(plansWithBalance.map(\.plan.id))
        let fetchedIds = Set(result.map(\.plan.id))
        if currentIds == fetchedIds {
            plansWithBalance = result
        }
    }

    private func fetchBalance(exchange: Exchange, currency: String, isSandbox: Bool) async -> Decimal? {
        // Try live balance from exchange API
        if let credentials = deps.credentialsStore.get(for: exchange, isSandbox: isSandbox) {
            let api = deps.exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            let balance = await withTimeoutOrNil(seconds: 10) {
                await api.getBalance(currency: currency)
            }
            if let balance {
                // Cache in DB
                try? deps.activeDatabase.exchangeBalanceDao.upsert(
                    exchange: exchange, currency: currency, balance: balance
                )
                return balance
            }
        }
        // Fallback to cached balance
        return try? deps.activeDatabase.exchangeBalanceDao.getBalance(
            exchange: exchange, currency: currency
        )
    }

    func togglePlan(_ plan: DcaPlan, enabled: Bool) {
        Task {
            try? deps.activeDatabase.planDao.setEnabled(id: plan.id, enabled: enabled)
        }
    }

    func runSelectedPlans() {
        guard !selectedPlanIds.isEmpty else { return }
        isRunning = true
        let planCount = selectedPlanIds.count
        let planIds = Array(selectedPlanIds)

        Task {
            // Note: executePlans is non-throwing; individual plan errors are
            // recorded as failed transactions in the DB, not propagated here.
            await deps.dcaExecutionEngine.executePlans(planIds)
            let msg = String(localized: "\(planCount) plans executed successfully")
            runResultMessage = msg
            announceForVoiceOver(msg)
            isRunning = false
            showRunNowSheet = false
            selectedPlanIds.removeAll()
            loadData()
        }
    }

    func refreshPrices() {
        isRefreshingPrices = true
        Task {
            await loadHoldings()
            await fetchBalancesForPlans()
            isRefreshingPrices = false
            announceForVoiceOver(String(localized: "Prices refreshed"))
        }
    }

    func announceForVoiceOver(_ message: String) {
        UIAccessibility.post(notification: .announcement, argument: message)
    }
}

