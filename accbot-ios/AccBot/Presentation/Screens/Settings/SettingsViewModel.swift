import Foundation
import LocalAuthentication

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var connectedExchanges: [Exchange] = []
    @Published var deleteTarget: DeleteTarget?
    @Published var biometricType: BiometricType = .none
    @Published var activeAlert: AlertType?
    @Published var planCount = 0
    @Published var transactionCount = 0
    @Published var notificationCount = 0

    // Withdrawal thresholds
    @Published var withdrawalThresholds: [WithdrawalThreshold] = []
    @Published var availableCryptoExchangePairs: [(crypto: String, exchange: Exchange)] = []
    @Published var showWithdrawalThresholdDialog = false

    /// Stable identifiers for ForEach over crypto/exchange pairs.
    var withdrawalPairIds: [String] {
        availableCryptoExchangePairs.map { "\($0.crypto)_\($0.exchange.rawValue)" }
    }

    @Published var errorMessage: String?

    /// Routes an error message to an alert. Call this instead of setting errorMessage directly
    /// when the error should be shown as an alert.
    func showError(_ message: String) {
        activeAlert = .error(message)
    }

    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }

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
            case .plans: return String(localized: "Delete All Plans")
            case .transactions: return String(localized: "Delete All Transactions")
            case .notifications: return String(localized: "Delete All Notifications")
            case .allData: return String(localized: "Delete All Data")
            }
        }

        var message: String {
            switch self {
            case .plans: return String(localized: "This will delete all DCA plans. This action cannot be undone.")
            case .transactions: return String(localized: "This will delete all transaction history. This action cannot be undone.")
            case .notifications: return String(localized: "This will delete all notifications. This action cannot be undone.")
            case .allData: return String(localized: "This will delete ALL data including plans, transactions, credentials, and settings. This action cannot be undone.")
            }
        }
    }

    enum AlertType: Identifiable {
        case deleteConfirmation
        case languageRestart
        case sandboxRestart(isSandbox: Bool)
        case deleteAllDataComplete
        case error(String)

        var id: String {
            switch self {
            case .deleteConfirmation: return "deleteConfirmation"
            case .languageRestart: return "languageRestart"
            case .sandboxRestart: return "sandboxRestart"
            case .deleteAllDataComplete: return "deleteAllDataComplete"
            case .error: return "error"
            }
        }

        var title: String {
            switch self {
            case .deleteConfirmation: return String(localized: "Confirm Delete")
            case .languageRestart: return String(localized: "Restart Required")
            case .sandboxRestart: return String(localized: "Sandbox Mode")
            case .deleteAllDataComplete: return String(localized: "Data Deleted")
            case .error: return String(localized: "Error")
            }
        }
    }

    enum BiometricType {
        case faceId, touchId, none
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadData()
        checkBiometricType()
    }

    func loadData() {
        loadConnectedExchanges()
        loadCounts()
        loadWithdrawalThresholds()
    }

    func loadConnectedExchanges() {
        let isSandbox = deps.userPreferences.isSandboxMode()
        connectedExchanges = deps.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
    }

    func loadCounts() {
        let db = deps.activeDatabase
        do {
            planCount = try db.planDao.getAll().count
            transactionCount = try db.transactionDao.getTotalCount()
            notificationCount = try db.notificationDao.getAll().count
        } catch {
            showError(error.localizedDescription)
        }
    }

    func checkBiometricType() {
        let context = LAContext()
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            switch context.biometryType {
            case .faceID: biometricType = .faceId
            case .touchID: biometricType = .touchId
            @unknown default: biometricType = .none
            }
        } else {
            biometricType = .none
        }
    }

    var biometricLabel: String {
        switch biometricType {
        case .faceId: return String(localized: "Face ID Lock")
        case .touchId: return String(localized: "Touch ID Lock")
        case .none: return String(localized: "Biometric Lock")
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
        activeAlert = .deleteConfirmation
    }

    func executeDelete() {
        guard let target = deleteTarget else { return }
        let db = deps.activeDatabase

        do {
            switch target {
            case .plans:
                try db.planDao.deleteAll()
            case .transactions:
                try db.transactionDao.deleteAll()
            case .notifications:
                try db.notificationDao.deleteAll()
            case .allData:
                try db.transactionDao.deleteAll()
                try db.withdrawalDao.deleteAll()
                try db.notificationDao.deleteAll()
                try db.withdrawalThresholdDao.deleteAll()
                try db.exchangeBalanceDao.deleteAll()
                try db.dailyPriceDao.deleteAll()
                try db.monthlySummaryDao.deleteAll()
                try db.planDao.deleteAll()
                deps.credentialsStore.clearAllBothEnvironments()
                deps.onboardingPreferences.onboardingCompleted = false
                deleteTarget = nil
                loadData()
                activeAlert = .deleteAllDataComplete
                return
            }
        } catch {
            showError(error.localizedDescription)
        }

        deleteTarget = nil
        loadData()
    }

    func loadWithdrawalThresholds() {
        let db = deps.activeDatabase
        do {
            withdrawalThresholds = try db.withdrawalThresholdDao.getAll()

            let plans = try db.planDao.getAll()
            var seen = Set<String>()
            var pairs: [(crypto: String, exchange: Exchange)] = []
            for plan in plans {
                let key = "\(plan.crypto)_\(plan.exchange.rawValue)"
                if !seen.contains(key) {
                    seen.insert(key)
                    pairs.append((crypto: plan.crypto, exchange: plan.exchange))
                }
            }
            availableCryptoExchangePairs = pairs
        } catch {
            showError(error.localizedDescription)
        }
    }

    func setWithdrawalThreshold(crypto: String, exchange: Exchange, amount: Decimal) {
        let threshold = WithdrawalThreshold(
            crypto: crypto,
            exchange: exchange,
            thresholdAmount: amount
        )
        do {
            try deps.activeDatabase.withdrawalThresholdDao.upsert(threshold)
            loadWithdrawalThresholds()
        } catch {
            showError(error.localizedDescription)
        }
    }

    func removeWithdrawalThreshold(crypto: String, exchange: Exchange) {
        do {
            try deps.activeDatabase.withdrawalThresholdDao.delete(
                crypto: crypto,
                exchange: exchange
            )
            loadWithdrawalThresholds()
        } catch {
            showError(error.localizedDescription)
        }
    }

    func setLanguage(_ langCode: String) {
        deps.userPreferences.appLanguage = langCode
        if !langCode.isEmpty {
            UserDefaults.standard.set([langCode], forKey: "AppleLanguages")
        } else {
            UserDefaults.standard.removeObject(forKey: "AppleLanguages")
        }
        activeAlert = .languageRestart
    }

    var lastBackgroundRunText: String {
        guard let date = dependencies?.userPreferences.lastBackgroundRun else {
            return String(localized: "Never")
        }
        return AccBotFormatters.relativeDate(date)
    }
}
