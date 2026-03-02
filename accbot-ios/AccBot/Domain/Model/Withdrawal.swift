import Foundation

/// Withdrawal status
enum WithdrawalStatus: String, Codable {
    case pending = "PENDING"
    case processing = "PROCESSING"
    case completed = "COMPLETED"
    case failed = "FAILED"
}

/// Withdrawal record
struct Withdrawal: Identifiable, Equatable {
    let id: Int64
    let planId: Int64
    let exchange: Exchange
    let crypto: String
    let amount: Decimal
    let address: String
    let txHash: String?
    let fee: Decimal
    let status: WithdrawalStatus
    let errorMessage: String?
    let createdAt: Date

    init(
        id: Int64 = 0,
        planId: Int64,
        exchange: Exchange,
        crypto: String,
        amount: Decimal,
        address: String,
        txHash: String? = nil,
        fee: Decimal,
        status: WithdrawalStatus,
        errorMessage: String? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.planId = planId
        self.exchange = exchange
        self.crypto = crypto
        self.amount = amount
        self.address = address
        self.txHash = txHash
        self.fee = fee
        self.status = status
        self.errorMessage = errorMessage
        self.createdAt = createdAt
    }
}
