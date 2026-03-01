import Foundation
import Combine

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var notifications: [AppNotification] = []
    @Published var unreadCount = 0
    @Published var showDeleteAllConfirmation = false
    @Published var errorMessage: String?

    private var cancellables = Set<AnyCancellable>()
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        observeNotifications()
    }

    func loadData() {
        do {
            notifications = try deps.activeDatabase.notificationDao.getAll()
            unreadCount = try deps.activeDatabase.notificationDao.getUnreadCount()
        } catch {
            notifications = []
            errorMessage = error.localizedDescription
        }
    }

    private func observeNotifications() {
        deps.activeDatabase.notificationDao.observeAll()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        #if DEBUG
                        print("[NotificationsVM] Observation error: \(error.localizedDescription)")
                        #endif
                    }
                },
                receiveValue: { [weak self] notifications in
                    self?.notifications = notifications
                }
            )
            .store(in: &cancellables)

        deps.activeDatabase.notificationDao.observeUnreadCount()
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        #if DEBUG
                        print("[NotificationsVM] Observation error: \(error.localizedDescription)")
                        #endif
                    }
                },
                receiveValue: { [weak self] count in
                    self?.unreadCount = count
                }
            )
            .store(in: &cancellables)
    }

    func refresh() async {
        loadData()
    }

    func markAsRead(_ notification: AppNotification) {
        do {
            try deps.activeDatabase.notificationDao.markAsRead(id: notification.id)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func markAllAsRead() {
        do {
            try deps.activeDatabase.notificationDao.markAllAsRead()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteNotification(_ notification: AppNotification) {
        do {
            try deps.activeDatabase.notificationDao.delete(id: notification.id)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteAll() {
        do {
            try deps.activeDatabase.notificationDao.deleteAllRead()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
