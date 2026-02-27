import SwiftUI

/// Card displaying an exchange with logo, display name, and connection
/// status indicator dot. Tappable via onTap closure.
struct ExchangeCard: View {
    let exchange: Exchange
    let isConnected: Bool
    let onTap: () -> Void

    @Environment(\.accBotColors) private var colors

    var body: some View {
        Button(action: {
            if !UIAccessibility.isReduceMotionEnabled {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
            onTap()
        }) {
            HStack(spacing: Spacing.md) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 44, height: 44)
                    .clipShape(Circle())
                    .accessibilityHidden(true)

                Text(exchange.displayName)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)

                Spacer()

                connectionStatusDot

                Image(systemName: "chevron.right")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .accessibilityHidden(true)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("\(exchange.displayName), \(isConnected ? String(localized: "Connected") : String(localized: "Not configured"))")
            .accessibilityAddTraits(.isButton)
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
    }

    private var connectionStatusDot: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: isConnected ? "checkmark.circle.fill" : "xmark.circle")
                .font(AccBotFonts.caption)
                .foregroundStyle(isConnected ? colors.success : colors.onSurfaceVariant)
                .accessibilityHidden(true)

            Text(isConnected
                 ? String(localized: "Connected")
                 : String(localized: "Not configured"))
                .font(AccBotFonts.caption)
                .foregroundStyle(isConnected ? colors.success : colors.onSurfaceVariant)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: Spacing.sm) {
        ExchangeCard(exchange: .binance, isConnected: true, onTap: {})
        ExchangeCard(exchange: .coinmate, isConnected: false, onTap: {})
        ExchangeCard(exchange: .kraken, isConnected: true, onTap: {})
    }
    .padding()
    .background(Color.backgroundDark)
}
