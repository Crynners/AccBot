import Foundation
import os

/// Core DCA execution logic, shared across BGTask, foreground catch-up, and manual "Run Now".
/// Ported from Android DcaWorker.doWork().
final class DcaExecutionEngine {
    private let database: DcaDatabase
    private let sandboxDatabase: DcaDatabase
    private let credentialsStore: CredentialsStore
    private let userPreferences: UserPreferences
    private let exchangeApiFactory: ExchangeApiFactory
    private let notificationService: NotificationService
    private let marketDataService: MarketDataService
    private let strategyMultiplierUseCase: CalculateStrategyMultiplierUseCase
    private let logger = Logger(subsystem: "com.accbot.dca", category: "DcaExecutionEngine")

    private let maxAttempts = 3
    private let retryDelayNs: UInt64 = 2_000_000_000 // 2s

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .none
        f.timeStyle = .short
        return f
    }()

    init(
        database: DcaDatabase,
        sandboxDatabase: DcaDatabase,
        credentialsStore: CredentialsStore,
        userPreferences: UserPreferences,
        exchangeApiFactory: ExchangeApiFactory,
        notificationService: NotificationService,
        marketDataService: MarketDataService
    ) {
        self.database = database
        self.sandboxDatabase = sandboxDatabase
        self.credentialsStore = credentialsStore
        self.userPreferences = userPreferences
        self.exchangeApiFactory = exchangeApiFactory
        self.notificationService = notificationService
        self.marketDataService = marketDataService
        self.strategyMultiplierUseCase = CalculateStrategyMultiplierUseCase(marketDataService: marketDataService)
    }

    private var activeDb: DcaDatabase {
        userPreferences.sandboxMode ? sandboxDatabase : database
    }

    // MARK: - Execute Due Plans

    /// Execute all enabled plans that are due. Called from BGTask, foreground catch-up, etc.
    /// - Parameter before: cutoff date for due plans (default: now).
    ///   Pass a future date to include plans due within a tolerance window
    ///   (used by Shortcuts automations that may fire slightly early).
    func executeDuePlans(before: Date? = nil) async {
        logger.info("DCA execution started")

        // Resolve PENDING transactions from previous runs
        await resolvePendingTransactions()

        do {
            let cutoff = before ?? Date()
            let duePlans = try activeDb.planDao.getDuePlans(before: cutoff)
            if duePlans.isEmpty {
                logger.info("No due DCA plans")
                return
            }

            for plan in duePlans {
                // Scenario B: detect missed purchases from phone being off
                detectMissedFromBoot(plan: plan, now: cutoff)
                await executePlan(plan, forceRun: false)
            }
        } catch {
            logger.error("Failed to fetch due plans: \(error.localizedDescription)")
        }

        // Update widget data after execution
        if let deps = await MainActor.run(body: { AppDependencies.shared }) {
            await MainActor.run { WidgetDataService.update(from: deps) }
        }
    }

    /// Execute a specific plan (for "Run Now")
    func executePlan(_ planId: Int64) async {
        do {
            guard let plan = try activeDb.planDao.getById(planId) else {
                logger.error("Plan \(planId) not found")
                return
            }
            await executePlan(plan, forceRun: true)
        } catch {
            logger.error("Failed to fetch plan \(planId): \(error.localizedDescription)")
        }
    }

    /// Execute selected plans (for "Run Now" with multi-select)
    func executePlans(_ planIds: [Int64]) async {
        for planId in planIds {
            await executePlan(planId)
        }
    }

    // MARK: - Core Execution

    private func executePlan(_ plan: DcaPlan, forceRun: Bool) async {
        let now = Date()

        // Check if it's time to execute
        if !forceRun, let nextExecution = plan.nextExecutionAt, nextExecution > now {
            logger.info("Plan \(plan.id) not due yet, skipping")
            return
        }

        // Atomically claim the plan to prevent double-purchase from concurrent BGTasks.
        // Advances nextExecutionAt only if still in the past, returning false
        // if another task already claimed it.
        if !forceRun {
            let nextExecution = calculateNextExecution(plan: plan, from: now)
            let claimed = (try? activeDb.planDao.claimPlanForExecution(
                id: plan.id, now: now, nextExecutionAt: nextExecution
            )) ?? false
            guard claimed else {
                logger.info("Plan \(plan.id) already claimed by another task, skipping")
                return
            }
            logger.info("Plan \(plan.id) claimed, nextExecution advanced to \(nextExecution)")
        }

        // Get credentials
        let isSandbox = userPreferences.isSandboxMode()
        guard let credentials = credentialsStore.get(for: plan.exchange, isSandbox: isSandbox) else {
            logger.error("No credentials for \(plan.exchange.displayName) (sandbox=\(isSandbox))")
            let failedTx = Transaction(
                planId: plan.id,
                exchange: plan.exchange,
                crypto: plan.crypto,
                fiat: plan.fiat,
                fiatAmount: plan.amount,
                cryptoAmount: 0,
                price: 0,
                fee: 0,
                status: .failed,
                errorMessage: "No API credentials configured (sandbox=\(isSandbox))"
            )
            saveTransactionAndAdvance(failedTx, plan: plan, now: now)
            return
        }

        // Calculate strategy multiplier
        let strategyResult = await calculateStrategyMultiplier(
            strategy: plan.strategy,
            crypto: plan.crypto,
            fiat: plan.fiat
        )

        let purchaseAmount = roundDecimal(
            plan.amount * Decimal(Double(strategyResult.multiplier)),
            scale: 2
        )

        logger.info("Strategy: \(plan.strategy.dbString), Base: \(plan.amount), Multiplier: \(strategyResult.multiplier), Final: \(purchaseAmount)")

        // Check minimum order size
        let minOrderSize = MinOrderSizeRepository.getMinOrderSize(exchange: plan.exchange, fiat: plan.fiat)
        if purchaseAmount < minOrderSize {
            logger.warning("Plan \(plan.id): \(purchaseAmount) < minimum \(minOrderSize), skipping")
            let failedTx = Transaction(
                planId: plan.id,
                exchange: plan.exchange,
                crypto: plan.crypto,
                fiat: plan.fiat,
                fiatAmount: purchaseAmount,
                cryptoAmount: 0,
                price: 0,
                fee: 0,
                status: .failed,
                errorMessage: "Amount \(purchaseAmount) \(plan.fiat) below minimum \(minOrderSize) \(plan.fiat)"
            )
            saveTransactionAndAdvance(failedTx, plan: plan, now: now)
            let args = NotificationTemplateArgs.belowMinimum(
                crypto: plan.crypto, purchaseAmount: "\(purchaseAmount)",
                fiat: plan.fiat, minOrderSize: "\(minOrderSize)"
            )
            saveInAppNotification(type: .error, title: String(localized: "DCA Failed"), message: String(localized: "Amount below minimum for \(plan.crypto)"), plan: plan, templateArgs: args)
            return
        }

        // Execute with retry
        let api = exchangeApiFactory.create(credentials: credentials)
        var failedAttemptMessages: [String] = []
        var finalResult: DcaResult?

        for attempt in 1...maxAttempts {
            let result = await withTimeout(seconds: 30) {
                await api.marketBuy(crypto: plan.crypto, fiat: plan.fiat, fiatAmount: purchaseAmount)
            }

            let attemptResult = result ?? .error(message: "API call timed out after 30s", retryable: true)

            if case .success = attemptResult {
                finalResult = attemptResult
                break
            }

            if case .error(let msg, _) = attemptResult {
                failedAttemptMessages.append("Attempt \(attempt): \(msg)")
                logger.warning("Plan \(plan.id) attempt \(attempt)/\(self.maxAttempts) failed: \(msg)")
            }

            if attempt < maxAttempts {
                try? await Task.sleep(nanoseconds: retryDelayNs)
            } else {
                finalResult = attemptResult
            }
        }

        let warningMessage: String? = if case .success = finalResult, !failedAttemptMessages.isEmpty {
            failedAttemptMessages.joined(separator: "; ")
        } else {
            nil
        }

        switch finalResult {
        case .success(let tx):
            let savedTx = Transaction(
                planId: plan.id,
                exchange: plan.exchange,
                crypto: plan.crypto,
                fiat: plan.fiat,
                fiatAmount: tx.fiatAmount,
                cryptoAmount: tx.cryptoAmount,
                price: tx.price,
                fee: tx.fee,
                feeAsset: tx.feeAsset,
                status: tx.status,
                exchangeOrderId: tx.exchangeOrderId,
                warningMessage: warningMessage
            )
            saveTransactionAndAdvance(savedTx, plan: plan, now: now)

            // Delay info: use originalScheduledAt (retry series) or plan.nextExecutionAt
            let scheduledAt = forceRun ? nil : (plan.originalScheduledAt ?? plan.nextExecutionAt)
            let showTimes = !forceRun && (scheduledAt.map { now.timeIntervalSince($0) > 300 } ?? false)
            let timeFmt = Self.timeFormatter

            if userPreferences.notificationsEnabled && userPreferences.purchaseNotifications {
                notificationService.postPurchaseNotification(
                    crypto: plan.crypto,
                    fiat: plan.fiat,
                    amount: tx.fiatAmount,
                    exchange: plan.exchange,
                    scheduledAt: showTimes ? scheduledAt : nil,
                    executedAt: now
                )
            }
            let purchaseArgs = NotificationTemplateArgs.purchase(
                cryptoAmount: "\(tx.cryptoAmount)", crypto: plan.crypto,
                fiatAmount: "\(tx.fiatAmount)", fiat: plan.fiat,
                price: "\(tx.price)",
                scheduledAt: showTimes ? scheduledAt.map { timeFmt.string(from: $0) } : nil,
                executedAt: showTimes ? timeFmt.string(from: now) : nil
            )
            saveInAppNotification(
                type: .purchase,
                title: String(localized: "DCA Purchase Completed"),
                message: String(localized: "Bought \(tx.cryptoAmount as NSDecimalNumber) \(plan.crypto) for \(tx.fiatAmount as NSDecimalNumber) \(plan.fiat)"),
                plan: plan,
                templateArgs: purchaseArgs
            )

            // Detect missed purchases after a retry series.
            // Subtract 1 because the purchase that just succeeded covers one slot.
            if !forceRun, let originalScheduled = plan.originalScheduledAt {
                let actualMissed = calculateMissedPurchases(plan: plan, from: originalScheduled, to: now) - 1
                if actualMissed > 0 {
                    try? activeDb.planDao.setMissedPurchaseCount(id: plan.id, count: actualMissed)
                    notifyMissedPurchases(plan: plan, count: actualMissed)
                }
            }

            // Clear retry state
            if plan.networkRetryCount > 0 {
                try? activeDb.planDao.clearNetworkRetry(id: plan.id)
            }

            // Check withdrawal threshold
            await checkWithdrawalThreshold(plan: plan, api: api)

            // Check low balance
            await checkLowBalance(api: api, plan: plan)

            logger.info("DCA purchase successful: \(tx.cryptoAmount) \(plan.crypto)")

        case .error(let msg, let retryable):
            if retryable {
                // Network error - retry in 5 min
                let retryTime = now.addingTimeInterval(300)
                let newRetryCount = plan.networkRetryCount + 1
                let isFirstFailure = plan.networkRetryCount == 0

                // Save retry state on plan (originalScheduledAt set only on first failure)
                try? activeDb.planDao.setNetworkRetry(
                    id: plan.id,
                    count: newRetryCount,
                    nextRetryAt: retryTime,
                    originalScheduledAt: plan.nextExecutionAt
                )
                logger.warning("Network error for plan \(plan.id) (retry #\(newRetryCount)), next at \(retryTime): \(msg)")

                // Push + in-app notification only on FIRST failure
                if isFirstFailure {
                    if userPreferences.notificationsEnabled && userPreferences.errorNotifications {
                        notificationService.postNetworkRetryNotification(
                            crypto: plan.crypto,
                            exchange: plan.exchange
                        )
                    }
                    let retryArgs = NotificationTemplateArgs.networkRetry(
                        crypto: plan.crypto,
                        exchangeName: plan.exchange.displayName
                    )
                    saveInAppNotification(
                        type: .networkRetry,
                        title: String(localized: "Network Error"),
                        message: String(localized: "\(plan.crypto) purchase on \(plan.exchange.displayName) failed - no internet."),
                        plan: plan,
                        templateArgs: retryArgs
                    )
                }
            } else {
                let failedTx = Transaction(
                    planId: plan.id,
                    exchange: plan.exchange,
                    crypto: plan.crypto,
                    fiat: plan.fiat,
                    fiatAmount: plan.amount,
                    cryptoAmount: 0,
                    price: 0,
                    fee: 0,
                    status: .failed,
                    errorMessage: msg,
                    warningMessage: failedAttemptMessages.count > 1
                        ? failedAttemptMessages.dropLast().joined(separator: "; ")
                        : nil
                )
                saveTransactionAndAdvance(failedTx, plan: plan, now: now)

                // Clear any retry state on non-retryable failure
                if plan.networkRetryCount > 0 {
                    try? activeDb.planDao.clearNetworkRetry(id: plan.id)
                }

                if userPreferences.notificationsEnabled && userPreferences.errorNotifications {
                    notificationService.postErrorNotification(exchange: plan.exchange, message: msg)
                }
                let errorArgs = NotificationTemplateArgs.error(crypto: plan.crypto, errorMessage: msg)
                saveInAppNotification(type: .error, title: String(localized: "DCA Failed"), message: "\(plan.crypto): \(msg)", plan: plan, templateArgs: errorArgs)
            }

        case nil:
            logger.error("Plan \(plan.id): no result (unexpected)")
        }
    }

    // MARK: - Helpers

    private func saveTransactionAndAdvance(_ transaction: Transaction, plan: DcaPlan, now: Date) {
        do {
            try activeDb.transactionDao.insert(transaction)
            let nextExecution = calculateNextExecution(plan: plan, from: now)
            try activeDb.planDao.updateExecution(id: plan.id, lastExecutedAt: now, nextExecutionAt: nextExecution)
        } catch {
            logger.error("Failed to save transaction for plan \(plan.id): \(error.localizedDescription)")
        }
    }

    private func calculateNextExecution(plan: DcaPlan, from now: Date) -> Date {
        plan.calculateNextExecution(from: now)
    }

    private func calculateStrategyMultiplier(
        strategy: DcaStrategy,
        crypto: String,
        fiat: String
    ) async -> StrategyMultiplierResult {
        await strategyMultiplierUseCase.invoke(strategy: strategy, crypto: crypto, fiat: fiat)
    }

    private func checkWithdrawalThreshold(plan: DcaPlan, api: ExchangeApi) async {
        do {
            guard let threshold = try activeDb.withdrawalThresholdDao.get(crypto: plan.crypto, exchange: plan.exchange) else { return }
            guard let balance = await api.getBalance(currency: plan.crypto) else { return }
            if balance >= threshold.thresholdAmount {
                notificationService.postWithdrawalThresholdNotification(
                    crypto: plan.crypto,
                    exchange: plan.exchange,
                    amount: balance
                )
                let wtArgs = NotificationTemplateArgs.withdrawalThreshold(
                    amount: "\(balance)", crypto: plan.crypto, exchangeName: plan.exchange.displayName
                )
                saveInAppNotification(
                    type: .withdrawalThreshold,
                    title: String(localized: "Withdrawal Recommended"),
                    message: String(localized: "You have accumulated \(balance as NSDecimalNumber) \(plan.crypto) on \(plan.exchange.displayName) - consider withdrawing to cold wallet"),
                    plan: plan,
                    templateArgs: wtArgs
                )
            }
        } catch {
            logger.error("Error checking withdrawal threshold: \(error.localizedDescription)")
        }
    }

    private func checkLowBalance(api: ExchangeApi, plan: DcaPlan) async {
        do {
            guard let balance = await api.getBalance(currency: plan.fiat) else { return }
            guard plan.amount > 0 else { return }
            let remainingExec = NSDecimalNumber(decimal: balance / plan.amount).doubleValue
            let intervalMinutes = plan.cronExpression != nil
                ? (CronUtils.getIntervalMinutesEstimate(cron: plan.cronExpression!) ?? 1440)
                : plan.frequency.intervalMinutes
            let remainingDays = remainingExec * Double(intervalMinutes) / 1440.0
            let thresholdDays = userPreferences.lowBalanceThresholdDays
            let displayDays = max(1, Int(ceil(remainingDays)))

            if remainingDays < Double(thresholdDays) {
                if userPreferences.notificationsEnabled {
                    notificationService.postLowBalanceNotification(
                        exchange: plan.exchange,
                        fiat: plan.fiat,
                        balance: balance,
                        daysLeft: displayDays
                    )
                }
                let lbArgs = NotificationTemplateArgs.lowBalance(
                    exchangeName: plan.exchange.displayName, fiat: plan.fiat,
                    balance: "\(balance as NSDecimalNumber)", remainingDays: displayDays
                )
                saveInAppNotification(
                    type: .lowBalance,
                    title: String(localized: "Low balance on \(plan.exchange.displayName)"),
                    message: String(localized: "~\(displayDays) days of \(plan.fiat) remaining for DCA"),
                    plan: plan,
                    templateArgs: lbArgs
                )
            }
        } catch {
            logger.error("Error checking low balance: \(error.localizedDescription)")
        }
    }

    private func saveInAppNotification(type: NotificationType, title: String, message: String, plan: DcaPlan, templateArgs: NotificationTemplateArgs? = nil) {
        let notification = AppNotification(
            type: type,
            title: title,
            message: message,
            planId: plan.id,
            crypto: plan.crypto,
            exchange: plan.exchange,
            templateArgs: templateArgs
        )
        try? activeDb.notificationDao.insert(notification)
    }

    // MARK: - Missed Purchase Detection

    /// Calculate how many purchases were missed between originalScheduledAt and now.
    private func calculateMissedPurchases(plan: DcaPlan, from: Date, to: Date) -> Int {
        if let cron = plan.cronExpression {
            // For cron plans: count actual cron executions between dates
            return CronUtils.countExecutions(cron: cron, from: from, to: to)
        } else {
            // For interval plans: floor((elapsed) / interval)
            let intervalSeconds = TimeInterval((plan.frequency.intervalMinutes > 0 ? plan.frequency.intervalMinutes : 1440) * 60)
            let elapsed = to.timeIntervalSince(from)
            return max(0, Int(floor(elapsed / intervalSeconds)))
        }
    }

    /// Scenario B: Detect missed purchases when phone was off (no retry state).
    /// Called at the start of executeDuePlans for plans that are significantly overdue.
    private func detectMissedFromBoot(plan: DcaPlan, now: Date) {
        guard plan.networkRetryCount == 0,
              plan.originalScheduledAt == nil,
              plan.missedPurchaseCount == 0,
              let nextExec = plan.nextExecutionAt
        else { return }

        let intervalMinutes = plan.cronExpression != nil
            ? (CronUtils.getIntervalMinutesEstimate(cron: plan.cronExpression!) ?? 1440)
            : (plan.frequency.intervalMinutes > 0 ? plan.frequency.intervalMinutes : 1440)
        let intervalSeconds = TimeInterval(intervalMinutes * 60)

        // Only if overdue by more than 1 interval
        let overdueSeconds = now.timeIntervalSince(nextExec)
        guard overdueSeconds > intervalSeconds else { return }

        // Subtract 1 because executePlan() will run right after and cover one slot.
        let actualMissed = calculateMissedPurchases(plan: plan, from: nextExec, to: now) - 1
        if actualMissed > 0 {
            try? activeDb.planDao.setMissedPurchaseCount(id: plan.id, count: actualMissed)
            notifyMissedPurchases(plan: plan, count: actualMissed)
            logger.info("Scenario B: plan \(plan.id) missed \(actualMissed) purchases (phone was off)")
        }
    }

    private func notifyMissedPurchases(plan: DcaPlan, count: Int) {
        if userPreferences.notificationsEnabled && userPreferences.errorNotifications {
            notificationService.postMissedPurchasesNotification(
                crypto: plan.crypto,
                exchange: plan.exchange,
                count: count
            )
        }
        let args = NotificationTemplateArgs.missedPurchases(
            count: count,
            crypto: plan.crypto,
            exchangeName: plan.exchange.displayName
        )
        saveInAppNotification(
            type: .missedPurchases,
            title: String(localized: "Missed Purchases"),
            message: String(localized: "\(count) missed \(plan.crypto) purchases on \(plan.exchange.displayName) while offline."),
            plan: plan,
            templateArgs: args
        )
    }

    // MARK: - Pending Resolution

    private func resolvePendingTransactions() async {
        do {
            let pendingTxs = try activeDb.transactionDao.getPendingTransactions()
            let isSandbox = userPreferences.isSandboxMode()

            for tx in pendingTxs {
                guard let orderId = tx.exchangeOrderId else { continue }
                guard let credentials = credentialsStore.get(for: tx.exchange, isSandbox: isSandbox) else { continue }

                let api = exchangeApiFactory.create(credentials: credentials)
                if let resolved = await api.getOrderStatus(orderId: orderId) {
                    let updatedTx = Transaction(
                        id: tx.id,
                        planId: tx.planId,
                        exchange: tx.exchange,
                        crypto: tx.crypto,
                        fiat: tx.fiat,
                        fiatAmount: resolved.fiatAmount,
                        cryptoAmount: resolved.cryptoAmount,
                        price: resolved.price,
                        fee: resolved.fee,
                        feeAsset: resolved.feeAsset,
                        status: resolved.status,
                        exchangeOrderId: tx.exchangeOrderId,
                        executedAt: tx.executedAt
                    )
                    try activeDb.transactionDao.update(updatedTx)
                }
            }
        } catch {
            logger.warning("Failed to resolve pending transactions: \(error.localizedDescription)")
        }
    }

    private func withTimeout<T>(seconds: TimeInterval, operation: @escaping () async -> T) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask {
                await operation()
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                return nil
            }
            let result = await group.next() ?? nil
            group.cancelAll()
            return result
        }
    }

    private func roundDecimal(_ value: Decimal, scale: Int) -> Decimal {
        let handler = NSDecimalNumberHandler(
            roundingMode: .plain, scale: Int16(scale),
            raiseOnExactness: false, raiseOnOverflow: false,
            raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: value).rounding(accordingToBehavior: handler).decimalValue
    }
}
