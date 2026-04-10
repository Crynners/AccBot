import Foundation

/// Structured arguments for notification templates.
/// Stored as JSON in the `templateArgs` column - text is rendered at display time
/// using the current locale, so language switches re-render old notifications.
///
/// Numbers are stored as raw strings (Decimal description) and formatted at render time.
enum NotificationTemplateArgs: Codable, Equatable {
    case purchase(cryptoAmount: String, crypto: String, fiatAmount: String, fiat: String, price: String? = nil, scheduledAt: String? = nil, executedAt: String? = nil)
    case error(crypto: String, errorMessage: String)
    case lowBalance(exchangeName: String, fiat: String, balance: String, remainingDays: Int, connectionName: String? = nil)
    case withdrawalThreshold(amount: String, crypto: String, exchangeName: String, connectionName: String? = nil)
    case belowMinimum(crypto: String, purchaseAmount: String? = nil, fiat: String? = nil, minOrderSize: String? = nil)
    case networkRetry(crypto: String, exchangeName: String, connectionName: String? = nil)
    case missedPurchases(count: Int, crypto: String, exchangeName: String, connectionName: String? = nil)

    // MARK: - Render

    /// Render localized title + message for display using the current locale.
    func render() -> (title: String, message: String) {
        switch self {
        case .purchase(let cryptoAmount, let crypto, let fiatAmount, let fiat, let price, let scheduledAt, let executedAt):
            var base = String(localized: "Bought \(cryptoAmount) \(crypto) for \(fiatAmount) \(fiat)")
            if let price {
                base += " @ \(price) \(fiat)"
            }
            if let scheduled = scheduledAt, let executed = executedAt {
                return (
                    String(localized: "DCA Purchase Completed"),
                    String(localized: "Plan \(scheduled) → executed \(executed)")
                    + " · " + base
                )
            }
            return (
                String(localized: "DCA Purchase Completed"),
                base
            )
        case .error(let crypto, let errorMessage):
            return (
                String(localized: "DCA Failed"),
                String(localized: "Failed to buy \(crypto): \(errorMessage)")
            )
        case .lowBalance(let exchangeName, let fiat, let _, let remainingDays, let connectionName):
            let days = max(1, remainingDays)
            let label = Self.connectionLabel(exchangeName, connectionName)
            return (
                String(localized: "Low balance on \(label)"),
                String(localized: "~\(days) days of \(fiat) remaining for DCA")
            )
        case .withdrawalThreshold(let amount, let crypto, let exchangeName, let connectionName):
            let label = Self.connectionLabel(exchangeName, connectionName)
            return (
                String(localized: "Withdrawal Recommended"),
                String(localized: "You have accumulated \(amount) \(crypto) on \(label) - consider withdrawing to cold wallet")
            )
        case .belowMinimum(let crypto, let purchaseAmount, let fiat, let minOrderSize):
            if let purchaseAmount, let fiat, let minOrderSize {
                return (
                    String(localized: "DCA Failed"),
                    String(localized: "Failed to buy \(crypto): Amount \(purchaseAmount) \(fiat) below exchange minimum \(minOrderSize) \(fiat)")
                )
            }
            return (
                String(localized: "DCA Failed"),
                String(localized: "Amount below minimum for \(crypto)")
            )
        case .networkRetry(let crypto, let exchangeName, let connectionName):
            let label = Self.connectionLabel(exchangeName, connectionName)
            return (
                String(localized: "Network Error"),
                String(localized: "\(crypto) purchase on \(label) failed - no internet.")
            )
        case .missedPurchases(let count, let crypto, let exchangeName, let connectionName):
            let label = Self.connectionLabel(exchangeName, connectionName)
            return (
                String(localized: "Missed Purchases"),
                String(localized: "\(count) missed \(crypto) purchases on \(label) while offline.")
            )
        }
    }

    // MARK: - JSON Coding

    /// Build "exchangeName - connectionName" label, or just exchangeName when no connection name.
    private static func connectionLabel(_ exchangeName: String, _ connectionName: String?) -> String {
        if let name = connectionName, !name.isEmpty {
            return "\(exchangeName) - \(name)"
        }
        return exchangeName
    }

    private enum CodingKeys: String, CodingKey {
        case type
        case cryptoAmount, crypto, fiatAmount, fiat
        case errorMessage
        case exchangeName, remainingDays, balance
        case amount, price
        case purchaseAmount, minOrderSize
        case scheduledAt, executedAt
        case retryAt
        case count
        case connectionName
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .purchase(let cryptoAmount, let crypto, let fiatAmount, let fiat, let price, let scheduledAt, let executedAt):
            try container.encode("purchase", forKey: .type)
            try container.encode(cryptoAmount, forKey: .cryptoAmount)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(fiatAmount, forKey: .fiatAmount)
            try container.encode(fiat, forKey: .fiat)
            try container.encodeIfPresent(price, forKey: .price)
            try container.encodeIfPresent(scheduledAt, forKey: .scheduledAt)
            try container.encodeIfPresent(executedAt, forKey: .executedAt)
        case .error(let crypto, let errorMessage):
            try container.encode("error", forKey: .type)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(errorMessage, forKey: .errorMessage)
        case .lowBalance(let exchangeName, let fiat, let balance, let remainingDays, let connectionName):
            try container.encode("lowBalance", forKey: .type)
            try container.encode(exchangeName, forKey: .exchangeName)
            try container.encode(fiat, forKey: .fiat)
            try container.encode(balance, forKey: .balance)
            try container.encode(remainingDays, forKey: .remainingDays)
            try container.encodeIfPresent(connectionName, forKey: .connectionName)
        case .withdrawalThreshold(let amount, let crypto, let exchangeName, let connectionName):
            try container.encode("withdrawalThreshold", forKey: .type)
            try container.encode(amount, forKey: .amount)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(exchangeName, forKey: .exchangeName)
            try container.encodeIfPresent(connectionName, forKey: .connectionName)
        case .belowMinimum(let crypto, let purchaseAmount, let fiat, let minOrderSize):
            try container.encode("belowMinimum", forKey: .type)
            try container.encode(crypto, forKey: .crypto)
            try container.encodeIfPresent(purchaseAmount, forKey: .purchaseAmount)
            try container.encodeIfPresent(fiat, forKey: .fiat)
            try container.encodeIfPresent(minOrderSize, forKey: .minOrderSize)
        case .networkRetry(let crypto, let exchangeName, let connectionName):
            try container.encode("networkRetry", forKey: .type)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(exchangeName, forKey: .exchangeName)
            try container.encodeIfPresent(connectionName, forKey: .connectionName)
        case .missedPurchases(let count, let crypto, let exchangeName, let connectionName):
            try container.encode("missedPurchases", forKey: .type)
            try container.encode(count, forKey: .count)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(exchangeName, forKey: .exchangeName)
            try container.encodeIfPresent(connectionName, forKey: .connectionName)
        }
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "purchase":
            self = .purchase(
                cryptoAmount: try container.decode(String.self, forKey: .cryptoAmount),
                crypto: try container.decode(String.self, forKey: .crypto),
                fiatAmount: try container.decode(String.self, forKey: .fiatAmount),
                fiat: try container.decode(String.self, forKey: .fiat),
                price: try container.decodeIfPresent(String.self, forKey: .price),
                scheduledAt: try container.decodeIfPresent(String.self, forKey: .scheduledAt),
                executedAt: try container.decodeIfPresent(String.self, forKey: .executedAt)
            )
        case "error":
            self = .error(
                crypto: try container.decode(String.self, forKey: .crypto),
                errorMessage: try container.decode(String.self, forKey: .errorMessage)
            )
        case "lowBalance":
            self = .lowBalance(
                exchangeName: try container.decode(String.self, forKey: .exchangeName),
                fiat: try container.decode(String.self, forKey: .fiat),
                balance: try container.decodeIfPresent(String.self, forKey: .balance) ?? "",
                remainingDays: try container.decode(Int.self, forKey: .remainingDays),
                connectionName: try container.decodeIfPresent(String.self, forKey: .connectionName)
            )
        case "withdrawalThreshold":
            self = .withdrawalThreshold(
                amount: try container.decode(String.self, forKey: .amount),
                crypto: try container.decode(String.self, forKey: .crypto),
                exchangeName: try container.decode(String.self, forKey: .exchangeName),
                connectionName: try container.decodeIfPresent(String.self, forKey: .connectionName)
            )
        case "belowMinimum":
            self = .belowMinimum(
                crypto: try container.decode(String.self, forKey: .crypto),
                purchaseAmount: try container.decodeIfPresent(String.self, forKey: .purchaseAmount),
                fiat: try container.decodeIfPresent(String.self, forKey: .fiat),
                minOrderSize: try container.decodeIfPresent(String.self, forKey: .minOrderSize)
            )
        case "networkRetry":
            self = .networkRetry(
                crypto: try container.decode(String.self, forKey: .crypto),
                exchangeName: try container.decode(String.self, forKey: .exchangeName),
                connectionName: try container.decodeIfPresent(String.self, forKey: .connectionName)
            )
        case "missedPurchases":
            self = .missedPurchases(
                count: try container.decode(Int.self, forKey: .count),
                crypto: try container.decode(String.self, forKey: .crypto),
                exchangeName: try container.decode(String.self, forKey: .exchangeName),
                connectionName: try container.decodeIfPresent(String.self, forKey: .connectionName)
            )
        default:
            throw DecodingError.dataCorruptedError(forKey: .type, in: container, debugDescription: "Unknown type: \(type)")
        }
    }

    // MARK: - Helpers

    func toJSON() -> String? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func fromJSON(_ json: String) -> NotificationTemplateArgs? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(NotificationTemplateArgs.self, from: data)
    }
}
