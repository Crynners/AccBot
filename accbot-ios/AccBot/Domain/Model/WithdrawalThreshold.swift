import Foundation

/// Withdrawal threshold configuration - per crypto per connection
struct WithdrawalThreshold: Equatable {
    let crypto: String
    let connectionId: Int64
    let thresholdAmount: Decimal
}
