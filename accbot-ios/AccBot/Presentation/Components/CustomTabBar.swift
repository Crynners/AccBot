import SwiftUI

/// Custom tab bar with animated pill indicator, notification badge, and haptic feedback.
/// Replaces the native SwiftUI tab bar for better visual integration with AccBot's design system.
struct CustomTabBar: View {
    @Binding var selectedTab: TabItem
    let unreadNotificationCount: Int
    let onTabSelected: (TabItem) -> Void

    @Environment(\.accBotColors) private var colors
    @Namespace private var tabAnimation

    var body: some View {
        VStack(spacing: 0) {
            // Top separator line
            Rectangle()
                .fill(colors.onSurfaceVariant.opacity(0.2))
                .frame(height: 0.5)

            HStack(spacing: 0) {
                ForEach(TabItem.allCases) { tab in
                    tabButton(for: tab)
                }
            }
            .padding(.horizontal, Spacing.sm)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.xs)
        }
        .background(colors.surface)
    }

    private func tabButton(for tab: TabItem) -> some View {
        Button {
            onTabSelected(tab)
        } label: {
            VStack(spacing: Spacing.xs) {
                ZStack {
                    // Animated pill indicator behind selected tab
                    if selectedTab == tab {
                        Capsule()
                            .fill(colors.primary.opacity(0.15))
                            .frame(width: 56, height: 32)
                            .matchedGeometryEffect(id: "tabIndicator", in: tabAnimation)
                    }

                    tabIcon(for: tab)
                }
                .frame(height: 32)

                Text(tab.title)
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(selectedTab == tab ? colors.primary : colors.onSurfaceVariant)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(selectedTab == tab ? [.isSelected] : [])
        .accessibilityValue(
            tab == .notifications && unreadNotificationCount > 0
                ? String(localized: "\(unreadNotificationCount) unread")
                : ""
        )
    }

    private func tabIcon(for tab: TabItem) -> some View {
        let isSelected = selectedTab == tab
        let image = Image(systemName: tab.systemImage(isSelected: isSelected))
            .font(.system(size: 20))
            .foregroundStyle(isSelected ? colors.primary : colors.onSurfaceVariant)

        return Group {
            if tab == .notifications && unreadNotificationCount > 0 {
                image.overlay(alignment: .topTrailing) {
                    NotificationBadge(count: unreadNotificationCount)
                        .scaleEffect(0.7)
                        .offset(x: 10, y: -6)
                }
            } else {
                image
            }
        }
    }
}
