import SwiftUI

/// Centered empty-state placeholder with an SF Symbol icon, title,
/// and optional subtitle. Used when lists or data sets are empty.
struct EmptyStateView: View {
    let systemImage: String
    let title: String
    let subtitle: String?

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    init(systemImage: String, title: String, subtitle: String? = nil) {
        self.systemImage = systemImage
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: systemImage)
                .font(.system(size: 48))
                .foregroundColor(colors.onSurfaceVariant.opacity(0.5))

            Text(title)
                .font(AccBotFonts.titleSmall)
                .foregroundColor(colors.onSurface)
                .multilineTextAlignment(.center)

            if let subtitle {
                Text(subtitle)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(Spacing.xxxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Preview

#Preview {
    EmptyStateView(
        systemImage: "chart.bar.xaxis",
        title: "No DCA Plans Yet",
        subtitle: "Create your first dollar-cost averaging plan to get started."
    )
    .background(Color.backgroundDark)
}
