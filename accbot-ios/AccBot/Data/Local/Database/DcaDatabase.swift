import Foundation
import GRDB

/// GRDB database manager.
/// Manages the DatabasePool, migrations, and DAO access.
final class DcaDatabase {
    let dbPool: DatabasePool

    // MARK: - DAOs

    let planDao: DcaPlanDao
    let transactionDao: TransactionDao
    let withdrawalDao: WithdrawalDao
    let exchangeBalanceDao: ExchangeBalanceDao
    let dailyPriceDao: DailyPriceDao
    let notificationDao: NotificationDao
    let withdrawalThresholdDao: WithdrawalThresholdDao
    let monthlySummaryDao: MonthlySummaryDao

    // MARK: - Initialization

    /// Create a database at the given path. Pass nil for in-memory (testing).
    init(path: String? = nil) throws {
        if let path = path {
            dbPool = try DatabasePool(path: path)
        } else {
            dbPool = try DatabasePool(path: ":memory:")
        }
        try Self.runMigrations(on: dbPool)
        planDao = DcaPlanDao(dbPool: dbPool)
        transactionDao = TransactionDao(dbPool: dbPool)
        withdrawalDao = WithdrawalDao(dbPool: dbPool)
        exchangeBalanceDao = ExchangeBalanceDao(dbPool: dbPool)
        dailyPriceDao = DailyPriceDao(dbPool: dbPool)
        notificationDao = NotificationDao(dbPool: dbPool)
        withdrawalThresholdDao = WithdrawalThresholdDao(dbPool: dbPool)
        monthlySummaryDao = MonthlySummaryDao(dbPool: dbPool)
    }

    /// Standard production database
    static func production() throws -> DcaDatabase {
        let url = try FileManager.default
            .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("accbot_prod.sqlite")
        return try DcaDatabase(path: url.path)
    }

    /// Sandbox database (separate from production)
    static func sandbox() throws -> DcaDatabase {
        let url = try FileManager.default
            .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("accbot_sandbox.sqlite")
        return try DcaDatabase(path: url.path)
    }

    // MARK: - Migrations

    /// All Android Room migrations (1→11) collapsed into a single initial migration.
    /// Future iOS-only migrations added incrementally.
    private static func runMigrations(on dbPool: DatabasePool) throws {
        var migrator = DatabaseMigrator()

        // Collapse all 11 Android migrations into final v11 schema
        migrator.registerMigration("v1_initial") { db in

            // dca_plans
            try db.create(table: "dca_plans") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("exchange", .text).notNull()
                t.column("crypto", .text).notNull()
                t.column("fiat", .text).notNull()
                t.column("amount", .text).notNull()
                t.column("frequency", .text).notNull()
                t.column("cronExpression", .text)
                t.column("strategy", .text).notNull().defaults(to: "CLASSIC")
                t.column("isEnabled", .boolean).notNull().defaults(to: true)
                t.column("withdrawalEnabled", .boolean).notNull().defaults(to: false)
                t.column("withdrawalAddress", .text)
                t.column("createdAt", .double).notNull()
                t.column("lastExecutedAt", .double)
                t.column("nextExecutionAt", .double)
            }
            try db.create(index: "idx_plans_isEnabled", on: "dca_plans", columns: ["isEnabled"])
            try db.create(index: "idx_plans_exchange", on: "dca_plans", columns: ["exchange"])
            try db.create(index: "idx_plans_nextExecution", on: "dca_plans", columns: ["nextExecutionAt"])
            try db.create(index: "idx_plans_enabled_next", on: "dca_plans", columns: ["isEnabled", "nextExecutionAt"])

            // transactions
            try db.create(table: "transactions") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("planId", .integer).notNull()
                    .references("dca_plans", onDelete: .cascade)
                t.column("exchange", .text).notNull()
                t.column("crypto", .text).notNull()
                t.column("fiat", .text).notNull()
                t.column("fiatAmount", .text).notNull()
                t.column("cryptoAmount", .text).notNull()
                t.column("price", .text).notNull()
                t.column("fee", .text).notNull()
                t.column("feeAsset", .text).notNull().defaults(to: "")
                t.column("status", .text).notNull()
                t.column("exchangeOrderId", .text)
                t.column("errorMessage", .text)
                t.column("warningMessage", .text)
                t.column("executedAt", .double).notNull()
            }
            try db.create(index: "idx_tx_planId", on: "transactions", columns: ["planId"])
            try db.create(index: "idx_tx_exchange", on: "transactions", columns: ["exchange"])
            try db.create(index: "idx_tx_crypto", on: "transactions", columns: ["crypto"])
            try db.create(index: "idx_tx_status", on: "transactions", columns: ["status"])
            try db.create(index: "idx_tx_executedAt", on: "transactions", columns: ["executedAt"])
            try db.create(index: "idx_tx_plan_status", on: "transactions", columns: ["planId", "status"])
            try db.create(index: "idx_tx_crypto_fiat_status", on: "transactions", columns: ["crypto", "fiat", "status"])

            // withdrawals
            try db.create(table: "withdrawals") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("planId", .integer).notNull()
                    .references("dca_plans", onDelete: .cascade)
                t.column("exchange", .text).notNull()
                t.column("crypto", .text).notNull()
                t.column("amount", .text).notNull()
                t.column("address", .text).notNull()
                t.column("txHash", .text)
                t.column("fee", .text).notNull()
                t.column("status", .text).notNull()
                t.column("errorMessage", .text)
                t.column("createdAt", .double).notNull()
            }
            try db.create(index: "idx_wd_planId", on: "withdrawals", columns: ["planId"])
            try db.create(index: "idx_wd_status", on: "withdrawals", columns: ["status"])
            try db.create(index: "idx_wd_createdAt", on: "withdrawals", columns: ["createdAt"])

            // exchange_balances
            try db.create(table: "exchange_balances") { t in
                t.primaryKey("id", .text)
                t.column("exchange", .text).notNull()
                t.column("currency", .text).notNull()
                t.column("balance", .text).notNull()
                t.column("lastUpdated", .double).notNull()
            }
            try db.create(index: "idx_bal_exchange", on: "exchange_balances", columns: ["exchange"])

            // monthly_summaries
            try db.create(table: "monthly_summaries") { t in
                t.primaryKey("id", .text)
                t.column("year", .integer).notNull()
                t.column("month", .integer).notNull()
                t.column("totalInvestedEur", .text).notNull()
                t.column("totalBtcAccumulated", .text).notNull()
                t.column("transactionCount", .integer).notNull()
                t.column("averageBtcPrice", .text).notNull()
                t.column("lastUpdated", .double).notNull()
            }

            // daily_prices
            try db.create(table: "daily_prices") { t in
                t.column("crypto", .text).notNull()
                t.column("fiat", .text).notNull()
                t.column("dateEpochDay", .integer).notNull()
                t.column("price", .text).notNull()
                t.column("fetchedAt", .double).notNull()
                t.primaryKey(["crypto", "fiat", "dateEpochDay"])
            }
            try db.create(index: "idx_dp_crypto_fiat", on: "daily_prices", columns: ["crypto", "fiat"])

            // notifications
            try db.create(table: "notifications") { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("type", .text).notNull()
                t.column("title", .text).notNull()
                t.column("message", .text).notNull()
                t.column("planId", .integer).references("dca_plans", onDelete: .setNull)
                t.column("crypto", .text)
                t.column("exchange", .text)
                t.column("isRead", .boolean).notNull().defaults(to: false)
                t.column("isArchived", .boolean).notNull().defaults(to: false)
                t.column("createdAt", .double).notNull()
            }
            try db.create(index: "idx_notif_isRead", on: "notifications", columns: ["isRead"])
            try db.create(index: "idx_notif_isArchived", on: "notifications", columns: ["isArchived"])
            try db.create(index: "idx_notif_createdAt", on: "notifications", columns: ["createdAt"])
            try db.create(index: "idx_notifications_read_archived", on: "notifications", columns: ["isRead", "isArchived"], ifNotExists: true)

            // withdrawal_thresholds
            try db.create(table: "withdrawal_thresholds") { t in
                t.column("crypto", .text).notNull()
                t.column("exchange", .text).notNull()
                t.column("thresholdAmount", .text).notNull()
                t.primaryKey(["crypto", "exchange"])
            }
        }

        // Delete previously archived notifications so they don't reappear
        // after removing the archive UI
        migrator.registerMigration("v2_remove_archived_notifications") { db in
            try db.execute(sql: "DELETE FROM notifications WHERE isArchived = 1")
        }

        // Add optional target amount for goal tracking on DCA plans
        migrator.registerMigration("v3_add_target_amount") { db in
            try db.alter(table: "dca_plans") { t in
                t.add(column: "targetAmount", .text)
            }
        }

        try migrator.migrate(dbPool)
    }
}
