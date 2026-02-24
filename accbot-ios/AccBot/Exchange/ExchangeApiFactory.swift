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
            return CoinmateApi(credentials: credentials, isSandbox: sandboxMode, client: networkClient)
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
