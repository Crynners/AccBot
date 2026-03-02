import SwiftUI

/// Reusable error banner with warning icon and message.
/// Used across plan creation, editing, and details screens.
/// Optionally shows a Retry button when a retry action is provided.
struct ErrorBanner: View {
    let message: String
    var retryAction: (() -> Void)?

    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(colors.error)
                .accessibilityHidden(true)
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.error)

            if let retryAction {
                Spacer()
                Button(String(localized: "Retry"), action: retryAction)
                    .font(AccBotFonts.label)
                    .foregroundStyle(colors.error)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
        }
        .padding(Spacing.md)
        .background(colors.error.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(String(localized: "Error: \(message)"))
        .onAppear {
            UIAccessibility.post(notification: .announcement, argument: String(localized: "Error: \(message)"))
        }
    }
}
