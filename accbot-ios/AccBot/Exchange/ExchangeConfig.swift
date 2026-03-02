import Foundation

/// Base URLs and sandbox URLs for all exchanges
enum ExchangeConfig {
    // MARK: - Coinmate
    static let coinmateBaseUrl = "https://coinmate.io/api"

    // MARK: - Binance
    static let binanceBaseUrl = "https://api.binance.com"
    static let binanceSandboxUrl = "https://testnet.binance.vision"

    // MARK: - Kraken
    static let krakenBaseUrl = "https://api.kraken.com"

    // MARK: - KuCoin
    static let kucoinBaseUrl = "https://api.kucoin.com"
    static let kucoinSandboxUrl = "https://openapi-sandbox.kucoin.com"

    // MARK: - Coinbase
    static let coinbaseBaseUrl = "https://api.coinbase.com"
    static let coinbaseSandboxUrl = "https://api-public.sandbox.exchange.coinbase.com"

    // MARK: - Bitfinex
    static let bitfinexBaseUrl = "https://api.bitfinex.com"

    // MARK: - Huobi
    static let huobiBaseUrl = "https://api.huobi.pro"

    static func baseUrl(for exchange: Exchange, isSandbox: Bool) -> String {
        switch exchange {
        case .coinmate: return coinmateBaseUrl
        case .binance: return isSandbox ? binanceSandboxUrl : binanceBaseUrl
        case .kraken: return krakenBaseUrl
        case .kucoin: return isSandbox ? kucoinSandboxUrl : kucoinBaseUrl
        case .coinbase: return isSandbox ? coinbaseSandboxUrl : coinbaseBaseUrl
        case .bitfinex: return bitfinexBaseUrl
        case .huobi: return huobiBaseUrl
        }
    }
}
