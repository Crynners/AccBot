import Foundation
import Combine

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var activeNotifications: [AppNotification] = []
    @Published var archivedNotifications: [AppNotification] = []
    @Published var showArchive = false
    @Published var unreadCount = 0
    @Published var showClearArchiveConfirmation = false

    private var cancellables = Set<AnyCancellable>()
    private let dependencies: AppDependencies

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
        observeNotifications()
    }

    func loadData() {
        do {
            activeNotifications = try dependencies.activeDatabase.notificationDao.getActive()
            archivedNotifications = try dependencies.activeDatabase.notificationDao.getArchived()
            unreadCount = try dependencies.activeDatabase.notificationDao.getUnreadCount()
        } catch {
            activeNotifications = []
            archivedNotifications = []
        }
    }

    private func observeNotifications() {
        dependencies.activeDatabase.notificationDao.observeActive()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { [weak self] notifications in
                    self?.activeNotifications = notifications
                }
            )
            .store(in: &cancellables)

        dependencies.activeDatabase.notificationDao.observeUnreadCount()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { _ in },
                receiveValue: { [weak self] count in
                    self?.unreadCount = count
                }
            )
            .store(in: &cancellables)
    }

    func markAsRead(_ notification: AppNotification) {
        try? dependencies.activeDatabase.notificationDao.markAsRead(id: notification.id)
        loadData()
    }

    func archive(_ notification: AppNotification) {
        try? dependencies.activeDatabase.notificationDao.archive(id: notification.id)
        loadData()
    }

    func dismissAll() {
        try? dependencies.activeDatabase.notificationDao.archiveAll()
        loadData()
    }

    func clearArchive() {
        try? dependencies.activeDatabase.notificationDao.clearArchive()
        loadData()
    }
}
