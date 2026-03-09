import SwiftUI

/// Navigation state management for the app.
/// Uses typed [AppRoute] arrays instead of NavigationPath so the class
/// compiles on iOS 15 (NavigationPath requires iOS 16+).
@MainActor
final class AppRouter: ObservableObject {
    @Published var selectedTab: TabItem = .dashboard
    @Published var dashboardStack: [AppRoute] = []
    @Published var portfolioStack: [AppRoute] = []
    @Published var notificationsStack: [AppRoute] = []
    @Published var settingsStack: [AppRoute] = []

    /// When set, Portfolio will auto-select this crypto/fiat pair
    @Published var portfolioSelectedCrypto: String?
    @Published var portfolioSelectedFiat: String?

    /// Unread notification count for badge display
    @Published var unreadNotificationCount: Int = 0

    /// Debounce guard to prevent duplicate pushes from rapid taps
    private var lastNavigationTime = Date.distantPast

    /// Navigate to a route within the current tab
    func navigate(to route: AppRoute) {
        // Prevent duplicate pushes from rapid taps
        let now = Date()
        guard now.timeIntervalSince(lastNavigationTime) > 0.3 else { return }
        lastNavigationTime = now

        switch selectedTab {
        case .dashboard: dashboardStack.append(route)
        case .portfolio: portfolioStack.append(route)
        case .notifications: notificationsStack.append(route)
        case .settings: settingsStack.append(route)
        }
    }

    /// Navigate to a route and switch tab if needed
    func navigate(to route: AppRoute, tab: TabItem) {
        // Prevent duplicate pushes from rapid taps
        let now = Date()
        guard now.timeIntervalSince(lastNavigationTime) > 0.3 else { return }
        lastNavigationTime = now

        selectedTab = tab
        switch tab {
        case .dashboard: dashboardStack.append(route)
        case .portfolio: portfolioStack.append(route)
        case .notifications: notificationsStack.append(route)
        case .settings: settingsStack.append(route)
        }
    }

    /// Pop the current navigation stack
    func pop() {
        switch selectedTab {
        case .dashboard: if !dashboardStack.isEmpty { dashboardStack.removeLast() }
        case .portfolio: if !portfolioStack.isEmpty { portfolioStack.removeLast() }
        case .notifications: if !notificationsStack.isEmpty { notificationsStack.removeLast() }
        case .settings: if !settingsStack.isEmpty { settingsStack.removeLast() }
        }
    }

    /// Pop to root of the current tab
    func popToRoot() {
        switch selectedTab {
        case .dashboard: dashboardStack.removeAll()
        case .portfolio: portfolioStack.removeAll()
        case .notifications: notificationsStack.removeAll()
        case .settings: settingsStack.removeAll()
        }
    }

    /// Handle deep link URL routing (accbot:// scheme)
    func handleDeepLink(_ url: URL) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let host = components.host else { return }

        switch host {
        case "plan":
            if let idStr = components.queryItems?.first(where: { $0.name == "id" })?.value,
               let id = Int64(idStr) {
                selectedTab = .dashboard
                dashboardStack = [.planDetails(id)]
            }
        case "history":
            selectedTab = .dashboard
            dashboardStack = [.history()]
        case "notifications":
            selectedTab = .notifications
            notificationsStack.removeAll()
        case "portfolio":
            selectedTab = .portfolio
            portfolioStack.removeAll()
        case "settings":
            selectedTab = .settings
            settingsStack.removeAll()
        case "exchanges":
            selectedTab = .settings
            settingsStack = [.exchangeManagement]
        default:
            break
        }
    }
}
