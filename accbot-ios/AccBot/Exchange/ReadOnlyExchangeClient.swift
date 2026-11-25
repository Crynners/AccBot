import Foundation

/// The only way the app talks to an exchange with a key received in a shared plan.
///
/// It wraps an `ExchangeApi` but exposes reads only: key checks, trade history and price.
/// There is no buy, sell, cancel or withdraw here, so code holding a shared key cannot
/// trade with it. This is a guard inside AccBot; what really protects the sender is that
/// the key itself has no trading or withdrawal permission on the exchange.
struct ReadOnlyExchangeClient {
    enum KeyCheck: Equatable {
        /// The exchange confirmed the key cannot trade or withdraw.
        case readOnly
        /// The exchange reports trading or withdrawal permission.
        case notReadOnly
        /// The exchange cannot report key permissions (Coinmate, Kraken).
        case unverifiable
    }

    private let api: ExchangeApi

    var exchange: Exchange { api.exchange }

    /// Nil when the exchange has no trade history API, so live updates are not possible.
    static func make(
        credentials: ExchangeCredentials,
        isSandbox: Bool,
        factory: ExchangeApiFactory
    ) -> ReadOnlyExchangeClient? {
        guard credentials.exchange.supportsApiImport else { return nil }
        return ReadOnlyExchangeClient(api: factory.create(credentials: credentials, isSandbox: isSandbox))
    }

    private init(api: ExchangeApi) {
        self.api = api
    }

    func verifyKey() async throws -> KeyCheck {
        guard let permissions = try await api.getKeyPermissions() else { return .unverifiable }
        return permissions.isReadOnly ? .readOnly : .notReadOnly
    }

    func validateCredentials() async throws -> Bool {
        try await api.validateCredentials()
    }

    func tradeHistory(crypto: String, fiat: String, since: Date?, limit: Int) async throws -> TradeHistoryPage {
        try await api.getTradeHistory(crypto: crypto, fiat: fiat, since: since, limit: limit)
    }

    func currentPrice(crypto: String, fiat: String) async -> Decimal? {
        await api.getCurrentPrice(crypto: crypto, fiat: fiat)
    }
}
