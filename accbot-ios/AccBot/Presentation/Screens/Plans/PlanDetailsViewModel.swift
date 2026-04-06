import Foundation
import Combine

@MainActor
class PlanDetailsViewModel: ObservableObject {
    @Published var plan: DcaPlan?
    @Published var recentTransactions: [Transaction] = []
    @Published var isLoading: Bool = true
    @Published var errorMessage: String?

    // Performance metrics
    @Published var currentPrice: Decimal?
    @Published var currentValue: Decimal?
    @Published var roi: Decimal?  // percentage
    @Published var fiatGainLoss: Decimal?
    @Published var exchangeBalance: Decimal?
    @Published var remainingDays: Int?
    @Published var remainingExecutions: Int?
    @Published var totalInvested: Decimal = 0
    @Published var totalAccumulated: Decimal = 0
    @Published var avgPrice: Decimal = 0
    @Published var totalTransactionCount: Int = 0

    private let planId: Int64
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }
    private var cancellables = Set<AnyCancellable>()
    private var loadTask: Task<Void, Never>?

    init(planId: Int64) {
        self.planId = planId
    }

    deinit {
        loadTask?.cancel()
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadData()
    }

    // MARK: - Loading

    func loadData() {
        loadTask?.cancel()
        loadTask = Task {
            isLoading = true
            errorMessage = nil

            do {
                plan = try deps.activeDatabase.planDao.getById(planId)
                recentTransactions = try deps.activeDatabase.transactionDao.getByPlanId(
                    planId,
                    limit: 10
                )

                // Load all transactions for this plan (not just recent 10) for accurate totals
                let allTransactions = try deps.activeDatabase.transactionDao.getByPlanId(planId, limit: 10000)
                totalTransactionCount = allTransactions.count
                let allCompleted = allTransactions.filter { $0.status == .completed || $0.status == .partial }

                totalAccumulated = allCompleted.reduce(Decimal(0)) { $0 + $1.cryptoAmount }
                totalInvested = allCompleted.reduce(Decimal(0)) { $0 + $1.fiatAmount }

                if totalAccumulated > 0 {
                    avgPrice = totalInvested / totalAccumulated
                } else {
                    avgPrice = 0
                }

                // Fetch current price
                if let plan = plan {
                    currentPrice = await withTimeoutOrNil(seconds: 10) {
                        await self.deps.marketDataService.getCurrentPrice(
                            crypto: plan.crypto,
                            fiat: plan.fiat
                        )
                    }

                    if let price = currentPrice, totalAccumulated > 0 {
                        let value = totalAccumulated * price
                        currentValue = value

                        if totalInvested > 0 {
                            fiatGainLoss = value - totalInvested
                            roi = ((value - totalInvested) / totalInvested) * 100
                        }
                    }

                    // Exchange balance
                    exchangeBalance = try? deps.activeDatabase.exchangeBalanceDao.getBalance(
                        exchange: plan.exchange,
                        currency: plan.fiat
                    )

                    if let balance = exchangeBalance, plan.amount > 0 {
                        let remainingExec = NSDecimalNumber(decimal: balance / plan.amount).doubleValue
                        remainingExecutions = max(0, Int(floor(remainingExec)))

                        // Calculate remaining days — same formula as Dashboard
                        let rawInterval: Int
                        if let cron = plan.cronExpression {
                            rawInterval = CronUtils.getIntervalMinutesEstimate(cron: cron) ?? 1440
                        } else {
                            rawInterval = plan.frequency.intervalMinutes
                        }
                        let effectiveInterval = rawInterval > 0 ? rawInterval : 1440
                        remainingDays = max(0, Int(ceil(remainingExec * Double(effectiveInterval) / 1440.0)))
                    }
                }
            } catch {
                errorMessage = error.localizedDescription
            }

            isLoading = false
        }
    }

    // MARK: - Actions

    func toggleEnabled() {
        guard let plan = plan else { return }
        let newEnabled = !plan.isEnabled

        do {
            if newEnabled {
                // Atomically enable + recalculate next execution to avoid stale dates
                let next = plan.calculateNextExecution(from: Date())
                try deps.activeDatabase.planDao.setEnabledAndNextExecution(
                    id: planId, enabled: true, nextExecutionAt: next
                )
            } else {
                try deps.activeDatabase.planDao.setEnabled(id: planId, enabled: false)
            }
            // Reload to reflect the change
            self.plan = try deps.activeDatabase.planDao.getById(planId)

            // Reschedule background layers to reflect the change
            DcaBackgroundService.shared.rescheduleAllLayers(
                using: deps.activeDatabase,
                notificationService: deps.notificationService
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deletePlan() -> Bool {
        do {
            // Delete associated transactions first
            try deps.activeDatabase.transactionDao.deleteByPlanId(planId)
            // Delete the plan
            try deps.activeDatabase.planDao.delete(id: planId)

            // Reschedule background layers (removed plan may have been the next to execute)
            DcaBackgroundService.shared.rescheduleAllLayers(
                using: deps.activeDatabase,
                notificationService: deps.notificationService
            )

            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func deleteAllTransactions() -> Bool {
        do {
            try deps.activeDatabase.transactionDao.deleteByPlanId(planId)
            recentTransactions = []
            totalInvested = 0
            totalAccumulated = 0
            avgPrice = 0
            currentValue = nil
            roi = nil
            fiatGainLoss = nil
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}
