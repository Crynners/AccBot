import CryptoKit
import Foundation

// MARK: - Wire format

/// Wire format of a read-only plan share (`accbot-plan-share`), identical to Android's
/// `SharedPlanCodec.kt`. Amounts are plain decimal strings and timestamps are epoch milliseconds.
///
/// Two kinds of share exist:
/// - a plain snapshot: unencrypted, never contains API credentials, exchange order IDs,
///   connection names or withdrawal addresses, so it cannot run the plan;
/// - a live-update share: `{format, version, encrypted: true, data}`, where `data` is the
///   snapshot plus `access` (a separate read-only exchange key), encrypted with a password
///   in the backup format (PBKDF2-SHA256 600k, AES-256-GCM).
///
/// `local` is only written to the recipient's own stored copy and is ignored in received files.
struct SharedPlanSnapshotDto: Codable, Equatable {
    var format: String?
    var version: Int?
    var shareId: String?
    var sharedAt: Int64?
    var environment: String?
    var includesAmounts: Bool?
    var plan: SharedPlanInfoDto?
    var summary: SharedPlanSummaryDto?
    var transactions: [SharedPlanTradeDto]?
    var encrypted: Bool?
    var data: String?
    var access: SharedPlanAccessDto?
    var local: SharedPlanLocalDto?
}

struct SharedPlanInfoDto: Codable, Equatable {
    var name: String?
    var exchange: String?
    var crypto: String?
    var fiat: String?
    /// Base purchase amount per execution. Nil when the share hides amounts.
    var amount: String?
    var frequency: String?
    var cronExpression: String?
    var strategy: String?
    var isEnabled: Bool?
    var createdAt: Int64?
}

struct SharedPlanSummaryDto: Codable, Equatable {
    var avgBuyPrice: String?
}

struct SharedPlanTradeDto: Codable, Equatable {
    var executedAt: Int64?
    var side: String?
    var price: String?
    var cryptoAmount: String?
    var fiatAmount: String?
    var fee: String?
    var feeAsset: String?
    /// Hash of the exchange order (or history) ID, used only to avoid duplicates on refresh.
    var ref: String?
}

/// Exchange key of a live-update share. Only ever inside the encrypted payload.
struct SharedPlanAccessDto: Codable, Equatable {
    var exchange: String?
    var apiKey: String?
    var apiSecret: String?
    var passphrase: String?
    var clientId: String?
    var verifiedReadOnly: Bool?
    /// True when the sender knowingly shared the key the plan trades with instead of a separate
    /// read-only key. The recipient then accepts a key that can trade, but still only reads with it.
    var fullAccess: Bool? = nil
}

/// Recipient-side state of a stored share.
struct SharedPlanLocalDto: Codable, Equatable {
    var liveUpdates: Bool?
    var verifiedReadOnly: Bool?
    var fullAccessKey: Bool? = nil
    var refreshedAt: Int64?
    var refreshFailed: Bool?
}

// MARK: - Domain

enum SharedPlanSide: String, Sendable {
    case buy = "BUY"
    case sell = "SELL"
}

struct SharedPlanTrade: Equatable, Sendable {
    let executedAt: Date
    let side: SharedPlanSide
    let price: Decimal
    let cryptoAmount: Decimal?
    let fiatAmount: Decimal?
    let fee: Decimal?
    let feeAsset: String?
    var ref: String? = nil
}

/// Validated, read-only plan received from someone else.
struct SharedPlan: Identifiable, Equatable, Sendable {
    let shareId: String
    let sharedAt: Date
    let isSandbox: Bool
    let includesAmounts: Bool
    let name: String
    /// Nil when the sender used an exchange this app version does not know.
    let exchange: Exchange?
    let exchangeName: String
    let crypto: String
    let fiat: String
    let amount: Decimal?
    let frequency: DcaFrequency?
    let cronExpression: String?
    let strategy: DcaStrategy
    let isEnabled: Bool
    let createdAt: Date
    /// Average buy price stated by the sender (used when amounts are hidden).
    let statedAvgBuyPrice: Decimal?
    /// Oldest first.
    var trades: [SharedPlanTrade]
    /// A read-only key is stored for this share, so new trades can be fetched.
    var liveUpdates: Bool = false
    /// The exchange confirmed the key cannot trade or withdraw.
    var verifiedReadOnly: Bool = false
    /// The sender shared the plan's own trading key (see `SharedPlanAccessDto.fullAccess`).
    var fullAccessKey: Bool = false
    var refreshedAt: Date? = nil
    var refreshFailed: Bool = false

    var id: String { shareId }
    var pair: String { "\(crypto)/\(fiat)" }
    var displayName: String { name.isEmpty ? pair : name }
    var exchangeDisplayName: String { exchange?.displayName ?? exchangeName }
    var summary: SharedPlanSummary { SharedPlanSummary(plan: self) }
}

/// Figures shown on the read-only detail. Amount-based values are nil when hidden.
struct SharedPlanSummary: Equatable {
    let buyCount: Int
    let sellCount: Int
    let firstTradeAt: Date?
    let lastTradeAt: Date?
    let avgBuyPrice: Decimal?
    let totalInvested: Decimal?
    let totalSoldFiat: Decimal?
    let heldCrypto: Decimal?

    init(plan: SharedPlan) {
        let buys = plan.trades.filter { $0.side == .buy }
        let sells = plan.trades.filter { $0.side == .sell }
        buyCount = buys.count
        sellCount = sells.count
        firstTradeAt = plan.trades.first?.executedAt
        lastTradeAt = plan.trades.last?.executedAt
        if plan.includesAmounts {
            let bought = buys.reduce(Decimal.zero) { $0 + ($1.cryptoAmount ?? 0) }
            let invested = buys.reduce(Decimal.zero) { $0 + ($1.fiatAmount ?? 0) }
            let sold = sells.reduce(Decimal.zero) { $0 + ($1.cryptoAmount ?? 0) }
            totalInvested = invested
            totalSoldFiat = sells.reduce(Decimal.zero) { $0 + ($1.fiatAmount ?? 0) }
            heldCrypto = max(bought - sold, 0)
            avgBuyPrice = bought > 0 ? SharedPlanCodec.round(invested / bought, scale: 8) : plan.statedAvgBuyPrice
        } else {
            totalInvested = nil
            totalSoldFiat = nil
            heldCrypto = nil
            avgBuyPrice = plan.statedAvgBuyPrice
        }
    }

    /// Performance of `currentPrice` against the average buy price, in percent.
    func performancePercent(currentPrice: Decimal?) -> Decimal? {
        guard let avg = avgBuyPrice, avg > 0, let price = currentPrice, price > 0 else { return nil }
        return SharedPlanCodec.round((price - avg) * 100 / avg, scale: 2)
    }

    func currentValue(currentPrice: Decimal?) -> Decimal? {
        guard let held = heldCrypto, let price = currentPrice, price > 0 else { return nil }
        return SharedPlanCodec.round(held * price, scale: 2)
    }
}

/// Locale-free decode failure; the UI maps each case to a message.
enum SharedPlanError: Error, Equatable {
    case notAPlanShare
    case unsupportedVersion
    case tooLarge
    case invalid
    case passwordRequired
    case wrongPassword

    var message: String {
        switch self {
        case .notAPlanShare: return String(localized: "This file is not an AccBot shared plan.")
        case .unsupportedVersion: return String(localized: "This shared plan needs a newer version of AccBot.")
        case .tooLarge: return String(localized: "The file is too large.")
        case .invalid: return String(localized: "The shared plan is damaged or incomplete.")
        case .passwordRequired: return String(localized: "This plan includes live updates. Enter the password you got from the sender.")
        case .wrongPassword: return String(localized: "Wrong password.")
        }
    }
}

/// A received share: the plan and, for a live-update share, its read-only key.
struct DecodedShare {
    let plan: SharedPlan
    let credentials: ExchangeCredentials?
}

// MARK: - Codec

enum SharedPlanCodec {
    static let format = "accbot-plan-share"
    static let currentVersion = 1
    static let maxFileBytes = 5 * 1024 * 1024
    static let maxTrades = 50_000
    private static let maxNameLength = 100
    private static let posix = Locale(identifier: "en_US_POSIX")

    static func isValidShareId(_ value: String) -> Bool {
        value.range(
            of: "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            options: .regularExpression
        ) != nil
    }

    static func isAsset(_ value: String) -> Bool {
        value.range(of: "^[A-Z0-9]{2,10}$", options: .regularExpression) != nil
    }

    private static func isRef(_ value: String) -> Bool {
        value.range(of: "^[0-9a-f]{32}$", options: .regularExpression) != nil
    }

    /// Opaque reference of an exchange order: the first 16 bytes of
    /// SHA-256("accbot-order:<EXCHANGE>:<orderId>") in hex. Same as Android.
    static func orderRef(exchange: String, orderId: String) -> String {
        let digest = SHA256.hash(data: Data("accbot-order:\(exchange):\(orderId)".utf8))
        return digest.prefix(16).map { String(format: "%02x", $0) }.joined()
    }

    /// Build a snapshot of `plan`. Only confirmed fills are included and, unless
    /// `includeAmounts` is set, all absolute amounts are left out.
    /// iOS records purchases only, so every trade is a BUY.
    static func createSnapshot(
        plan: DcaPlan,
        transactions: [Transaction],
        shareId: String,
        isSandbox: Bool,
        includeAmounts: Bool,
        now: Date = Date()
    ) -> SharedPlanSnapshotDto {
        precondition(isValidShareId(shareId))
        let fills = transactions
            .filter {
                $0.planId == plan.id && ($0.status == .completed || $0.status == .partial)
                    && $0.cryptoAmount > 0 && $0.fiatAmount > 0 && $0.price > 0
            }
            .sorted { $0.executedAt < $1.executedAt }
        let bought = fills.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
        let invested = fills.reduce(Decimal.zero) { $0 + $1.fiatAmount }
        let avg = bought > 0 ? round(invested / bought, scale: 8) : nil

        return SharedPlanSnapshotDto(
            format: format,
            version: currentVersion,
            shareId: shareId,
            sharedAt: millis(now),
            environment: isSandbox ? "sandbox" : "prod",
            includesAmounts: includeAmounts,
            plan: SharedPlanInfoDto(
                name: String(plan.name.prefix(maxNameLength)),
                exchange: plan.exchange.rawValue,
                crypto: plan.crypto,
                fiat: plan.fiat,
                amount: includeAmounts ? string(plan.amount) : nil,
                frequency: plan.frequency.rawValue,
                cronExpression: plan.cronExpression,
                strategy: plan.strategy.dbString,
                isEnabled: plan.isEnabled,
                createdAt: millis(plan.createdAt)
            ),
            summary: SharedPlanSummaryDto(avgBuyPrice: avg.map(string)),
            transactions: fills.map { tx in
                SharedPlanTradeDto(
                    executedAt: millis(tx.executedAt),
                    side: SharedPlanSide.buy.rawValue,
                    price: string(tx.price),
                    cryptoAmount: includeAmounts ? string(tx.cryptoAmount) : nil,
                    fiatAmount: includeAmounts ? string(tx.fiatAmount) : nil,
                    fee: includeAmounts ? string(tx.fee) : nil,
                    feeAsset: includeAmounts && !tx.feeAsset.isEmpty ? tx.feeAsset : nil,
                    ref: tx.exchangeOrderId.flatMap { id in
                        id.trimmingCharacters(in: .whitespaces).isEmpty
                            ? nil : orderRef(exchange: plan.exchange.rawValue, orderId: id)
                    }
                )
            }
        )
    }

    static func encode(_ snapshot: SharedPlanSnapshotDto) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(snapshot)
    }

    /// Encode a validated plan for local storage, including `local` state. Never contains a key.
    static func encode(_ plan: SharedPlan) throws -> Data {
        try encode(dto(from: plan))
    }

    /// Encode a file to send. Without `access` this is a plain snapshot. With `access` the
    /// snapshot and key are encrypted with `password`; amounts must be included because
    /// the key exposes them anyway.
    static func encodeShare(
        _ snapshot: SharedPlanSnapshotDto,
        access: SharedPlanAccessDto?,
        password: String?,
        encrypt: (Data, String) throws -> String
    ) throws -> Data {
        var clean = snapshot
        clean.encrypted = nil
        clean.data = nil
        clean.local = nil
        clean.access = nil
        guard let access else { return try encode(clean) }
        guard let password, !password.isEmpty else { throw SharedPlanError.passwordRequired }
        guard clean.includesAmounts == true else { throw SharedPlanError.invalid }
        clean.access = access
        let inner = try encode(clean)
        return try encode(SharedPlanSnapshotDto(
            format: format,
            version: currentVersion,
            encrypted: true,
            data: try encrypt(inner, password)
        ))
    }

    /// Decode a received file. A live-update share needs `password`; without it this
    /// throws `SharedPlanError.passwordRequired`.
    static func decodeShare(
        _ data: Data,
        password: String?,
        decrypt: (String, String) throws -> Data
    ) throws -> DecodedShare {
        let outer = try parseEnvelope(data)
        guard outer.encrypted == true else {
            if outer.access != nil || outer.data != nil { throw SharedPlanError.invalid }
            return DecodedShare(plan: try convert(outer, trustLocal: false, live: nil), credentials: nil)
        }
        guard let payload = outer.data else { throw SharedPlanError.invalid }
        guard let password, !password.isEmpty else { throw SharedPlanError.passwordRequired }
        let innerData: Data
        do {
            innerData = try decrypt(payload, password)
        } catch {
            throw SharedPlanError.wrongPassword
        }
        let inner = try parseEnvelope(innerData)
        guard inner.encrypted != true, inner.includesAmounts == true, let access = inner.access,
              let exchange = access.exchange.flatMap(Exchange.init(rawValue:)),
              let apiKey = access.apiKey?.trimmingCharacters(in: .whitespacesAndNewlines),
              !apiKey.isEmpty, apiKey.count <= 512,
              let apiSecret = access.apiSecret?.trimmingCharacters(in: .whitespacesAndNewlines),
              !apiSecret.isEmpty, apiSecret.count <= 2048
        else { throw SharedPlanError.invalid }
        let credentials = ExchangeCredentials(
            exchange: exchange,
            apiKey: apiKey,
            apiSecret: apiSecret,
            passphrase: access.passphrase.flatMap { $0.isEmpty ? nil : $0 },
            clientId: access.clientId.flatMap { $0.isEmpty ? nil : $0 }
        )
        let verifiedReadOnly = access.verifiedReadOnly == true
        var plan = try convert(inner, trustLocal: false, live: verifiedReadOnly)
        plan.fullAccessKey = access.fullAccess == true && !verifiedReadOnly
        guard plan.exchange == exchange else { throw SharedPlanError.invalid }
        return DecodedShare(plan: plan, credentials: credentials)
    }

    /// Decode a stored share (written by `encode(_: SharedPlan)`).
    static func decode(_ data: Data) throws -> SharedPlan {
        let dto = try parseEnvelope(data)
        guard dto.encrypted != true, dto.access == nil else { throw SharedPlanError.invalid }
        return try convert(dto, trustLocal: true, live: nil)
    }

    private static func parseEnvelope(_ data: Data) throws -> SharedPlanSnapshotDto {
        guard data.count <= maxFileBytes else { throw SharedPlanError.tooLarge }
        guard let dto = try? JSONDecoder().decode(SharedPlanSnapshotDto.self, from: data),
              dto.format == format else { throw SharedPlanError.notAPlanShare }
        guard let version = dto.version else { throw SharedPlanError.invalid }
        guard (1...currentVersion).contains(version) else { throw SharedPlanError.unsupportedVersion }
        return dto
    }

    /// - Parameter live: nil for a snapshot without key, otherwise whether the key was verified read-only.
    private static func convert(_ dto: SharedPlanSnapshotDto, trustLocal: Bool, live: Bool?) throws -> SharedPlan {
        var result = try plan(from: dto)
        if let live {
            result.liveUpdates = true
            result.verifiedReadOnly = live
        } else if trustLocal, let local = dto.local {
            result.liveUpdates = local.liveUpdates == true
            result.verifiedReadOnly = local.verifiedReadOnly == true
            result.fullAccessKey = local.fullAccessKey == true
            result.refreshedAt = local.refreshedAt.map { date($0) }
            result.refreshFailed = local.refreshFailed == true
        }
        return result
    }

    private static func plan(from dto: SharedPlanSnapshotDto) throws -> SharedPlan {
        guard let shareId = dto.shareId, isValidShareId(shareId),
              let includesAmounts = dto.includesAmounts,
              let sharedAt = dto.sharedAt,
              let info = dto.plan,
              let crypto = info.crypto, isAsset(crypto),
              let fiat = info.fiat, isAsset(fiat),
              let exchangeName = info.exchange?.trimmingCharacters(in: .whitespaces),
              !exchangeName.isEmpty, exchangeName.count <= 40
        else { throw SharedPlanError.invalid }

        let rawTrades = dto.transactions ?? []
        guard rawTrades.count <= maxTrades else { throw SharedPlanError.tooLarge }
        let trades: [SharedPlanTrade] = try rawTrades.map { trade in
            guard let executedAt = trade.executedAt,
                  let side = trade.side.flatMap(SharedPlanSide.init(rawValue:)),
                  let price = positive(trade.price) else { throw SharedPlanError.invalid }
            var crypto: Decimal?
            var fiatAmount: Decimal?
            if includesAmounts {
                guard let c = positive(trade.cryptoAmount), let f = positive(trade.fiatAmount) else {
                    throw SharedPlanError.invalid
                }
                crypto = c
                fiatAmount = f
            }
            return SharedPlanTrade(
                executedAt: date(executedAt),
                side: side,
                price: price,
                cryptoAmount: crypto,
                fiatAmount: fiatAmount,
                fee: includesAmounts ? nonNegative(trade.fee) : nil,
                feeAsset: includesAmounts ? trade.feeAsset.flatMap { isAsset($0) ? $0 : nil } : nil,
                ref: trade.ref.flatMap { isRef($0) ? $0 : nil }
            )
        }.sorted { $0.executedAt < $1.executedAt }

        let cron = info.cronExpression?.trimmingCharacters(in: .whitespaces)
        return SharedPlan(
            shareId: shareId.lowercased(),
            sharedAt: date(sharedAt),
            isSandbox: dto.environment == "sandbox",
            includesAmounts: includesAmounts,
            name: String((info.name ?? "").trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxNameLength)),
            exchange: Exchange(rawValue: exchangeName),
            exchangeName: exchangeName,
            crypto: crypto,
            fiat: fiat,
            amount: includesAmounts ? positive(info.amount) : nil,
            frequency: info.frequency.flatMap(DcaFrequency.init(rawValue:)),
            cronExpression: (cron?.isEmpty == false && (cron?.count ?? 0) <= 100) ? cron : nil,
            strategy: DcaStrategy.fromDbString(info.strategy ?? ""),
            isEnabled: info.isEnabled ?? false,
            createdAt: date(info.createdAt ?? 0),
            statedAvgBuyPrice: positive(dto.summary?.avgBuyPrice),
            trades: trades
        )
    }

    private static func dto(from plan: SharedPlan) -> SharedPlanSnapshotDto {
        SharedPlanSnapshotDto(
            format: format,
            version: currentVersion,
            shareId: plan.shareId,
            sharedAt: millis(plan.sharedAt),
            environment: plan.isSandbox ? "sandbox" : "prod",
            includesAmounts: plan.includesAmounts,
            plan: SharedPlanInfoDto(
                name: plan.name,
                exchange: plan.exchangeName,
                crypto: plan.crypto,
                fiat: plan.fiat,
                amount: plan.amount.map(string),
                frequency: plan.frequency?.rawValue,
                cronExpression: plan.cronExpression,
                strategy: plan.strategy.dbString,
                isEnabled: plan.isEnabled,
                createdAt: millis(plan.createdAt)
            ),
            summary: SharedPlanSummaryDto(avgBuyPrice: plan.statedAvgBuyPrice.map(string)),
            transactions: plan.trades.map {
                SharedPlanTradeDto(
                    executedAt: millis($0.executedAt),
                    side: $0.side.rawValue,
                    price: string($0.price),
                    cryptoAmount: $0.cryptoAmount.map(string),
                    fiatAmount: $0.fiatAmount.map(string),
                    fee: $0.fee.map(string),
                    feeAsset: $0.feeAsset,
                    ref: $0.ref
                )
            },
            local: SharedPlanLocalDto(
                liveUpdates: plan.liveUpdates,
                verifiedReadOnly: plan.verifiedReadOnly,
                fullAccessKey: plan.fullAccessKey ? true : nil,
                refreshedAt: plan.refreshedAt.map { millis($0) },
                refreshFailed: plan.refreshFailed
            )
        )
    }

    // MARK: Helpers

    static func round(_ value: Decimal, scale: Int) -> Decimal {
        var input = value
        var result = Decimal()
        NSDecimalRound(&result, &input, scale, .plain)
        return result
    }

    private static func string(_ value: Decimal) -> String {
        NSDecimalNumber(decimal: value).description(withLocale: posix)
    }

    private static func positive(_ value: String?) -> Decimal? {
        guard let value, let number = Decimal(string: value, locale: posix), number > 0 else { return nil }
        return number
    }

    private static func nonNegative(_ value: String?) -> Decimal? {
        guard let value, let number = Decimal(string: value, locale: posix), number >= 0 else { return nil }
        return number
    }

    static func millis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    static func date(_ millis: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    }
}
