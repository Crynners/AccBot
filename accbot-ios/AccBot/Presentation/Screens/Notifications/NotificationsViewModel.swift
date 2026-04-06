import Foundation
import Combine

@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published var notifications: [AppNotification] = []
    @Published var unreadCount = 0
    @Published var showDeleteAllConfirmation = false
    @Published var errorMessage: String?

    private var cancellables = Set<AnyCancellable>()
    private var autoMarkTask: Task<Void, Never>?
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false
    private var isVisible = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() - call setup() in onAppear")
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
        // Combine both publishers into a single debounced update to avoid
        // double view rebuilds when a single DB change fires both publishers.
        deps.activeDatabase.notificationDao.observeAll()
            .combineLatest(deps.activeDatabase.notificationDao.observeUnreadCount())
            .debounce(for: .milliseconds(100), scheduler: DispatchQueue.main)
            .sink(
                receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        #if DEBUG
                        print("[NotificationsVM] Observation error: \(error.localizedDescription)")
                        #endif
                    }
                },
                receiveValue: { [weak self] notifications, count in
                    self?.notifications = notifications
                    self?.unreadCount = count
                    if count > 0, self?.isVisible == true {
                        self?.scheduleAutoMarkAsRead()
                    }
                }
            )
            .store(in: &cancellables)
    }

    func refresh() async {
        loadData()
    }

    /// Called from onAppear - marks the tab as visible and schedules auto-mark.
    func onTabVisible() {
        isVisible = true
        scheduleAutoMarkAsRead()
    }

    /// Called from onDisappear - cancels pending auto-mark timer.
    func onTabHidden() {
        isVisible = false
        autoMarkTask?.cancel()
        autoMarkTask = nil
    }

    private func scheduleAutoMarkAsRead() {
        autoMarkTask?.cancel()
        guard unreadCount > 0 else { return }
        autoMarkTask = Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !Task.isCancelled else { return }
            markAllAsRead()
        }
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
            try deps.activeDatabase.notificationDao.deleteAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
