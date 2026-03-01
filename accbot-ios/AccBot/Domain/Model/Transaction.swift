import Foundation

/// Transaction status
enum TransactionStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case completed = "COMPLETED"
    case failed = "FAILED"
    case partial = "PARTIAL"

    var displayName: String {
        switch self {
        case .pending: return String(localized: "Pending")
        case .completed: return String(localized: "Completed")
        case .failed: return String(localized: "Failed")
        case .partial: return String(localized: "Partial")
        }
    }
}

/// Purchase transaction record
struct Transaction: Identifiable, Equatable, Sendable {
    let id: Int64
    let planId: Int64
    let exchange: Exchange
    let crypto: String
    let fiat: String
    let fiatAmount: Decimal
    let cryptoAmount: Decimal
    let price: Decimal
    let fee: Decimal
    let feeAsset: String
    let status: TransactionStatus
    let exchangeOrderId: String?
    let errorMessage: String?
    let warningMessage: String?
    let executedAt: Date

    init(
        id: Int64 = 0,
        planId: Int64,
        exchange: Exchange,
        crypto: String,
        fiat: String,
        fiatAmount: Decimal,
        cryptoAmount: Decimal,
        price: Decimal,
        fee: Decimal,
        feeAsset: String = "",
        status: TransactionStatus,
        exchangeOrderId: String? = nil,
        errorMessage: String? = nil,
        warningMessage: String? = nil,
        executedAt: Date = Date()
    ) {
        self.id = id
        self.planId = planId
        self.exchange = exchange
        self.crypto = crypto
        self.fiat = fiat
        self.fiatAmount = fiatAmount
        self.cryptoAmount = cryptoAmount
        self.price = price
        self.fee = fee
        self.feeAsset = feeAsset
        self.status = status
        self.exchangeOrderId = exchangeOrderId
        self.errorMessage = errorMessage
        self.warningMessage = warningMessage
        self.executedAt = executedAt
    }

    /// Display string for the trading pair
    var pair: String { "\(crypto)/\(fiat)" }
}
