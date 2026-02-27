import SwiftUI

/// NavigationPath management for the app
@MainActor
final class AppRouter: ObservableObject {
    @Published var selectedTab: TabItem = .dashboard
    @Published var dashboardPath = NavigationPath()
    @Published var portfolioPath = NavigationPath()
    @Published var notificationsPath = NavigationPath()
    @Published var settingsPath = NavigationPath()

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
        case .dashboard: dashboardPath.append(route)
        case .portfolio: portfolioPath.append(route)
        case .notifications: notificationsPath.append(route)
        case .settings: settingsPath.append(route)
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
        case .dashboard: dashboardPath.append(route)
        case .portfolio: portfolioPath.append(route)
        case .notifications: notificationsPath.append(route)
        case .settings: settingsPath.append(route)
        }
    }

    /// Pop the current navigation stack
    func pop() {
        switch selectedTab {
        case .dashboard: if !dashboardPath.isEmpty { dashboardPath.removeLast() }
        case .portfolio: if !portfolioPath.isEmpty { portfolioPath.removeLast() }
        case .notifications: if !notificationsPath.isEmpty { notificationsPath.removeLast() }
        case .settings: if !settingsPath.isEmpty { settingsPath.removeLast() }
        }
    }

    /// Pop to root of the current tab
    func popToRoot() {
        switch selectedTab {
        case .dashboard: dashboardPath = NavigationPath()
        case .portfolio: portfolioPath = NavigationPath()
        case .notifications: notificationsPath = NavigationPath()
        case .settings: settingsPath = NavigationPath()
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
                dashboardPath = NavigationPath()
                dashboardPath.append(AppRoute.planDetails(id))
            }
        case "history":
            selectedTab = .dashboard
            dashboardPath = NavigationPath()
            dashboardPath.append(AppRoute.history())
        case "notifications":
            selectedTab = .notifications
            notificationsPath = NavigationPath()
        case "portfolio":
            selectedTab = .portfolio
            portfolioPath = NavigationPath()
        case "settings":
            selectedTab = .settings
            settingsPath = NavigationPath()
        case "exchanges":
            selectedTab = .settings
            settingsPath = NavigationPath()
            settingsPath.append(AppRoute.exchangeManagement)
        default:
            break
        }
    }
}
