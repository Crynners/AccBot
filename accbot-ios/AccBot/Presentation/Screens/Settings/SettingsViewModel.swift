import Foundation
import LocalAuthentication

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var connectedExchanges: [Exchange] = []
    @Published var showDeleteConfirmation = false
    @Published var deleteTarget: DeleteTarget?
    @Published var biometricType: BiometricType = .none
    @Published var showLanguageRestart = false

    private let dependencies: AppDependencies

    enum DeleteTarget: Identifiable {
        case plans, transactions, notifications, allData

        var id: String {
            switch self {
            case .plans: return "plans"
            case .transactions: return "transactions"
            case .notifications: return "notifications"
            case .allData: return "allData"
            }
        }

        var title: String {
            switch self {
            case .plans: return "Delete All Plans"
            case .transactions: return "Delete All Transactions"
            case .notifications: return "Delete All Notifications"
            case .allData: return "Delete All Data"
            }
        }

        var message: String {
            switch self {
            case .plans: return "This will delete all DCA plans. This action cannot be undone."
            case .transactions: return "This will delete all transaction history. This action cannot be undone."
            case .notifications: return "This will delete all notifications. This action cannot be undone."
            case .allData: return "This will delete ALL data including plans, transactions, credentials, and settings. This action cannot be undone."
            }
        }
    }

    enum BiometricType {
        case faceId, touchId, none
    }

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
        loadConnectedExchanges()
        checkBiometricType()
    }

    func loadConnectedExchanges() {
        let isSandbox = dependencies.userPreferences.isSandboxMode()
        connectedExchanges = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
    }

    func checkBiometricType() {
        let context = LAContext()
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            switch context.biometryType {
            case .faceID: biometricType = .faceId
            case .touchID: biometricType = .touchId
            default: biometricType = .none
            }
        } else {
            biometricType = .none
        }
    }

    var biometricLabel: String {
        switch biometricType {
        case .faceId: return "Face ID Lock"
        case .touchId: return "Touch ID Lock"
        case .none: return "Biometric Lock"
        }
    }

    var biometricIcon: String {
        switch biometricType {
        case .faceId: return "faceid"
        case .touchId: return "touchid"
        case .none: return "lock"
        }
    }

    func confirmDelete(_ target: DeleteTarget) {
        deleteTarget = target
        showDeleteConfirmation = true
    }

    func executeDelete() {
        guard let target = deleteTarget else { return }
        let db = dependencies.activeDatabase

        do {
            switch target {
            case .plans:
                try db.planDao.deleteAll()
            case .transactions:
                try db.transactionDao.deleteAll()
            case .notifications:
                try db.notificationDao.deleteAll()
            case .allData:
                try db.planDao.deleteAll()
                try db.transactionDao.deleteAll()
                try db.notificationDao.deleteAll()
                try db.withdrawalThresholdDao.deleteAll()
                try db.exchangeBalanceDao.deleteAll()
                try db.dailyPriceDao.deleteAll()
                dependencies.credentialsStore.clearAllBothEnvironments()
                dependencies.onboardingPreferences.onboardingCompleted = false
            }
        } catch {
            // Log error
        }

        deleteTarget = nil
        loadConnectedExchanges()
    }

    func setLanguage(_ langCode: String) {
        dependencies.userPreferences.appLanguage = langCode
        if !langCode.isEmpty {
            UserDefaults.standard.set([langCode], forKey: "AppleLanguages")
        } else {
            UserDefaults.standard.removeObject(forKey: "AppleLanguages")
        }
        showLanguageRestart = true
    }

    var lastBackgroundRunText: String {
        guard let date = dependencies.userPreferences.lastBackgroundRun else {
            return "Never"
        }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
