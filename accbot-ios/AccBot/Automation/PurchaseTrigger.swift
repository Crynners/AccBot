import Foundation

/// What started a run of due purchases. Recorded so the app can show how purchases run
/// and tell whether the Shortcuts automation is set up.
enum PurchaseTrigger: String, CaseIterable {
    case shortcut
    case background
    case notification
    case app

    var label: String {
        switch self {
        case .shortcut: return String(localized: "Shortcuts automation")
        case .background: return String(localized: "Background task")
        case .notification: return String(localized: "Notification")
        case .app: return String(localized: "Opening the app")
        }
    }
}
