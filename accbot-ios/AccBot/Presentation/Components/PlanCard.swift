import SwiftUI

/// Card displaying a DCA plan summary with exchange logo, pair, amount,
/// frequency, strategy badge, next execution, and enable/disable toggle.
struct PlanCard: View {
    let plan: DcaPlan
    let onTap: () -> Void
    let onToggle: (Bool) -> Void
    var balanceDuration: String? = nil
    var isLowBalance: Bool = false
    var withdrawalReady: Bool = false
    var withdrawalBalanceText: String? = nil
    var goalProgress: Double? = nil
    var goalText: String? = nil
    var goalReached: Bool = false

    @Environment(\.accBotColors) private var colors

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.md) {
                headerRow
                Divider().background(colors.onSurfaceVariant.opacity(Opacity.divider))
                detailsRow
                nextExecutionRow
                goalProgressRow
                balanceDurationRow
                withdrawalWarningRow
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .contentShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isButton)
    }

    // MARK: - Header

    private var headerRow: some View {
        HStack(spacing: Spacing.md) {
            CryptoIcon(symbol: plan.crypto, size: 36)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                HStack(spacing: Spacing.sm) {
                    Text(plan.pair)
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)
                    Text(plan.strategy.displayName)
                        .font(AccBotFonts.caption)
                        .italic()
                        .foregroundStyle(colors.primary)
                }

                Text(plan.exchange.displayName)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            Spacer()

            Toggle(isOn: Binding(
                get: { plan.isEnabled },
                set: { onToggle($0) }
            )) {
                Text(String(localized: "Enable plan"))
            }
            .labelsHidden()
            .tint(colors.primary)
            .accessibilityLabel(String(localized: "Toggle plan \(plan.pair)"))
        }
    }

    // MARK: - Details

    private var detailsRow: some View {
        HStack(spacing: Spacing.md) {
            detailItem(
                label: String(localized: "Amount"),
                value: "\(plan.amount) \(plan.fiat)"
            )

            Spacer()

            detailItem(
                label: String(localized: "Frequency"),
                value: plan.frequency.displayName
            )

        }
    }

    private func detailItem(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            Text(value)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .allowsTightening(true)
        }
    }

    // MARK: - Next Execution

    private var nextExecutionRow: some View {
        TimelineView(.periodic(from: .now, by: 60)) { _ in
            HStack(spacing: Spacing.xs) {
                Image(systemName: "clock")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .accessibilityHidden(true)

                if let next = plan.nextExecutionAt {
                    Text(String(localized: "Next: \(next.formatted(.relative(presentation: .named)))"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                } else {
                    Text(String(localized: "Next execution: --"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                Spacer()

                if !plan.isEnabled {
                    Text(String(localized: "Paused"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.warning)
                }
            }
        }
    }

    // MARK: - Goal Progress

    @ViewBuilder
    private var goalProgressRow: some View {
        if let progress = goalProgress, let text = goalText {
            let goalColor = goalReached ? colors.success : colors.primary
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                ProgressView(value: progress)
                    .tint(goalColor)
                    .scaleEffect(y: 0.6)
                Text(text)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(goalColor)
                    .fontWeight(.medium)
            }
        }
    }

    // MARK: - Balance Duration

    @ViewBuilder
    private var balanceDurationRow: some View {
        if let balanceDuration {
            let balanceColor: Color = isLowBalance ? colors.warning : colors.onSurfaceVariant
            HStack(spacing: Spacing.xs) {
                Image(systemName: isLowBalance ? "exclamationmark.triangle.fill" : "banknote")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(balanceColor)
                    .accessibilityHidden(true)
                Text(balanceDuration)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(balanceColor)
                    .lineLimit(2)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(isLowBalance
                ? String(localized: "Low balance warning: \(balanceDuration)")
                : balanceDuration)
        }
    }

    // MARK: - Withdrawal Warning

    @ViewBuilder
    private var withdrawalWarningRow: some View {
        if withdrawalReady {
            HStack(spacing: Spacing.xs) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.warning)
                    .accessibilityHidden(true)
                if let balanceText = withdrawalBalanceText {
                    Text("\(balanceText) \(String(localized: "on exchange — consider withdrawal"))")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.warning)
                } else {
                    Text(String(localized: "Withdrawal ready"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.warning)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(
                withdrawalBalanceText != nil
                    ? String(localized: "Withdrawal warning: \(withdrawalBalanceText!) on exchange, consider withdrawal")
                    : String(localized: "Withdrawal ready, consider withdrawal")
            )
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: Spacing.lg) {
        PlanCard(
            plan: DcaPlan(
                id: 1,
                exchange: .binance,
                crypto: "BTC",
                fiat: "EUR",
                amount: 50,
                frequency: .daily,
                strategy: .athBased(),
                isEnabled: true,
                nextExecutionAt: Date().addingTimeInterval(3600)
            ),
            onTap: {},
            onToggle: { _ in },
            balanceDuration: "5 days remaining",
            withdrawalReady: true
        )

        PlanCard(
            plan: DcaPlan(
                id: 2,
                exchange: .coinmate,
                crypto: "ETH",
                fiat: "CZK",
                amount: 500,
                frequency: .weekly,
                strategy: .classic,
                isEnabled: false
            ),
            onTap: {},
            onToggle: { _ in }
        )
    }
    .padding()
    .background(Color.backgroundDark)
}
