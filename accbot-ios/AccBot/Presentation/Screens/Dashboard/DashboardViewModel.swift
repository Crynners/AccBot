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
    @Published var plansInRetry: [DcaPlan] = []
    @Published var plansWithMissed: [DcaPlan] = []
    @Published var isBuyingMissed = false

    // Market Pulse
    @Published var fearGreedValue: Int?
    @Published var fearGreedLabel: String?
    @Published var athData: [AthCryptoInfo] = []
    @Published var isMarketPulseExpanded: Bool = true
    @Published var showMarketPulse: Bool = true

    private var cancellables = Set<AnyCancellable>()
    private var balanceTask: Task<Void, Never>?
    private var loadTask: Task<Void, Never>?
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
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
        let accumulatedCrypto: Decimal?

        init(plan: DcaPlan,
             fiatBalance: Decimal? = nil,
             remainingExecutions: Int? = nil,
             remainingDays: Double? = nil,
             isLowBalance: Bool = false,
             isOverWithdrawalThreshold: Bool = false,
             exchangeCryptoBalance: Decimal? = nil,
             accumulatedCrypto: Decimal? = nil) {
            self.plan = plan
            self.fiatBalance = fiatBalance
            self.remainingExecutions = remainingExecutions
            self.remainingDays = remainingDays
            self.isLowBalance = isLowBalance
            self.isOverWithdrawalThreshold = isOverWithdrawalThreshold
            self.exchangeCryptoBalance = exchangeCryptoBalance
            self.accumulatedCrypto = accumulatedCrypto
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

    struct AthCryptoInfo: Identifiable {
        var id: String { crypto }
        let crypto: String
        let currentPrice: Decimal
        let ath: Decimal
        let athDistancePercent: Double  // 0-100, how far below ATH
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        showMarketPulse = dependencies.userPreferences.marketPulseEnabled
        isMarketPulseExpanded = dependencies.userPreferences.marketPulseExpanded
        loadData()
        observePlans()
        observeTransactions()
        observeMarketPulsePreference()
    }

    func loadData() {
        loadTask?.cancel()
        loadTask = Task {
            isLoading = true
            await loadDataAsync()
            isLoading = false
        }
    }

    func loadDataAsync() async {
        async let plansResult: () = loadPlans()
        async let holdingsResult: () = loadHoldings()
        _ = await (plansResult, holdingsResult)
        loadRetryAndMissedState()
        announceForVoiceOver(String(localized: "Dashboard loaded"))
        // Balances and market data can also run in parallel
        let shouldFetchMarket = showMarketPulse
        async let balancesResult: () = fetchBalancesForPlans()
        async let marketResult: () = shouldFetchMarket ? fetchMarketData() : ()
        _ = await (balancesResult, marketResult)

        // Update widget with latest data
        if let deps = dependencies {
            WidgetDataService.update(from: deps)
        }
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
            let summaries = try deps.activeDatabase.transactionDao.getHoldingSummaries()
            guard !summaries.isEmpty else {
                holdings = []
                return
            }

            // Fetch current prices for all pairs in parallel
            var priceCache: [String: Decimal?] = [:]
            await withTaskGroup(of: (String, Decimal?).self) { group in
                for summary in summaries {
                    let key = "\(summary.crypto)/\(summary.fiat)"
                    group.addTask {
                        let price = await withTimeoutOrNil(seconds: 10) {
                            await self.deps.marketDataService.getCurrentPrice(
                                crypto: summary.crypto, fiat: summary.fiat
                            )
                        }
                        return (key, price)
                    }
                }
                for await (key, price) in group {
                    priceCache[key] = price
                }
            }

            // Don't let a cancelled task overwrite valid holdings with nil prices
            guard !Task.isCancelled else { return }

            // Build holdings from summaries + cached prices.
            // Fall back to existing price when API returned nil (timeout/rate-limit)
            // so KPI cards don't flash blank on transient network failures.
            let existingPrices = Dictionary(uniqueKeysWithValues: holdings.compactMap { h in
                h.currentPrice.map { (h.id, $0) }
            })
            var holdingsResult: [HoldingInfo] = []
            for summary in summaries {
                let key = "\(summary.crypto)/\(summary.fiat)"
                let avgPrice = summary.totalCrypto > 0
                    ? summary.totalInvested / summary.totalCrypto
                    : Decimal.zero
                let currentPrice = priceCache[key] ?? existingPrices[key]
                let currentValue = currentPrice.map { summary.totalCrypto * $0 }
                let roi: Decimal? = if let cv = currentValue, summary.totalInvested > 0 {
                    ((cv - summary.totalInvested) / summary.totalInvested) * 100
                } else {
                    nil
                }
                let fiatGainLoss: Decimal? = if let cv = currentValue, summary.totalInvested > 0 {
                    cv - summary.totalInvested
                } else {
                    nil
                }

                holdingsResult.append(HoldingInfo(
                    id: key,
                    crypto: summary.crypto,
                    fiat: summary.fiat,
                    totalCrypto: summary.totalCrypto,
                    totalInvested: summary.totalInvested,
                    avgPrice: avgPrice,
                    transactionCount: summary.txCount,
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
            .debounce(for: .milliseconds(300), scheduler: DispatchQueue.main)
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
                    // Reactively update retry/missed banners from plan fields
                    self?.plansInRetry = plans.filter { $0.networkRetryCount > 0 }
                    self?.plansWithMissed = plans.filter { $0.missedPurchaseCount > 0 }
                    // Filter ATH data to only include cryptos from current plans
                    let activeCryptos = Set(plans.map(\.crypto))
                    self?.athData.removeAll { !activeCryptos.contains($0.crypto) }
                    self?.balanceTask?.cancel()
                    self?.balanceTask = Task { [weak self] in
                        await self?.fetchBalancesForPlans()
                    }
                }
            )
            .store(in: &cancellables)
    }

    private func observeTransactions() {
        deps.activeDatabase.transactionDao.observeCount()
            .removeDuplicates()
            .dropFirst()
            .debounce(for: .milliseconds(500), scheduler: DispatchQueue.main)
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { [weak self] _ in
                    Task { await self?.loadHoldings() }
                }
            )
            .store(in: &cancellables)
    }

    func fetchBalancesForPlans() async {
        let enabledPlans = plans.filter { $0.isEnabled }
        guard !enabledPlans.isEmpty else { return }

        let isSandbox = deps.userPreferences.sandboxMode
        let thresholdDays = deps.userPreferences.lowBalanceThresholdDays

        // Collect unique balance keys to fetch
        var keysToFetch = Set<String>()
        for plan in enabledPlans {
            keysToFetch.insert("\(plan.exchange.rawValue)_\(plan.fiat)")
            keysToFetch.insert("\(plan.exchange.rawValue)_\(plan.crypto)")
        }

        // Fetch all balances in parallel
        var balanceCache: [String: Decimal?] = [:]
        await withTaskGroup(of: (String, Decimal?).self) { group in
            for key in keysToFetch {
                let parts = key.split(separator: "_", maxSplits: 1)
                let exchange = Exchange(rawValue: String(parts[0]))!
                let currency = String(parts[1])
                group.addTask {
                    let balance = await self.fetchBalance(exchange: exchange, currency: currency, isSandbox: isSandbox)
                    return (key, balance)
                }
            }
            for await (key, balance) in group {
                balanceCache[key] = balance
            }
        }

        guard !Task.isCancelled else { return }

        // Pre-fetch accumulated crypto for plans with targetAmount
        var accumulatedCache: [Int64: Decimal] = [:]
        for plan in plans {
            if plan.targetAmount != nil {
                accumulatedCache[plan.id] = (try? deps.activeDatabase.transactionDao.getAccumulatedCryptoByPlan(plan.id)) ?? 0
            }
        }

        // Build result from cache (no more awaits)
        var result: [PlanWithBalance] = []

        for plan in plans {
            guard plan.isEnabled else {
                result.append(PlanWithBalance(plan: plan, accumulatedCrypto: accumulatedCache[plan.id]))
                continue
            }

            let fiatKey = "\(plan.exchange.rawValue)_\(plan.fiat)"
            let cryptoKey = "\(plan.exchange.rawValue)_\(plan.crypto)"
            let fiatBalance = balanceCache[fiatKey] ?? nil
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
                let remainingExecDecimal = NSDecimalNumber(decimal: balance / plan.amount)
                let remainingExec = Int(floor(remainingExecDecimal.doubleValue))

                let rawInterval: Int
                if let cron = plan.cronExpression {
                    rawInterval = CronUtils.getIntervalMinutesEstimate(cron: cron) ?? 1440
                } else {
                    rawInterval = plan.frequency.intervalMinutes
                }
                let effectiveInterval = rawInterval > 0 ? rawInterval : 1440
                let remainingDaysVal = remainingExecDecimal.doubleValue * Double(effectiveInterval) / 1440.0

                result.append(PlanWithBalance(
                    plan: plan,
                    fiatBalance: balance,
                    remainingExecutions: remainingExec,
                    remainingDays: remainingDaysVal,
                    isLowBalance: remainingDaysVal < Double(thresholdDays),
                    isOverWithdrawalThreshold: isOverThreshold,
                    exchangeCryptoBalance: cryptoBalance,
                    accumulatedCrypto: accumulatedCache[plan.id]
                ))
            } else {
                result.append(PlanWithBalance(
                    plan: plan,
                    isOverWithdrawalThreshold: isOverThreshold,
                    exchangeCryptoBalance: cryptoBalance,
                    accumulatedCrypto: accumulatedCache[plan.id]
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
        do {
            if enabled {
                // Atomically enable + recalculate next execution to avoid stale dates
                let next = plan.calculateNextExecution(from: Date())
                try deps.activeDatabase.planDao.setEnabledAndNextExecution(
                    id: plan.id, enabled: true, nextExecutionAt: next
                )
            } else {
                try deps.activeDatabase.planDao.setEnabled(id: plan.id, enabled: false)
            }

            // Reschedule background layers to reflect the change
            DcaBackgroundService.shared.rescheduleAllLayers(
                using: deps.activeDatabase,
                notificationService: deps.notificationService
            )
        } catch {
            errorMessage = error.localizedDescription
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

    func loadRetryAndMissedState() {
        plansInRetry = (try? deps.activeDatabase.planDao.getPlansInRetry()) ?? []
        plansWithMissed = (try? deps.activeDatabase.planDao.getPlansWithMissedPurchases()) ?? []
    }

    func dismissRetryBanner(planId: Int64) {
        // Optimistic UI: hide immediately
        plansInRetry.removeAll { $0.id == planId }
        Task {
            try? deps.activeDatabase.planDao.clearNetworkRetry(id: planId)
        }
    }

    func runRetryPlan(_ planId: Int64) {
        // Optimistic UI: hide immediately
        plansInRetry.removeAll { $0.id == planId }
        isRunning = true
        Task {
            try? deps.activeDatabase.planDao.clearNetworkRetry(id: planId)
            await deps.dcaExecutionEngine.executePlan(planId)
            isRunning = false
            loadData()
        }
    }

    func dismissMissedBanner(planId: Int64) {
        plansWithMissed.removeAll { $0.id == planId }
        Task {
            try? deps.activeDatabase.planDao.setMissedPurchaseCount(id: planId, count: 0)
        }
    }

    func buyMissedPurchases(planId: Int64, count: Int) {
        plansWithMissed.removeAll { $0.id == planId }
        isBuyingMissed = true
        Task {
            try? deps.activeDatabase.planDao.setMissedPurchaseCount(id: planId, count: 0)
            for i in 1...count {
                await deps.dcaExecutionEngine.executePlan(planId)
                if i < count {
                    try? await Task.sleep(nanoseconds: 3_000_000_000) // 3s pause
                }
            }
            isBuyingMissed = false
            loadData()
        }
    }

    func refreshPrices() {
        isRefreshingPrices = true
        Task {
            await loadHoldings()
            await fetchBalancesForPlans()
            if showMarketPulse {
                await fetchMarketData()
            }
            isRefreshingPrices = false
            announceForVoiceOver(String(localized: "Prices refreshed"))
        }
    }

    // MARK: - Market Pulse

    func fetchMarketData() async {
        // Fetch Fear & Greed Index
        let fgValue = await withTimeoutOrNil(seconds: 10) {
            await self.deps.marketDataService.getFearGreedIndex()
        }
        if let fgValue {
            fearGreedValue = fgValue
            fearGreedLabel = FearGreedClassification.label(for: fgValue)
        }

        // Collect unique crypto/fiat pairs from plans and fetch ATH data in parallel.
        // Uses single API call per pair (getPriceAndATH) to avoid rate limiting.
        let uniquePairs = Array(Set(plans.map { "\($0.crypto)/\($0.fiat)" }))

        let newAthData: [AthCryptoInfo] = await withTaskGroup(of: AthCryptoInfo?.self) { group in
            for pairKey in uniquePairs {
                let parts = pairKey.split(separator: "/")
                let crypto = String(parts[0])
                let fiat = String(parts[1])
                group.addTask {
                    let result = await withTimeoutOrNil(seconds: 15) {
                        await self.deps.marketDataService.getPriceAndATH(crypto: crypto, fiat: fiat)
                    }
                    guard let result, result.ath > 0 else { return nil }
                    let distance = NSDecimalNumber(decimal: (result.ath - result.price) / result.ath * 100).doubleValue
                    return AthCryptoInfo(
                        crypto: crypto,
                        currentPrice: result.price,
                        ath: result.ath,
                        athDistancePercent: max(0, min(100, distance))
                    )
                }
            }
            var results: [AthCryptoInfo] = []
            for await info in group {
                if let info { results.append(info) }
            }
            return results
        }

        athData = newAthData.sorted { $0.crypto < $1.crypto }
    }

    func toggleMarketPulseExpanded() {
        isMarketPulseExpanded.toggle()
        dependencies?.userPreferences.marketPulseExpanded = isMarketPulseExpanded
    }

    private func observeMarketPulsePreference() {
        deps.userPreferences.$marketPulseEnabled
            .receive(on: DispatchQueue.main)
            .sink { [weak self] enabled in
                guard let self else { return }
                let wasDisabled = !self.showMarketPulse
                self.showMarketPulse = enabled
                if enabled && wasDisabled {
                    Task { await self.fetchMarketData() }
                }
            }
            .store(in: &cancellables)
    }

    func announceForVoiceOver(_ message: String) {
        UIAccessibility.post(notification: .announcement, argument: message)
    }
}

