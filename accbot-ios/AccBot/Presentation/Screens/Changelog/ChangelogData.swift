import Foundation

struct ChangelogEntry: Identifiable {
    let id: Int  // build number this entry was introduced
    let version: String
    let title: String
    let features: [String]
}

/// Hardcoded changelog entries, ordered newest first.
enum ChangelogData {
    static let entries: [ChangelogEntry] = [
        ChangelogEntry(
            id: 3,
            version: "1.2.0",
            title: "Goal Tracking, Charts & Search",
            features: [
                "Target amount goal tracking with progress bar on dashboard",
                "Avg buy price chart line in portfolio (single-pair FIAT mode)",
                "Improved portfolio KPIs: Invested, Crypto Price, Accumulated",
                "Strategy name shown inline next to pair in plan cards",
                "Transaction history search",
                "Changelog / What's New screen",
                "Zoom header moved below chart in portrait",
                "Notification info help button in settings",
            ]
        ),
        ChangelogEntry(
            id: 2,
            version: "1.1.0",
            title: "Notifications, Settings & Dashboard",
            features: [
                "Push notification support for purchases and errors",
                "Weekly summary notifications",
                "Reorganized settings with WCAG accessibility",
                "Swipeable tab navigation matching Android",
                "Dashboard holdings with real-time price data",
            ]
        ),
        ChangelogEntry(
            id: 1,
            version: "1.0.0",
            title: "Initial Release",
            features: [
                "DCA plan management with multiple exchanges",
                "Portfolio analytics with interactive charts",
                "Transaction history with filters and CSV export",
                "Backup & restore with encryption",
                "Biometric lock and sandbox mode",
            ]
        ),
    ]

    /// Returns entries newer than the given build number, newest first.
    static func getNewEntries(since buildNumber: Int) -> [ChangelogEntry] {
        entries.filter { $0.id > buildNumber }
    }
}
