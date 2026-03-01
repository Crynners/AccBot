import Foundation

/// ATH distance tier configuration
struct AthTier: Codable, Equatable, Sendable {
    let maxDistancePercent: Float
    let multiplier: Float
}

/// Default ATH tiers (even 20-point bands):
/// - 0-20% below ATH: buy 50% (market is hot)
/// - 20-40% below: buy 100% (normal)
/// - 40-60% below: buy 150%
/// - 60-80% below: buy 200%
/// - 80%+ below: buy 300% (maximum opportunity)
let defaultAthTiers: [AthTier] = [
    AthTier(maxDistancePercent: 0.20, multiplier: 0.5),
    AthTier(maxDistancePercent: 0.40, multiplier: 1.0),
    AthTier(maxDistancePercent: 0.60, multiplier: 1.5),
    AthTier(maxDistancePercent: 0.80, multiplier: 2.0),
    AthTier(maxDistancePercent: 1.00, multiplier: 3.0),
]

/// Fear & Greed index tier configuration
struct FearGreedTier: Codable, Equatable, Sendable {
    let maxIndex: Int
    let multiplier: Float
}

/// Single source of truth for Fear & Greed classification.
/// Used by DashboardViewModel, DashboardView, and StrategyInfoSheet.
enum FearGreedClassification {
    /// Localized sentiment label for an F&G index value
    static func label(for value: Int) -> String {
        switch value {
        case 0...19: return String(localized: "Extreme Fear")
        case 20...39: return String(localized: "Fear")
        case 40...59: return String(localized: "Neutral")
        case 60...79: return String(localized: "Greed")
        default: return String(localized: "Extreme Greed")
        }
    }

    /// Range string for tier at given index in defaultFearGreedTiers
    static func rangeString(tierIndex: Int) -> String {
        let tier = defaultFearGreedTiers[tierIndex]
        let lower = tierIndex == 0 ? 0 : defaultFearGreedTiers[tierIndex - 1].maxIndex + 1
        return "\(lower) - \(tier.maxIndex)"
    }
}

/// Default Fear & Greed tiers (even 20-point bands):
/// - Extreme Fear (0-19): buy 250%
/// - Fear (20-39): buy 150%
/// - Neutral (40-59): buy 100%
/// - Greed (60-79): buy 50%
/// - Extreme Greed (80-100): buy 25%
let defaultFearGreedTiers: [FearGreedTier] = [
    FearGreedTier(maxIndex: 19, multiplier: 2.5),
    FearGreedTier(maxIndex: 39, multiplier: 1.5),
    FearGreedTier(maxIndex: 59, multiplier: 1.0),
    FearGreedTier(maxIndex: 79, multiplier: 0.5),
    FearGreedTier(maxIndex: 100, multiplier: 0.25),
]

/// DCA Strategy types with their configurations
enum DcaStrategy: Equatable, Sendable {
    case classic
    case athBased(tiers: [AthTier] = defaultAthTiers)
    case fearAndGreed(tiers: [FearGreedTier] = defaultFearGreedTiers)

    var displayName: String {
        switch self {
        case .classic: return String(localized: "Classic")
        case .athBased: return String(localized: "ATH-Based")
        case .fearAndGreed: return String(localized: "Fear & Greed")
        }
    }

    var description: String {
        switch self {
        case .classic:
            return String(localized: "Fixed amount at regular intervals. Simple and effective.")
        case .athBased:
            return String(localized: "Buy more when price is far from All-Time High. Buy less near ATH.")
        case .fearAndGreed:
            return String(localized: "Buy more during market fear, less during greed. Uses Fear & Greed Index.")
        }
    }

    /// Database serialization string
    var dbString: String {
        switch self {
        case .classic: return "CLASSIC"
        case .athBased: return "ATH_BASED"
        case .fearAndGreed: return "FEAR_AND_GREED"
        }
    }

    /// Deserialize from database string
    static func fromDbString(_ value: String) -> DcaStrategy {
        switch value {
        case "CLASSIC": return .classic
        case "ATH_BASED": return .athBased()
        case "FEAR_AND_GREED": return .fearAndGreed()
        default: return .classic
        }
    }

    static let allStrategies: [DcaStrategy] = [.classic, .athBased(), .fearAndGreed()]
}

/// Market data required for strategy calculations
struct MarketData {
    let currentPrice: Decimal
    let allTimeHigh: Decimal?
    let fearGreedIndex: Int?
}

/// Result of strategy multiplier calculation
struct StrategyMultiplierResult {
    let multiplier: Float
    let reason: String
    let marketData: MarketData?

    init(multiplier: Float, reason: String, marketData: MarketData? = nil) {
        self.multiplier = multiplier
        self.reason = reason
        self.marketData = marketData
    }
}
