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

    // MARK: - Coinbase
    static let coinbaseBaseUrl = "https://api.coinbase.com"

    // MARK: - Bitfinex
    static let bitfinexBaseUrl = "https://api.bitfinex.com"

    // MARK: - Huobi
    static let huobiBaseUrl = "https://api.huobi.pro"

    static func baseUrl(for exchange: Exchange, isSandbox: Bool) -> String? {
        if isSandbox {
            switch exchange {
            case .binance: return binanceSandboxUrl
            case .coinmate, .kraken, .kucoin, .coinbase, .bitfinex, .huobi: return nil
            }
        }
        switch exchange {
        case .coinmate: return coinmateBaseUrl
        case .binance: return binanceBaseUrl
        case .kraken: return krakenBaseUrl
        case .kucoin: return kucoinBaseUrl
        case .coinbase: return coinbaseBaseUrl
        case .bitfinex: return bitfinexBaseUrl
        case .huobi: return huobiBaseUrl
        }
    }
}
