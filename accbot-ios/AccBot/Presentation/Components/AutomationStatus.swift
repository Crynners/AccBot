import SwiftUI
import UIKit
import UserNotifications

/// Things outside AccBot that decide whether scheduled purchases happen: Background App
/// Refresh (without it purchases only run while the app is open) and notifications
/// (without them a failed purchase goes unnoticed).
enum AutomationIssue: String, CaseIterable, Identifiable {
    case shortcutNotSetUp = "shortcut_automation"
    case backgroundRefreshOff = "background_refresh"
    case notificationsBlocked = "notifications"

    var id: String { rawValue }

    var message: String {
        switch self {
        case .shortcutNotSetUp:
            return String(localized: "Purchases don't run at an exact time yet. A Shortcuts automation fixes that in about a minute.")
        case .backgroundRefreshOff:
            return String(localized: "Background App Refresh is off, so purchases run only when you open the app.")
        case .notificationsBlocked:
            return String(localized: "Notifications are off, so you won't hear about a failed purchase.")
        }
    }

    var fixTitle: String {
        self == .shortcutNotSetUp ? String(localized: "Set up") : String(localized: "Fix")
    }

    /// Opens the guide or the iOS settings page that fixes this issue.
    func openSettings() {
        if self == .shortcutNotSetUp {
            ShortcutsSetupPresenter.shared.isPresented = true
            return
        }
        let urlString = self == .notificationsBlocked
            ? UIApplication.openNotificationSettingsURLString
            : UIApplication.openSettingsURLString
        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }
}

@MainActor
final class AutomationHealthMonitor: ObservableObject {
    @Published private(set) var issues: [AutomationIssue] = []

    var isOk: Bool { issues.isEmpty }

    func refresh() async {
        var found: [AutomationIssue] = []
        if !AppDependencies.shared.userPreferences.isShortcutAutomationActive() {
            found.append(.shortcutNotSetUp)
        }
        if UIApplication.shared.backgroundRefreshStatus != .available {
            found.append(.backgroundRefreshOff)
        }
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        if settings.authorizationStatus == .denied {
            found.append(.notificationsBlocked)
        }
        issues = found
    }
}

/// One row per automation problem, each with a button to the settings page that fixes it.
struct AutomationIssueList: View {
    let issues: [AutomationIssue]
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ForEach(issues) { issue in
                HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
                    Text(issue.message)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button(issue.fixTitle) { issue.openSettings() }
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.primary)
                        .frame(minHeight: 44)
                }
            }
        }
    }
}

/// "Automatic purchases: OK / may be limited" for Settings.
struct AutomationStatusRow: View {
    @ObservedObject var monitor: AutomationHealthMonitor
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: monitor.isOk ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .foregroundStyle(monitor.isOk ? colors.success : colors.warning)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Automatic purchases"))
                    Text(monitor.isOk
                         ? String(localized: "Working: a Shortcuts automation runs purchases on time")
                         : String(localized: "Automatic purchases may be limited"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(monitor.isOk ? colors.onSurfaceVariant : colors.warning)
                }
            }
            .accessibilityElement(children: .combine)
            if !monitor.isOk {
                AutomationIssueList(issues: monitor.issues)
            }
        }
    }
}
