import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel = NotificationsViewModel()
    @Environment(\.accBotColors) private var colors
    @State private var showDismissAllConfirmation = false

    var body: some View {
        VStack(spacing: 0) {
            // Active / Archive toggle
            Picker(String(localized: "Notification filter"), selection: $viewModel.showArchive) {
                Text(String(localized: "Active")).tag(false)
                Text(viewModel.archivedNotifications.isEmpty
                     ? String(localized: "Archive")
                     : String(localized: "Archive (\(viewModel.archivedNotifications.count))")
                ).tag(true)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)

            if viewModel.showArchive {
                archiveList
            } else {
                activeList
            }
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Notifications"))
        .toolbar {
            if !viewModel.showArchive && !viewModel.activeNotifications.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button(String(localized: "Dismiss All")) {
                        showDismissAllConfirmation = true
                    }
                    .accessibilityHint(String(localized: "Archives all active notifications"))
                }
            }
            if viewModel.showArchive && !viewModel.archivedNotifications.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button(String(localized: "Clear Archive")) {
                        viewModel.showClearArchiveConfirmation = true
                    }
                }
            }
        }
        .alert(String(localized: "Clear Archive"), isPresented: $viewModel.showClearArchiveConfirmation) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Clear"), role: .destructive) { viewModel.clearArchive() }
        } message: {
            Text(String(localized: "This will permanently delete all archived notifications."))
        }
        .alert(String(localized: "Dismiss All?"), isPresented: $showDismissAllConfirmation) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Dismiss All"), role: .destructive) { viewModel.dismissAll() }
        } message: {
            Text(String(localized: "All active notifications will be archived."))
        }
        .refreshable {
            await viewModel.refresh()
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
        .onAppear {
            viewModel.setup(dependencies)
        }
    }

    private var activeList: some View {
        Group {
            if viewModel.activeNotifications.isEmpty {
                EmptyStateView(
                    systemImage: "bell.slash",
                    title: String(localized: "You're all caught up!"),
                    subtitle: String(localized: "DCA alerts and updates will appear here")
                )
            } else {
                List {
                    ForEach(viewModel.activeNotifications) { notification in
                        Button {
                            viewModel.markAsRead(notification)
                        } label: {
                            notificationRow(notification)
                        }
                        .buttonStyle(.plain)
                        .swipeActions(edge: .trailing) {
                            Button {
                                viewModel.archive(notification)
                            } label: {
                                Label(String(localized: "Archive"), systemImage: "archivebox")
                            }
                            .tint(colors.warning)
                        }
                        .listRowBackground(colors.surface)
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    private var archiveList: some View {
        Group {
            if viewModel.archivedNotifications.isEmpty {
                EmptyStateView(
                    systemImage: "archivebox",
                    title: String(localized: "Archive is empty"),
                    subtitle: String(localized: "Dismissed notifications will appear here")
                )
            } else {
                List {
                    ForEach(viewModel.archivedNotifications) { notification in
                        notificationRow(notification)
                            .listRowBackground(colors.surface)
                    }
                }
                .listStyle(.plain)
            }
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
