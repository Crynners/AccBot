import Foundation

/// Monthly cost estimate with range for dynamic strategies.
struct MonthlyCostEstimate {
    let minMonthly: Decimal
    let maxMonthly: Decimal
    let currentMonthly: Decimal?
    let currentInfo: String?
}

/// Estimates monthly DCA cost based on amount, frequency, and strategy.
final class CalculateMonthlyCostUseCase {
    private let marketDataService: MarketDataService

    init(marketDataService: MarketDataService) {
        self.marketDataService = marketDataService
    }

    func getEffectiveIntervalMinutes(frequency: DcaFrequency, cronExpression: String?) -> Int {
        if frequency == .custom {
            return CronUtils.getIntervalMinutesEstimate(cron: cronExpression ?? "") ?? 1440
        }
        return frequency.intervalMinutes
    }

    func computeEstimate(
        amount: Decimal,
        frequency: DcaFrequency,
        cronExpression: String?,
        strategy: DcaStrategy,
        crypto: String,
        fiat: String
    ) async -> MonthlyCostEstimate? {
        guard amount > 0 else { return nil }

        let intervalMinutes = getEffectiveIntervalMinutes(frequency: frequency, cronExpression: cronExpression)
        guard intervalMinutes > 0 else { return nil }

        let minutesPerMonth = Decimal(30 * 24 * 60)
        let runsPerMonth = divideDecimal(minutesPerMonth, by: Decimal(intervalMinutes), scale: 2)

        switch strategy {
        case .classic:
            let monthly = amount * runsPerMonth
            return MonthlyCostEstimate(minMonthly: monthly, maxMonthly: monthly, currentMonthly: monthly, currentInfo: nil)

        case .athBased(let tiers):
            return await computeStrategyEstimate(
                amount: amount,
                runsPerMonth: runsPerMonth,
                minMult: tiers.map(\.multiplier).min() ?? 0.5,
                maxMult: tiers.map(\.multiplier).max() ?? 3.0
            ) {
                guard let currentPrice = await self.marketDataService.getCurrentPrice(crypto: crypto, fiat: fiat),
                      let ath = await self.marketDataService.getAllTimeHigh(crypto: crypto, fiat: fiat),
                      ath > 0
                else { return nil }

                let athDistance = Float(1.0 - NSDecimalNumber(decimal: currentPrice).doubleValue / NSDecimalNumber(decimal: ath).doubleValue)
                let distPct = Int(athDistance * 100)
                let mult = tiers.sorted(by: { $0.maxDistancePercent < $1.maxDistancePercent })
                    .first(where: { athDistance <= $0.maxDistancePercent })?.multiplier ?? 1.0
                return (mult, "\(crypto) is \(distPct)% below ATH")
            }

        case .fearAndGreed(let tiers):
            return await computeStrategyEstimate(
                amount: amount,
                runsPerMonth: runsPerMonth,
                minMult: tiers.map(\.multiplier).min() ?? 0.25,
                maxMult: tiers.map(\.multiplier).max() ?? 2.5
            ) {
                guard let index = await self.marketDataService.getFearGreedIndex() else { return nil }
                let mult = tiers.sorted(by: { $0.maxIndex < $1.maxIndex })
                    .first(where: { index <= $0.maxIndex })?.multiplier ?? 1.0
                let classification: String = switch index {
                case 0...24: "Extreme Fear"
                case 25...44: "Fear"
                case 45...54: "Neutral"
                case 55...74: "Greed"
                default: "Extreme Greed"
                }
                return (mult, "Fear & Greed: \(index) (\(classification))")
            }
        }
    }

    private func computeStrategyEstimate(
        amount: Decimal,
        runsPerMonth: Decimal,
        minMult: Float,
        maxMult: Float,
        fetchCurrentMultiplier: () async -> (Float, String)?
    ) async -> MonthlyCostEstimate {
        let minMonthly = amount * Decimal(Double(minMult)) * runsPerMonth
        let maxMonthly = amount * Decimal(Double(maxMult)) * runsPerMonth

        let result = await fetchCurrentMultiplier()
        let currentMonthly = result.map { amount * Decimal(Double($0.0)) * runsPerMonth }

        return MonthlyCostEstimate(
            minMonthly: minMonthly,
            maxMonthly: maxMonthly,
            currentMonthly: currentMonthly,
            currentInfo: result?.1
        )
    }

    private func divideDecimal(_ a: Decimal, by b: Decimal, scale: Int) -> Decimal {
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
