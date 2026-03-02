import SwiftUI

/// Orange banner displayed at the top of the screen when sandbox mode
/// is active. Automatically hides when sandbox mode is off.
struct SandboxBanner: View {
    @Environment(\.accBotColors) private var colors

    var body: some View {
        if colors.isSandbox {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "testtube.2")
                    .font(AccBotFonts.label)
                    .accessibilityHidden(true)

                Text(String(localized: "SANDBOX MODE - Test trades only"))
                    .font(AccBotFonts.label)
            }
            .foregroundStyle(colors.onPrimary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.sm)
            .background(colors.primary)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(String(localized: "Sandbox mode active, test trades only"))
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 0) {
        SandboxBanner()
        Spacer()
    }
    .background(Color.backgroundDark)
    .environment(\.isSandboxMode, true)
}
