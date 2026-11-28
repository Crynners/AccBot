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

    /// Last time the Shortcuts automation ran the "run due purchases" action.
    @Published var lastShortcutRun: Date? {
        didSet { UserPreferences.store(lastShortcutRun, key: Keys.lastShortcutRun, in: defaults) }
    }

    /// Last run of due purchases from any trigger, and which trigger it was.
    @Published var lastAutomationRun: Date? {
        didSet { UserPreferences.store(lastAutomationRun, key: Keys.lastAutomationRun, in: defaults) }
    }

    @Published var lastAutomationTrigger: String? {
        didSet { defaults.set(lastAutomationTrigger, forKey: Keys.lastAutomationTrigger) }
    }

    /// When the user said in the setup guide that the Shortcuts automation is set up.
    /// Until its first run, this keeps the "not set up" warning away for a while.
    @Published var shortcutSetupConfirmedAt: Date? {
        didSet { UserPreferences.store(shortcutSetupConfirmedAt, key: Keys.shortcutSetupConfirmedAt, in: defaults) }
    }

    /// Whether the Shortcuts automation looks set up: it ran, or the user just set it up.
    func isShortcutAutomationActive(now: Date = Date()) -> Bool {
        let window: TimeInterval = 36 * 3600
        let recent = { (date: Date?) in date.map { now.timeIntervalSince($0) < window } ?? false }
        return recent(lastShortcutRun) || recent(shortcutSetupConfirmedAt)
    }

    func recordAutomationRun(_ trigger: PurchaseTrigger, at date: Date = Date()) {
        lastAutomationRun = date
        lastAutomationTrigger = trigger.rawValue
        if trigger == .shortcut {
            lastShortcutRun = date
        }
    }

    private static func store(_ date: Date?, key: String, in defaults: UserDefaults) {
        if let date {
            defaults.set(date.timeIntervalSince1970, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    private static func date(forKey key: String, in defaults: UserDefaults) -> Date? {
        let timestamp = defaults.double(forKey: key)
        return timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : nil
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
        self.marketPulseEnabled = defaults.object(forKey: Keys.marketPulseEnabled) as? Bool ?? false
        self.marketPulseExpanded = defaults.object(forKey: Keys.marketPulseExpanded) as? Bool ?? true
        self.lowBalanceThresholdDays = max(1, min(14, defaults.object(forKey: Keys.lowBalanceThresholdDays) as? Int ?? 2))
        self.lastSeenBuildNumber = defaults.integer(forKey: Keys.lastSeenBuildNumber)
        let bgTimestamp = defaults.double(forKey: Keys.lastBackgroundRun)
        self.lastBackgroundRun = bgTimestamp > 0 ? Date(timeIntervalSince1970: bgTimestamp) : nil
        self.lastShortcutRun = UserPreferences.date(forKey: Keys.lastShortcutRun, in: defaults)
        self.lastAutomationRun = UserPreferences.date(forKey: Keys.lastAutomationRun, in: defaults)
        self.lastAutomationTrigger = defaults.string(forKey: Keys.lastAutomationTrigger)
        self.shortcutSetupConfirmedAt = UserPreferences.date(forKey: Keys.shortcutSetupConfirmedAt, in: defaults)
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
        static let lastShortcutRun = "lastShortcutRun"
        static let lastAutomationRun = "lastAutomationRun"
        static let lastAutomationTrigger = "lastAutomationTrigger"
        static let shortcutSetupConfirmedAt = "shortcutSetupConfirmedAt"
    }
}
