import Foundation

/// Common interface for all exchange API implementations.
/// Allows AccBot to work with multiple exchanges through unified interface.
protocol ExchangeApi {
    var exchange: Exchange { get }

    /// Execute a market buy order
    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult

    /// Get current balance for a currency
    func getBalance(currency: String) async -> Decimal?

    /// Get current market price
    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal?

    /// Withdraw crypto to external wallet
    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String

    /// Get withdrawal fee
    func getWithdrawalFee(crypto: String) async -> Decimal?

    /// Validate credentials (test API connection)
    func validateCredentials() async throws -> Bool

    /// Query the status and fill details of a previously placed order
    func getOrderStatus(orderId: String) async -> Transaction?

    /// Get trade history for a currency pair
    func getTradeHistory(
        crypto: String,
        fiat: String,
        since: Date?,
        limit: Int
    ) async throws -> TradeHistoryPage
}

/// Default implementations for optional methods
extension ExchangeApi {
    func getOrderStatus(orderId: String) async -> Transaction? {
        nil
    }

    func getTradeHistory(
        crypto: String,
        fiat: String,
        since: Date? = nil,
        limit: Int = 100
    ) async throws -> TradeHistoryPage {
        throw ExchangeError.unsupportedOperation("\(exchange.displayName) does not support API trade history import")
    }
}

/// Shared helpers used by all exchange API implementations.
/// Extracted from per-class copies of parseJson/formatDecimal/roundDecimal.
extension ExchangeApi {
    func parseJson(_ data: Data) throws -> [String: Any] {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NetworkError.decodingError("Invalid JSON")
        }
        return json
    }

    func formatDecimal(_ value: Decimal, scale: Int) -> String {
        let handler = NSDecimalNumberHandler(
            roundingMode: .down, scale: Int16(scale),
            raiseOnExactness: false, raiseOnOverflow: false,
            raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: value).rounding(accordingToBehavior: handler).stringValue
    }

    func roundDecimal(_ value: Decimal, scale: Int) -> Decimal {
        let handler = NSDecimalNumberHandler(
            roundingMode: .plain, scale: Int16(scale),
            raiseOnExactness: false, raiseOnOverflow: false,
            raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: value).rounding(accordingToBehavior: handler).decimalValue
    }
}

enum ExchangeError: LocalizedError {
    case unsupportedOperation(String)
    case invalidCredentials
    case apiError(String)

    var errorDescription: String? {
        switch self {
        case .unsupportedOperation(let msg): return msg
        case .invalidCredentials: return "Invalid API credentials"
        case .apiError(let msg): return msg
        }
    }
}
