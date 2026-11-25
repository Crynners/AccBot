import Foundation

/// Factory for creating exchange API instances.
/// Configures APIs for sandbox or production mode based on user preferences.
final class ExchangeApiFactory {
    private let userPreferences: UserPreferences
    private let networkClient: NetworkClient
    private let sandboxNetworkClient: NetworkClient

    init(userPreferences: UserPreferences, networkClient: NetworkClient = NetworkClient()) {
        self.userPreferences = userPreferences
        self.networkClient = networkClient
        self.sandboxNetworkClient = networkClient.refusingRedirects()
    }

    func create(credentials: ExchangeCredentials, isSandbox: Bool? = nil) -> ExchangeApi {
        let sandboxMode = isSandbox ?? userPreferences.isSandboxMode()
        guard let baseUrl = ExchangeConfig.baseUrl(for: credentials.exchange, isSandbox: sandboxMode) else {
            return StubExchangeApi(exchange: credentials.exchange, message: "Exchange has no spot sandbox endpoint")
        }
        let client = sandboxMode ? sandboxNetworkClient : networkClient

        switch credentials.exchange {
        case .coinmate:
            guard let api = CoinmateApi(credentials: credentials, baseUrl: baseUrl, client: client) else {
                return StubExchangeApi(exchange: .coinmate)
            }
            return api
        case .binance:
            return BinanceApi(credentials: credentials, baseUrl: baseUrl, client: client)
        case .kraken:
            return KrakenApi(credentials: credentials, baseUrl: baseUrl, client: client)
        case .kucoin:
            return KuCoinApi(credentials: credentials, baseUrl: baseUrl, client: client)
        case .coinbase:
            return CoinbaseApi(credentials: credentials, baseUrl: baseUrl, client: client)
        case .bitfinex:
            return BitfinexApi(credentials: credentials, isSandbox: sandboxMode)
        case .huobi:
            return HuobiApi(credentials: credentials, isSandbox: sandboxMode)
        }
    }
}

/// Fallback API that returns errors for all operations.
/// Used when an exchange API fails to initialize (e.g. missing clientId for Coinmate).
private struct StubExchangeApi: ExchangeApi {
    let exchange: Exchange
    var message = "Exchange API not configured"

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        .rejected(message: message)
    }
    func getBalance(currency: String) async -> Decimal? { nil }
    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? { nil }
    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        throw ExchangeError.apiError(message)
    }
    func getWithdrawalFee(crypto: String) async -> Decimal? { nil }
    func validateCredentials() async throws -> Bool { false }
}
