import Foundation

struct CryptoHolding {
    let crypto: String
    let totalAmount: Decimal
    let totalInvested: Decimal
    let averagePrice: Decimal
    let transactionCount: Int
}

struct ExchangeHolding {
    let exchange: Exchange
    let holdings: [CryptoHolding]
    let totalInvested: Decimal
}

struct MonthlyPerformance {
    let month: String        // "Jan 2024"
    let yearMonth: String    // "2024-01"
    let totalInvested: Decimal
    let totalCrypto: Decimal
    let transactionCount: Int
    let averagePrice: Decimal
}

struct PortfolioSummary {
    let cryptoHoldings: [CryptoHolding]
    let exchangeHoldings: [ExchangeHolding]
    let monthlyPerformance: [MonthlyPerformance]
    let totalInvested: Decimal
    let totalBtc: Decimal
    let totalTransactions: Int
    let averageMonthlyInvestment: Decimal
}

/// Calculates portfolio holdings, exchange breakdowns, and monthly performance from transactions.
final class CalculatePortfolioUseCase {

    func calculatePortfolioSummary(transactions: [Transaction]) -> PortfolioSummary {
        let cryptoHoldings = calculateCryptoHoldings(transactions: transactions)
        let exchangeHoldings = calculateExchangeHoldings(transactions: transactions)
        let monthlyPerformance = calculateMonthlyPerformance(transactions: transactions)

        let totalInvested = transactions.reduce(Decimal.zero) { $0 + $1.fiatAmount }
        let totalBtc = transactions.filter { $0.crypto == "BTC" }.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
        let totalTransactions = transactions.count

        let averageMonthly: Decimal = if !monthlyPerformance.isEmpty {
            safeDiv(totalInvested, by: Decimal(monthlyPerformance.count), scale: 2)
        } else {
            0
        }

        return PortfolioSummary(
            cryptoHoldings: cryptoHoldings,
            exchangeHoldings: exchangeHoldings,
            monthlyPerformance: monthlyPerformance,
            totalInvested: totalInvested,
            totalBtc: totalBtc,
            totalTransactions: totalTransactions,
            averageMonthlyInvestment: averageMonthly
        )
    }

    func calculateCryptoHoldings(transactions: [Transaction]) -> [CryptoHolding] {
        Dictionary(grouping: transactions, by: \.crypto)
            .map { (crypto, txs) in
                let totalAmount = txs.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
                let totalInvested = txs.reduce(Decimal.zero) { $0 + $1.fiatAmount }
                return CryptoHolding(
                    crypto: crypto,
                    totalAmount: totalAmount,
                    totalInvested: totalInvested,
                    averagePrice: safeDiv(totalInvested, by: totalAmount, scale: 2),
                    transactionCount: txs.count
                )
            }
            .sorted { $0.totalInvested > $1.totalInvested }
    }

    func calculateExchangeHoldings(transactions: [Transaction]) -> [ExchangeHolding] {
        Dictionary(grouping: transactions, by: \.exchange)
            .map { (exchange, txs) in
                let holdings = Dictionary(grouping: txs, by: \.crypto)
                    .map { (crypto, cryptoTxs) in
                        let totalAmount = cryptoTxs.reduce(Decimal.zero) { $0 + $1.cryptoAmount }
                        let totalInvested = cryptoTxs.reduce(Decimal.zero) { $0 + $1.fiatAmount }
                        return CryptoHolding(
                            crypto: crypto,
                            totalAmount: totalAmount,
                            totalInvested: totalInvested,
                            averagePrice: safeDiv(totalInvested, by: totalAmount, scale: 2),
                            transactionCount: cryptoTxs.count
                        )
                    }
                    .sorted { $0.totalInvested > $1.totalInvested }

                return ExchangeHolding(
                    exchange: exchange,
                    holdings: holdings,
                    totalInvested: txs.reduce(Decimal.zero) { $0 + $1.fiatAmount }
                )
            }
            .sorted { $0.totalInvested > $1.totalInvested }
    }

    func calculateMonthlyPerformance(transactions: [Transaction]) -> [MonthlyPerformance] {
        let calendar = Calendar.current
        let monthFormatter = DateFormatter()
        monthFormatter.dateFormat = "MMM yyyy"
        let yearMonthFormatter = DateFormatter()
        yearMonthFormatter.dateFormat = "yyyy-MM"

        return Dictionary(grouping: transactions) { tx in
            let components = calendar.dateComponents([.year, .month], from: tx.executedAt)
            return "\(components.year!)-\(String(format: "%02d", components.month!))"
        }
        .map { (ym, txs) in
            let totalInvested = txs.reduce(Decimal.zero) { $0 + $1.fiatAmount }
            let totalCrypto = txs.reduce(Decimal.zero) { $0 + $1.cryptoAmount }

            let sampleDate = txs.first!.executedAt
            let displayMonth = monthFormatter.string(from: sampleDate)

            return MonthlyPerformance(
                month: displayMonth,
                yearMonth: ym,
                totalInvested: totalInvested,
                totalCrypto: totalCrypto,
                transactionCount: txs.count,
                averagePrice: safeDiv(totalInvested, by: totalCrypto, scale: 2)
            )
        }
        .sorted { $0.yearMonth > $1.yearMonth }
    }

    private func safeDiv(_ a: Decimal, by b: Decimal, scale: Int) -> Decimal {
        guard b > 0 else { return 0 }
        let handler = NSDecimalNumberHandler(
            roundingMode: .plain, scale: Int16(scale),
            raiseOnExactness: false, raiseOnOverflow: false,
            raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: a)
            .dividing(by: NSDecimalNumber(decimal: b), withBehavior: handler)
            .decimalValue
    }
}
