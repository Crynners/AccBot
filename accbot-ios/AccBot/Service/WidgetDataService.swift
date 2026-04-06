import Foundation
import WidgetKit

/// Writes app data snapshots to shared UserDefaults for the widget extension.
/// Call `update()` after data changes (DCA execution, data load, refresh).
enum WidgetDataService {
    private static let suiteName = "group.com.accbot.dca"
    private static let key = "widgetData"

    /// Update widget data from current app state.
    @MainActor
    static func update(from deps: AppDependencies) {
        let db = deps.activeDatabase

        // Plans
        let plans = (try? db.planDao.getAll()) ?? []
        let activePlans = plans.filter { $0.isEnabled }
        let nextExecution = activePlans.compactMap(\.nextExecutionAt).min()
        let lastExecution = plans.compactMap(\.lastExecutedAt).max()

        // Holdings (first pair as primary display)
        let summaries = (try? db.transactionDao.getHoldingSummaries()) ?? []
        let primary = summaries.first

        // Build widget data
        let data = WidgetSnapshot(
            lastExecutedAt: lastExecution,
            nextExecutionAt: nextExecution,
            totalCrypto: primary.map { formatDecimal($0.totalCrypto) } ?? "---",
            cryptoSymbol: primary?.crypto ?? "BTC",
            totalInvested: primary.map { formatDecimal($0.totalInvested) } ?? "---",
            fiatSymbol: primary?.fiat ?? "CZK",
            portfolioValue: nil, // Would need current price - skip for now
            roiPercent: nil,
            planCount: plans.count,
            activePlanCount: activePlans.count,
            updatedAt: Date()
        )

        // Write to shared UserDefaults
        guard let defaults = UserDefaults(suiteName: suiteName) else { return }
        if let encoded = try? JSONEncoder().encode(data) {
            defaults.set(encoded, forKey: key)
        }

        // Tell WidgetKit to refresh
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static func formatDecimal(_ value: Decimal) -> String {
        let number = NSDecimalNumber(decimal: value)
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 8
        formatter.locale = .current
        return formatter.string(from: number) ?? "0"
    }
}

/// Matches the WidgetData struct in the widget extension.
private struct WidgetSnapshot: Codable {
    let lastExecutedAt: Date?
    let nextExecutionAt: Date?
    let totalCrypto: String
    let cryptoSymbol: String
    let totalInvested: String
    let fiatSymbol: String
    let portfolioValue: String?
    let roiPercent: String?
    let planCount: Int
    let activePlanCount: Int
    let updatedAt: Date
}
