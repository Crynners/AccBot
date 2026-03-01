import SwiftUI

struct PlanDetailsView: View {
    let planId: Int64

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: PlanDetailsViewModel

    @State private var showDeleteConfirmation = false
    @State private var showDeleteTransactionsConfirmation = false

    @Environment(\.accBotColors) private var colors

    init(planId: Int64) {
        self.planId = planId
        _viewModel = StateObject(wrappedValue: PlanDetailsViewModel(planId: planId))
    }

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(colors.background)
            } else if let plan = viewModel.plan {
                planContent(plan)
            } else {
                EmptyStateView(
                    systemImage: "doc.questionmark",
                    title: String(localized: "Plan Not Found"),
                    subtitle: String(localized: "This plan may have been deleted.")
                )
                .background(colors.background)
            }
        }
        .navigationTitle(String(localized: "Plan Details"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let plan = viewModel.plan {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button {
                            router.navigate(to: .editPlan(planId))
                        } label: {
                            Label(String(localized: "Edit Plan"), systemImage: "pencil")
                        }

                        Button {
                            showDeleteTransactionsConfirmation = true
                        } label: {
                            Label(String(localized: "Delete All Transactions"), systemImage: "trash")
                        }

                        if plan.exchange.supportsApiImport {
                            Button {
                                router.navigate(to: .importCsv(planId))
                            } label: {
                                Label(String(localized: "Import from API"), systemImage: "square.and.arrow.down")
                            }
                        }

                        Divider()

                        Button(role: .destructive) {
                            showDeleteConfirmation = true
                        } label: {
                            Label(String(localized: "Delete Plan"), systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .foregroundStyle(colors.primary)
                    }
                }
            }
        }
        .refreshable {
            viewModel.loadData()
        }
        .onAppear {
            viewModel.setup(dependencies)
        }
        .sheet(isPresented: $showDeleteConfirmation) {
            DestructiveConfirmSheet(
                title: String(localized: "Delete Plan"),
                message: String(localized: "This will delete the plan and all associated transactions. Type \(viewModel.plan?.pair ?? "—") to confirm."),
                confirmWord: viewModel.plan?.pair ?? "—",
                confirmButtonLabel: String(localized: "Delete Plan"),
                onConfirm: {
                    if viewModel.deletePlan() {
                        router.pop()
                    }
                }
            )
        }
        .sheet(isPresented: $showDeleteTransactionsConfirmation) {
            DestructiveConfirmSheet(
                title: String(localized: "Delete All Transactions"),
                message: String(localized: "Are you sure you want to delete all transactions for this plan? This action cannot be undone."),
                confirmWord: viewModel.plan?.pair ?? "—",
                confirmButtonLabel: String(localized: "Delete All"),
                onConfirm: {
                    if viewModel.deleteAllTransactions() {
                        viewModel.loadData()
                    }
                }
            )
        }
    }

    // MARK: - Plan Content

    private func planContent(_ plan: DcaPlan) -> some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                // Plan header card
                planHeaderCard(plan)

                // Status card
                statusCard(plan)

                // Execution info card
                executionCard(plan)

                // Performance card
                if viewModel.totalInvested > 0 || viewModel.currentPrice != nil {
                    performanceCard(plan)
                }

                // Balance card
                if let balance = viewModel.exchangeBalance {
                    balanceCard(plan, balance: balance)
                }

                // Withdrawal info
                if plan.withdrawalEnabled {
                    withdrawalCard(plan)
                }

                // Recent transactions
                transactionsSection

                // Error message
                if let error = viewModel.errorMessage {
                    ErrorBanner(message: error)
                }

                Spacer(minLength: Spacing.xxl)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
            .maxFormWidth()
        }
        .background(colors.background)
    }

    // MARK: - Plan Header

    private func planHeaderCard(_ plan: DcaPlan) -> some View {
        VStack(spacing: Spacing.lg) {
            HStack(spacing: Spacing.md) {
                CryptoIcon(symbol: plan.crypto, size: 48)

                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    HStack(spacing: Spacing.sm) {
                        Text(plan.pair)
                            .font(AccBotFonts.titleMedium)
                            .foregroundStyle(colors.onSurface)
                        Text(plan.strategy.displayName)
                            .font(AccBotFonts.bodySmall)
                            .italic()
                            .foregroundStyle(colors.primary)
                    }

                    Text(plan.exchange.displayName)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                Spacer()
            }

            Divider().background(colors.onSurfaceVariant.opacity(0.3))

            HStack {
                detailColumn(
                    label: String(localized: "Amount"),
                    value: AccBotFormatters.formatFiat(plan.amount, symbol: plan.fiat)
                )
                Spacer()
                detailColumn(
                    label: String(localized: "Frequency"),
                    value: plan.frequency == .custom
                        ? plan.cronExpression ?? plan.frequency.displayName
                        : plan.frequency.displayName
                )
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Status Card

    private func statusCard(_ plan: DcaPlan) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Status"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)

                HStack(spacing: Spacing.sm) {
                    Circle()
                        .fill(plan.isEnabled ? colors.success : colors.warning)
                        .frame(width: 10, height: 10)
                        .accessibilityHidden(true)

                    Text(plan.isEnabled
                         ? String(localized: "Active")
                         : String(localized: "Paused"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)
                }
            }

            Spacer()

            Toggle(String(localized: "Enable plan"), isOn: Binding(
                get: { plan.isEnabled },
                set: { _ in viewModel.toggleEnabled() }
            ))
            .labelsHidden()
            .tint(colors.primary)
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Execution Card

    private func executionCard(_ plan: DcaPlan) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Execution"))
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)

            HStack {
                executionItem(
                    icon: "clock.arrow.circlepath",
                    label: String(localized: "Last Executed"),
                    value: plan.lastExecutedAt?.formatted(date: .abbreviated, time: .shortened)
                        ?? String(localized: "Never")
                )

                Spacer()

                executionItem(
                    icon: "clock",
                    label: String(localized: "Next Execution"),
                    value: plan.nextExecutionAt?.formatted(date: .abbreviated, time: .shortened)
                        ?? String(localized: "--")
                )
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func executionItem(icon: String, label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack(spacing: Spacing.xs) {
                Image(systemName: icon)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                Text(label)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            Text(value)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)
                .lineLimit(2)
                .allowsTightening(true)
        }
    }

    // MARK: - Performance Card

    private func performanceCard(_ plan: DcaPlan) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Performance"))
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)

            VStack(spacing: Spacing.sm) {
                performanceRow(
                    label: String(localized: "Invested"),
                    value: formatFiat(viewModel.totalInvested, symbol: plan.fiat)
                )

                performanceRow(
                    label: String(localized: "Accumulated"),
                    value: formatCrypto(viewModel.totalAccumulated, symbol: plan.crypto)
                )

                performanceRow(
                    label: String(localized: "Avg Price"),
                    value: formatFiat(viewModel.avgPrice, symbol: plan.fiat)
                )

                performanceRow(
                    label: String(localized: "Current Price"),
                    value: viewModel.currentPrice.map { formatFiat($0, symbol: plan.fiat) } ?? "--"
                )

                performanceRow(
                    label: String(localized: "Current Value"),
                    value: viewModel.currentValue.map { formatFiat($0, symbol: plan.fiat) } ?? "--"
                )

                Divider().background(colors.onSurfaceVariant.opacity(0.3))

                // ROI row
                HStack {
                    Text(String(localized: "ROI"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)

                    Spacer()

                    if let gainLoss = viewModel.fiatGainLoss, let roi = viewModel.roi {
                        let isPositive = gainLoss >= 0
                        let roiColor = isPositive ? colors.success : colors.error
                        VStack(alignment: .trailing, spacing: Spacing.xxs) {
                            Text(formatSignedFiat(gainLoss, symbol: plan.fiat))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(roiColor)
                            Text(AccBotFormatters.formatSignedPercent(roi))
                                .font(AccBotFonts.bodySmall)
                                .foregroundStyle(roiColor)
                        }
                    } else {
                        Text("--")
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurface)
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func performanceRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)
            Spacer()
            Text(value)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurface)
        }
    }

    // MARK: - Balance Card

    private func balanceCard(_ plan: DcaPlan, balance: Decimal) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Exchange Balance"))
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)

            HStack {
                Text(String(localized: "Balance"))
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurfaceVariant)
                Spacer()
                Text(formatFiat(balance, symbol: plan.fiat))
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurface)
            }

            if let days = viewModel.remainingDays, let executions = viewModel.remainingExecutions {
                HStack {
                    Text(String(localized: "Remaining"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Spacer()
                    Text("\(days) \(String(localized: "days")) (\(executions) \(String(localized: "executions")))")
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurface)
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Withdrawal Card

    private func withdrawalCard(_ plan: DcaPlan) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "arrow.up.right.circle.fill")
                    .foregroundStyle(colors.primary)
                Text(String(localized: "Auto-Withdrawal"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
            }

            if let address = plan.withdrawalAddress, !address.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Wallet Address"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text(address)
                        .font(AccBotFonts.monoSmall)
                        .foregroundStyle(colors.onSurface)
                        .lineLimit(2)
                        .textSelection(.enabled)
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Transactions Section

    private var transactionsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text(String(localized: "Recent Transactions"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)

                Spacer()

                if viewModel.totalTransactionCount > 0 {
                    Text("\(viewModel.totalTransactionCount)")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }

            if viewModel.recentTransactions.isEmpty {
                EmptyStateView(
                    systemImage: "list.bullet.rectangle",
                    title: String(localized: "No Transactions Yet"),
                    subtitle: String(localized: "Transactions will appear here after the plan executes.")
                )
                .frame(height: 120)
            } else {
                LazyVStack(spacing: Spacing.sm) {
                    ForEach(viewModel.recentTransactions) { tx in
                        Button {
                            router.navigate(to: .transactionDetails(tx.id))
                        } label: {
                            TransactionCard(transaction: tx)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(String(localized: "View transaction details"))
                    }
                }

                if let plan = viewModel.plan,
                   viewModel.totalTransactionCount > viewModel.recentTransactions.count {
                    Button {
                        router.navigate(to: .history(crypto: plan.crypto, fiat: plan.fiat))
                    } label: {
                        HStack {
                            Text(String(localized: "View all \(viewModel.totalTransactionCount) transactions"))
                                .font(AccBotFonts.body)
                            Image(systemName: "chevron.right")
                                .font(AccBotFonts.caption)
                        }
                        .foregroundStyle(colors.primary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .padding(.vertical, Spacing.sm)
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private func detailColumn(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            Text(value)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurface)
        }
    }

    private func formatCrypto(_ value: Decimal, symbol: String) -> String {
        AccBotFormatters.formatCrypto(value, symbol: symbol)
    }

    private func formatFiat(_ value: Decimal, symbol: String) -> String {
        AccBotFormatters.formatFiat(value, symbol: symbol)
    }

    private func formatSignedFiat(_ value: Decimal, symbol: String) -> String {
        let number = NSDecimalNumber(decimal: value)
        return "\(AccBotFormatters.signedFiat.string(from: number) ?? "0") \(symbol)"
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        PlanDetailsView(planId: 1)
    }
}
