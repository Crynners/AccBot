import Foundation

/// DCA Plan configuration - stored locally only
struct DcaPlan: Identifiable, Equatable, Sendable {
    let id: Int64
    let exchange: Exchange
    let crypto: String
    let fiat: String
    let amount: Decimal
    let frequency: DcaFrequency
    let cronExpression: String?
    let strategy: DcaStrategy
    let isEnabled: Bool
    let withdrawalEnabled: Bool
    let withdrawalAddress: String?
    let targetAmount: Decimal?
    let createdAt: Date
    let lastExecutedAt: Date?
    let nextExecutionAt: Date?

    init(
        id: Int64 = 0,
        exchange: Exchange,
        crypto: String,
        fiat: String,
        amount: Decimal,
        frequency: DcaFrequency,
        cronExpression: String? = nil,
        strategy: DcaStrategy = .classic,
        isEnabled: Bool = true,
        withdrawalEnabled: Bool = false,
        withdrawalAddress: String? = nil,
        targetAmount: Decimal? = nil,
        createdAt: Date = Date(),
        lastExecutedAt: Date? = nil,
        nextExecutionAt: Date? = nil
    ) {
        self.id = id
        self.exchange = exchange
        self.crypto = crypto
        self.fiat = fiat
        self.amount = amount
        self.frequency = frequency
        self.cronExpression = cronExpression
        self.strategy = strategy
        self.isEnabled = isEnabled
        self.withdrawalEnabled = withdrawalEnabled
        self.withdrawalAddress = withdrawalAddress
        self.targetAmount = targetAmount
        self.createdAt = createdAt
        self.lastExecutedAt = lastExecutedAt
        self.nextExecutionAt = nextExecutionAt
    }

    /// Display string for the trading pair (e.g., "BTC/EUR")
    var pair: String { "\(crypto)/\(fiat)" }
}
