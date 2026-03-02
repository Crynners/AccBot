import Foundation

/// Non-sensitive settings stored in UserDefaults (same as Android SharedPreferences).
/// UserDefaults is thread-safe for reads; @Published writes happen from UI (MainActor).
final class UserPreferences: ObservableObject {
    private let defaults: UserDefaults
    private let suiteName = "com.accbot.dca.preferences"

    convenience init() {
        self.init(defaults: UserDefaults(suiteName: "com.accbot.dca.preferences") ?? .standard)
    }

    // MARK: - Theme

    @Published var appTheme: AppTheme {
        didSet { defaults.set(appTheme.rawValue, forKey: Keys.appTheme) }
    }

    // MARK: - Notifications

    @Published var notificationsEnabled: Bool {
        didSet { defaults.set(notificationsEnabled, forKey: Keys.notificationsEnabled) }
    }

    @Published var purchaseNotifications: Bool {
        didSet { defaults.set(purchaseNotifications, forKey: Keys.purchaseNotifications) }
    }

    @Published var errorNotifications: Bool {
        didSet { defaults.set(errorNotifications, forKey: Keys.errorNotifications) }
    }

    @Published var weeklySummaryNotifications: Bool {
        didSet { defaults.set(weeklySummaryNotifications, forKey: Keys.weeklySummaryNotifications) }
    }

    // MARK: - Language

    @Published var appLanguage: String {
        didSet { defaults.set(appLanguage, forKey: Keys.appLanguage) }
    }

    // MARK: - Security

    @Published var biometricLockEnabled: Bool {
        didSet { defaults.set(biometricLockEnabled, forKey: Keys.biometricLockEnabled) }
    }

    // MARK: - Sandbox

    @Published var sandboxMode: Bool {
        didSet { defaults.set(sandboxMode, forKey: Keys.sandboxMode) }
    }

    // MARK: - Low Balance

    @Published var lowBalanceThresholdDays: Int {
        didSet { defaults.set(lowBalanceThresholdDays, forKey: Keys.lowBalanceThresholdDays) }
    }

    // MARK: - Market Pulse

    @Published var marketPulseEnabled: Bool {
        didSet { defaults.set(marketPulseEnabled, forKey: Keys.marketPulseEnabled) }
    }

    @Published var marketPulseExpanded: Bool {
        didSet { defaults.set(marketPulseExpanded, forKey: Keys.marketPulseExpanded) }
    }

    // MARK: - Changelog

    @Published var lastSeenBuildNumber: Int {
        didSet { defaults.set(lastSeenBuildNumber, forKey: Keys.lastSeenBuildNumber) }
    }

    // MARK: - Background Execution

    @Published var lastBackgroundRun: Date? {
        didSet {
            if let date = lastBackgroundRun {
                defaults.set(date.timeIntervalSince1970, forKey: Keys.lastBackgroundRun)
            } else {
                defaults.removeObject(forKey: Keys.lastBackgroundRun)
            }
        }
    }

    // MARK: - Initialization

    init(defaults: UserDefaults) {
        self.defaults = defaults
        self.appTheme = AppTheme(rawValue: defaults.string(forKey: Keys.appTheme) ?? "") ?? .system
        self.notificationsEnabled = defaults.object(forKey: Keys.notificationsEnabled) as? Bool ?? true
        self.purchaseNotifications = defaults.object(forKey: Keys.purchaseNotifications) as? Bool ?? true
        self.errorNotifications = defaults.object(forKey: Keys.errorNotifications) as? Bool ?? true
        self.weeklySummaryNotifications = defaults.object(forKey: Keys.weeklySummaryNotifications) as? Bool ?? true
        self.appLanguage = defaults.string(forKey: Keys.appLanguage) ?? ""
        self.biometricLockEnabled = defaults.bool(forKey: Keys.biometricLockEnabled)
        self.sandboxMode = defaults.bool(forKey: Keys.sandboxMode)
        self.marketPulseEnabled = defaults.object(forKey: Keys.marketPulseEnabled) as? Bool ?? true
        self.marketPulseExpanded = defaults.object(forKey: Keys.marketPulseExpanded) as? Bool ?? true
        self.lowBalanceThresholdDays = max(1, min(14, defaults.object(forKey: Keys.lowBalanceThresholdDays) as? Int ?? 2))
        self.lastSeenBuildNumber = defaults.integer(forKey: Keys.lastSeenBuildNumber)
        let bgTimestamp = defaults.double(forKey: Keys.lastBackgroundRun)
        self.lastBackgroundRun = bgTimestamp > 0 ? Date(timeIntervalSince1970: bgTimestamp) : nil
    }

    func isSandboxMode() -> Bool { sandboxMode }

    // MARK: - Keys

    private enum Keys {
        static let appTheme = "appTheme"
        static let notificationsEnabled = "notificationsEnabled"
        static let purchaseNotifications = "purchaseNotifications"
        static let errorNotifications = "errorNotifications"
        static let weeklySummaryNotifications = "weeklySummaryNotifications"
        static let appLanguage = "appLanguage"
        static let biometricLockEnabled = "biometricLockEnabled"
        static let sandboxMode = "sandboxMode"
        static let marketPulseEnabled = "marketPulseEnabled"
        static let marketPulseExpanded = "marketPulseExpanded"
        static let lowBalanceThresholdDays = "lowBalanceThresholdDays"
        static let lastSeenBuildNumber = "lastSeenBuildNumber"
        static let lastBackgroundRun = "lastBackgroundRun"
    }
}
