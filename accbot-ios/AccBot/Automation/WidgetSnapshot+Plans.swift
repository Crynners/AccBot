import Foundation

extension WidgetSnapshot {
    static func make(plans: [DcaPlan], lastPurchaseAt: Date?, isSandbox: Bool, now: Date = Date()) -> WidgetSnapshot {
        let enabled = plans.filter(\.isEnabled)
        let next = enabled
            .filter { $0.nextExecutionAt != nil }
            .min { ($0.nextExecutionAt ?? .distantFuture) < ($1.nextExecutionAt ?? .distantFuture) }
        return WidgetSnapshot(
            nextPurchaseAt: next?.nextExecutionAt,
            nextPlanName: next?.displayName,
            nextAmountText: next.map { AccBotFormatters.formatFiat($0.amount, symbol: $0.fiat) },
            lastPurchaseAt: lastPurchaseAt,
            activePlanCount: enabled.count,
            isSandbox: isSandbox,
            updatedAt: now
        )
    }
}
