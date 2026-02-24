import Foundation

/// Huobi API implementation (stub).
/// Note: Huobi testnet has been discontinued.
/// Trading is not yet implemented.
final class HuobiApi: ExchangeApi {
    let exchange = Exchange.huobi

    private let credentials: ExchangeCredentials
    private let isSandbox: Bool

    init(credentials: ExchangeCredentials, isSandbox: Bool) {
        self.credentials = credentials
        self.isSandbox = isSandbox
    }

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        .error(message: "Huobi trading is not yet implemented", retryable: false)
    }

    func getBalance(currency: String) async -> Decimal? {
        nil
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        nil
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        throw ExchangeError.unsupportedOperation("Huobi withdrawal is not yet implemented")
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        nil
    }

    func validateCredentials() async throws -> Bool {
        false
    }
}
