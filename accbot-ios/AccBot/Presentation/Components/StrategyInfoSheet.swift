import SwiftUI

/// Bottom sheet that explains the three DCA strategies and their
/// multiplier tier tables.
struct StrategyInfoSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xxl) {
                    classicSection
                    Divider().background(colors.onSurfaceVariant.opacity(0.3))
                    athBasedSection
                    Divider().background(colors.onSurfaceVariant.opacity(0.3))
                    fearGreedSection
                }
                .padding(Spacing.lg)
            }
            .background(colors.background)
            .navigationTitle(String(localized: "DCA Strategies"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(String(localized: "Done")) { dismiss() }
                        .foregroundColor(colors.primary)
                }
            }
        }
    }

    // MARK: - Classic

    private var classicSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            strategyHeader(
                name: DcaStrategy.classic.displayName,
                icon: "arrow.right",
                description: DcaStrategy.classic.description
            )

            infoText(String(localized: "Buys a fixed amount at every scheduled interval. No market-based adjustments. Simple, time-tested, and effective for long-term accumulation."))
        }
    }

    // MARK: - ATH-Based

    private var athBasedSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            strategyHeader(
                name: DcaStrategy.athBased().displayName,
                icon: "chart.line.uptrend.xyaxis",
                description: DcaStrategy.athBased().description
            )

            infoText(String(localized: "Adjusts the purchase amount based on how far the current price is from the All-Time High. Buy more when the market is down, less when it's near ATH."))

            tierTable(
                columns: [
                    String(localized: "Distance from ATH"),
                    String(localized: "Multiplier")
                ],
                rows: defaultAthTiers.map { tier in
                    [
                        "\(Int(tier.maxDistancePercent * 100))%",
                        "\(String(format: "%.1f", tier.multiplier))x"
                    ]
                }
            )
        }
    }

    // MARK: - Fear & Greed

    private var fearGreedSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            strategyHeader(
                name: DcaStrategy.fearAndGreed().displayName,
                icon: "face.dashed",
                description: DcaStrategy.fearAndGreed().description
            )

            infoText(String(localized: "Uses the Fear & Greed Index (0-100) to adjust purchase amounts. Buy more during extreme fear, less during extreme greed."))

            tierTable(
                columns: [
                    String(localized: "Index Range"),
                    String(localized: "Sentiment"),
                    String(localized: "Multiplier")
                ],
                rows: [
                    ["0 - 24", String(localized: "Extreme Fear"), "2.5x"],
                    ["25 - 44", String(localized: "Fear"), "1.5x"],
                    ["45 - 54", String(localized: "Neutral"), "1.0x"],
                    ["55 - 74", String(localized: "Greed"), "0.5x"],
                    ["75 - 100", String(localized: "Extreme Greed"), "0.25x"],
                ]
            )
        }
    }

    // MARK: - Reusable Subviews

    private func strategyHeader(name: String, icon: String, description: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: icon)
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(colors.primary)

                Text(name)
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(colors.onSurface)
            }

            Text(description)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.onSurfaceVariant)
        }
    }

    private func infoText(_ text: String) -> some View {
        Text(text)
            .font(AccBotFonts.bodySmall)
            .foregroundColor(colors.onSurface.opacity(0.8))
    }

    private func tierTable(columns: [String], rows: [[String]]) -> some View {
        VStack(spacing: 0) {
            // Header row
            HStack(spacing: 0) {
                ForEach(columns.indices, id: \.self) { index in
                    Text(columns[index])
                        .font(AccBotFonts.captionSmall)
                        .foregroundColor(colors.primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, Spacing.sm)
                        .padding(.horizontal, Spacing.sm)
                }
            }
            .background(colors.surfaceVariant.opacity(0.3))

            // Data rows
            ForEach(rows.indices, id: \.self) { rowIndex in
                HStack(spacing: 0) {
                    ForEach(rows[rowIndex].indices, id: \.self) { colIndex in
                        Text(rows[rowIndex][colIndex])
                            .font(AccBotFonts.caption)
                            .foregroundColor(colors.onSurface)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, Spacing.sm)
                            .padding(.horizontal, Spacing.sm)
                    }
                }
                .background(
                    rowIndex % 2 == 0
                    ? Color.clear
                    : colors.surfaceVariant.opacity(0.15)
                )
            }
        }
        .cornerRadius(CornerRadius.sm)
        .overlay(
            RoundedRectangle(cornerRadius: CornerRadius.sm)
                .strokeBorder(colors.onSurfaceVariant.opacity(0.2), lineWidth: 1)
        )
    }
}

// MARK: - Preview

#Preview {
    StrategyInfoSheet()
        .preferredColorScheme(.dark)
}
