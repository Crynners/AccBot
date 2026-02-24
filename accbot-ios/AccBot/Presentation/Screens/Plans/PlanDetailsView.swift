import SwiftUI

struct PlanDetailsView: View {
    let planId: Int64

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: PlanDetailsViewModel

    @State private var showDeleteConfirmation = false

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    init(planId: Int64) {
        self.planId = planId
        _viewModel = StateObject(wrappedValue: PlanDetailsViewModel(
            planId: planId,
            dependencies: AppDependencies()
        ))
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
            if viewModel.plan != nil {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        router.navigate(to: .editPlan(planId))
                    } label: {
                        Image(systemName: "pencil")
                            .foregroundColor(colors.primary)
                    }
                }
            }
        }
        .onAppear {
            viewModel.loadData()
        }
        .alert(
            String(localized: "Delete Plan"),
            isPresented: $showDeleteConfirmation
        ) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Delete"), role: .destructive) {
                if viewModel.deletePlan() {
                    router.pop()
                }
            }
        } message: {
            Text(String(localized: "Are you sure you want to delete this plan and all associated transactions? This action cannot be undone."))
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

                // Withdrawal info
                if plan.withdrawalEnabled {
                    withdrawalCard(plan)
                }

                // Recent transactions
                transactionsSection

                // Import history button
                if plan.exchange.supportsApiImport {
                    importHistoryButton(plan)
                }

                // Error message
                if let error = viewModel.errorMessage {
                    errorBanner(error)
                }

                // Delete button
                deleteButton

                Spacer(minLength: Spacing.xxl)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colors.background)
    }

    // MARK: - Plan Header

    private func planHeaderCard(_ plan: DcaPlan) -> some View {
        VStack(spacing: Spacing.lg) {
            HStack(spacing: Spacing.md) {
                Image(plan.exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 48, height: 48)
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(plan.pair)
                        .font(AccBotFonts.titleMedium)
                        .foregroundColor(colors.onSurface)

                    Text(plan.exchange.displayName)
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(colors.onSurfaceVariant)
                }

                Spacer()

                // Strategy badge
                Text(plan.strategy.displayName)
                    .font(AccBotFonts.captionSmall)
                    .foregroundColor(colors.primary)
                    .padding(.horizontal, Spacing.sm)
                    .padding(.vertical, Spacing.xs)
                    .background(colors.primary.opacity(0.15))
                    .cornerRadius(CornerRadius.sm)
            }

            Divider().background(colors.onSurfaceVariant.opacity(0.3))

            HStack {
                detailColumn(
                    label: String(localized: "Amount"),
                    value: "\(NSDecimalNumber(decimal: plan.amount).stringValue) \(plan.fiat)"
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
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Status Card

    private func statusCard(_ plan: DcaPlan) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Status"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                HStack(spacing: Spacing.sm) {
                    Circle()
                        .fill(plan.isEnabled ? colors.success : colors.warning)
                        .frame(width: 10, height: 10)

                    Text(plan.isEnabled
                         ? String(localized: "Active")
                         : String(localized: "Paused"))
                        .font(AccBotFonts.headline)
                        .foregroundColor(colors.onSurface)
                }
            }

            Spacer()

            Toggle("", isOn: Binding(
                get: { plan.isEnabled },
                set: { _ in viewModel.toggleEnabled() }
            ))
            .labelsHidden()
            .tint(colors.primary)
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Execution Card

    private func executionCard(_ plan: DcaPlan) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Execution"))
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

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
        .cornerRadius(CornerRadius.md)
    }

    private func executionItem(icon: String, label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack(spacing: Spacing.xs) {
                Image(systemName: icon)
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
                Text(label)
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }
            Text(value)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.onSurface)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
        }
    }

    // MARK: - Withdrawal Card

    private func withdrawalCard(_ plan: DcaPlan) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "arrow.up.right.circle.fill")
                    .foregroundColor(colors.primary)
                Text(String(localized: "Auto-Withdrawal"))
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.onSurface)
            }

            if let address = plan.withdrawalAddress, !address.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Wallet Address"))
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.onSurfaceVariant)
                    Text(address)
                        .font(AccBotFonts.monoSmall)
                        .foregroundColor(colors.onSurface)
                        .lineLimit(2)
                        .textSelection(.enabled)
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Transactions Section

    private var transactionsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text(String(localized: "Recent Transactions"))
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.onSurface)

                Spacer()

                if !viewModel.recentTransactions.isEmpty {
                    Text("\(viewModel.recentTransactions.count)")
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.onSurfaceVariant)
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
                    }
                }
            }
        }
    }

    // MARK: - Import History

    private func importHistoryButton(_ plan: DcaPlan) -> some View {
        Button {
            router.navigate(to: .importCsv(plan.id))
        } label: {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "square.and.arrow.down")
                Text(String(localized: "Import History"))
            }
            .font(AccBotFonts.headline)
            .foregroundColor(colors.primary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(colors.primary.opacity(0.1))
            .cornerRadius(CornerRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(colors.primary.opacity(0.3), lineWidth: 1)
            )
        }
    }

    // MARK: - Delete Button

    private var deleteButton: some View {
        Button {
            showDeleteConfirmation = true
        } label: {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "trash")
                Text(String(localized: "Delete Plan"))
            }
            .font(AccBotFonts.headline)
            .foregroundColor(colors.error)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(colors.error.opacity(0.1))
            .cornerRadius(CornerRadius.md)
        }
    }

    // MARK: - Error Banner

    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(colors.error)
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.error)
        }
        .padding(Spacing.md)
        .background(colors.error.opacity(0.1))
        .cornerRadius(CornerRadius.sm)
    }

    // MARK: - Helpers

    private func detailColumn(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundColor(colors.onSurfaceVariant)
            Text(value)
                .font(AccBotFonts.body)
                .foregroundColor(colors.onSurface)
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        PlanDetailsView(planId: 1)
    }
    .preferredColorScheme(.dark)
}
