import Foundation

/// Repository for minimum order sizes per exchange/fiat pair.
/// Falls back to exchange static config if no dynamic data available.
enum MinOrderSizeRepository {
    /// Get the minimum order size for a given exchange and fiat currency
    static func getMinOrderSize(exchange: Exchange, fiat: String) -> Decimal {
        exchange.minOrderSize[fiat] ?? 10
    }

    /// Check if an amount meets the minimum order size
    static func meetsMinimum(exchange: Exchange, fiat: String, amount: Decimal) -> Bool {
        amount >= getMinOrderSize(exchange: exchange, fiat: fiat)
    }
}
