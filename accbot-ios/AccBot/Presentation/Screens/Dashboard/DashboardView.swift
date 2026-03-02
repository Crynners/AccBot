import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = DashboardViewModel()
    @Environment(\.accBotColors) private var colors
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var showRunConfirmation = false

    private var isLandscape: Bool {
        verticalSizeClass == .compact
    }

    var body: some View {
        Group {
            if viewModel.isLoading {
                LoadingStateView(message: "Loading dashboard...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(colors.background)
            } else {
                ScrollView {
                    Group {
                        if isLandscape {
                            landscapeLayout
                        } else {
                            portraitLayout
                        }
                    }
                    .maxFormWidth()
                }
                .background(colors.background)
                .refreshable {
                    await viewModel.loadDataAsync()
                }
                .sheet(isPresented: $viewModel.showRunNowSheet) {
                    runNowSheet
                }
                .alert(String(localized: "Error"), isPresented: Binding(
                    get: { viewModel.errorMessage != nil },
                    set: { if !$0 { viewModel.errorMessage = nil } }
                )) {
                    Button(String(localized: "OK"), role: .cancel) {}
                } message: {
                    if let msg = viewModel.errorMessage {
                        Text(msg)
                    }
                }
                .alert(String(localized: "Execution Complete"), isPresented: Binding(
                    get: { viewModel.runResultMessage != nil },
                    set: { if !$0 { viewModel.runResultMessage = nil } }
                )) {
                    Button(String(localized: "OK"), role: .cancel) {}
                } message: {
                    if let msg = viewModel.runResultMessage {
                        Text(msg)
                    }
                }
            }
        }
        .navigationTitle(String(localized: "Dashboard"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                AccBotHeaderLogo(isSandbox: colors.isSandbox)
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    router.navigate(to: .addPlan)
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel(String(localized: "Add DCA plan"))
            }
        }
        .onAppear {
            viewModel.setup(dependencies)
        }
    }

    // MARK: - Portrait Layout

    private var portraitLayout: some View {
        VStack(spacing: Spacing.lg) {
            if colors.isSandbox {
                SandboxBanner()
            }

            if viewModel.showMarketPulse && (viewModel.fearGreedValue != nil || !viewModel.athData.isEmpty) {
                marketPulseCard
            }

            if viewModel.holdings.isEmpty {
                holdingsEmptyState
            } else {
                holdingsPager
            }

            plansSection
            quickActions
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Landscape Layout (two-column)

    private var landscapeLayout: some View {
        VStack(spacing: Spacing.sm) {
            if colors.isSandbox {
                SandboxBanner()
                    .padding(.horizontal, Spacing.lg)
            }

            if viewModel.showMarketPulse && (viewModel.fearGreedValue != nil || !viewModel.athData.isEmpty) {
                marketPulseCard
                    .padding(.horizontal, Spacing.lg)
            }

            HStack(alignment: .top, spacing: Spacing.lg) {
                // Left column: Holdings + Quick Actions
                VStack(spacing: Spacing.lg) {
                    if viewModel.holdings.isEmpty {
                        holdingsEmptyState
                    } else {
                        holdingsPager
                    }
                    quickActions
                }
                .frame(maxWidth: .infinity)

                // Right column: DCA Plans
                VStack(spacing: Spacing.lg) {
                    plansSection
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.xxl)
        }
    }

    // MARK: - Holdings Empty State

    private var holdingsEmptyState: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(String(localized: "Total Accumulated"))
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)
                .accessibilityAddTraits(.isHeader)

            EmptyStateView(
                systemImage: "chart.pie",
                title: String(localized: "No Holdings Yet"),
                subtitle: String(localized: "Your portfolio will appear here after your first DCA purchase")
            )

            if !viewModel.plans.isEmpty {
                Button {
                    let exchanges = Set(viewModel.plans.map(\.exchange))
                    if exchanges.count == 1, let exchange = exchanges.first {
                        router.navigate(to: .exchangeDetail(exchange))
                    } else {
                        router.navigate(to: .exchangeManagement)
                    }
                } label: {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "icloud.and.arrow.down")
                        Text(String(localized: "Import via API"))
                    }
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .overlay(
                        RoundedRectangle(cornerRadius: CornerRadius.sm)
                            .stroke(colors.primary, lineWidth: 1.5)
                    )
                }
            }
        }
    }

    // MARK: - Holdings Pager

    private var holdingsPager: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text(String(localized: "Total Accumulated"))
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.onSurface)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button {
                    viewModel.refreshPrices()
                } label: {
                    if viewModel.isRefreshingPrices {
                        ProgressView()
                            .frame(width: 18, height: 18)
                    } else {
                        Image(systemName: "arrow.clockwise")
                            .font(AccBotFonts.label)
                    }
                }
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
                .foregroundStyle(colors.primary)
                .disabled(viewModel.isRefreshingPrices)
                .accessibilityLabel(String(localized: "Refresh prices"))
            }

            TabView {
                ForEach(viewModel.holdings) { holding in
                    Button {
                        router.portfolioSelectedCrypto = holding.crypto
                        router.portfolioSelectedFiat = holding.fiat
                        router.selectedTab = .portfolio
                    } label: {
                        holdingCard(holding)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(localized: "\(holding.crypto)/\(holding.fiat) holding"))
                    .accessibilityHint(String(localized: "Tap to view in portfolio"))
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
            .frame(minHeight: 220)
        }
    }

    private func holdingCard(_ holding: DashboardViewModel.HoldingInfo) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text(holding.id)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
                Spacer()
                Text(String(localized: "\(holding.transactionCount) txns"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            HStack {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Amount"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text(formatCrypto(holding.totalCrypto, symbol: holding.crypto))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurface)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: Spacing.xs) {
                    Text(String(localized: "Invested"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text(formatFiat(holding.totalInvested, symbol: holding.fiat))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurface)
                }
            }

            HStack {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Avg Price"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text(formatFiat(holding.avgPrice, symbol: holding.fiat))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurface)
                }

                Spacer()

                if let currentPrice = holding.currentPrice {
                    VStack(alignment: .trailing, spacing: Spacing.xs) {
                        Text(String(localized: "Current Price"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                        Text(formatFiat(currentPrice, symbol: holding.fiat))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurface)
                    }
                }
            }

            if let roi = holding.roi {
                HStack {
                    Spacer()
                    VStack(alignment: .trailing, spacing: Spacing.xs) {
                        HStack(spacing: Spacing.xs) {
                            Text(String(localized: "ROI"))
                                .font(AccBotFonts.caption)
                                .foregroundStyle(colors.onSurfaceVariant)
                            Text(roi >= 0 ? String(localized: "Gain") : String(localized: "Loss"))
                                .font(AccBotFonts.captionSmall)
                                .foregroundStyle(roi >= 0 ? colors.success : colors.error)
                        }
                        if let fiatGainLoss = holding.fiatGainLoss {
                            let sign = fiatGainLoss >= 0 ? "+" : "-"
                            let fiatPart = "\(sign)\(formatFiatValue(abs(fiatGainLoss))) \(holding.fiat)"
                            let pctPart = AccBotFormatters.formatSignedPercent(roi)
                            Text("\(fiatPart) (\(pctPart))")
                                .font(AccBotFonts.headline)
                                .foregroundStyle(roi >= 0 ? colors.success : colors.error)
                        } else {
                            Text(AccBotFormatters.formatSignedPercent(roi))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(roi >= 0 ? colors.success : colors.error)
                        }
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Market Pulse Card

    private var marketPulseCard: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            // Header
            Button {
                withAnimation(.easeInOut(duration: 0.25)) {
                    viewModel.toggleMarketPulseExpanded()
                }
            } label: {
                HStack {
                    Text(String(localized: "Market Pulse"))
                        .font(AccBotFonts.titleSmall)
                        .foregroundStyle(colors.onSurface)
                    Spacer()
                    Image(systemName: viewModel.isMarketPulseExpanded ? "chevron.up" : "chevron.down")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(viewModel.isMarketPulseExpanded
                ? String(localized: "Double tap to collapse")
                : String(localized: "Double tap to expand"))

            // Gauge area (single shared gauge matching Android layout)
            VStack(spacing: 0) {
                // Expanded: F&G labels above the bar
                if let fgValue = viewModel.fearGreedValue, viewModel.isMarketPulseExpanded {
                    VStack(spacing: Spacing.xs) {
                        Text(String(localized: "Fear & Greed"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .frame(maxWidth: .infinity)

                        HStack {
                            Text(String(localized: "Fear"))
                                .font(AccBotFonts.captionSmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                            Spacer()
                            Text("\(fgValue) — \(viewModel.fearGreedLabel ?? "")")
                                .font(AccBotFonts.headline)
                                .foregroundStyle(FearGreedColors.color(for: fgValue))
                            Spacer()
                            Text(String(localized: "Greed"))
                                .font(AccBotFonts.captionSmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                    }
                    .padding(.bottom, Spacing.xs)
                }

                // ▼ F&G triangle (above bar, pointing down) — matches Android line 1297-1314
                if let fgValue = viewModel.fearGreedValue {
                    GeometryReader { _ in
                        Canvas { context, size in
                            let w: CGFloat = 8, h: CGFloat = 6
                            let x = CGFloat(fgValue) / 100.0 * size.width
                            var path = Path()
                            path.move(to: CGPoint(x: x - w / 2, y: size.height - h))
                            path.addLine(to: CGPoint(x: x + w / 2, y: size.height - h))
                            path.addLine(to: CGPoint(x: x, y: size.height))
                            path.closeSubpath()
                            context.fill(path, with: .color(colors.onSurface))
                        }
                    }
                    .frame(height: 8)
                }

                // 5 colored segments (shared bar — always visible)
                HStack(spacing: 2) {
                    ForEach(FearGreedColors.gaugeColors.indices, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 2)
                            .fill(FearGreedColors.gaugeColors[i])
                            .frame(height: 8)
                    }
                }

                // ▲ ATH triangle(s) (below bar, pointing up) — matches Android line 1331-1350
                if !viewModel.athData.isEmpty {
                    GeometryReader { _ in
                        Canvas { context, size in
                            let w: CGFloat = 8, h: CGFloat = 6
                            for info in viewModel.athData {
                                let x = CGFloat(100 - info.athDistancePercent) / 100.0 * size.width
                                var path = Path()
                                path.move(to: CGPoint(x: x, y: 0))
                                path.addLine(to: CGPoint(x: x - w / 2, y: h))
                                path.addLine(to: CGPoint(x: x + w / 2, y: h))
                                path.closeSubpath()
                                context.fill(path, with: .color(colors.onSurface))
                            }
                        }
                    }
                    .frame(height: 8)
                }

                // Expanded: ATH labels below the bar
                if !viewModel.athData.isEmpty, viewModel.isMarketPulseExpanded {
                    VStack(spacing: Spacing.xs) {
                        let athCenterText: String = {
                            if viewModel.athData.count == 1, let info = viewModel.athData.first {
                                return "-\(info.athDistancePercent) %"
                            } else {
                                return viewModel.athData
                                    .map { "\($0.crypto) -\($0.athDistancePercent) %" }
                                    .joined(separator: ", ")
                            }
                        }()

                        HStack {
                            Text("0")
                                .font(AccBotFonts.captionSmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                            Spacer()
                            Text(athCenterText)
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.onSurfaceVariant)
                            Spacer()
                            Text(String(localized: "ATH"))
                                .font(AccBotFonts.captionSmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }

                        Text(String(localized: "ATH Distance"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(.top, Spacing.xs)
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        .animation(.easeInOut(duration: 0.25), value: viewModel.isMarketPulseExpanded)
    }

    // MARK: - Plans Section

    private var plansSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text(String(localized: "My DCA Plans"))
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.onSurface)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Button {
                    router.navigate(to: .addPlan)
                } label: {
                    Image(systemName: "plus")
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.primary)
                        .frame(width: 28, height: 28)
                        .background(colors.primary.opacity(0.15))
                        .clipShape(Circle())
                }
                .accessibilityLabel(String(localized: "Add DCA plan"))
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
            }

            if viewModel.plans.isEmpty {
                VStack(spacing: Spacing.md) {
                    EmptyStateView(
                        systemImage: "plus.circle",
                        title: String(localized: "No plans yet"),
                        subtitle: String(localized: "Create your first DCA plan to start accumulating crypto")
                    )

                    Button {
                        router.navigate(to: .addPlan)
                    } label: {
                        Text(String(localized: "Create DCA Plan"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.md)
                            .background(colors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }
                }
            } else {
                LazyVStack(spacing: Spacing.sm) {
                    ForEach(viewModel.plansWithBalance) { pwb in
                        PlanCard(
                            plan: pwb.plan,
                            onTap: {
                                router.navigate(to: .planDetails(pwb.plan.id))
                            },
                            onToggle: { enabled in
                                viewModel.togglePlan(pwb.plan, enabled: enabled)
                            },
                            balanceDuration: formatBalanceDuration(pwb),
                            isLowBalance: pwb.isLowBalance,
                            withdrawalReady: pwb.isOverWithdrawalThreshold,
                            withdrawalBalanceText: pwb.isOverWithdrawalThreshold
                                ? pwb.exchangeCryptoBalance.map {
                                    formatCryptoBalance($0, symbol: pwb.plan.crypto)
                                }
                                : nil,
                            goalProgress: goalProgress(for: pwb),
                            goalText: goalText(for: pwb),
                            goalReached: goalReached(for: pwb)
                        )
                    }
                }
            }
        }
    }

    // MARK: - Quick Actions

    private var quickActions: some View {
        HStack(spacing: Spacing.md) {
            Button {
                router.navigate(to: .history())
            } label: {
                Label(String(localized: "History"), systemImage: "clock.arrow.circlepath")
                    .font(AccBotFonts.label)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.vertical, Spacing.md)
                    .background(colors.surface)
                    .foregroundStyle(colors.onSurface)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            }

            Button {
                viewModel.showRunNowSheet = true
            } label: {
                Label(String(localized: "Run Now"), systemImage: "bolt.fill")
                    .font(AccBotFonts.label)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.vertical, Spacing.md)
                    .background(colors.primary)
                    .foregroundStyle(colors.onPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            }
        }
    }

    // MARK: - Run Now Sheet

    private var runNowSheet: some View {
        VStack(spacing: 0) {
            // Header bar (replaces nested NavigationStack)
            HStack {
                Button(String(localized: "Cancel")) {
                    viewModel.showRunNowSheet = false
                }
                .foregroundStyle(colors.primary)
                Spacer()
                Text(String(localized: "Run Now"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
                Spacer()
                // Spacer for symmetry
                Color.clear.frame(width: 60, height: 0)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.md)

            Divider()

            VStack(spacing: Spacing.lg) {
                Text(String(localized: "Select plans to execute now"))
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurfaceVariant)

                List {
                    // Select All row
                    Button {
                        toggleSelectAll()
                    } label: {
                        HStack {
                            Image(systemName: allPlansSelected ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(allPlansSelected ? colors.primary : colors.onSurfaceVariant)
                            Text(String(localized: "Select all plans"))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.onSurface)
                            Spacer()
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityValue(allPlansSelected ? String(localized: "Selected") : String(localized: "Not selected"))
                    .listRowBackground(colors.surface)

                    Divider()

                    ForEach(viewModel.plans.filter { $0.isEnabled }) { plan in
                        Button {
                            if viewModel.selectedPlanIds.contains(plan.id) {
                                viewModel.selectedPlanIds.remove(plan.id)
                            } else {
                                viewModel.selectedPlanIds.insert(plan.id)
                            }
                        } label: {
                            HStack {
                                Image(systemName: viewModel.selectedPlanIds.contains(plan.id)
                                      ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(viewModel.selectedPlanIds.contains(plan.id)
                                                     ? colors.primary : colors.onSurfaceVariant)

                                VStack(alignment: .leading) {
                                    HStack(spacing: Spacing.sm) {
                                        Text(plan.pair)
                                            .font(AccBotFonts.headline)
                                            .foregroundStyle(colors.onSurface)
                                        Text(plan.strategy.displayName)
                                            .font(AccBotFonts.caption)
                                            .italic()
                                            .foregroundStyle(colors.primary)
                                    }
                                    Text("\(plan.amount) \(plan.fiat) · \(plan.exchange.displayName)")
                                        .font(AccBotFonts.caption)
                                        .foregroundStyle(colors.onSurfaceVariant)
                                        .lineLimit(1)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(colors.surface)
                        .accessibilityValue(viewModel.selectedPlanIds.contains(plan.id) ? String(localized: "Selected") : String(localized: "Not selected"))
                    }
                }
                .listStyle(.plain)

                Button {
                    showRunConfirmation = true
                } label: {
                    if viewModel.isRunning {
                        ProgressView()
                            .tint(colors.onPrimary)
                    } else {
                        Text(String(localized: "Run \(viewModel.selectedPlanIds.count) Plans"))
                    }
                }
                .font(AccBotFonts.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(viewModel.selectedPlanIds.isEmpty ? colors.disabledBackground : colors.primary)
                .foregroundStyle(viewModel.selectedPlanIds.isEmpty ? colors.disabledForeground : colors.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                .disabled(viewModel.selectedPlanIds.isEmpty || viewModel.isRunning)
                .accessibilityHint(viewModel.selectedPlanIds.isEmpty
                    ? String(localized: "Select at least one plan first")
                    : "")
                .padding(.horizontal, Spacing.lg)
                .alert(String(localized: "Confirm Execution"), isPresented: $showRunConfirmation) {
                    Button(String(localized: "Cancel"), role: .cancel) {}
                    Button(String(localized: "Run Now")) {
                        viewModel.runSelectedPlans()
                    }
                } message: {
                    Text(String(localized: "Are you sure you want to execute \(viewModel.selectedPlanIds.count) plans? This will place real orders on the exchange."))
                }
            }
        }
        .background(colors.background)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    // MARK: - Select All

    private var allPlansSelected: Bool {
        let enabledIds = Set(viewModel.plans.filter { $0.isEnabled }.map(\.id))
        return !enabledIds.isEmpty && enabledIds.isSubset(of: viewModel.selectedPlanIds)
    }

    private func toggleSelectAll() {
        let enabledIds = Set(viewModel.plans.filter { $0.isEnabled }.map(\.id))
        if allPlansSelected {
            viewModel.selectedPlanIds.subtract(enabledIds)
        } else {
            viewModel.selectedPlanIds.formUnion(enabledIds)
        }
    }

    // MARK: - Formatting

    private func formatCrypto(_ value: Decimal, symbol: String) -> String {
        AccBotFormatters.formatCrypto(value, symbol: symbol)
    }

    private func formatFiat(_ value: Decimal, symbol: String) -> String {
        AccBotFormatters.formatFiat(value, symbol: symbol)
    }

    private func formatFiatValue(_ value: Decimal) -> String {
        AccBotFormatters.formatFiatValue(value)
    }

    private func formatCryptoBalance(_ value: Decimal, symbol: String) -> String {
        AccBotFormatters.formatCrypto(value, symbol: symbol)
    }

    private func formatBalanceDuration(_ pwb: DashboardViewModel.PlanWithBalance) -> String? {
        guard pwb.plan.isEnabled, let days = pwb.remainingDays else { return nil }
        let daysText = formatRemainingDays(days)
        if let exec = pwb.remainingExecutions {
            return String(localized: "~\(daysText) remaining (\(exec) exec)")
        }
        return String(localized: "~\(daysText) remaining")
    }

    private func formatRemainingDays(_ days: Double) -> String {
        if days < 1 {
            let hours = Int(days * 24)
            if hours <= 0 {
                return String(localized: "<1 hour")
            }
            return String(localized: "\(hours)h")
        } else if days < 2 {
            return String(localized: "1 day")
        } else {
            return String(localized: "\(Int(days)) days")
        }
    }

    // MARK: - Goal Progress

    private func goalProgress(for pwb: DashboardViewModel.PlanWithBalance) -> Double? {
        guard let target = pwb.plan.targetAmount, target > 0,
              let accumulated = pwb.accumulatedCrypto else { return nil }
        return min(Double(truncating: accumulated as NSDecimalNumber) / Double(truncating: target as NSDecimalNumber), 1.0)
    }

    private func goalText(for pwb: DashboardViewModel.PlanWithBalance) -> String? {
        guard let target = pwb.plan.targetAmount, target > 0,
              let accumulated = pwb.accumulatedCrypto else { return nil }
        let crypto = pwb.plan.crypto
        let accStr = AccBotFormatters.formatCrypto(accumulated, symbol: crypto)
        let targetStr = AccBotFormatters.formatCrypto(target, symbol: crypto)
        if accumulated >= target {
            return String(localized: "Goal reached! \(accStr) / \(targetStr)")
        }
        let pct = Int((Double(truncating: accumulated as NSDecimalNumber) / Double(truncating: target as NSDecimalNumber)) * 100)
        return "\(accStr) / \(targetStr) (\(pct)%)"
    }

    private func goalReached(for pwb: DashboardViewModel.PlanWithBalance) -> Bool {
        guard let target = pwb.plan.targetAmount, target > 0,
              let accumulated = pwb.accumulatedCrypto else { return false }
        return accumulated >= target
    }
}

// MARK: - AccBot Header Logo

/// Custom header matching Android's "Acc₿ot" logo with app icon overlay on the ₿ character.
struct AccBotHeaderLogo: View {
    var isSandbox: Bool = false
    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(spacing: 0) {
            Text("Acc")
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.onBackground)

            AppLogoIcon(accent: colors.primary, size: 32, centerColor: colors.background)

            Text("ot")
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.onBackground)
        }
    }
}

// MARK: - App Logo Icon (Vector reproduction of ic_launcher_foreground)

/// SwiftUI reproduction of the Android vector app icon.
struct AppLogoIcon: View {
    var accent: Color = .accentTeal
    var size: CGFloat = 32
    var centerColor: Color = .backgroundDark

    var body: some View {
        Canvas { context, canvasSize in
            let s = canvasSize.width / 108 // scale factor (Android viewport = 108x108)
            let center = CGPoint(x: 54 * s, y: 54 * s)

            // Outer ring (r=35.86)
            let outerRing = Path(ellipseIn: CGRect(
                x: (54 - 35.86) * s, y: (54 - 35.86) * s,
                width: 71.72 * s, height: 71.72 * s
            ))
            context.stroke(outerRing, with: .color(accent.opacity(0.3)), lineWidth: 1.69 * s)

            // Inner circle fill (r=29.53)
            let innerCircle = Path(ellipseIn: CGRect(
                x: (54 - 29.53) * s, y: (54 - 29.53) * s,
                width: 59.06 * s, height: 59.06 * s
            ))
            context.fill(innerCircle, with: .color(accent.opacity(0.05)))
            context.stroke(innerCircle, with: .color(accent), lineWidth: 0.84 * s)

            // Bar chart (clipped to inner circle r≈29.11)
            let clipCircle = Path(ellipseIn: CGRect(
                x: (54 - 29.11) * s, y: (54 - 29.11) * s,
                width: 58.22 * s, height: 58.22 * s
            ))

            context.clipToLayer(opacity: 1) { clipped in
                clipped.clip(to: clipCircle)

                let barWidth: CGFloat = 8.01 * s
                let cornerR: CGFloat = 1.27 * s
                let bars: [(x: CGFloat, top: CGFloat, opacity: Double)] = [
                    (27.85, 59.06, 0.3),
                    (42.61, 48.52, 0.5),
                    (57.38, 35.86, 0.7),
                    (72.15, 21.09, 0.9),
                ]
                let barBottom: CGFloat = 101.25 * s

                for bar in bars {
                    let rect = CGRect(
                        x: bar.x * s, y: bar.top * s,
                        width: barWidth, height: barBottom - bar.top * s
                    )
                    let barPath = Path(roundedRect: rect, cornerRadius: cornerR)
                    clipped.fill(barPath, with: .color(accent.opacity(bar.opacity)))
                }
            }

            // Central dark circle (r=17.93)
            let darkCircle = Path(ellipseIn: CGRect(
                x: (54 - 17.93) * s, y: (54 - 17.93) * s,
                width: 35.86 * s, height: 35.86 * s
            ))
            context.fill(darkCircle, with: .color(centerColor))
            context.stroke(darkCircle, with: .color(accent), lineWidth: 0.84 * s)

            // Inner dashed circle (r=15.19)
            let dashedCircle = Path(ellipseIn: CGRect(
                x: (54 - 15.19) * s, y: (54 - 15.19) * s,
                width: 30.38 * s, height: 30.38 * s
            ))
            context.stroke(dashedCircle, with: .color(accent.opacity(0.6)), lineWidth: 0.42 * s)

            // Bitcoin symbol — vertical strokes
            let vertStrokes: [(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat)] = [
                (49, 40, 2, 3), (53.5, 40, 2, 3), // top
                (49, 65, 2, 3), (53.5, 65, 2, 3), // bottom
            ]
            for vs in vertStrokes {
                let rect = CGRect(x: vs.x * s, y: vs.y * s, width: vs.w * s, height: vs.h * s)
                context.fill(Path(rect), with: .color(.white))
            }

            // Bitcoin B body (simplified)
            var bBody = Path()
            // Main body rectangle
            bBody.addRoundedRect(in: CGRect(x: 46 * s, y: 43 * s, width: 17 * s, height: 22 * s), cornerSize: CGSize(width: 2 * s, height: 2 * s))
            context.fill(bBody, with: .color(.white))

            // Top bump (upper lobe)
            let topLobe = Path(ellipseIn: CGRect(x: 53 * s, y: 43 * s, width: 9 * s, height: 11 * s))
            context.fill(topLobe, with: .color(.white))

            // Bottom bump (lower lobe, slightly wider)
            let bottomLobe = Path(ellipseIn: CGRect(x: 53 * s, y: 54 * s, width: 10 * s, height: 11 * s))
            context.fill(bottomLobe, with: .color(.white))

            // Counter holes (dark, to make the B shape)
            let topHole = Path(ellipseIn: CGRect(x: 50 * s, y: 46 * s, width: 9 * s, height: 5.5 * s))
            context.fill(topHole, with: .color(centerColor))

            let bottomHole = Path(ellipseIn: CGRect(x: 50 * s, y: 56.5 * s, width: 10 * s, height: 6 * s))
            context.fill(bottomHole, with: .color(centerColor))

            _ = center // suppress unused warning
        }
        .frame(width: size, height: size)
    }
}
