import SwiftUI

struct NotificationsView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel: NotificationsViewModel

    init() {
        _viewModel = StateObject(wrappedValue: NotificationsViewModel(
            dependencies: AppDependencies()
        ))
    }

    var body: some View {
        VStack(spacing: 0) {
            // Active / Archive toggle
            Picker("", selection: $viewModel.showArchive) {
                Text("Active").tag(false)
                Text("Archive").tag(true)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)

            if viewModel.showArchive {
                archiveList
            } else {
                activeList
            }
        }
        .background(Color.backgroundDark)
        .navigationTitle("Notifications")
        .toolbar {
            if !viewModel.showArchive && !viewModel.activeNotifications.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button("Dismiss All") {
                        viewModel.dismissAll()
                    }
                }
            }
            if viewModel.showArchive && !viewModel.archivedNotifications.isEmpty {
                ToolbarItem(placement: .primaryAction) {
                    Button("Clear Archive") {
                        viewModel.showClearArchiveConfirmation = true
                    }
                }
            }
        }
        .alert("Clear Archive", isPresented: $viewModel.showClearArchiveConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) { viewModel.clearArchive() }
        } message: {
            Text("This will permanently delete all archived notifications.")
        }
        .onAppear {
            viewModel.loadData()
        }
    }

    private var activeList: some View {
        Group {
            if viewModel.activeNotifications.isEmpty {
                EmptyStateView(
                    icon: "bell.slash",
                    title: "No Notifications",
                    subtitle: "DCA alerts and updates will appear here"
                )
            } else {
                List {
                    ForEach(viewModel.activeNotifications) { notification in
                        notificationRow(notification)
                            .swipeActions(edge: .trailing) {
                                Button {
                                    viewModel.archive(notification)
                                } label: {
                                    Label("Archive", systemImage: "archivebox")
                                }
                                .tint(.warningOrange)
                            }
                            .listRowBackground(Color.surfaceDark)
                            .onTapGesture {
                                viewModel.markAsRead(notification)
                            }
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
                    icon: "archivebox",
                    title: "No Archived Notifications",
                    subtitle: "Dismissed notifications will appear here"
                )
            } else {
                List {
                    ForEach(viewModel.archivedNotifications) { notification in
                        notificationRow(notification)
                            .listRowBackground(Color.surfaceDark)
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    private func notificationRow(_ notification: AppNotification) -> some View {
        HStack(spacing: Spacing.md) {
            notificationIcon(notification.type)
                .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                HStack {
                    Text(notification.title)
                        .font(notification.isRead ? AccBotFonts.body : AccBotFonts.headline)
                        .foregroundColor(.white)

                    if !notification.isRead {
                        Circle()
                            .fill(Color.accentTeal)
                            .frame(width: 8, height: 8)
                    }
                }

                Text(notification.message)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(.onSurfaceVariantColor)
                    .lineLimit(2)

                Text(formatDate(notification.createdAt))
                    .font(AccBotFonts.captionSmall)
                    .foregroundColor(.onSurfaceVariantColor)
            }
        }
        .padding(.vertical, Spacing.xs)
    }

    private func notificationIcon(_ type: NotificationType) -> some View {
        let (icon, color): (String, Color) = switch type {
        case .purchase: ("checkmark.circle.fill", .accentTeal)
        case .error: ("xmark.circle.fill", .errorRed)
        case .lowBalance: ("exclamationmark.triangle.fill", .warningOrange)
        case .withdrawalThreshold: ("arrow.up.forward.circle.fill", .accentTeal)
        }

        return Image(systemName: icon)
            .font(.title2)
            .foregroundColor(color)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
