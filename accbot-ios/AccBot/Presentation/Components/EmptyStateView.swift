import SwiftUI

/// Centered empty-state placeholder with an SF Symbol icon, title,
/// optional subtitle, and optional action button. Used when lists or data sets are empty.
struct EmptyStateView: View {
    let systemImage: String
    let title: String
    let subtitle: String?
    let actionTitle: String?
    let action: (() -> Void)?

    @Environment(\.accBotColors) private var colors

    init(systemImage: String, title: String, subtitle: String? = nil,
         actionTitle: String? = nil, action: (() -> Void)? = nil) {
        self.systemImage = systemImage
        self.title = title
        self.subtitle = subtitle
        self.actionTitle = actionTitle
        self.action = action
    }

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: systemImage)
                .font(AccBotFonts.displayLarge)
                .foregroundStyle(colors.onSurfaceVariant)
                .accessibilityHidden(true)

            Text(title)
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)

            if let subtitle {
                Text(subtitle)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }

            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.primary)
                }
                .accessibilityLabel(actionTitle)
            }
        }
        .padding(Spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
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
