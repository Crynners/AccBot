import Foundation

/// Portfolio statistics - calculated locally
struct PortfolioStats {
    let totalInvestedFiat: [String: Decimal]
    let totalCryptoHoldings: [String: Decimal]
    let totalTransactions: Int
    let averageBuyPrice: [String: Decimal]
    let lastUpdated: Date
}
