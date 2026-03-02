import Foundation

/// API credentials - encrypted and stored locally only
struct ExchangeCredentials: Codable, Equatable {
    let exchange: Exchange
    let apiKey: String
    let apiSecret: String
    let passphrase: String?  // Some exchanges require this (KuCoin)
    let clientId: String?    // Coinmate requires separate Client ID

    init(
        exchange: Exchange,
        apiKey: String,
        apiSecret: String,
        passphrase: String? = nil,
        clientId: String? = nil
    ) {
        self.exchange = exchange
        self.apiKey = apiKey
        self.apiSecret = apiSecret
        self.passphrase = passphrase
        self.clientId = clientId
    }
}
