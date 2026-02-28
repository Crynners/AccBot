import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel = NotificationsViewModel()
    @Environment(\.accBotColors) private var colors

    var body: some View {
        Group {
            if viewModel.notifications.isEmpty {
                EmptyStateView(
                    systemImage: "bell.slash",
                    title: String(localized: "You're all caught up!"),
                    subtitle: String(localized: "DCA alerts and updates will appear here")
                )
            } else {
                List {
                    ForEach(viewModel.notifications) { notification in
                        Button {
                            viewModel.markAsRead(notification)
                        } label: {
                            notificationRow(notification)
                        }
                        .buttonStyle(.plain)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                viewModel.deleteNotification(notification)
                            } label: {
                                Label(String(localized: "Delete"), systemImage: "trash")
                            }
                        }
                        .listRowBackground(
                            notification.isRead ? colors.surface : colors.primary.opacity(0.08)
                        )
                        .accessibilityValue(notification.isRead
                            ? String(localized: "Read")
                            : String(localized: "Unread"))
                    }
                }
                .listStyle(.plain)
            }
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Notifications"))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if viewModel.unreadCount > 0 {
                    Button(String(localized: "Mark All Read")) {
                        viewModel.markAllAsRead()
                    }
                } else if !viewModel.notifications.isEmpty {
                    Button(String(localized: "Delete All")) {
                        viewModel.showDeleteAllConfirmation = true
                    }
                }
            }
        }
        .alert(String(localized: "Delete All?"), isPresented: $viewModel.showDeleteAllConfirmation) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Delete All"), role: .destructive) { viewModel.deleteAll() }
        } message: {
            Text(String(localized: "This will permanently delete all read notifications."))
        }
        .alert(String(localized: "Error"), isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button(String(localized: "OK"), role: .cancel) {}
        } message: {
            if let msg = viewModel.errorMessage {
                Text(msg)
            }
        }
        .refreshable {
            await viewModel.refresh()
        }
        .onAppear {
            viewModel.setup(dependencies)
        }
    }

    private func notificationRow(_ notification: AppNotification) -> some View {
        HStack(spacing: Spacing.md) {
            notificationIcon(notification.type)
                .frame(width: 44, height: 44)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                HStack {
                    Text(notification.title)
                        .font(notification.isRead ? AccBotFonts.body : AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)

                    if !notification.isRead {
                        Circle()
                            .fill(colors.primary)
                            .frame(width: 8, height: 8)
                            .accessibilityHidden(true)
                    }
                }

                Text(notification.message)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .lineLimit(2)

                Text(formatDate(notification.createdAt))
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
        .padding(.vertical, Spacing.xs)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(notificationAccessibilityLabel(notification))
    }

    private func notificationAccessibilityLabel(_ notification: AppNotification) -> String {
        let readPrefix = notification.isRead ? "" : "\(String(localized: "Unread")), "
        return "\(readPrefix)\(notification.title). \(notification.message). \(formatDate(notification.createdAt))"
    }

    private func notificationIcon(_ type: NotificationType) -> some View {
        let (icon, color): (String, Color) = switch type {
        case .purchase: ("checkmark.circle.fill", colors.primary)
        case .error: ("exclamationmark.circle.fill", colors.error)
        case .lowBalance: ("exclamationmark.triangle.fill", colors.warning)
        case .withdrawalThreshold: ("arrow.up.forward.circle.fill", colors.warning)
        }

        return ZStack {
            RoundedRectangle(cornerRadius: CornerRadius.sm)
                .fill(color.opacity(0.15))
                .frame(width: 44, height: 44)
            Image(systemName: icon)
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(color)
        }
    }

    private func formatDate(_ date: Date) -> String {
        AccBotFormatters.relativeDate(date)
    }
}
