import Foundation
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "DataParsing")

/// GRDB Record for dca_plans table
struct DcaPlanRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "dca_plans"

    var id: Int64?
    var exchange: String
    var connectionId: Int64 = 0
    var crypto: String
    var fiat: String
    var amount: String      // Decimal stored as TEXT
    var frequency: String
    var cronExpression: String?
    var strategy: String
    var isEnabled: Bool
    var withdrawalEnabled: Bool
    var withdrawalAddress: String?
    var targetAmount: String? // Decimal stored as TEXT, nil = no goal
    var createdAt: Double    // timeIntervalSince1970
    var lastExecutedAt: Double?
    var nextExecutionAt: Double?
    var name: String = ""           // Optional custom label
    var displayOrder: Int = 0       // Dashboard sort order (lower = higher)
    var networkRetryCount: Int
    var nextNetworkRetryAt: Double?
    var originalScheduledAt: Double?
    var missedPurchaseCount: Int

    // MARK: - Domain Mapping

    func toDomain() -> DcaPlan {
        if Exchange(rawValue: exchange) == nil {
            logger.warning("Unknown exchange rawValue '\(self.exchange)' in plan \(self.id ?? 0), defaulting to coinmate")
        }
        if Decimal(string: amount) == nil {
            logger.error("Invalid amount '\(self.amount)' in plan \(self.id ?? 0)")
        }
        return DcaPlan(
            id: id ?? 0,
            exchange: Exchange(rawValue: exchange) ?? .coinmate,
            connectionId: connectionId,
            crypto: crypto,
            fiat: fiat,
            amount: Decimal(string: amount) ?? 0,
            frequency: DcaFrequency(rawValue: frequency) ?? .daily,
            cronExpression: cronExpression,
            strategy: DcaStrategy.fromDbString(strategy),
            isEnabled: isEnabled,
            withdrawalEnabled: withdrawalEnabled,
            withdrawalAddress: withdrawalAddress,
            targetAmount: targetAmount.flatMap { Decimal(string: $0) },
            name: name,
            displayOrder: displayOrder,
            createdAt: Date(timeIntervalSince1970: createdAt),
            lastExecutedAt: lastExecutedAt.map { Date(timeIntervalSince1970: $0) },
            nextExecutionAt: nextExecutionAt.map { Date(timeIntervalSince1970: $0) },
            networkRetryCount: networkRetryCount,
            nextNetworkRetryAt: nextNetworkRetryAt.map { Date(timeIntervalSince1970: $0) },
            originalScheduledAt: originalScheduledAt.map { Date(timeIntervalSince1970: $0) },
            missedPurchaseCount: missedPurchaseCount
        )
    }

    static func fromDomain(_ plan: DcaPlan) -> DcaPlanRecord {
        DcaPlanRecord(
            id: plan.id == 0 ? nil : plan.id,
            exchange: plan.exchange.rawValue,
            connectionId: plan.connectionId,
            crypto: plan.crypto,
            fiat: plan.fiat,
            amount: "\(plan.amount)",
            frequency: plan.frequency.rawValue,
            cronExpression: plan.cronExpression,
            strategy: plan.strategy.dbString,
            isEnabled: plan.isEnabled,
            withdrawalEnabled: plan.withdrawalEnabled,
            withdrawalAddress: plan.withdrawalAddress,
            targetAmount: plan.targetAmount.map { "\($0)" },
            name: plan.name,
            displayOrder: plan.displayOrder,
            createdAt: plan.createdAt.timeIntervalSince1970,
            lastExecutedAt: plan.lastExecutedAt?.timeIntervalSince1970,
            nextExecutionAt: plan.nextExecutionAt?.timeIntervalSince1970,
            networkRetryCount: plan.networkRetryCount,
            nextNetworkRetryAt: plan.nextNetworkRetryAt?.timeIntervalSince1970,
            originalScheduledAt: plan.originalScheduledAt?.timeIntervalSince1970,
            missedPurchaseCount: plan.missedPurchaseCount
        )
    }
}
