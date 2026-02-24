import SwiftUI

/// Centered loading indicator with an optional descriptive message.
struct LoadingStateView: View {
    let message: String?

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    init(message: String? = nil) {
        self.message = message
    }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: colors.primary))
                .scaleEffect(1.2)

            if let message {
                Text(message)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: Spacing.xxxl) {
        LoadingStateView()
        LoadingStateView(message: "Loading your plans...")
    }
    .background(Color.backgroundDark)
}
