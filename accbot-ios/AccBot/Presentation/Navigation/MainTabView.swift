import SwiftUI
import Combine

/// Main tab view with 4 tabs: Dashboard, Portfolio, Notifications, Settings.
/// Uses a manual ZStack + CustomTabBar instead of native TabView to avoid
/// known SwiftUI TabView + NavigationStack lifecycle bugs and to provide
/// a custom animated tab bar design.
///
/// Each tab is lazily created on first visit via `LazyTab`, so only the
/// active tab's view hierarchy is fully computed. Once created, tabs stay
/// alive (offset-hidden) to preserve their NavigationStack state.
///
/// Tab switching uses offset-based horizontal paging with continuous drag
/// tracking, matching the Android HorizontalPager "book flipping" feel.
struct MainTabView: View {
    @EnvironmentObject var router: AppRouter
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors

    /// Tracks which tabs have been visited at least once.
    @State private var loadedTabs: Set<TabItem> = [.dashboard]

    /// Real-time horizontal drag offset for continuous page tracking.
    @State private var dragOffset: CGFloat = 0

    /// Whether the current gesture has been identified as horizontal.
    @State private var isDraggingHorizontally: Bool? = nil

    /// Changelog auto-show on version update.
    @State private var showChangelog = false
    @State private var changelogEntries: [ChangelogEntry] = []

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { geo in
                let screenWidth = geo.size.width

                ZStack {
                    ForEach(TabItem.allCases) { tab in
                        LazyTab(
                            tab: tab,
                            selectedTab: router.selectedTab,
                            dragOffset: dragOffset,
                            screenWidth: screenWidth,
                            loadedTabs: $loadedTabs
                        ) {
                            tabContent(for: tab)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
                .simultaneousGesture(
                    DragGesture(minimumDistance: 20)
                        .onChanged { value in
                            let h = value.translation.width
                            let v = value.translation.height

                            // Lock direction on first significant movement
                            if isDraggingHorizontally == nil && (abs(h) > 10 || abs(v) > 10) {
                                isDraggingHorizontally = abs(h) > abs(v) * 1.5
                            }
                            guard isDraggingHorizontally == true else { return }

                            let i = router.selectedTab.rawValue
                            // Rubber-band at edges (first/last tab)
                            if (i == 0 && h > 0) || (i == TabItem.allCases.count - 1 && h < 0) {
                                dragOffset = h * 0.3
                            } else {
                                dragOffset = h
                            }
                        }
                        .onEnded { value in
                            let wasHorizontal = isDraggingHorizontally == true
                            isDraggingHorizontally = nil

                            guard wasHorizontal else { return }

                            let h = value.translation.width
                            let velocity = value.predictedEndTranslation.width
                            let threshold = screenWidth * 0.25
                            let current = router.selectedTab.rawValue
                            var target = current

                            if h < -threshold || velocity < -screenWidth * 0.5 {
                                target = min(current + 1, TabItem.allCases.count - 1)
                            } else if h > threshold || velocity > screenWidth * 0.5 {
                                target = max(current - 1, 0)
                            }

                            if let newTab = TabItem(rawValue: target), newTab != router.selectedTab {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.86)) {
                                    router.selectedTab = newTab
                                    dragOffset = 0
                                }
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            } else {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.86)) {
                                    dragOffset = 0
                                }
                            }
                        }
                )
            }

            CustomTabBar(
                selectedTab: $router.selectedTab,
                unreadNotificationCount: router.unreadNotificationCount,
                onTabSelected: { newTab in
                    if newTab == router.selectedTab {
                        router.popToRoot()
                    } else {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.86)) {
                            router.selectedTab = newTab
                            dragOffset = 0
                        }
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                }
            )
        }
        .ignoresSafeArea(.keyboard)
        .dynamicTypeSize(...DynamicTypeSize.accessibility3)
        .onChange(of: router.selectedTab) { newTab in
            // Preload adjacent tabs so peeking during drag shows content
            let i = newTab.rawValue
            if i > 0, let prev = TabItem(rawValue: i - 1) { loadedTabs.insert(prev) }
            if i < TabItem.allCases.count - 1, let next = TabItem(rawValue: i + 1) { loadedTabs.insert(next) }
        }
        .onReceive(
            dependencies.activeDatabase.notificationDao.observeUnreadCount()
                .replaceError(with: 0)
                .receive(on: DispatchQueue.main)
                .removeDuplicates()
        ) { count in
            router.unreadNotificationCount = count
        }
        .onAppear {
            let currentBuild = Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0") ?? 0
            let lastSeen = dependencies.userPreferences.lastSeenBuildNumber
            if lastSeen > 0 && currentBuild > lastSeen {
                let newEntries = ChangelogData.getNewEntries(since: lastSeen)
                if !newEntries.isEmpty {
                    changelogEntries = newEntries
                    showChangelog = true
                }
            }
            dependencies.userPreferences.lastSeenBuildNumber = currentBuild
        }
        .sheet(isPresented: $showChangelog) {
            ChangelogView(entries: changelogEntries)
        }
    }

    @ViewBuilder
    private func tabContent(for tab: TabItem) -> some View {
        switch tab {
        case .dashboard: dashboardTab
        case .portfolio: portfolioTab
        case .notifications: notificationsTab
        case .settings: settingsTab
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
/// (but offset off-screen) once created. This avoids computing all 4 heavy
/// view hierarchies (NavigationStack + ViewModel + DB observations) on startup.
///
/// Uses horizontal offset positioning for continuous page-tracking during drag,
/// matching the Android HorizontalPager experience.
private struct LazyTab<Content: View>: View {
    let tab: TabItem
    let selectedTab: TabItem
    let dragOffset: CGFloat
    let screenWidth: CGFloat
    @Binding var loadedTabs: Set<TabItem>
    @ViewBuilder let content: () -> Content

    private var isSelected: Bool { tab == selectedTab }

    private var xOffset: CGFloat {
        CGFloat(tab.rawValue - selectedTab.rawValue) * screenWidth + dragOffset
    }

    var body: some View {
        Group {
            if loadedTabs.contains(tab) {
                content()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .offset(x: xOffset)
        .allowsHitTesting(isSelected && dragOffset == 0)
        .accessibilityHidden(!isSelected)
        .onChange(of: selectedTab) { newTab in
            if newTab == tab {
                loadedTabs.insert(tab)
            }
        }
    }
}
