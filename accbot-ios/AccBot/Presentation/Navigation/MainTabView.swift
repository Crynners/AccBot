import SwiftUI
import Combine

/// Main tab view with 4 tabs: Dashboard, Portfolio, Notifications, Settings.
/// Uses a manual ZStack + CustomTabBar instead of native TabView to avoid
/// known SwiftUI TabView + NavigationStack lifecycle bugs and to provide
/// a custom animated tab bar design.
struct MainTabView: View {
    @EnvironmentObject var router: AppRouter
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                dashboardTab
                    .opacity(router.selectedTab == .dashboard ? 1 : 0)
                portfolioTab
                    .opacity(router.selectedTab == .portfolio ? 1 : 0)
                notificationsTab
                    .opacity(router.selectedTab == .notifications ? 1 : 0)
                settingsTab
                    .opacity(router.selectedTab == .settings ? 1 : 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            CustomTabBar(
                selectedTab: $router.selectedTab,
                unreadNotificationCount: router.unreadNotificationCount,
                onTabSelected: { newTab in
                    if newTab == router.selectedTab {
                        router.popToRoot()
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            router.selectedTab = newTab
                        }
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                }
            )
        }
        .ignoresSafeArea(.keyboard)
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
    }

    private var portfolioTab: some View {
        NavigationStack(path: $router.portfolioPath) {
            PortfolioView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
    }

    private var notificationsTab: some View {
        NavigationStack(path: $router.notificationsPath) {
            NotificationsView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
    }

    private var settingsTab: some View {
        NavigationStack(path: $router.settingsPath) {
            SettingsView()
                .navigationDestination(for: AppRoute.self) { route in
                    routeDestination(route)
                }
        }
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
