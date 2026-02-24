import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: DashboardViewModel
    @Environment(\.isSandboxMode) var isSandboxMode

    init() {
        // ViewModel will be properly initialized in onAppear
        _viewModel = StateObject(wrappedValue: DashboardViewModel(
            dependencies: AppDependencies()
        ))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                if isSandboxMode {
                    SandboxBanner()
                }

                // Holdings pager
                if !viewModel.holdings.isEmpty {
                    holdingsPager
                }

                // Active plans
                plansSection

                // Quick actions
                quickActions
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.xxl)
        }
        .background(Color.backgroundDark)
        .navigationTitle("Dashboard")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    router.navigate(to: .addPlan)
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .refreshable {
            viewModel.loadData()
        }
        .onAppear {
            viewModel.loadData()
        }
        .sheet(isPresented: $viewModel.showRunNowSheet) {
            runNowSheet
        }
    }

    // MARK: - Holdings Pager

    private var holdingsPager: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("Holdings")
                .font(AccBotFonts.titleSmall)
                .foregroundColor(.white)

            TabView {
                ForEach(viewModel.holdings) { holding in
                    holdingCard(holding)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .automatic))
            .frame(height: 160)
        }
    }

    private func holdingCard(_ holding: DashboardViewModel.HoldingInfo) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text(holding.id)
                    .font(AccBotFonts.headline)
                    .foregroundColor(.white)
                Spacer()
                Text("\(holding.transactionCount) txns")
                    .font(AccBotFonts.caption)
                    .foregroundColor(.onSurfaceVariantColor)
            }

            HStack {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Amount")
                        .font(AccBotFonts.caption)
                        .foregroundColor(.onSurfaceVariantColor)
                    Text(formatCrypto(holding.totalCrypto, symbol: holding.crypto))
                        .font(AccBotFonts.body)
                        .foregroundColor(.white)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: Spacing.xs) {
                    Text("Invested")
                        .font(AccBotFonts.caption)
                        .foregroundColor(.onSurfaceVariantColor)
                    Text(formatFiat(holding.totalInvested, symbol: holding.fiat))
                        .font(AccBotFonts.body)
                        .foregroundColor(.white)
                }
            }

            HStack {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Avg Price")
                        .font(AccBotFonts.caption)
                        .foregroundColor(.onSurfaceVariantColor)
                    Text(formatFiat(holding.avgPrice, symbol: holding.fiat))
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(.white)
                }

                Spacer()

                if let roi = holding.roi {
                    VStack(alignment: .trailing, spacing: Spacing.xs) {
                        Text("ROI")
                            .font(AccBotFonts.caption)
                            .foregroundColor(.onSurfaceVariantColor)
                        Text(String(format: "%+.1f%%", NSDecimalNumber(decimal: roi).doubleValue))
                            .font(AccBotFonts.headline)
                            .foregroundColor(roi >= 0 ? .accentTeal : .errorRed)
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Plans Section

    private var plansSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                Text("Active Plans")
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(.white)
                Spacer()
                Text("\(viewModel.plans.filter { $0.isEnabled }.count) active")
                    .font(AccBotFonts.caption)
                    .foregroundColor(.onSurfaceVariantColor)
            }

            if viewModel.plans.isEmpty {
                EmptyStateView(
                    icon: "chart.bar.doc.horizontal",
                    title: "No DCA Plans",
                    subtitle: "Create your first plan to start accumulating crypto"
                )
                .onTapGesture {
                    router.navigate(to: .addPlan)
                }
            } else {
                LazyVStack(spacing: Spacing.sm) {
                    ForEach(viewModel.plans) { plan in
                        PlanCard(
                            plan: plan,
                            onTap: {
                                router.navigate(to: .planDetails(plan.id))
                            },
                            onToggle: { enabled in
                                viewModel.togglePlan(plan, enabled: enabled)
                            }
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
                Label("History", systemImage: "clock.arrow.circlepath")
                    .font(AccBotFonts.label)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(Color.surfaceDark)
                    .foregroundColor(.white)
                    .cornerRadius(CornerRadius.sm)
            }

            Button {
                viewModel.showRunNowSheet = true
            } label: {
                Label("Run Now", systemImage: "play.fill")
                    .font(AccBotFonts.label)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(isSandboxMode ? Color.sandboxPrimary : Color.accentTeal)
                    .foregroundColor(Color.surfaceDark)
                    .cornerRadius(CornerRadius.sm)
            }
        }
    }

    // MARK: - Run Now Sheet

    private var runNowSheet: some View {
        NavigationStack {
            VStack(spacing: Spacing.lg) {
                Text("Select plans to execute now")
                    .font(AccBotFonts.body)
                    .foregroundColor(.onSurfaceVariantColor)

                List {
                    ForEach(viewModel.plans.filter { $0.isEnabled }) { plan in
                        HStack {
                            Image(systemName: viewModel.selectedPlanIds.contains(plan.id)
                                  ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(viewModel.selectedPlanIds.contains(plan.id)
                                                 ? .accentTeal : .onSurfaceVariantColor)

                            VStack(alignment: .leading) {
                                Text(plan.pair)
                                    .font(AccBotFonts.headline)
                                Text("\(plan.amount) \(plan.fiat) · \(plan.exchange.displayName)")
                                    .font(AccBotFonts.caption)
                                    .foregroundColor(.onSurfaceVariantColor)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if viewModel.selectedPlanIds.contains(plan.id) {
                                viewModel.selectedPlanIds.remove(plan.id)
                            } else {
                                viewModel.selectedPlanIds.insert(plan.id)
                            }
                        }
                        .listRowBackground(Color.surfaceDark)
                    }
                }
                .listStyle(.plain)

                Button {
                    viewModel.runSelectedPlans()
                } label: {
                    if viewModel.isRunning {
                        ProgressView()
                            .tint(.surfaceDark)
                    } else {
                        Text("Run \(viewModel.selectedPlanIds.count) Plans")
                    }
                }
                .font(AccBotFonts.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(viewModel.selectedPlanIds.isEmpty ? Color.onSurfaceVariantColor : Color.accentTeal)
                .foregroundColor(.surfaceDark)
                .cornerRadius(CornerRadius.sm)
                .disabled(viewModel.selectedPlanIds.isEmpty || viewModel.isRunning)
                .padding(.horizontal, Spacing.lg)
            }
            .background(Color.backgroundDark)
            .navigationTitle("Run Now")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        viewModel.showRunNowSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Formatting

    private func formatCrypto(_ value: Decimal, symbol: String) -> String {
        let number = NSDecimalNumber(decimal: value)
        let formatter = NumberFormatter()
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 8
        formatter.numberStyle = .decimal
        return "\(formatter.string(from: number) ?? "0") \(symbol)"
    }

    private func formatFiat(_ value: Decimal, symbol: String) -> String {
        let number = NSDecimalNumber(decimal: value)
        let formatter = NumberFormatter()
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.numberStyle = .decimal
        return "\(formatter.string(from: number) ?? "0") \(symbol)"
    }
}
