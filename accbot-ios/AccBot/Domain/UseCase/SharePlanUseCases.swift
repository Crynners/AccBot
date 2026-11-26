import Foundation

/// A file ready to send: a plain snapshot or an encrypted live-update share.
struct PlanShareFile {
    let fileName: String
    let content: Data
}

/// Which exchange key a live-update share carries.
enum LiveShareKey {
    /// A separate key the user created for sharing (the default). The check is the result of
    /// `VerifyShareKeyUseCase`; `.unverifiable` requires the user's explicit confirmation.
    case separate(ExchangeCredentials, keyCheck: ReadOnlyExchangeClient.KeyCheck)
    /// The key the plan itself trades with. The user accepted a warning that the recipient gets
    /// the key with all its exchange permissions. `CreatePlanShareUseCase` loads the key itself,
    /// so its secret never passes through the UI.
    case planKey
}

/// Exchange key and file password for a live-update share.
struct LiveShareAccess {
    let key: LiveShareKey
    let password: String
}

/// Reasons a key cannot be shared or used; the UI maps each case to a message.
enum ShareKeyProblem: Error, Equatable {
    case liveUpdatesUnsupported
    case wrongExchange
    case sameAsPlanKey
    case notReadOnly
    case checkFailed
    case planKeyMissing

    var message: String {
        switch self {
        case .liveUpdatesUnsupported: return String(localized: "Live updates are not available for this exchange.")
        case .wrongExchange: return String(localized: "The key must belong to the plan’s exchange.")
        case .sameAsPlanKey: return String(localized: "This is the key the plan trades with. Create a separate read-only key.")
        case .notReadOnly: return String(localized: "This key can trade or withdraw. Create a key with read-only access.")
        case .checkFailed: return String(localized: "The key could not be checked. Make sure it is correct and try again.")
        case .planKeyMissing: return String(localized: "This plan has no stored API key to share.")
        }
    }
}

/// Checks a key the user wants to share. It must be a separate key for the plan's exchange,
/// different from the key the plan trades with, and read-only where the exchange can tell.
@MainActor
struct VerifyShareKeyUseCase {
    let dependencies: AppDependencies

    func execute(planId: Int64, credentials: ExchangeCredentials) async throws -> ReadOnlyExchangeClient.KeyCheck {
        guard let plan = try dependencies.activeDatabase.planDao.getById(planId) else {
            throw SharedPlanError.invalid
        }
        guard credentials.exchange == plan.exchange else { throw ShareKeyProblem.wrongExchange }
        let isSandbox = dependencies.userPreferences.isSandboxMode()
        if let tradingKey = dependencies.credentialsStore.get(for: plan.exchange, isSandbox: isSandbox),
           tradingKey.apiKey.trimmingCharacters(in: .whitespaces) == credentials.apiKey.trimmingCharacters(in: .whitespaces) {
            throw ShareKeyProblem.sameAsPlanKey
        }
        guard let client = ReadOnlyExchangeClient.make(
            credentials: credentials, isSandbox: isSandbox, factory: dependencies.exchangeApiFactory
        ) else { throw ShareKeyProblem.liveUpdatesUnsupported }

        let check: ReadOnlyExchangeClient.KeyCheck
        do {
            check = try await client.verifyKey()
        } catch {
            throw ShareKeyProblem.checkFailed
        }
        switch check {
        case .notReadOnly:
            throw ShareKeyProblem.notReadOnly
        case .unverifiable:
            // Still prove the key works before it is handed to someone else.
            let valid = (try? await client.validateCredentials()) ?? false
            guard valid else { throw ShareKeyProblem.checkFailed }
        case .readOnly:
            break
        }
        return check
    }
}

/// Creates a share file of one of the user's own plans.
@MainActor
struct CreatePlanShareUseCase {
    let dependencies: AppDependencies
    var store: SharedPlanStore = .shared

    func execute(planId: Int64, includeAmounts: Bool, live: LiveShareAccess? = nil) async throws -> PlanShareFile {
        guard let plan = try dependencies.activeDatabase.planDao.getById(planId) else {
            throw SharedPlanError.invalid
        }
        let isSandbox = dependencies.userPreferences.isSandboxMode()
        let access: SharedPlanAccessDto?
        switch live?.key {
        case nil:
            access = nil
        case .separate(let credentials, let keyCheck):
            guard credentials.exchange == plan.exchange else { throw ShareKeyProblem.wrongExchange }
            guard keyCheck != .notReadOnly else { throw ShareKeyProblem.notReadOnly }
            access = Self.accessDto(credentials, verifiedReadOnly: keyCheck == .readOnly, fullAccess: false)
        case .planKey:
            guard let planKey = dependencies.credentialsStore.get(for: plan.exchange, isSandbox: isSandbox),
                  planKey.exchange == plan.exchange
            else { throw ShareKeyProblem.planKeyMissing }
            guard ReadOnlyExchangeClient.make(
                credentials: planKey, isSandbox: isSandbox, factory: dependencies.exchangeApiFactory
            ) != nil else { throw ShareKeyProblem.liveUpdatesUnsupported }
            access = Self.accessDto(planKey, verifiedReadOnly: false, fullAccess: true)
        }
        let transactions = try dependencies.activeDatabase.transactionDao.getByPlanId(planId, limit: SharedPlanCodec.maxTrades)
        let snapshot = SharedPlanCodec.createSnapshot(
            plan: plan,
            transactions: transactions,
            shareId: store.shareIdForOwnPlan(planId, isSandbox: isSandbox),
            isSandbox: isSandbox,
            // A shared key reveals amounts anyway, so live shares always include them.
            includeAmounts: includeAmounts || live != nil
        )
        let password = live?.password
        // Key derivation is deliberately slow, so keep it off the main thread.
        let content = try await Task.detached(priority: .userInitiated) {
            let crypto = BackupCryptoUseCase(bip39: Bip39WordList())
            return try SharedPlanCodec.encodeShare(snapshot, access: access, password: password) { data, password in
                try crypto.encrypt(plaintext: data, passphrase: password)
            }
        }.value
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let fileName = "accbot_plan_\(plan.crypto.lowercased())_\(plan.fiat.lowercased())_\(formatter.string(from: Date())).json"
        return PlanShareFile(fileName: fileName, content: content)
    }

    private static func accessDto(
        _ credentials: ExchangeCredentials, verifiedReadOnly: Bool, fullAccess: Bool
    ) -> SharedPlanAccessDto {
        func clean(_ value: String?) -> String? {
            let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return trimmed.isEmpty ? nil : trimmed
        }
        return SharedPlanAccessDto(
            exchange: credentials.exchange.rawValue,
            apiKey: clean(credentials.apiKey),
            apiSecret: clean(credentials.apiSecret),
            passphrase: clean(credentials.passphrase),
            clientId: clean(credentials.clientId),
            verifiedReadOnly: verifiedReadOnly,
            fullAccess: fullAccess ? true : nil
        )
    }
}

/// Validates a received share and stores it as a read-only plan.
@MainActor
struct ImportSharedPlanUseCase {
    let dependencies: AppDependencies
    var store: SharedPlanStore = .shared

    /// Throws `SharedPlanError` for an invalid file (`.passwordRequired` for a live-update share
    /// without password) and `ShareKeyProblem.notReadOnly` when the key can trade or withdraw.
    func execute(data: Data, password: String?) async throws -> SharedPlanStore.SaveResult {
        let decoded = try await Task.detached(priority: .userInitiated) {
            let crypto = BackupCryptoUseCase(bip39: Bip39WordList())
            return try SharedPlanCodec.decodeShare(data, password: password) { payload, password in
                try crypto.decrypt(base64Data: payload, passphrase: password)
            }
        }.value
        var plan = decoded.plan
        if let credentials = decoded.credentials {
            guard let client = ReadOnlyExchangeClient.make(
                credentials: credentials, isSandbox: plan.isSandbox, factory: dependencies.exchangeApiFactory
            ) else { throw ShareKeyProblem.liveUpdatesUnsupported }
            // Offline: keep the key, the next refresh checks it again.
            let check = try? await client.verifyKey()
            // A key the sender knowingly shared with full access is expected to allow trading.
            if check == .notReadOnly && !plan.fullAccessKey { throw ShareKeyProblem.notReadOnly }
            if check == .readOnly {
                plan.verifiedReadOnly = true
                plan.fullAccessKey = false
            }
        }
        return try store.save(plan, credentials: decoded.credentials)
    }
}

/// Fetches trades made since the snapshot of a live-update share, using only the
/// `ReadOnlyExchangeClient`. The exchange reports history per account and pair, so trades of
/// other plans or manual trades of the same pair on that account appear as well.
@MainActor
struct RefreshSharedPlanUseCase {
    enum Result {
        case updated(SharedPlan, newTrades: Int)
        /// The share has no key (plain snapshot).
        case notLive
        /// The key now allows trading or withdrawals; it was removed and updates stopped.
        case keyNotReadOnly
        /// The exchange rejected the key or could not be reached.
        case failed(SharedPlan)
    }

    let dependencies: AppDependencies
    var store: SharedPlanStore = .shared

    private static let overlap: TimeInterval = 3600
    private static let pageSize = 100
    private static let maxPages = 50
    /// Trades this close after the last snapshot trade are treated as that trade.
    private static let snapshotMargin: TimeInterval = 5 * 60

    func execute(shareId: String, now: Date = Date()) async -> Result {
        guard let plan = store.get(shareId), let credentials = store.credentials(plan.shareId),
              let client = ReadOnlyExchangeClient.make(
                credentials: credentials, isSandbox: plan.isSandbox, factory: dependencies.exchangeApiFactory
              ) else { return .notLive }
        do {
            // Expected for a key shared with full access; it is still used only for reads.
            if try await client.verifyKey() == .notReadOnly, !plan.fullAccessKey {
                try store.save(plan, credentials: nil)
                return .keyNotReadOnly
            }
            let lastKnown = max(plan.trades.last?.executedAt ?? plan.createdAt, plan.sharedAt)
            let fetched = try await fetch(client, plan: plan, since: lastKnown.addingTimeInterval(-Self.overlap))
            var updated = plan
            updated.trades = Self.merge(plan, fetched: fetched)
            updated.refreshedAt = now
            updated.refreshFailed = false
            try store.update(updated)
            return .updated(updated, newTrades: updated.trades.count - plan.trades.count)
        } catch {
            var failed = plan
            failed.refreshFailed = true
            try? store.update(failed)
            return .failed(failed)
        }
    }

    private func fetch(_ client: ReadOnlyExchangeClient, plan: SharedPlan, since: Date) async throws -> [HistoricalTrade] {
        var cursor = since
        var trades: [HistoricalTrade] = []
        for _ in 0..<Self.maxPages {
            let page = try await client.tradeHistory(crypto: plan.crypto, fiat: plan.fiat, since: cursor, limit: Self.pageSize)
            trades += page.trades
            guard page.hasMore, let next = page.trades.map(\.timestamp).max(), next > cursor else { break }
            cursor = next
        }
        return trades
    }

    /// Appends fetched trades that are newer than the snapshot and not yet known.
    ///
    /// The snapshot identifies trades by order ID, but some exchanges (Binance, Coinbase)
    /// report history by trade ID, so the two cannot be matched by ref. Everything up to the
    /// snapshot (its share time, or shortly after its last trade) is left to the snapshot,
    /// and refs only dedupe trades added by earlier refreshes. Fills reported under the same
    /// ID are grouped into one trade. Same rules as Android.
    nonisolated static func merge(_ plan: SharedPlan, fetched: [HistoricalTrade]) -> [SharedPlanTrade] {
        var known = Set(plan.trades.compactMap(\.ref))
        let lastSnapshotTrade = plan.trades.filter { $0.executedAt <= plan.sharedAt }.map(\.executedAt).max()
        let cutoff = max(plan.sharedAt, lastSnapshotTrade?.addingTimeInterval(snapshotMargin) ?? plan.sharedAt)
        let relevant = fetched.filter {
            $0.crypto == plan.crypto && $0.fiat == plan.fiat && !$0.orderId.isEmpty
                && ($0.side == "BUY" || $0.side == "SELL")
        }
        var order: [String] = []
        var groups: [String: [HistoricalTrade]] = [:]
        for trade in relevant {
            if groups[trade.orderId] == nil { order.append(trade.orderId) }
            groups[trade.orderId, default: []].append(trade)
        }
        var added: [SharedPlanTrade] = []
        for orderId in order {
            guard let fills = groups[orderId], let first = fills.first else { continue }
            let ref = SharedPlanCodec.orderRef(exchange: plan.exchangeName, orderId: orderId)
            guard !known.contains(ref) else { continue }
            let crypto = fills.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
            let fiat = fills.reduce(Decimal.zero) { $0 + $1.fiatAmount }
            guard crypto > 0, fiat > 0 else { continue }
            let executedAt = fills.map(\.timestamp).min() ?? first.timestamp
            guard executedAt > cutoff else { continue }
            known.insert(ref)
            added.append(SharedPlanTrade(
                executedAt: executedAt,
                side: first.side == "SELL" ? .sell : .buy,
                price: SharedPlanCodec.round(fiat / crypto, scale: 8),
                cryptoAmount: crypto,
                fiatAmount: fiat,
                fee: fills.reduce(Decimal.zero) { $0 + $1.fee },
                feeAsset: SharedPlanCodec.isAsset(first.feeAsset) ? first.feeAsset : nil,
                ref: ref
            ))
        }
        return (plan.trades + added).sorted { $0.executedAt < $1.executedAt }
    }
}
