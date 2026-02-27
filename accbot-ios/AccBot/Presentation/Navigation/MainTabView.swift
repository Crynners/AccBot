import SwiftUI
import Combine

/// Main tab view with 4 tabs: Dashboard, Portfolio, Notifications, Settings
struct MainTabView: View {
    @EnvironmentObject var router: AppRouter
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors

    /// Custom binding that intercepts tab selection to pop-to-root when re-selecting the active tab
    private var tabSelection: Binding<TabItem> {
        Binding(
            get: { router.selectedTab },
            set: { newTab in
                if newTab == router.selectedTab {
                    // Re-selecting the same tab: pop to root (standard iOS pattern)
                    router.popToRoot()
                } else {
                    router.selectedTab = newTab
                }
            }
        )
    }

    var body: some View {
        TabView(selection: tabSelection) {
            dashboardTab
            portfolioTab
            notificationsTab
            settingsTab
        }
        .tint(colors.primary)
        .dynamicTypeSize(...DynamicTypeSize.accessibility3)
        .onReceive(
            dependencies.activeDatabase.notificationDao.observeUnreadCount()
                .replaceError(with: 0)
                .receive(on: DispatchQueue.main)
        ) { count in
            router.unreadNotificationCount = count
        }
    }

    private var dashboardTab: some View {
        NavigationStack(path: $router.dashboardPath) {
            DashboardView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
        .tabItem {
            Label(TabItem.dashboard.title, systemImage: TabItem.dashboard.systemImage(isSelected: router.selectedTab == .dashboard))
        }
        .tag(TabItem.dashboard)
    }

    private var portfolioTab: some View {
        NavigationStack(path: $router.portfolioPath) {
            PortfolioView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
        .tabItem {
            Label(TabItem.portfolio.title, systemImage: TabItem.portfolio.systemImage(isSelected: router.selectedTab == .portfolio))
        }
        .tag(TabItem.portfolio)
    }

    private var notificationsTab: some View {
        NavigationStack(path: $router.notificationsPath) {
            NotificationsView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
        .tabItem {
            Label(TabItem.notifications.title, systemImage: TabItem.notifications.systemImage(isSelected: router.selectedTab == .notifications))
        }
        .tag(TabItem.notifications)
        .badge(router.unreadNotificationCount)
        .accessibilityValue(router.unreadNotificationCount > 0
            ? String(localized: "\(router.unreadNotificationCount) unread notifications")
            : String(localized: "No unread notifications"))
    }

    private var settingsTab: some View {
        NavigationStack(path: $router.settingsPath) {
            SettingsView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
        .tabItem {
            Label(TabItem.settings.title, systemImage: TabItem.settings.systemImage(isSelected: router.selectedTab == .settings))
        }
        .tag(TabItem.settings)
    }

    @ViewBuilder
    private func routeDestination(_ route: AppRoute) -> some View {
        switch route {
        case .addPlan:
            AddPlanView()
        case .planDetails(let planId):
            PlanDetailsView(planId: planId)
        case .editPlan(let planId):
            EditPlanView(planId: planId)
        case .importCsv(let planId):
            ImportCsvView(planId: planId)
        case .exchangeManagement:
            ExchangeManagementView()
        case .exchangeDetail(let exchange):
            ExchangeDetailView(exchange: exchange)
        case .addExchange(let exchange):
            AddExchangeView(preselectedExchange: exchange)
        case .history(let crypto, let fiat):
            HistoryView(filterCrypto: crypto, filterFiat: fiat)
        case .transactionDetails(let txId):
            TransactionDetailsView(transactionId: txId)
        case .backupExport:
            BackupExportView()
        case .backupImport:
            BackupImportView()
        }
    }
}
