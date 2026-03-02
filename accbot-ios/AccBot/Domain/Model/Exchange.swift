import Foundation

/// Sandbox support levels for exchanges
enum SandboxSupport: String, Codable {
    case full           // Full sandbox/testnet available (Binance, KuCoin, Coinbase)
    case paperTrading   // Paper trading mode (Bitfinex)
    case futuresOnly    // Only futures demo available (Kraken)
    case none           // No sandbox available (Coinmate, Huobi)
}

/// Supported cryptocurrency exchanges
enum Exchange: String, Codable, CaseIterable, Hashable, Identifiable, Sendable {
    case coinmate = "COINMATE"
    case binance = "BINANCE"
    case kraken = "KRAKEN"
    case kucoin = "KUCOIN"
    case bitfinex = "BITFINEX"
    case huobi = "HUOBI"
    case coinbase = "COINBASE"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .coinmate: return "Coinmate"
        case .binance: return "Binance"
        case .kraken: return "Kraken"
        case .kucoin: return "KuCoin"
        case .bitfinex: return "Bitfinex"
        case .huobi: return "Huobi"
        case .coinbase: return "Coinbase"
        }
    }

    var logoName: String {
        switch self {
        case .coinmate: return "ic_exchange_coinmate"
        case .binance: return "ic_exchange_binance"
        case .kraken: return "ic_exchange_kraken"
        case .kucoin: return "ic_exchange_kucoin"
        case .bitfinex: return "ic_exchange_bitfinex"
        case .huobi: return "ic_exchange_huobi"
        case .coinbase: return "ic_exchange_coinbase"
        }
    }

    var supportedFiats: [String] {
        switch self {
        case .coinmate: return ["EUR", "CZK"]
        case .binance: return ["EUR", "USDT"]
        case .kraken: return ["EUR", "USD", "GBP"]
        case .kucoin: return ["USDT"]
        case .bitfinex: return ["USD", "EUR"]
        case .huobi: return ["USDT"]
        case .coinbase: return ["EUR", "USD", "GBP"]
        }
    }

    var supportedCryptos: [String] {
        switch self {
        case .coinmate: return ["BTC", "ETH", "LTC"]
        case .binance: return ["BTC", "ETH", "SOL", "ADA", "DOT"]
        case .kraken: return ["BTC", "ETH", "SOL", "DOT"]
        case .kucoin: return ["BTC", "ETH", "SOL", "ADA"]
        case .bitfinex: return ["BTC", "ETH"]
        case .huobi: return ["BTC", "ETH", "SOL"]
        case .coinbase: return ["BTC", "ETH", "SOL", "ADA"]
        }
    }

    var minOrderSize: [String: Decimal] {
        switch self {
        case .coinmate: return ["EUR": 10, "CZK": 50]
        case .binance: return ["EUR": 10, "USDT": 10]
        case .kraken: return ["EUR": 10, "USD": 10]
        case .kucoin: return ["USDT": 10]
        case .bitfinex: return ["USD": 25, "EUR": 25]
        case .huobi: return ["USDT": 10]
        case .coinbase: return ["EUR": 1, "USD": 1]
        }
    }

    var sandboxSupport: SandboxSupport {
        switch self {
        case .coinmate: return .none
        case .binance: return .full
        case .kraken: return .futuresOnly
        case .kucoin: return .full
        case .bitfinex: return .paperTrading
        case .huobi: return .none
        case .coinbase: return .full
        }
    }

    var supportsSandbox: Bool {
        sandboxSupport == .full
    }

    /// Whether exchange supports CSV transaction history import
    var supportsImport: Bool {
        self == .coinmate
    }

    /// Whether exchange supports API-based transaction history import
    var supportsApiImport: Bool {
        [.coinmate, .binance, .kraken, .coinbase].contains(self)
    }

    /// Whether exchange requires a passphrase (KuCoin, Coinbase)
    var requiresPassphrase: Bool {
        self == .kucoin || self == .coinbase
    }

    /// Whether exchange requires a client ID (Coinmate)
    var requiresClientId: Bool {
        self == .coinmate
    }
}

/// Utility to filter exchanges based on sandbox mode
enum ExchangeFilter {
    static func getAvailableExchanges(isSandboxMode: Bool) -> [Exchange] {
        if isSandboxMode {
            return Exchange.allCases.filter { $0.supportsSandbox }
        }
        return Array(Exchange.allCases)
    }
}
