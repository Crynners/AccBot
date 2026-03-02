import Foundation

/// DCA execution result
enum DcaResult {
    case success(Transaction)
    case error(message: String, retryable: Bool = true)
}
