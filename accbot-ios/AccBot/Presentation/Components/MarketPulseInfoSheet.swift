import SwiftUI

/// Educational bottom sheet explaining the Market Pulse indicators (Fear & Greed Index and ATH Distance).
struct MarketPulseInfoSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                // Header
                VStack(spacing: Spacing.sm) {
                    ZStack {
                        RoundedRectangle(cornerRadius: CornerRadius.md)
                            .fill(colors.primary.opacity(0.15))
                            .frame(width: 48, height: 48)
                        Image(systemName: "chart.bar.fill")
                            .font(.system(size: 24))
                            .foregroundStyle(colors.primary)
                    }

                    Text(String(localized: "Market Pulse"))
                        .font(AccBotFonts.titleMedium)
                        .foregroundStyle(colors.onSurface)

                    Text(String(localized: "Real-time market indicators to help you understand market conditions"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)

                // Overview
                Text(String(localized: "Market Pulse combines two key indicators to give you a quick snapshot of current market sentiment and price levels. These indicators can be used with DCA strategies to adjust purchase amounts automatically."))
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurface)

                // Fear & Greed Section
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Label {
                        Text(String(localized: "Fear & Greed Index"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)
                    } icon: {
                        Image(systemName: "face.dashed")
                            .foregroundStyle(colors.primary)
                    }

                    Text(String(localized: "Measures overall market sentiment on a scale from 0 (Extreme Fear) to 100 (Extreme Greed). When the market is fearful, it may be a good opportunity to buy. When greedy, prices may be overextended."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)

                    // Color-coded scale
                    VStack(spacing: Spacing.xs) {
                        fearGreedScaleRow(range: "0–19", label: String(localized: "Extreme Fear"), description: String(localized: "Buy more — market is very fearful"), color: FearGreedColors.gaugeColors[0])
                        fearGreedScaleRow(range: "20–39", label: String(localized: "Fear"), description: String(localized: "Buy slightly more than usual"), color: FearGreedColors.gaugeColors[1])
                        fearGreedScaleRow(range: "40–59", label: String(localized: "Neutral"), description: String(localized: "Standard DCA amount"), color: FearGreedColors.gaugeColors[2])
                        fearGreedScaleRow(range: "60–79", label: String(localized: "Greed"), description: String(localized: "Buy slightly less than usual"), color: FearGreedColors.gaugeColors[3])
                        fearGreedScaleRow(range: "80–100", label: String(localized: "Extreme Greed"), description: String(localized: "Buy less — market may be overextended"), color: FearGreedColors.gaugeColors[4])
                    }
                    .padding(Spacing.md)
                    .background(colors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                }

                // ATH Distance Section
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Label {
                        Text(String(localized: "ATH Distance"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)
                    } icon: {
                        Image(systemName: "chart.line.downtrend.xyaxis")
                            .foregroundStyle(colors.primary)
                    }

                    Text(String(localized: "Shows how far the current price is from the All-Time High. A larger distance means the price has dropped more from its peak, potentially offering a better buying opportunity for long-term DCA investors."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                // DCA Tip
                HStack(alignment: .top, spacing: Spacing.md) {
                    Image(systemName: "lightbulb.fill")
                        .foregroundStyle(colors.warning)

                    Text(String(localized: "DCA strategies like \"Fear & Greed\" and \"ATH-Based\" use these indicators to automatically adjust your purchase amounts. You don't need to act on them manually."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurface)
                }
                .padding(Spacing.lg)
                .background(colors.warning.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))

                // Close button
                Button {
                    dismiss()
                } label: {
                    Text(String(localized: "Got It"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(colors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                }
            }
            .padding(.horizontal, Spacing.xxl)
            .padding(.vertical, Spacing.xxl)
        }
        .background(colors.background)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private func fearGreedScaleRow(range: String, label: String, description: String, color: Color) -> some View {
        HStack(spacing: Spacing.md) {
            RoundedRectangle(cornerRadius: 2)
                .fill(color)
                .frame(width: 4, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: Spacing.sm) {
                    Text(range)
                        .font(AccBotFonts.label)
                        .foregroundStyle(color)
                        .frame(width: 44, alignment: .leading)
                    Text(label)
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.onSurface)
                }
                Text(description)
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            Spacer()
        }
    }
}
