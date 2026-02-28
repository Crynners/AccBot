import SwiftUI
import Combine

/// Main tab view with 4 tabs: Dashboard, Portfolio, Notifications, Settings.
/// Uses a manual ZStack + CustomTabBar instead of native TabView to avoid
/// known SwiftUI TabView + NavigationStack lifecycle bugs and to provide
/// a custom animated tab bar design.
///
/// Each tab is lazily created on first visit via `LazyTab`, so only the
/// active tab's view hierarchy is fully computed. Once created, tabs stay
/// alive (opacity-hidden) to preserve their NavigationStack state.
struct MainTabView: View {
    @EnvironmentObject var router: AppRouter
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors

    /// Tracks which tabs have been visited at least once.
    @State private var loadedTabs: Set<TabItem> = [.dashboard]

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                LazyTab(tab: .dashboard, selectedTab: router.selectedTab, loadedTabs: $loadedTabs) {
                    dashboardTab
                }
                LazyTab(tab: .portfolio, selectedTab: router.selectedTab, loadedTabs: $loadedTabs) {
                    portfolioTab
                }
                LazyTab(tab: .notifications, selectedTab: router.selectedTab, loadedTabs: $loadedTabs) {
                    notificationsTab
                }
                LazyTab(tab: .settings, selectedTab: router.selectedTab, loadedTabs: $loadedTabs) {
                    settingsTab
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .simultaneousGesture(
                DragGesture(minimumDistance: 30)
                    .onEnded { value in
                        let h = value.translation.width
                        let v = value.translation.height
                        guard abs(h) > abs(v) * 1.5, abs(h) > 50 else { return }

                        let current = router.selectedTab.rawValue
                        let next = h < 0
                            ? min(current + 1, TabItem.allCases.count - 1)
                            : max(current - 1, 0)
                        guard next != current,
                              let newTab = TabItem(rawValue: next) else { return }

                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            router.selectedTab = newTab
                        }
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
            )

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
                .removeDuplicates()
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

// MARK: - Lazy Tab

/// Lazily creates a tab's content on first selection and keeps it alive
/// (but hidden) once created. This avoids computing all 4 heavy view
/// hierarchies (NavigationStack + ViewModel + DB observations) on startup.
private struct LazyTab<Content: View>: View {
    let tab: TabItem
    let selectedTab: TabItem
    @Binding var loadedTabs: Set<TabItem>
    @ViewBuilder let content: () -> Content

    private var isSelected: Bool { tab == selectedTab }

    var body: some View {
        Group {
            if loadedTabs.contains(tab) {
                content()
            }
        }
        .opacity(isSelected ? 1 : 0)
        .allowsHitTesting(isSelected)
        .accessibilityHidden(!isSelected)
        .onChange(of: selectedTab) { newTab in
            if newTab == tab {
                loadedTabs.insert(tab)
            }
        }
    }
}
