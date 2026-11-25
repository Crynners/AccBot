import SwiftUI

/// Read-only detail of one shared plan, with refresh for live-update shares.
@MainActor
final class SharedPlanDetailViewModel: ObservableObject {
    @Published private(set) var plan: SharedPlan?
    @Published private(set) var currentPrice: Decimal?
    @Published private(set) var refreshing = false
    @Published var message: String?

    let shareId: String
    private var dependencies: AppDependencies?
    private var isSetUp = false

    init(shareId: String) {
        self.shareId = shareId
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        plan = SharedPlanStore.shared.get(shareId)
        guard let plan else { return }
        Task { currentPrice = await dependencies.marketDataService.getCurrentPrice(crypto: plan.crypto, fiat: plan.fiat) }
        if plan.liveUpdates { refresh() }
    }

    func refresh() {
        guard let dependencies, let plan, plan.liveUpdates, !refreshing else { return }
        refreshing = true
        Task {
            switch await RefreshSharedPlanUseCase(dependencies: dependencies).execute(shareId: plan.shareId) {
            case .updated(let updated, let newTrades):
                self.plan = updated
                if newTrades > 0 {
                    message = String(localized: "\(newTrades) new trades")
                }
            case .failed(let failed):
                self.plan = failed
            case .keyNotReadOnly:
                self.plan = SharedPlanStore.shared.get(plan.shareId)
                message = String(localized: "The shared key now allows trading or withdrawals. AccBot removed it and stopped live updates.")
            case .notLive:
                self.plan = SharedPlanStore.shared.get(plan.shareId)
            }
            refreshing = false
        }
    }

    func delete() {
        SharedPlanStore.shared.delete(shareId)
    }
}

struct SharedPlanDetailView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @Environment(\.accBotColors) private var colors
    @StateObject private var viewModel: SharedPlanDetailViewModel
    @State private var showDeleteConfirmation = false

    init(shareId: String) {
        _viewModel = StateObject(wrappedValue: SharedPlanDetailViewModel(shareId: shareId))
    }

    var body: some View {
        Group {
            if let plan = viewModel.plan {
                content(plan)
            } else {
                EmptyStateView(
                    systemImage: "doc.questionmark",
                    title: String(localized: "This shared plan is no longer available.")
                )
            }
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Shared plan"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let plan = viewModel.plan {
                ToolbarItemGroup(placement: .primaryAction) {
                    if plan.liveUpdates {
                        if viewModel.refreshing {
                            ProgressView()
                        } else {
                            Button {
                                viewModel.refresh()
                            } label: {
                                Image(systemName: "arrow.clockwise")
                            }
                            .accessibilityLabel(String(localized: "Refresh"))
                        }
                    }
                    Button(role: .destructive) {
                        showDeleteConfirmation = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .refreshable { viewModel.refresh() }
        .confirmationDialog(
            String(localized: "Remove shared plan?"),
            isPresented: $showDeleteConfirmation,
            titleVisibility: .visible
        ) {
            Button(String(localized: "Remove"), role: .destructive) {
                viewModel.delete()
                router.pop()
            }
        } message: {
            Text(String(localized: "The snapshot is removed from this device. The sender’s plan is not affected."))
        }
        .alert(viewModel.message ?? "", isPresented: messageBinding) {
            Button(String(localized: "OK"), role: .cancel) {}
        }
        .onAppear { viewModel.setup(dependencies) }
    }

    private var messageBinding: Binding<Bool> {
        Binding(get: { viewModel.message != nil }, set: { if !$0 { viewModel.message = nil } })
    }

    private func content(_ plan: SharedPlan) -> some View {
        let summary = plan.summary
        return ScrollView {
            VStack(spacing: Spacing.lg) {
                banner(plan)
                card {
                    HStack(spacing: Spacing.md) {
                        CryptoIcon(symbol: plan.crypto, size: 44)
                        VStack(alignment: .leading, spacing: Spacing.xxs) {
                            Text(plan.displayName)
                                .font(AccBotFonts.titleSmall)
                                .foregroundStyle(colors.onSurface)
                            Text("\(plan.pair) · \(plan.exchangeDisplayName)")
                                .font(AccBotFonts.bodySmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                        Spacer()
                    }
                    if let amount = plan.amount {
                        row(String(localized: "Amount per purchase"), AccBotFormatters.formatFiat(amount, symbol: plan.fiat))
                    }
                    row(String(localized: "Schedule"), schedule(plan))
                    row(String(localized: "Strategy"), plan.strategy.displayName)
                    row(String(localized: "State when shared"), plan.isEnabled ? String(localized: "Active") : String(localized: "Paused"))
                    row(String(localized: "Created"), AccBotFormatters.mediumDate.string(from: plan.createdAt))
                }

                card {
                    Text(String(localized: "Summary"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)
                    row(String(localized: "Purchases"), "\(summary.buyCount)")
                    Group {
                        if summary.sellCount > 0 {
                            row(String(localized: "Sells"), "\(summary.sellCount)")
                        }
                        if let first = summary.firstTradeAt {
                            row(String(localized: "First purchase"), AccBotFormatters.mediumDate.string(from: first))
                        }
                        if let invested = summary.totalInvested {
                            row(String(localized: "Invested"), AccBotFormatters.formatFiat(invested, symbol: plan.fiat))
                        }
                        if let held = summary.heldCrypto {
                            row(String(localized: "Held"), AccBotFormatters.formatCrypto(held, symbol: plan.crypto))
                        }
                        if let sold = summary.totalSoldFiat, summary.sellCount > 0 {
                            row(String(localized: "Sold for"), AccBotFormatters.formatFiat(sold, symbol: plan.fiat))
                        }
                    }
                    Group {
                        if let avg = summary.avgBuyPrice {
                            row(String(localized: "Average buy price"), AccBotFormatters.formatFiat(avg, symbol: plan.fiat))
                        }
                        if let price = viewModel.currentPrice {
                            row(String(localized: "Current price"), AccBotFormatters.formatFiat(price, symbol: plan.fiat))
                        }
                        if let value = summary.currentValue(currentPrice: viewModel.currentPrice) {
                            row(String(localized: "Current value"), AccBotFormatters.formatFiat(value, symbol: plan.fiat))
                        }
                        if let performance = summary.performancePercent(currentPrice: viewModel.currentPrice) {
                            row(String(localized: "Performance vs. average"), AccBotFormatters.formatSignedPercent(performance),
                                color: performance >= 0 ? colors.success : colors.error)
                        }
                    }
                    if !plan.includesAmounts {
                        Text(String(localized: "The sender chose not to share amounts."))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }

                card {
                    Text(String(localized: "Trades (\(plan.trades.count))"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)
                    if plan.trades.isEmpty {
                        Text(String(localized: "No confirmed trades yet."))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                    } else {
                        ForEach(Array(plan.trades.reversed().enumerated()), id: \.offset) { _, trade in
                            tradeRow(trade, plan: plan)
                        }
                    }
                }
            }
            .padding(Spacing.lg)
            .maxFormWidth()
        }
    }

    private func banner(_ plan: SharedPlan) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Label(String(localized: "Read-only"), systemImage: "eye")
                .font(AccBotFonts.label)
                .foregroundStyle(colors.primary)
            Text(bannerText(plan))
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)
            if plan.liveUpdates {
                if plan.refreshFailed {
                    Text(String(localized: "The last refresh failed. The key may have been revoked or the exchange is unreachable."))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.error)
                }
                Text(plan.verifiedReadOnly
                     ? String(localized: "Key checked by the exchange as read-only.")
                     : String(localized: "The exchange cannot confirm the key is read-only; the sender confirmed it."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                Text(String(localized: "New trades include all trades of this pair on the sender’s exchange account."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            if plan.isSandbox {
                Text(String(localized: "Test environment data"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.warning)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.lg)
        .background(colors.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func bannerText(_ plan: SharedPlan) -> String {
        guard plan.liveUpdates else {
            return String(localized: "Snapshot from \(AccBotFormatters.mediumDate.string(from: plan.sharedAt)). It does not update by itself and cannot be edited or run.")
        }
        guard let refreshedAt = plan.refreshedAt else {
            return String(localized: "Live updates · not refreshed yet. Cannot be edited or run.")
        }
        return String(localized: "Live updates · last refreshed \(AccBotFormatters.relativeDate(refreshedAt)). Cannot be edited or run.")
    }

    private func schedule(_ plan: SharedPlan) -> String {
        if let cron = plan.cronExpression, let description = CronUtils.describeCron(cron) {
            return description
        }
        return plan.frequency?.displayName ?? String(localized: "Unknown")
    }

    private func tradeRow(_ trade: SharedPlanTrade, plan: SharedPlan) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(trade.side == .buy ? String(localized: "Purchase") : String(localized: "Sell"))
                    .font(AccBotFonts.label)
                    .foregroundStyle(trade.side == .buy ? colors.success : colors.error)
                Text(AccBotFormatters.mediumDate.string(from: trade.executedAt))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: Spacing.xxs) {
                if let fiat = trade.fiatAmount {
                    Text(AccBotFormatters.formatFiat(fiat, symbol: plan.fiat))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurface)
                }
                if let crypto = trade.cryptoAmount {
                    Text(AccBotFormatters.formatCrypto(crypto, symbol: plan.crypto))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                Text(String(localized: "at \(AccBotFormatters.formatFiat(trade.price, symbol: plan.fiat))"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
        .padding(.vertical, Spacing.xs)
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func row(_ label: String, _ value: String, color: Color? = nil) -> some View {
        HStack {
            Text(label)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
            Spacer()
            Text(value)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(color ?? colors.onSurface)
                .multilineTextAlignment(.trailing)
        }
    }
}
