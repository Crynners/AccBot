import Foundation

/// A single data point for the portfolio performance chart.
struct ChartDataPoint {
    let epochDay: Int64
    let portfolioValue: Decimal
    let totalInvested: Decimal
    let roiAbsolute: Decimal
    let roiPercent: Decimal
    var cumulativeCrypto: Decimal = 0
    var investedEquivCrypto: Decimal = 0
    var avgBuyPrice: Decimal = 0
    var price: Decimal = 0
}

/// Computes chart data for portfolio performance with hierarchical zoom levels.
/// Adaptive aggregation: daily (<3 months), weekly (3-12 months / Year zoom), monthly (>12 months).
/// Month zoom: always daily points.
final class CalculateChartDataUseCase {
    private let dailyPriceDao: DailyPriceDao

    init(dailyPriceDao: DailyPriceDao) {
        self.dailyPriceDao = dailyPriceDao
    }

    func calculate(
        transactions: [Transaction],
        crypto: String?,
        fiat: String?,
        zoomLevel: ChartZoomLevel
    ) throws -> [ChartDataPoint] {
        guard !transactions.isEmpty else { return [] }

        if crypto == nil, let fiat = fiat {
            return try calculateAggregateForFiat(transactions: transactions, fiat: fiat, zoomLevel: zoomLevel)
        } else if let crypto = crypto, let fiat = fiat {
            return try calculateForPair(allTransactions: transactions, crypto: crypto, fiat: fiat, zoomLevel: zoomLevel)
        }
        return []
    }

    // MARK: - Single Pair

    private func calculateForPair(
        allTransactions: [Transaction],
        crypto: String,
        fiat: String,
        zoomLevel: ChartZoomLevel
    ) throws -> [ChartDataPoint] {
        let pairTxs = allTransactions
            .filter { $0.crypto == crypto && $0.fiat == fiat }
            .sorted { $0.executedAt < $1.executedAt }
        guard !pairTxs.isEmpty else { return [] }

        let today = Self.today()
        let firstTxDay = Self.epochDay(from: pairTxs.first!.executedAt)
        let (startDay, endDay) = visiblePeriod(zoomLevel: zoomLevel, firstTxDay: firstTxDay, todayDay: today)

        let prices = try dailyPriceDao.getPrices(crypto: crypto, fiat: fiat, fromDay: startDay, toDay: endDay)
        let priceMap = Dictionary(uniqueKeysWithValues: prices.map { ($0.day, $0.price) })

        var cumulativeCrypto: Decimal = 0
        var cumulativeInvested: Decimal = 0
        var txIndex = 0

        // Pre-accumulate transactions before visible period
        while txIndex < pairTxs.count {
            let txDay = Self.epochDay(from: pairTxs[txIndex].executedAt)
            guard txDay < startDay else { break }
            cumulativeCrypto += pairTxs[txIndex].cryptoAmount
            cumulativeInvested += pairTxs[txIndex].fiatAmount
            txIndex += 1
        }

        let mode = aggregationMode(zoomLevel, startDay: startDay, endDay: endDay)
        var result: [ChartDataPoint] = []
        var lastKnownPrice: Decimal?
        var pendingDay: Int64 = 0
        var pendingCrypto: Decimal = 0
        var pendingInvested: Decimal = 0
        var pendingPrice: Decimal?
        var pendingBucket: Int? // bucket key (yearMonth or yearWeek)

        var currentDay = startDay
        while currentDay <= endDay {
            // Add transactions on this day
            while txIndex < pairTxs.count {
                let txDay = Self.epochDay(from: pairTxs[txIndex].executedAt)
                guard txDay <= currentDay else { break }
                cumulativeCrypto += pairTxs[txIndex].cryptoAmount
                cumulativeInvested += pairTxs[txIndex].fiatAmount
                txIndex += 1
            }

            let price = priceMap[currentDay] ?? lastKnownPrice
            if let price = price {
                lastKnownPrice = price

                switch mode {
                case .daily:
                    result.append(buildPoint(epochDay: currentDay, crypto: cumulativeCrypto, invested: cumulativeInvested, price: price))
                case .weekly, .monthly:
                    let currentBucket = mode == .weekly
                        ? Self.yearWeek(fromEpochDay: currentDay)
                        : Self.yearMonth(fromEpochDay: currentDay)
                    if let pb = pendingBucket, currentBucket != pb, let pp = pendingPrice {
                        result.append(buildPoint(epochDay: pendingDay, crypto: pendingCrypto, invested: pendingInvested, price: pp))
                    }
                    pendingDay = currentDay
                    pendingCrypto = cumulativeCrypto
                    pendingInvested = cumulativeInvested
                    pendingPrice = price
                    pendingBucket = currentBucket
                }
            }

            currentDay += 1
        }

        // Flush last pending bucket point
        if mode != .daily, let pp = pendingPrice {
            result.append(buildPoint(epochDay: pendingDay, crypto: pendingCrypto, invested: pendingInvested, price: pp))
        }

        return result
    }

    // MARK: - Aggregate for Fiat

    private func calculateAggregateForFiat(
        transactions: [Transaction],
        fiat: String,
        zoomLevel: ChartZoomLevel
    ) throws -> [ChartDataPoint] {
        let fiatTxs = transactions.filter { $0.fiat == fiat }.sorted { $0.executedAt < $1.executedAt }
        guard !fiatTxs.isEmpty else { return [] }

        let pairs = Set(fiatTxs.map { "\($0.crypto)|\($0.fiat)" })
        let today = Self.today()
        let firstTxDay = Self.epochDay(from: fiatTxs.first!.executedAt)
        let (startDay, endDay) = visiblePeriod(zoomLevel: zoomLevel, firstTxDay: firstTxDay, todayDay: today)

        struct PairState {
            var cumulativeCrypto: Decimal = 0
            var cumulativeInvested: Decimal = 0
            var lastKnownPrice: Decimal?
            var txs: [Transaction]
            var txIndex: Int = 0
        }

        var states: [String: PairState] = [:]
        var pairPriceMaps: [String: [Int64: Decimal]] = [:]

        let txsByPair = Dictionary(grouping: fiatTxs) { "\($0.crypto)|\($0.fiat)" }
        for pairKey in pairs {
            let parts = pairKey.split(separator: "|")
            let crypto = String(parts[0])
            let pairFiat = String(parts[1])
            let prices = try dailyPriceDao.getPrices(crypto: crypto, fiat: pairFiat, fromDay: startDay, toDay: endDay)
            pairPriceMaps[pairKey] = Dictionary(uniqueKeysWithValues: prices.map { ($0.day, $0.price) })
            states[pairKey] = PairState(txs: (txsByPair[pairKey] ?? []).sorted { $0.executedAt < $1.executedAt })
        }

        // Pre-accumulate before visible period
        for (key, _) in states {
            while states[key]!.txIndex < states[key]!.txs.count {
                let tx = states[key]!.txs[states[key]!.txIndex]
                let txDay = Self.epochDay(from: tx.executedAt)
                guard txDay < startDay else { break }
                states[key]!.cumulativeCrypto += tx.cryptoAmount
                states[key]!.cumulativeInvested += tx.fiatAmount
                states[key]!.txIndex += 1
            }
        }

        let mode = aggregationMode(zoomLevel, startDay: startDay, endDay: endDay)
        var result: [ChartDataPoint] = []
        var pendingDay: Int64 = 0
        var pendingValue: Decimal = 0
        var pendingInvested: Decimal = 0
        var hasPending = false
        var pendingBucket: Int?

        var currentDay = startDay
        while currentDay <= endDay {
            var totalValue: Decimal = 0
            var totalInvested: Decimal = 0
            var anyPrice = false

            for (key, _) in states {
                while states[key]!.txIndex < states[key]!.txs.count {
                    let tx = states[key]!.txs[states[key]!.txIndex]
                    let txDay = Self.epochDay(from: tx.executedAt)
                    guard txDay <= currentDay else { break }
                    states[key]!.cumulativeCrypto += tx.cryptoAmount
                    states[key]!.cumulativeInvested += tx.fiatAmount
                    states[key]!.txIndex += 1
                }

                let priceMap = pairPriceMaps[key]!
                let price = priceMap[currentDay] ?? states[key]!.lastKnownPrice
                if let price = price {
                    states[key]!.lastKnownPrice = price
                    totalValue += states[key]!.cumulativeCrypto * price
                    anyPrice = true
                }
                totalInvested += states[key]!.cumulativeInvested
            }

            if anyPrice {
                switch mode {
                case .daily:
                    result.append(buildAggregatePoint(epochDay: currentDay, value: totalValue, invested: totalInvested))
                case .weekly, .monthly:
                    let currentBucket = mode == .weekly
                        ? Self.yearWeek(fromEpochDay: currentDay)
                        : Self.yearMonth(fromEpochDay: currentDay)
                    if let pb = pendingBucket, currentBucket != pb, hasPending {
                        result.append(buildAggregatePoint(epochDay: pendingDay, value: pendingValue, invested: pendingInvested))
                    }
                    pendingDay = currentDay
                    pendingValue = totalValue
                    pendingInvested = totalInvested
                    hasPending = true
                    pendingBucket = currentBucket
                }
            }

            currentDay += 1
        }

        if mode != .daily, hasPending {
            result.append(buildAggregatePoint(epochDay: pendingDay, value: pendingValue, invested: pendingInvested))
        }

        return result
    }

    // MARK: - Helpers

    private func buildPoint(epochDay: Int64, crypto: Decimal, invested: Decimal, price: Decimal) -> ChartDataPoint {
        let value = crypto * price
        let roi = value - invested
        let roiPct: Decimal = invested > 0
            ? safeDiv(roi, by: invested, scale: 4) * 100
            : 0

        let investedEquiv: Decimal = price > 0 ? safeDiv(invested, by: price, scale: 8) : 0
        let avgBuy: Decimal = crypto > 0 ? safeDiv(invested, by: crypto, scale: 2) : 0

        return ChartDataPoint(
            epochDay: epochDay,
            portfolioValue: roundDec(value, 2),
            totalInvested: roundDec(invested, 2),
            roiAbsolute: roundDec(roi, 2),
            roiPercent: roundDec(roiPct, 2),
            cumulativeCrypto: crypto,
            investedEquivCrypto: investedEquiv,
            avgBuyPrice: avgBuy,
            price: price
        )
    }

    private func buildAggregatePoint(epochDay: Int64, value: Decimal, invested: Decimal) -> ChartDataPoint {
        let roi = value - invested
        let roiPct: Decimal = invested > 0 ? safeDiv(roi, by: invested, scale: 4) * 100 : 0

        return ChartDataPoint(
            epochDay: epochDay,
            portfolioValue: roundDec(value, 2),
            totalInvested: roundDec(invested, 2),
            roiAbsolute: roundDec(roi, 2),
            roiPercent: roundDec(roiPct, 2)
        )
    }

    func visiblePeriod(zoomLevel: ChartZoomLevel, firstTxDay: Int64, todayDay: Int64) -> (start: Int64, end: Int64) {
        switch zoomLevel {
        case .overview:
            return (firstTxDay, todayDay)
        case .year(let year):
            let cal = Calendar.current
            let yearStart = cal.date(from: DateComponents(year: year, month: 1, day: 1))!
            let yearEnd = cal.date(from: DateComponents(year: year, month: 12, day: 31))!
            let start = max(Self.epochDay(from: yearStart), firstTxDay)
            let end = min(Self.epochDay(from: yearEnd), todayDay)
            return (start, end)
        case .month(let year, let month):
            let cal = Calendar.current
            let monthStart = cal.date(from: DateComponents(year: year, month: month, day: 1))!
            let monthEnd = cal.date(byAdding: DateComponents(month: 1, day: -1), to: monthStart)!
            let start = max(Self.epochDay(from: monthStart), firstTxDay)
            let end = min(Self.epochDay(from: monthEnd), todayDay)
            return (start, end)
        }
    }

    func getAvailableYears(transactions: [Transaction]) -> [Int] {
        let cal = Calendar.current
        return Array(Set(transactions.map { cal.component(.year, from: $0.executedAt) })).sorted()
    }

    func getAvailableMonths(transactions: [Transaction], year: Int) -> [Int] {
        let cal = Calendar.current
        return Array(Set(transactions.compactMap { tx -> Int? in
            let y = cal.component(.year, from: tx.executedAt)
            return y == year ? cal.component(.month, from: tx.executedAt) : nil
        })).sorted()
    }

    enum AggregationMode {
        case daily
        case weekly
        case monthly
    }

    /// Determine aggregation granularity based on zoom level and data span.
    /// Shared logic — also used by PortfolioViewModel for chart point bucketing.
    static func aggregationMode(zoomLevel: ChartZoomLevel, spanDays: Int64) -> AggregationMode {
        switch zoomLevel {
        case .month:
            return .daily
        case .year:
            return .weekly
        case .overview:
            let spanMonths = spanDays / 30
            if spanMonths < 3 {
                return .daily
            } else if spanMonths <= 12 {
                return .weekly
            } else {
                return .monthly
            }
        }
    }

    /// Compute bucket key for a given date based on aggregation mode.
    static func bucketKey(for date: Date, mode: AggregationMode) -> Int {
        let cal = Calendar.current
        switch mode {
        case .daily:
            return 0 // not used for daily
        case .weekly:
            let yearForWeek = cal.component(.yearForWeekOfYear, from: date)
            let weekOfYear = cal.component(.weekOfYear, from: date)
            return yearForWeek * 100 + weekOfYear
        case .monthly:
            let year = cal.component(.year, from: date)
            let month = cal.component(.month, from: date)
            return year * 100 + month
        }
    }

    private func aggregationMode(_ zoomLevel: ChartZoomLevel, startDay: Int64, endDay: Int64) -> AggregationMode {
        Self.aggregationMode(zoomLevel: zoomLevel, spanDays: endDay - startDay)
    }

    static func epochDay(from date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 / 86400)
    }

    static func today() -> Int64 {
        epochDay(from: Date())
    }

    private static func yearMonth(fromEpochDay day: Int64) -> Int {
        let date = Date(timeIntervalSince1970: Double(day) * 86400)
        let cal = Calendar.current
        return cal.component(.year, from: date) * 100 + cal.component(.month, from: date)
    }

    private static func yearWeek(fromEpochDay day: Int64) -> Int {
        let date = Date(timeIntervalSince1970: Double(day) * 86400)
        let cal = Calendar.current
        let yearForWeek = cal.component(.yearForWeekOfYear, from: date)
        let weekOfYear = cal.component(.weekOfYear, from: date)
        return yearForWeek * 100 + weekOfYear
    }

    private func safeDiv(_ a: Decimal, by b: Decimal, scale: Int) -> Decimal {
        guard b != 0 else { return 0 }
        let handler = NSDecimalNumberHandler(
            roundingMode: .plain, scale: Int16(scale),
            raiseOnExactness: false, raiseOnOverflow: false,
            raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: a)
            .dividing(by: NSDecimalNumber(decimal: b), withBehavior: handler)
            .decimalValue
    }

    private func roundDec(_ value: Decimal, _ scale: Int) -> Decimal {
        let handler = NSDecimalNumberHandler(
            roundingMode: .plain, scale: Int16(scale),
            raiseOnExactness: false, raiseOnOverflow: false,
            raiseOnUnderflow: false, raiseOnDivideByZero: false
        )
        return NSDecimalNumber(decimal: value).rounding(accordingToBehavior: handler).decimalValue
    }
}
