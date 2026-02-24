import SwiftUI

/// Card displaying an exchange with logo, display name, and connection
/// status indicator dot. Tappable via onTap closure.
struct ExchangeCard: View {
    let exchange: Exchange
    let isConnected: Bool
    let onTap: () -> Void

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.md) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())

                Text(exchange.displayName)
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.onSurface)

                Spacer()

                connectionStatusDot

                Image(systemName: "chevron.right")
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .cornerRadius(CornerRadius.md)
        }
        .buttonStyle(.plain)
    }

    private var connectionStatusDot: some View {
        HStack(spacing: Spacing.xs) {
            Circle()
                .fill(isConnected ? colors.success : Color.gray)
                .frame(width: 8, height: 8)

            Text(isConnected
                 ? String(localized: "Connected")
                 : String(localized: "Not configured"))
                .font(AccBotFonts.captionSmall)
                .foregroundColor(isConnected ? colors.success : colors.onSurfaceVariant)
        }
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
