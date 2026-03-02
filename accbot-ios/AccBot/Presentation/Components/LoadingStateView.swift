import SwiftUI

/// Centered loading indicator with an optional descriptive message.
struct LoadingStateView: View {
    let message: LocalizedStringKey?

    @Environment(\.accBotColors) private var colors

    init(message: LocalizedStringKey? = nil) {
        self.message = message
    }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: colors.primary))
                .scaleEffect(1.2)
                .accessibilityLabel(Text(String(localized: "Loading")))

            if let message {
                Text(message)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            UIAccessibility.post(notification: .announcement, argument: String(localized: "Loading"))
        }
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
