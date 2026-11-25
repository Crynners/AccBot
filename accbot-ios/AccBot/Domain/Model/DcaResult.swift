import Foundation

/// DCA execution result
enum DcaResult {
    case success(Transaction)
    /// The order was definitively rejected before acceptance.
    case rejected(message: String)
    case error(message: String, retryable: Bool = true)
}
