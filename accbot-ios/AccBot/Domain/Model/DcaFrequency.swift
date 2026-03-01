import Foundation

/// DCA purchase frequency options
enum DcaFrequency: String, Codable, CaseIterable, Sendable {
    case every15Min = "EVERY_15_MIN"
    case hourly = "HOURLY"
    case every4Hours = "EVERY_4_HOURS"
    case every8Hours = "EVERY_8_HOURS"
    case daily = "DAILY"
    case weekly = "WEEKLY"
    case custom = "CUSTOM"

    var displayName: String {
        switch self {
        case .every15Min: return String(localized: "Every 15 minutes")
        case .hourly: return String(localized: "Hourly")
        case .every4Hours: return String(localized: "Every 4 hours")
        case .every8Hours: return String(localized: "Every 8 hours")
        case .daily: return String(localized: "Daily")
        case .weekly: return String(localized: "Weekly")
        case .custom: return String(localized: "Custom (CRON)")
        }
    }

    /// Interval in minutes (0 for custom, uses cronExpression)
    var intervalMinutes: Int {
        switch self {
        case .every15Min: return 15
        case .hourly: return 60
        case .every4Hours: return 240
        case .every8Hours: return 480
        case .daily: return 1440
        case .weekly: return 10080
        case .custom: return 0
        }
    }

    /// Whether this frequency can be reliably executed in iOS background
    var reliableInBackground: Bool {
        switch self {
        case .every15Min, .hourly: return false
        case .every4Hours, .every8Hours, .daily, .weekly, .custom: return true
        }
    }

    /// Warning text for frequencies that may not execute reliably in background on iOS
    var backgroundWarning: String? {
        if !reliableInBackground {
            return String(localized: "iOS may not execute this frequency reliably in the background. For best results, open the app regularly.")
        }
        return nil
    }
}
