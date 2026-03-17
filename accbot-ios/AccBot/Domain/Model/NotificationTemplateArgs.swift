import Foundation

/// Structured arguments for notification templates.
/// Stored as JSON in the `templateArgs` column — text is rendered at display time
/// using the current locale, so language switches re-render old notifications.
///
/// Numbers are stored as raw strings (Decimal description) and formatted at render time.
enum NotificationTemplateArgs: Codable, Equatable {
    case purchase(cryptoAmount: String, crypto: String, fiatAmount: String, fiat: String)
    case error(crypto: String, errorMessage: String)
    case lowBalance(exchangeName: String, fiat: String, remainingDays: Int)
    case withdrawalThreshold(amount: String, crypto: String, exchangeName: String)
    case belowMinimum(crypto: String)

    // MARK: - Render

    /// Render localized title + message for display using the current locale.
    func render() -> (title: String, message: String) {
        switch self {
        case .purchase(let cryptoAmount, let crypto, let fiatAmount, let fiat):
            return (
                String(localized: "DCA Purchase"),
                String(localized: "Bought \(cryptoAmount) \(crypto) for \(fiatAmount) \(fiat)")
            )
        case .error(let crypto, let errorMessage):
            return (
                String(localized: "DCA Failed"),
                "\(crypto): \(errorMessage)"
            )
        case .lowBalance(let exchangeName, let fiat, let remainingDays):
            return (
                String(localized: "Low Balance"),
                String(localized: "\(exchangeName): ~\(remainingDays) days of \(fiat) remaining")
            )
        case .withdrawalThreshold(let amount, let crypto, let exchangeName):
            return (
                String(localized: "Withdrawal Threshold"),
                String(localized: "\(amount) \(crypto) ready for withdrawal from \(exchangeName)")
            )
        case .belowMinimum(let crypto):
            return (
                String(localized: "DCA Failed"),
                String(localized: "Amount below minimum for \(crypto)")
            )
        }
    }

    // MARK: - JSON Coding

    private enum CodingKeys: String, CodingKey {
        case type
        case cryptoAmount, crypto, fiatAmount, fiat
        case errorMessage
        case exchangeName, remainingDays
        case amount
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .purchase(let cryptoAmount, let crypto, let fiatAmount, let fiat):
            try container.encode("purchase", forKey: .type)
            try container.encode(cryptoAmount, forKey: .cryptoAmount)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(fiatAmount, forKey: .fiatAmount)
            try container.encode(fiat, forKey: .fiat)
        case .error(let crypto, let errorMessage):
            try container.encode("error", forKey: .type)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(errorMessage, forKey: .errorMessage)
        case .lowBalance(let exchangeName, let fiat, let remainingDays):
            try container.encode("lowBalance", forKey: .type)
            try container.encode(exchangeName, forKey: .exchangeName)
            try container.encode(fiat, forKey: .fiat)
            try container.encode(remainingDays, forKey: .remainingDays)
        case .withdrawalThreshold(let amount, let crypto, let exchangeName):
            try container.encode("withdrawalThreshold", forKey: .type)
            try container.encode(amount, forKey: .amount)
            try container.encode(crypto, forKey: .crypto)
            try container.encode(exchangeName, forKey: .exchangeName)
        case .belowMinimum(let crypto):
            try container.encode("belowMinimum", forKey: .type)
            try container.encode(crypto, forKey: .crypto)
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
                fiat: try container.decode(String.self, forKey: .fiat)
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
                remainingDays: try container.decode(Int.self, forKey: .remainingDays)
            )
        case "withdrawalThreshold":
            self = .withdrawalThreshold(
                amount: try container.decode(String.self, forKey: .amount),
                crypto: try container.decode(String.self, forKey: .crypto),
                exchangeName: try container.decode(String.self, forKey: .exchangeName)
            )
        case "belowMinimum":
            self = .belowMinimum(
                crypto: try container.decode(String.self, forKey: .crypto)
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
