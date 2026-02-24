import SwiftUI

/// Main tab view with 4 tabs: Dashboard, Portfolio, Notifications, Settings
struct MainTabView: View {
    @EnvironmentObject var router: AppRouter
    @EnvironmentObject var dependencies: AppDependencies

    var body: some View {
        TabView(selection: $router.selectedTab) {
            dashboardTab
            portfolioTab
            notificationsTab
            settingsTab
        }
        .tint(dependencies.userPreferences.sandboxMode ? .sandboxPrimary : .accentTeal)
    }

    private var dashboardTab: some View {
        NavigationStack(path: $router.dashboardPath) {
            DashboardView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
        .tabItem {
            Label(TabItem.dashboard.title, systemImage: TabItem.dashboard.systemImage)
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
            Label(TabItem.portfolio.title, systemImage: TabItem.portfolio.systemImage)
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
            Label(TabItem.notifications.title, systemImage: TabItem.notifications.systemImage)
        }
        .tag(TabItem.notifications)
    }

    private var settingsTab: some View {
        NavigationStack(path: $router.settingsPath) {
            SettingsView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
        .tabItem {
            Label(TabItem.settings.title, systemImage: TabItem.settings.systemImage)
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
        default:
            EmptyView()
        }
    }
}
