import Foundation

/// Bitfinex API implementation (stub).
/// Note: Bitfinex uses paper trading mode (same URL).
/// Trading is not yet implemented.
final class BitfinexApi: ExchangeApi {
    let exchange = Exchange.bitfinex

    private let credentials: ExchangeCredentials
    private let isSandbox: Bool

    init(credentials: ExchangeCredentials, isSandbox: Bool) {
        self.credentials = credentials
        self.isSandbox = isSandbox
    }

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        .error(message: "Bitfinex trading is not yet implemented", retryable: false)
    }

    func getBalance(currency: String) async -> Decimal? {
        nil
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        nil
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        throw ExchangeError.unsupportedOperation("Bitfinex withdrawal is not yet implemented")
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        nil
    }

    func validateCredentials() async throws -> Bool {
        false
    }
}
