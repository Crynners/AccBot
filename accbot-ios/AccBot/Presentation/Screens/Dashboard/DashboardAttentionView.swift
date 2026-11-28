import SwiftUI

/// Tasks disappear once resolved; info notices can be dismissed.
enum AttentionTone {
    case error, warning, info
}

/// One item of the dashboard "Needs attention" section. All notices share this layout:
/// icon + title, an explanation, and the actions that resolve it.
struct AttentionCard<Actions: View>: View {
    let systemImage: String
    let tone: AttentionTone
    let title: String
    var message: String? = nil
    var onDismiss: (() -> Void)? = nil
    @ViewBuilder var actions: () -> Actions

    @Environment(\.accBotColors) private var colors

    private var toneColor: Color {
        switch tone {
        case .error: return colors.error
        case .warning: return colors.warning
        case .info: return colors.primary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: systemImage)
                    .foregroundStyle(toneColor)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(title)
                        .font(AccBotFonts.headline)
                        .foregroundStyle(toneColor)
                    if let message {
                        Text(message)
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurface)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let onDismiss {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(localized: "Dismiss"))
                }
            }
            actions()
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(toneColor.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }
}

extension AttentionCard where Actions == EmptyView {
    init(systemImage: String, tone: AttentionTone, title: String, message: String? = nil, onDismiss: (() -> Void)? = nil) {
        self.init(systemImage: systemImage, tone: tone, title: title, message: message, onDismiss: onDismiss) { EmptyView() }
    }
}

/// Everything on the dashboard that needs the user, in one place and one style:
/// tasks first (uncertain purchases, restricted automation), then information
/// (sandbox, first-plan guide).
struct DashboardAttentionSection: View {
    @ObservedObject var viewModel: DashboardViewModel
    @ObservedObject var automation: AutomationHealthMonitor
    let isSandbox: Bool
    let onReview: (Int64) -> Void

    @Environment(\.accBotColors) private var colors

    private var review: [Transaction] { viewModel.purchasesAwaitingReview }

    private var automationIssues: [AutomationIssue] {
        let hasEnabledPlans = viewModel.plans.contains(where: \.isEnabled)
        let keys = Set(automation.issues.map(\.rawValue))
        guard hasEnabledPlans, !automation.issues.isEmpty, !keys.isSubset(of: viewModel.dismissedAutomationIssues) else {
            return []
        }
        return automation.issues
    }

    private var guidePlan: DashboardViewModel.PlanWithBalance? {
        guard !viewModel.firstPlanGuideDismissed, viewModel.lastPurchaseByPlan.isEmpty, review.isEmpty else { return nil }
        return viewModel.plansWithBalance
            .filter { $0.plan.isEnabled }
            .min { ($0.plan.nextExecutionAt ?? .distantFuture) < ($1.plan.nextExecutionAt ?? .distantFuture) }
    }

    private var showSandbox: Bool { isSandbox && !viewModel.sandboxNoticeDismissed }

    private struct MissedItem: Identifiable {
        let plan: DcaPlan
        let count: Int
        var id: Int64 { plan.id }
    }

    /// Plans with missed purchases, in dashboard order.
    private var missed: [MissedItem] {
        viewModel.plans.compactMap { plan in
            guard let count = viewModel.missedPurchases[plan.id], count > 0 else { return nil }
            return MissedItem(plan: plan, count: count)
        }
    }

    var body: some View {
        let hasTasks = !review.isEmpty || !missed.isEmpty || !automationIssues.isEmpty
        if hasTasks || showSandbox || guidePlan != nil {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                if hasTasks {
                    Text(String(localized: "Needs attention"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)
                        .accessibilityAddTraits(.isHeader)
                }

                if let first = review.first {
                    AttentionCard(
                        systemImage: "questionmark.circle.fill",
                        tone: .error,
                        title: review.count == 1
                            ? String(localized: "A purchase needs your review")
                            : String(localized: "\(review.count) purchases need your review"),
                        message: String(localized: "AccBot sent a \(first.pair) purchase to \(first.exchange.displayName) but doesn't know whether the exchange executed it. The plan won't buy again until you check it.")
                    ) {
                        Button(String(localized: "Review")) { onReview(first.id) }
                            .buttonStyle(.borderedProminent)
                            .tint(colors.error)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }

                ForEach(missed) { item in
                    missedCard(plan: item.plan, count: item.count)
                }

                if !automationIssues.isEmpty {
                    AttentionCard(
                        systemImage: "exclamationmark.triangle.fill",
                        tone: .warning,
                        title: String(localized: "Automatic purchases may be limited"),
                        message: String(localized: "iOS may postpone purchases or hide a failure from you:")
                    ) {
                        AutomationIssueList(issues: automationIssues)
                        Button(String(localized: "Don't remind me")) {
                            viewModel.dismissAutomationWarning(automationIssues)
                        }
                        .font(AccBotFonts.label)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }

                if showSandbox {
                    AttentionCard(
                        systemImage: "testtube.2",
                        tone: .warning,
                        title: String(localized: "Sandbox mode"),
                        message: String(localized: "Orders go to the exchanges' test accounts only. No real money is used."),
                        onDismiss: { viewModel.sandboxNoticeDismissed = true }
                    )
                }

                if let pwb = guidePlan {
                    AttentionCard(
                        systemImage: "paperplane.fill",
                        tone: .info,
                        title: String(localized: "What happens next"),
                        message: guideMessage(pwb)
                    ) {
                        Button(String(localized: "Got it")) { viewModel.dismissFirstPlanGuide() }
                            .font(AccBotFonts.label)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }
            }
        }
    }

    private func missedCard(plan: DcaPlan, count: Int) -> some View {
        let amount = AccBotFormatters.formatFiat(plan.amount, symbol: plan.fiat)
        let total = AccBotFormatters.formatFiat(plan.amount * Decimal(count), symbol: plan.fiat)
        let isBuying = viewModel.buyingMissedPlanId == plan.id
        return AttentionCard(
            systemImage: "clock.arrow.circlepath",
            tone: .warning,
            title: String(localized: "Missed purchases: \(count)"),
            message: String(localized: "\(plan.displayName): iOS didn't start AccBot in time, so scheduled purchases of \(amount) were skipped. Buy them now (about \(total) in total) or skip them.")
        ) {
            HStack(spacing: Spacing.sm) {
                Spacer()
                Button(String(localized: "Skip")) { viewModel.skipMissedPurchases(planId: plan.id) }
                    .font(AccBotFonts.label)
                    .disabled(isBuying)
                Button {
                    viewModel.buyMissedPurchases(planId: plan.id)
                } label: {
                    if isBuying {
                        ProgressView()
                    } else {
                        Text(String(localized: "Buy missed"))
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.buyingMissedPlanId != nil)
            }
        }
    }

    private func guideMessage(_ pwb: DashboardViewModel.PlanWithBalance) -> String {
        let plan = pwb.plan
        let amount = AccBotFormatters.formatFiat(plan.amount, symbol: plan.fiat)
        let when = plan.nextExecutionAt.map { $0.formatted(.relative(presentation: .named)) } ?? "—"
        var lines = [
            String(localized: "First purchase \(when): \(amount) on \(plan.exchange.displayName)."),
            String(localized: "Keep enough \(plan.fiat) on \(plan.exchange.displayName) for several purchases.")
        ]
        if let balance = pwb.fiatBalance, balance < plan.amount {
            let balanceText = AccBotFormatters.formatFiat(balance, symbol: plan.fiat)
            lines.append(String(localized: "You have only \(balanceText) there now, which is not enough for the next purchase."))
        }
        lines.append(String(localized: "If iOS doesn't run a purchase on time, AccBot reminds you with a notification."))
        return lines.joined(separator: "\n")
    }
}
