import Foundation

/// What the AccBot widget shows. The app writes it to the shared App Group after every
/// run; the widget only reads it, so the widget never touches the database or API keys.
struct WidgetSnapshot: Codable, Equatable {
    var nextPurchaseAt: Date?
    var nextPlanName: String?
    var nextAmountText: String?
    var lastPurchaseAt: Date?
    var activePlanCount: Int
    var isSandbox: Bool
    var updatedAt: Date

    /// Matches the first purchase reminder: after this the purchase counts as waiting.
    static let lateAfter: TimeInterval = 15 * 60

    static let empty = WidgetSnapshot(
        nextPurchaseAt: nil, nextPlanName: nil, nextAmountText: nil, lastPurchaseAt: nil,
        activePlanCount: 0, isSandbox: false, updatedAt: .distantPast
    )

    func isLate(at date: Date) -> Bool {
        guard let next = nextPurchaseAt else { return false }
        return next.addingTimeInterval(Self.lateAfter) <= date
    }
}

enum WidgetSnapshotStore {
    /// Must match the App Group in both targets' entitlements.
    static let appGroup = "group.com.accbot.dca"
    private static let key = "widgetSnapshot"

    private static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }

    static func read() -> WidgetSnapshot? {
        guard let data = defaults?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    static func write(_ snapshot: WidgetSnapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults?.set(data, forKey: key)
    }
}
