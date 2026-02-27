import SwiftUI

/// Small red badge circle with a count number.
/// Designed to overlay tab bar icons or other navigation elements.
struct NotificationBadge: View {
    let count: Int

    @Environment(\.accBotColors) private var colors

    var body: some View {
        if count > 0 {
            Text(displayText)
                .font(AccBotFonts.captionSmall)
                .foregroundStyle(colors.onPrimary)
                .frame(minWidth: 20, minHeight: 20)
                .padding(.horizontal, count > 99 ? Spacing.xs : Spacing.xxs)
                .background(colors.error)
                .clipShape(Capsule())
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
                .accessibilityLabel(String(localized: "\(count) unread notifications"))
        }
    }

    private var displayText: String {
        count > 99 ? "99+" : "\(count)"
    }
}

/// View modifier that overlays a notification badge in the top-trailing corner.
struct NotificationBadgeModifier: ViewModifier {
    let count: Int

    func body(content: Content) -> some View {
        content.overlay(alignment: .topTrailing) {
            NotificationBadge(count: count)
                .alignmentGuide(.top) { $0[.bottom] - 8 }
                .alignmentGuide(.trailing) { $0[.leading] + 8 }
        }
    }
}

extension View {
    /// Attaches a notification badge to the top-trailing corner.
    func notificationBadge(_ count: Int) -> some View {
        modifier(NotificationBadgeModifier(count: count))
    }
}

// MARK: - Preview

#Preview {
    HStack(spacing: Spacing.xxxl) {
        Image(systemName: "bell")
            .font(.title2)
            .foregroundStyle(.white)
            .notificationBadge(3)

        Image(systemName: "bell")
            .font(.title2)
            .foregroundStyle(.white)
            .notificationBadge(42)

        Image(systemName: "bell")
            .font(.title2)
            .foregroundStyle(.white)
            .notificationBadge(150)

        Image(systemName: "bell")
            .font(.title2)
            .foregroundStyle(.white)
            .notificationBadge(0)
    }
    .padding(Spacing.xxxl)
    .background(Color.backgroundDark)
}
