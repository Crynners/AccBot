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
    let exchangeConnectionDao: ExchangeConnectionDao

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
        exchangeConnectionDao = ExchangeConnectionDao(dbPool: dbPool)
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

        // Add templateArgs JSON column for dynamic notification localization
        migrator.registerMigration("v4_notification_template_args") { db in
            try db.alter(table: "notifications") { t in
                t.add(column: "templateArgs", .text)
            }
        }

        // Add network retry and missed purchase tracking fields to dca_plans
        migrator.registerMigration("v5_plan_retry_fields") { db in
            try db.alter(table: "dca_plans") { t in
                t.add(column: "networkRetryCount", .integer).notNull().defaults(to: 0)
                t.add(column: "nextNetworkRetryAt", .double)
                t.add(column: "originalScheduledAt", .double)
                t.add(column: "missedPurchaseCount", .integer).notNull().defaults(to: 0)
            }
        }

        // Add exchange_connections table, connectionId to all entities,
        // recreate withdrawal_thresholds and exchange_balances with new PKs
        migrator.registerMigration("v6_multi_connections") { db in
            // 1) Create exchange_connections table
            try db.execute(sql: """
                CREATE TABLE IF NOT EXISTS exchange_connections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exchange TEXT NOT NULL,
                    name TEXT NOT NULL DEFAULT '',
                    createdAt REAL NOT NULL,
                    displayOrder INTEGER NOT NULL DEFAULT 0
                )
                """)
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_ec_exchange ON exchange_connections(exchange)")
            // Non-partial unique index: empty "" is a valid unique value per exchange
            try db.execute(sql: """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_ec_exchange_name
                ON exchange_connections(exchange, name)
                """)

            // 2) Seed one default connection per exchange from all existing data.
            // INSERT OR IGNORE converges on a single row per exchange thanks to the unique index.
            let nowEpoch = Date().timeIntervalSince1970
            let seedSources = [
                "SELECT DISTINCT exchange FROM dca_plans",
                "SELECT DISTINCT exchange FROM transactions",
                "SELECT DISTINCT exchange FROM withdrawals",
                "SELECT DISTINCT exchange FROM withdrawal_thresholds",
                "SELECT DISTINCT exchange FROM exchange_balances",
                "SELECT DISTINCT exchange FROM notifications WHERE exchange IS NOT NULL",
            ]
            for src in seedSources {
                try db.execute(sql: """
                    INSERT OR IGNORE INTO exchange_connections (exchange, name, createdAt, displayOrder)
                    SELECT exchange, '', \(nowEpoch), 0 FROM (\(src))
                    """)
            }

            // 3) dca_plans: add connectionId (NOT NULL DEFAULT 0), backfill, assert
            try db.execute(sql: "ALTER TABLE dca_plans ADD COLUMN connectionId INTEGER NOT NULL DEFAULT 0")
            try db.execute(sql: """
                UPDATE dca_plans SET connectionId = (
                    SELECT id FROM exchange_connections WHERE exchange = dca_plans.exchange LIMIT 1
                )
                """)
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_plans_connectionId ON dca_plans(connectionId)")

            // Sanity check: no plan should have connectionId = 0 after backfill
            let orphanCount = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM dca_plans WHERE connectionId = 0") ?? 0
            if orphanCount > 0 {
                throw DatabaseError(message: "Migration v6 left \(orphanCount) dca_plans rows with connectionId=0")
            }

            // 4) transactions: nullable connectionId, backfill
            try db.execute(sql: "ALTER TABLE transactions ADD COLUMN connectionId INTEGER DEFAULT NULL")
            try db.execute(sql: """
                UPDATE transactions SET connectionId = (
                    SELECT id FROM exchange_connections WHERE exchange = transactions.exchange LIMIT 1
                )
                """)
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_tx_connectionId ON transactions(connectionId)")

            // 5) withdrawals: nullable connectionId, backfill
            try db.execute(sql: "ALTER TABLE withdrawals ADD COLUMN connectionId INTEGER DEFAULT NULL")
            try db.execute(sql: """
                UPDATE withdrawals SET connectionId = (
                    SELECT id FROM exchange_connections WHERE exchange = withdrawals.exchange LIMIT 1
                )
                """)

            // 6) notifications: nullable connectionId, backfill
            try db.execute(sql: "ALTER TABLE notifications ADD COLUMN connectionId INTEGER DEFAULT NULL")
            try db.execute(sql: """
                UPDATE notifications SET connectionId = (
                    SELECT id FROM exchange_connections WHERE exchange = notifications.exchange LIMIT 1
                ) WHERE exchange IS NOT NULL
                """)

            // 7) withdrawal_thresholds: recreate with PK (crypto, connectionId)
            try db.execute(sql: """
                CREATE TABLE withdrawal_thresholds_new (
                    crypto TEXT NOT NULL,
                    connectionId INTEGER NOT NULL,
                    thresholdAmount TEXT NOT NULL,
                    PRIMARY KEY (crypto, connectionId)
                )
                """)
            try db.execute(sql: """
                INSERT INTO withdrawal_thresholds_new (crypto, connectionId, thresholdAmount)
                SELECT wt.crypto, ec.id, wt.thresholdAmount
                FROM withdrawal_thresholds wt
                JOIN exchange_connections ec ON ec.exchange = wt.exchange
                """)
            try db.execute(sql: "DROP TABLE withdrawal_thresholds")
            try db.execute(sql: "ALTER TABLE withdrawal_thresholds_new RENAME TO withdrawal_thresholds")
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_wt_connectionId ON withdrawal_thresholds(connectionId)")

            // 8) exchange_balances: recreate with PK (connectionId, currency)
            try db.execute(sql: """
                CREATE TABLE exchange_balances_new (
                    connectionId INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    exchange TEXT NOT NULL,
                    balance TEXT NOT NULL,
                    lastUpdated REAL NOT NULL,
                    PRIMARY KEY (connectionId, currency)
                )
                """)
            try db.execute(sql: """
                INSERT INTO exchange_balances_new (connectionId, currency, exchange, balance, lastUpdated)
                SELECT ec.id, eb.currency, eb.exchange, eb.balance, eb.lastUpdated
                FROM exchange_balances eb
                JOIN exchange_connections ec ON ec.exchange = eb.exchange
                """)
            try db.execute(sql: "DROP TABLE exchange_balances")
            try db.execute(sql: "ALTER TABLE exchange_balances_new RENAME TO exchange_balances")
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_bal_connectionId ON exchange_balances(connectionId)")
            try db.execute(sql: "CREATE INDEX IF NOT EXISTS idx_bal_exchange ON exchange_balances(exchange)")
        }

        try migrator.migrate(dbPool)
    }
}
