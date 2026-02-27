import Foundation
import Combine

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var activeNotifications: [AppNotification] = []
    @Published var archivedNotifications: [AppNotification] = []
    @Published var showArchive = false
    @Published var unreadCount = 0
    @Published var showClearArchiveConfirmation = false
    @Published var errorMessage: String?

    private var cancellables = Set<AnyCancellable>()
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            assertionFailure("ViewModel used before setup() — call setup() in onAppear")
            return dependencies!
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
            activeNotifications = try deps.activeDatabase.notificationDao.getActive()
            archivedNotifications = try deps.activeDatabase.notificationDao.getArchived()
            unreadCount = try deps.activeDatabase.notificationDao.getUnreadCount()
        } catch {
            activeNotifications = []
            archivedNotifications = []
            errorMessage = error.localizedDescription
        }
    }

    private func observeNotifications() {
        deps.activeDatabase.notificationDao.observeActive()
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
                    self?.activeNotifications = notifications
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
        // Archived list not observed — reload manually
        loadArchivedNotifications()
    }

    func archive(_ notification: AppNotification) {
        do {
            try deps.activeDatabase.notificationDao.archive(id: notification.id)
        } catch {
            errorMessage = error.localizedDescription
        }
        // Active list updates via observer; reload archived list
        loadArchivedNotifications()
    }

    func dismissAll() {
        do {
            try deps.activeDatabase.notificationDao.archiveAll()
        } catch {
            errorMessage = error.localizedDescription
        }
        loadArchivedNotifications()
    }

    func clearArchive() {
        do {
            try deps.activeDatabase.notificationDao.clearArchive()
        } catch {
            errorMessage = error.localizedDescription
        }
        loadArchivedNotifications()
    }

    private func loadArchivedNotifications() {
        do {
            archivedNotifications = try deps.activeDatabase.notificationDao.getArchived()
        } catch {
            archivedNotifications = []
        }
    }
}
