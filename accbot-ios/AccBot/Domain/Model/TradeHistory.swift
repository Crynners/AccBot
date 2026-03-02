import Foundation

/// A single historical trade from an exchange API
struct HistoricalTrade: Equatable {
    let orderId: String
    let timestamp: Date
    let crypto: String
    let fiat: String
    let cryptoAmount: Decimal
    let fiatAmount: Decimal
    let price: Decimal
    let fee: Decimal
    let feeAsset: String
    let side: String  // "BUY" or "SELL"
}

/// A page of trade history results
struct TradeHistoryPage {
    let trades: [HistoricalTrade]
    let hasMore: Bool
}
