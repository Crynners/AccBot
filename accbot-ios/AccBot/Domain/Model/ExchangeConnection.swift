import Foundation

/// Represents one set of API credentials for one exchange.
/// Multiple connections can target the same exchange (e.g. two Coinmate sub-accounts
/// named "Hlavni" and "Sporeni").
struct ExchangeConnection: Identifiable, Equatable, Sendable {
    let id: Int64
    let exchange: Exchange
    let name: String       // Empty = "Vychozi" (default envelope)
    let createdAt: Date
    let displayOrder: Int

    init(
        id: Int64 = 0,
        exchange: Exchange,
        name: String = "",
        createdAt: Date = Date(),
        displayOrder: Int = 0
    ) {
        self.id = id
        self.exchange = exchange
        self.name = name
        self.createdAt = createdAt
        self.displayOrder = displayOrder
    }

    /// Display label: "Coinmate" or "Coinmate - Sporeni"
    var displayLabel: String {
        if name.isEmpty {
            return exchange.displayName
        }
        return "\(exchange.displayName) - \(name)"
    }
}
