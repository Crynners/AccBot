import Foundation

/// Factory for creating exchange API instances.
/// Configures APIs for sandbox or production mode based on user preferences.
final class ExchangeApiFactory {
    private let userPreferences: UserPreferences
    private let networkClient: NetworkClient

    init(userPreferences: UserPreferences, networkClient: NetworkClient = NetworkClient()) {
        self.userPreferences = userPreferences
        self.networkClient = networkClient
    }

    func create(credentials: ExchangeCredentials, isSandbox: Bool? = nil) -> ExchangeApi {
        let sandboxMode = isSandbox ?? userPreferences.isSandboxMode()

        switch credentials.exchange {
        case .coinmate:
            guard let api = CoinmateApi(credentials: credentials, isSandbox: sandboxMode, client: networkClient) else {
                return StubExchangeApi(exchange: .coinmate)
            }
            return api
        case .binance:
            return BinanceApi(credentials: credentials, isSandbox: sandboxMode, client: networkClient)
        case .kraken:
            return KrakenApi(credentials: credentials, isSandbox: sandboxMode, client: networkClient)
        case .kucoin:
            return KuCoinApi(credentials: credentials, isSandbox: sandboxMode, client: networkClient)
        case .coinbase:
            return CoinbaseApi(credentials: credentials, isSandbox: sandboxMode, client: networkClient)
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

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        .error(message: "Exchange API not configured", retryable: false)
    }
    func getBalance(currency: String) async -> Decimal? { nil }
    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? { nil }
    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        throw ExchangeError.apiError("Exchange API not configured")
    }
    func getWithdrawalFee(crypto: String) async -> Decimal? { nil }
    func validateCredentials() async throws -> Bool { false }
}
