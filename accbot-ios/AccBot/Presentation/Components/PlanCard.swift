import SwiftUI

/// Card displaying a DCA plan summary with exchange logo, pair, amount,
/// frequency, strategy badge, next execution, and enable/disable toggle.
struct PlanCard: View {
    let plan: DcaPlan
    let onTap: () -> Void
    let onToggle: (Bool) -> Void

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: Spacing.md) {
                headerRow
                Divider().background(colors.onSurfaceVariant.opacity(0.3))
                detailsRow
                nextExecutionRow
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .cornerRadius(CornerRadius.md)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Header

    private var headerRow: some View {
        HStack(spacing: Spacing.md) {
            Image(plan.exchange.logoName)
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 36)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(plan.pair)
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.onSurface)

                Text(plan.exchange.displayName)
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }

            Spacer()

            Toggle("", isOn: Binding(
                get: { plan.isEnabled },
                set: { onToggle($0) }
            ))
            .labelsHidden()
            .tint(colors.primary)
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

            Spacer()

            strategyBadge
        }
    }

    private func detailItem(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(label)
                .font(AccBotFonts.captionSmall)
                .foregroundColor(colors.onSurfaceVariant)
            Text(value)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.onSurface)
                .lineLimit(1)
        }
    }

    private var strategyBadge: some View {
        Text(plan.strategy.displayName)
            .font(AccBotFonts.captionSmall)
            .foregroundColor(colors.primary)
            .padding(.horizontal, Spacing.sm)
            .padding(.vertical, Spacing.xs)
            .background(colors.primary.opacity(0.15))
            .cornerRadius(CornerRadius.sm)
    }

    // MARK: - Next Execution

    private var nextExecutionRow: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: "clock")
                .font(AccBotFonts.caption)
                .foregroundColor(colors.onSurfaceVariant)

            if let next = plan.nextExecutionAt {
                Text(String(localized: "Next: \(next.formatted(.relative(presentation: .named)))"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            } else {
                Text(String(localized: "Next execution: --"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }

            Spacer()

            if !plan.isEnabled {
                Text(String(localized: "Paused"))
                    .font(AccBotFonts.captionSmall)
                    .foregroundColor(colors.warning)
            }
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
            onToggle: { _ in }
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
