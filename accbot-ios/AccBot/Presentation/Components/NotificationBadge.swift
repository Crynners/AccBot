import SwiftUI

/// Small red badge circle with a count number.
/// Designed to overlay tab bar icons or other navigation elements.
struct NotificationBadge: View {
    let count: Int

    var body: some View {
        if count > 0 {
            Text(displayText)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.white)
                .frame(minWidth: 18, minHeight: 18)
                .padding(.horizontal, count > 99 ? 4 : 2)
                .background(Color.errorRed)
                .clipShape(Capsule())
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
                .offset(x: 8, y: -8)
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
            .foregroundColor(.white)
            .notificationBadge(3)

        Image(systemName: "bell")
            .font(.title2)
            .foregroundColor(.white)
            .notificationBadge(42)

        Image(systemName: "bell")
            .font(.title2)
            .foregroundColor(.white)
            .notificationBadge(150)

        Image(systemName: "bell")
            .font(.title2)
            .foregroundColor(.white)
            .notificationBadge(0)
    }
    .padding(Spacing.xxxl)
    .background(Color.backgroundDark)
}
