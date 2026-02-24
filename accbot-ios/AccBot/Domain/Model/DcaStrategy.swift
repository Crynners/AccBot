import Foundation

/// ATH distance tier configuration
struct AthTier: Codable, Equatable {
    let maxDistancePercent: Float
    let multiplier: Float
}

/// Default ATH tiers:
/// - 0-10% below ATH: buy 50% (market is hot)
/// - 10-30% below: buy 100% (normal)
/// - 30-50% below: buy 150%
/// - 50-70% below: buy 200%
/// - 70%+ below: buy 300% (maximum opportunity)
let defaultAthTiers: [AthTier] = [
    AthTier(maxDistancePercent: 0.10, multiplier: 0.5),
    AthTier(maxDistancePercent: 0.30, multiplier: 1.0),
    AthTier(maxDistancePercent: 0.50, multiplier: 1.5),
    AthTier(maxDistancePercent: 0.70, multiplier: 2.0),
    AthTier(maxDistancePercent: 1.00, multiplier: 3.0),
]

/// Fear & Greed index tier configuration
struct FearGreedTier: Codable, Equatable {
    let maxIndex: Int
    let multiplier: Float
}

/// Default Fear & Greed tiers:
/// - Extreme Fear (0-24): buy 250%
/// - Fear (25-44): buy 150%
/// - Neutral (45-54): buy 100%
/// - Greed (55-74): buy 50%
/// - Extreme Greed (75-100): buy 25%
let defaultFearGreedTiers: [FearGreedTier] = [
    FearGreedTier(maxIndex: 24, multiplier: 2.5),
    FearGreedTier(maxIndex: 44, multiplier: 1.5),
    FearGreedTier(maxIndex: 54, multiplier: 1.0),
    FearGreedTier(maxIndex: 74, multiplier: 0.5),
    FearGreedTier(maxIndex: 100, multiplier: 0.25),
]

/// DCA Strategy types with their configurations
enum DcaStrategy: Equatable {
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
}
