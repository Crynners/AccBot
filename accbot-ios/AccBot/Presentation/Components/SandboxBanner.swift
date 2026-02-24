import SwiftUI

/// Orange banner displayed at the top of the screen when sandbox mode
/// is active. Automatically hides when sandbox mode is off.
struct SandboxBanner: View {
    @Environment(\.isSandboxMode) private var isSandboxMode

    var body: some View {
        if isSandboxMode {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "testtube.2")
                    .font(AccBotFonts.label)

                Text(String(localized: "SANDBOX MODE"))
                    .font(AccBotFonts.label)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.sm)
            .background(Color.sandboxPrimary)
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
