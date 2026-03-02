import Foundation

/// Withdrawal threshold configuration
struct WithdrawalThreshold: Equatable {
    let crypto: String
    let exchange: Exchange
    let thresholdAmount: Decimal
}
