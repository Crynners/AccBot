import SwiftUI

/// NavigationPath management for the app
@MainActor
final class AppRouter: ObservableObject {
    @Published var selectedTab: TabItem = .dashboard
    @Published var dashboardPath = NavigationPath()
    @Published var portfolioPath = NavigationPath()
    @Published var notificationsPath = NavigationPath()
    @Published var settingsPath = NavigationPath()

    /// Navigate to a route within the current tab
    func navigate(to route: AppRoute) {
        switch selectedTab {
        case .dashboard: dashboardPath.append(route)
        case .portfolio: portfolioPath.append(route)
        case .notifications: notificationsPath.append(route)
        case .settings: settingsPath.append(route)
        }
    }

    /// Navigate to a route and switch tab if needed
    func navigate(to route: AppRoute, tab: TabItem) {
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
}
