import Foundation
import os

/// Owns the shared execution gate for foreground, background and manual purchases.
@MainActor
final class DcaExecutionEngine {
    private let database: DcaDatabase
    private let sandboxDatabase: DcaDatabase
    private let credentialsStore: CredentialsStore
    private let userPreferences: UserPreferences
    private let exchangeApiFactory: ExchangeApiFactory
    private let notificationService: NotificationService
    private let strategyMultiplierUseCase: CalculateStrategyMultiplierUseCase
    private let logger = Logger(subsystem: "com.accbot.dca", category: "DcaExecutionEngine")
    private var isExecuting = false

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
        self.strategyMultiplierUseCase = CalculateStrategyMultiplierUseCase(marketDataService: marketDataService)
    }

    @discardableResult
    func executeDuePlans() async -> Bool {
        guard !isExecuting, !Task.isCancelled else { return false }
        isExecuting = true
        defer { isExecuting = false }

        let isSandbox = userPreferences.isSandboxMode()
        let db = isSandbox ? sandboxDatabase : database
        await resolvePendingTransactions(in: db, isSandbox: isSandbox)

        do {
            var ranWithoutDeferral = true
            for plan in try db.planDao.getDuePlans() {
                guard canContinue(isSandbox: isSandbox) else { return false }
                if !(await executePlan(plan, in: db, isSandbox: isSandbox)) {
                    ranWithoutDeferral = false
                }
            }
            return ranWithoutDeferral && canContinue(isSandbox: isSandbox)
        } catch {
            logger.error("Could not load due DCA plans")
            return false
        }
    }

    func executePlan(_ planId: Int64) async {
        await executePlans([planId])
    }

    @discardableResult
    func executePlans(_ planIds: [Int64]) async -> Bool {
        guard !isExecuting, !Task.isCancelled else { return false }
        isExecuting = true
        defer { isExecuting = false }

        let isSandbox = userPreferences.isSandboxMode()
        let db = isSandbox ? sandboxDatabase : database
        await resolvePendingTransactions(in: db, isSandbox: isSandbox)

        var ranWithoutDeferral = true
        for planId in planIds {
            guard canContinue(isSandbox: isSandbox) else { return false }
            do {
                if let plan = try db.planDao.getById(planId),
                   !(await executePlan(plan, in: db, isSandbox: isSandbox, forceRun: true)) {
                    ranWithoutDeferral = false
                }
            } catch {
                logger.error("Could not load DCA plan \(planId)")
                ranWithoutDeferral = false
            }
        }
        return ranWithoutDeferral && canContinue(isSandbox: isSandbox)
    }

    func resolvePurchaseReview(_ transaction: Transaction, confirmedOrderId: String?, isSandbox: Bool) async throws {
        guard !isExecuting else { throw PurchaseReviewError.purchaseInProgress }
        guard canContinue(isSandbox: isSandbox) else { throw PurchaseReviewError.noPendingPurchase }
        isExecuting = true
        defer { isExecuting = false }
        let db = isSandbox ? sandboxDatabase : database
        do {
            try db.transactionDao.resolvePurchaseReview(transaction, confirmedOrderId: confirmedOrderId)
        } catch PurchaseReviewError.orderNotInHistory {
            guard let orderId = confirmedOrderId,
                  let credentials = try credentialsStore.load(for: transaction.exchange, isSandbox: isSandbox) else {
                throw PurchaseReviewError.orderNotInHistory
            }
            let api = exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            guard let receipt = await api.getOrderStatus(orderId: orderId, crypto: transaction.crypto, fiat: transaction.fiat),
                  canContinue(isSandbox: isSandbox),
                  Self.hasConfirmedFill(receipt), receipt.exchangeOrderId == orderId,
                  receipt.exchange == transaction.exchange, receipt.crypto == transaction.crypto, receipt.fiat == transaction.fiat,
                  receipt.executedAt >= transaction.executedAt.addingTimeInterval(-60),
                  receipt.executedAt <= transaction.executedAt.addingTimeInterval(5 * 60) else {
                throw PurchaseReviewError.orderNotInHistory
            }
            try db.transactionDao.resolvePurchaseReview(
                transaction, confirmedOrderId: orderId,
                confirmedPurchase: Self.recordedPurchase(transaction, receipt: receipt, orderId: orderId)
            )
        }
        if confirmedOrderId != nil { notifyIfTargetReached(for: transaction.planId, in: db) }
    }

    private func canContinue(isSandbox: Bool) -> Bool {
        !Task.isCancelled && userPreferences.isSandboxMode() == isSandbox
    }

    private func executePlan(
        _ plan: DcaPlan,
        in db: DcaDatabase,
        isSandbox: Bool,
        forceRun: Bool = false
    ) async -> Bool {
        guard plan.isEnabled else { return true }
        if !forceRun, let nextExecution = plan.nextExecutionAt, nextExecution > Date() { return true }

        do {
            if try db.transactionDao.hasUnidentifiedPurchase(for: plan.id) {
                if forceRun {
                    reportReview(for: plan, in: db)
                }
                return false
            }
        } catch {
            logger.error("Could not check outstanding purchases for plan \(plan.id)")
            return false
        }

        let credentials: ExchangeCredentials
        do {
            guard let stored = try credentialsStore.load(for: plan.exchange, isSandbox: isSandbox) else {
                let message = String(localized: "Connect the exchange before running this DCA plan.")
                if forceRun { reportError(message, for: plan, in: db) }
                logger.info("No credentials for plan \(plan.id)")
                return false
            }
            credentials = stored
        } catch CredentialsError.temporarilyUnavailable {
            logger.info("Waiting for protected exchange credentials for plan \(plan.id)")
            if forceRun {
                reportError(String(localized: "Unlock the device to make exchange credentials available."), for: plan, in: db)
            }
            return false
        } catch {
            logger.error("Could not read exchange credentials for plan \(plan.id)")
            if forceRun {
                reportError(String(localized: "Unable to read exchange credentials."), for: plan, in: db)
            }
            return false
        }

        let strategy = await strategyMultiplierUseCase.invoke(strategy: plan.strategy, crypto: plan.crypto, fiat: plan.fiat)
        let amount = roundDecimal(plan.amount * Decimal(Double(strategy.multiplier)), scale: 2)
        let minimum = MinOrderSizeRepository.getMinOrderSize(exchange: plan.exchange, fiat: plan.fiat)
        guard canContinue(isSandbox: isSandbox),
              (try? credentialsStore.load(for: plan.exchange, isSandbox: isSandbox)) == credentials else { return false }

        let started = Date()
        let belowMinimum = amount <= 0 || amount < minimum
        let minimumError = String(localized: "The purchase amount is below the exchange minimum.")
        let intent = Transaction(
            planId: plan.id, exchange: plan.exchange, crypto: plan.crypto, fiat: plan.fiat,
            fiatAmount: amount, cryptoAmount: 0, price: 0, fee: 0,
            status: belowMinimum ? .failed : .pending,
            errorMessage: belowMinimum ? minimumError : nil,
            warningMessage: belowMinimum ? nil : reviewMessage,
            executedAt: started
        )
        let intentId: Int64
        do {
            switch try db.planDao.beginPurchase(intent, for: plan, nextExecution: nextExecution(for: plan, from: started)) {
            case .started(let id): intentId = id
            case .targetReached:
                notifyTargetReached(for: plan, in: db)
                return true
            case .blocked:
                if forceRun {
                    reportError(String(localized: "The plan changed or has a purchase awaiting confirmation."), for: plan, in: db)
                }
                return false
            }
        } catch {
            logger.error("Could not reserve DCA plan \(plan.id); order was not sent")
            return false
        }

        if belowMinimum {
            reportError(minimumError, for: plan, in: db)
            return true
        }

        // Once the intent is stored, an interruption leaves a pending record.
        // Never infer that an order failed to reach the exchange or submit it again.
        let api = exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
        let result = await api.marketBuy(crypto: plan.crypto, fiat: plan.fiat, fiatAmount: amount)
        do {
            switch result {
            case .success(let order):
                let orderId = order.exchangeOrderId.flatMap { $0.isEmpty ? nil : $0 }
                let hasFill = orderId != nil && Self.hasConfirmedFill(order)
                let failed = orderId != nil && order.status == .failed
                let transaction = Transaction(
                    id: intentId, planId: plan.id, exchange: plan.exchange, crypto: plan.crypto, fiat: plan.fiat,
                    fiatAmount: hasFill ? order.fiatAmount : amount,
                    cryptoAmount: hasFill ? order.cryptoAmount : 0,
                    price: hasFill ? order.price : 0, fee: hasFill ? order.fee : 0,
                    feeAsset: hasFill ? order.feeAsset : "",
                    status: hasFill || failed ? order.status : .pending,
                    exchangeOrderId: orderId,
                    errorMessage: failed ? order.errorMessage : nil,
                    warningMessage: orderId == nil ? reviewMessage : nil,
                    executedAt: started
                )
                try db.transactionDao.update(transaction)
                let settled = if transaction.status == .pending && canContinue(isSandbox: isSandbox) {
                    try await settlePendingPurchase(transaction, api: api, in: db) ?? transaction
                } else {
                    transaction
                }
                if Self.hasConfirmedFill(settled) {
                    notifyPurchase(settled, in: db)
                    if canContinue(isSandbox: isSandbox) {
                        await checkWithdrawalThreshold(plan: plan, api: api, db: db)
                        if canContinue(isSandbox: isSandbox) {
                            await checkLowBalance(api: api, plan: plan, db: db)
                        }
                    }
                } else if settled.status == .failed {
                    reportError(settled.errorMessage ?? String(localized: "The order ended without a fill."), for: plan, in: db)
                } else if orderId == nil {
                    reportReview(for: plan, in: db)
                } else {
                    notify(
                        type: .purchase, title: String(localized: "Purchase awaiting confirmation"),
                        message: String(localized: "The exchange accepted the order. Fill details are pending."),
                        plan: plan, db: db
                    )
                }
            case .rejected(let message):
                try db.transactionDao.update(Transaction(
                    id: intentId, planId: plan.id, exchange: plan.exchange, crypto: plan.crypto, fiat: plan.fiat,
                    fiatAmount: amount, cryptoAmount: 0, price: 0, fee: 0,
                    status: .failed, errorMessage: message, executedAt: started
                ))
                reportError(message, for: plan, in: db)
            case .error:
                reportReview(for: plan, in: db)
            }
        } catch {
            logger.error("Could not record purchase outcome for plan \(plan.id)")
            reportReview(for: plan, in: db)
            return false
        }
        return true
    }

    private var reviewMessage: String {
        String(localized: "Check this purchase on the exchange before allowing the plan to run again.")
    }

    private func reportReview(for plan: DcaPlan, in db: DcaDatabase) {
        reportError(reviewMessage, for: plan, in: db)
    }

    private func reportError(_ message: String, for plan: DcaPlan, in db: DcaDatabase) {
        if userPreferences.notificationsEnabled && userPreferences.errorNotifications {
            notificationService.postErrorNotification(exchange: plan.exchange, message: message)
        }
        notify(type: .error, title: String(localized: "DCA Error"), message: message, plan: plan, db: db)
    }

    private func notifyPurchase(_ transaction: Transaction, in db: DcaDatabase) {
        let partial = transaction.status == .partial
        if userPreferences.notificationsEnabled && userPreferences.purchaseNotifications {
            notificationService.postPurchaseNotification(
                crypto: transaction.crypto, fiat: transaction.fiat,
                amount: transaction.fiatAmount, exchange: transaction.exchange, partial: partial
            )
        }
        let details = "\(transaction.cryptoAmount) \(transaction.crypto) · \(transaction.fiatAmount) \(transaction.fiat)"
        try? db.notificationDao.insert(AppNotification(
            type: .purchase,
            title: partial ? String(localized: "DCA Purchase Partially Filled") : String(localized: "DCA Purchase Completed"),
            message: details,
            planId: transaction.planId, crypto: transaction.crypto, exchange: transaction.exchange
        ))
        notifyIfTargetReached(for: transaction.planId, in: db)
    }

    private func notifyIfTargetReached(for planId: Int64, in db: DcaDatabase) {
        if let plan = try? db.planDao.pauseIfTargetReached(planId) {
            notifyTargetReached(for: plan, in: db)
        }
    }

    private func notifyTargetReached(for plan: DcaPlan, in db: DcaDatabase) {
        notify(
            type: .purchase, title: String(localized: "Accumulation target reached"),
            message: String(localized: "The plan has been paused."), plan: plan, db: db
        )
    }

    private func nextExecution(for plan: DcaPlan, from now: Date) -> Date {
        if let cron = plan.cronExpression, let next = CronUtils.getNextExecution(cron: cron, from: now) {
            return next
        }
        let interval = plan.frequency.intervalMinutes > 0 ? plan.frequency.intervalMinutes : 1440
        return now.addingTimeInterval(TimeInterval(interval * 60))
    }

    private func checkWithdrawalThreshold(plan: DcaPlan, api: ExchangeApi, db: DcaDatabase) async {
        do {
            guard let threshold = try db.withdrawalThresholdDao.get(crypto: plan.crypto, exchange: plan.exchange),
                  let balance = await api.getBalance(currency: plan.crypto),
                  balance >= threshold.thresholdAmount else { return }
            notificationService.postWithdrawalThresholdNotification(
                crypto: plan.crypto, exchange: plan.exchange, amount: balance
            )
            notify(
                type: .withdrawalThreshold, title: String(localized: "Withdrawal Threshold Reached"),
                message: "\(balance) \(plan.crypto) · \(plan.exchange.displayName)",
                plan: plan, db: db
            )
        } catch {
            logger.error("Could not read withdrawal threshold for plan \(plan.id)")
        }
    }

    private func checkLowBalance(api: ExchangeApi, plan: DcaPlan, db: DcaDatabase) async {
        guard let balance = await api.getBalance(currency: plan.fiat), plan.amount > 0 else { return }
        let executions = NSDecimalNumber(decimal: balance / plan.amount).doubleValue
        let interval = plan.cronExpression.flatMap { CronUtils.getIntervalMinutesEstimate(cron: $0) }
            ?? (plan.frequency.intervalMinutes > 0 ? plan.frequency.intervalMinutes : 1440)
        let days = floor(executions) * Double(interval) / 1440.0
        guard days.isFinite, days >= 0, days < Double(userPreferences.lowBalanceThresholdDays) else { return }

        if userPreferences.notificationsEnabled {
            notificationService.postLowBalanceNotification(
                exchange: plan.exchange, fiat: plan.fiat, balance: balance, daysLeft: Int(days)
            )
        }
        notify(
            type: .lowBalance, title: String(localized: "Low Balance Warning"),
            message: "\(plan.exchange.displayName) · \(balance) \(plan.fiat)", plan: plan, db: db
        )
    }

    private func notify(type: NotificationType, title: String, message: String, plan: DcaPlan, db: DcaDatabase) {
        try? db.notificationDao.insert(AppNotification(
            type: type, title: title, message: message, planId: plan.id, crypto: plan.crypto, exchange: plan.exchange
        ))
    }

    private func resolvePendingTransactions(in db: DcaDatabase, isSandbox: Bool) async {
        do {
            for tx in try db.transactionDao.getPendingTransactions() {
                guard canContinue(isSandbox: isSandbox) else { return }
                guard !tx.needsPurchaseReview,
                      let credentials = credentialsStore.get(for: tx.exchange, isSandbox: isSandbox) else { continue }
                let api = exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
                guard let settled = try await settlePendingPurchase(tx, api: api, in: db) else { continue }
                if Self.hasConfirmedFill(settled) {
                    notifyPurchase(settled, in: db)
                } else if let plan = try db.planDao.getById(tx.planId) {
                    reportError(settled.errorMessage ?? String(localized: "The order ended without a fill."), for: plan, in: db)
                }
            }
        } catch {
            logger.warning("Could not update pending purchase records")
        }
    }

    private func settlePendingPurchase(_ tx: Transaction, api: ExchangeApi, in db: DcaDatabase) async throws -> Transaction? {
        guard let orderId = tx.exchangeOrderId, !orderId.isEmpty,
              let resolved = await api.getOrderStatus(orderId: orderId, crypto: tx.crypto, fiat: tx.fiat),
              resolved.exchangeOrderId == orderId,
              Self.hasConfirmedFill(resolved) || resolved.status == .failed else { return nil }

        let updated = Self.recordedPurchase(tx, receipt: resolved, orderId: orderId)
        try db.transactionDao.update(updated)
        return updated
    }

    private static func recordedPurchase(_ tx: Transaction, receipt resolved: Transaction, orderId: String) -> Transaction {
        Transaction(
            id: tx.id, planId: tx.planId, exchange: tx.exchange, crypto: tx.crypto, fiat: tx.fiat,
            fiatAmount: resolved.fiatAmount, cryptoAmount: resolved.cryptoAmount,
            price: resolved.price, fee: resolved.fee, feeAsset: resolved.feeAsset.isEmpty ? tx.fiat : resolved.feeAsset,
            status: resolved.status, exchangeOrderId: orderId, errorMessage: resolved.errorMessage,
            executedAt: tx.executedAt
        )
    }

    private static func hasConfirmedFill(_ transaction: Transaction) -> Bool {
        (transaction.status == .completed || transaction.status == .partial)
            && transaction.cryptoAmount > 0 && transaction.fiatAmount > 0 && transaction.price > 0
    }

    private func roundDecimal(_ value: Decimal, scale: Int) -> Decimal {
        let handler = NSDecimalNumberHandler(
            roundingMode: .plain, scale: Int16(scale), raiseOnExactness: false,
            raiseOnOverflow: false, raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: value).rounding(accordingToBehavior: handler).decimalValue
    }
}
