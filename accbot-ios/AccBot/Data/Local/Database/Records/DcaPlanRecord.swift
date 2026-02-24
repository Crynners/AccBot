import Foundation
import GRDB

/// GRDB Record for dca_plans table
struct DcaPlanRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "dca_plans"

    var id: Int64?
    var exchange: String
    var crypto: String
    var fiat: String
    var amount: String      // Decimal stored as TEXT
    var frequency: String
    var cronExpression: String?
    var strategy: String
    var isEnabled: Bool
    var withdrawalEnabled: Bool
    var withdrawalAddress: String?
    var createdAt: Double    // timeIntervalSince1970
    var lastExecutedAt: Double?
    var nextExecutionAt: Double?

    // MARK: - Domain Mapping

    func toDomain() -> DcaPlan {
        DcaPlan(
            id: id ?? 0,
            exchange: Exchange(rawValue: exchange) ?? .coinmate,
            crypto: crypto,
            fiat: fiat,
            amount: Decimal(string: amount) ?? 0,
            frequency: DcaFrequency(rawValue: frequency) ?? .daily,
            cronExpression: cronExpression,
            strategy: DcaStrategy.fromDbString(strategy),
            isEnabled: isEnabled,
            withdrawalEnabled: withdrawalEnabled,
            withdrawalAddress: withdrawalAddress,
            createdAt: Date(timeIntervalSince1970: createdAt),
            lastExecutedAt: lastExecutedAt.map { Date(timeIntervalSince1970: $0) },
            nextExecutionAt: nextExecutionAt.map { Date(timeIntervalSince1970: $0) }
        )
    }

    static func fromDomain(_ plan: DcaPlan) -> DcaPlanRecord {
        DcaPlanRecord(
            id: plan.id == 0 ? nil : plan.id,
            exchange: plan.exchange.rawValue,
            crypto: plan.crypto,
            fiat: plan.fiat,
            amount: "\(plan.amount)",
            frequency: plan.frequency.rawValue,
            cronExpression: plan.cronExpression,
            strategy: plan.strategy.dbString,
            isEnabled: plan.isEnabled,
            withdrawalEnabled: plan.withdrawalEnabled,
            withdrawalAddress: plan.withdrawalAddress,
            createdAt: plan.createdAt.timeIntervalSince1970,
            lastExecutedAt: plan.lastExecutedAt?.timeIntervalSince1970,
            nextExecutionAt: plan.nextExecutionAt?.timeIntervalSince1970
        )
    }
}
