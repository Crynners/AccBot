import Foundation

/// All navigation routes in the app (replaces Android's Screen sealed class)
enum AppRoute: Hashable {
    // Onboarding flow
    case welcome
    case security
    case exchangeSetup
    case firstPlan
    case onboardingComplete

    // Plan screens
    case addPlan
    case planDetails(Int64)
    case editPlan(Int64)
    case importCsv(Int64)

    // Exchange screens
    case exchangeManagement
    case exchangeDetail(Exchange)
    case addExchange(Exchange?)

    // History screens
    case history(crypto: String? = nil, fiat: String? = nil)
    case transactionDetails(Int64)
}

/// Bottom navigation tab items
enum TabItem: Int, CaseIterable, Identifiable {
    case dashboard = 0
    case portfolio = 1
    case notifications = 2
    case settings = 3

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .dashboard: return String(localized: "Dashboard")
        case .portfolio: return String(localized: "Portfolio")
        case .notifications: return String(localized: "Notifications")
        case .settings: return String(localized: "Settings")
        }
    }

    var systemImage: String {
        switch self {
        case .dashboard: return "chart.bar.fill"
        case .portfolio: return "chart.pie.fill"
        case .notifications: return "bell.fill"
        case .settings: return "gearshape.fill"
        }
    }
}
