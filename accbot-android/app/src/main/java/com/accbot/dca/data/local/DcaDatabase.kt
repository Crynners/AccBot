package com.accbot.dca.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DcaPlanEntity::class,
        TransactionEntity::class,
        WithdrawalEntity::class,
        ExchangeBalanceEntity::class,
        MonthlySummaryEntity::class,
        DailyPriceEntity::class,
        NotificationEntity::class,
        WithdrawalThresholdEntity::class,
        ExchangeConnectionEntity::class
    ],
    version = 22,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DcaDatabase : RoomDatabase() {
    abstract fun dcaPlanDao(): DcaPlanDao
    abstract fun transactionDao(): TransactionDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun exchangeBalanceDao(): ExchangeBalanceDao
    abstract fun monthlySummaryDao(): MonthlySummaryDao
    abstract fun dailyPriceDao(): DailyPriceDao
    abstract fun notificationDao(): NotificationDao
    abstract fun withdrawalThresholdDao(): WithdrawalThresholdDao
    abstract fun exchangeConnectionDao(): ExchangeConnectionDao

    companion object {
        private const val LEGACY_DATABASE_NAME = "accbot_dca.db"
        private const val PROD_DATABASE_NAME = "accbot_prod.db"
        private const val SANDBOX_DATABASE_NAME = "accbot_sandbox.db"

        @Volatile
        private var prodInstance: DcaDatabase? = null

        @Volatile
        private var sandboxInstance: DcaDatabase? = null

        @Volatile
        private var legacyMigrationDone: Boolean = false

        // Migration from version 1 to 2: Add exchange_balances and monthly_summaries tables
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create exchange_balances table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS exchange_balances (
                        id TEXT PRIMARY KEY NOT NULL,
                        exchange TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        balance TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // Create monthly_summaries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS monthly_summaries (
                        id TEXT PRIMARY KEY NOT NULL,
                        year INTEGER NOT NULL,
                        month INTEGER NOT NULL,
                        totalInvestedEur TEXT NOT NULL,
                        totalBtcAccumulated TEXT NOT NULL,
                        transactionCount INTEGER NOT NULL,
                        averageBtcPrice TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
            }
        }

        // Migration from version 2 to 3: Add indexes for query optimization
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Indexes for dca_plans table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_isEnabled ON dca_plans (isEnabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_exchange ON dca_plans (exchange)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_nextExecutionAt ON dca_plans (nextExecutionAt)")

                // Indexes for transactions table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_planId ON transactions (planId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_exchange ON transactions (exchange)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_crypto ON transactions (crypto)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status ON transactions (status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_executedAt ON transactions (executedAt)")

                // Indexes for withdrawals table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawals_planId ON withdrawals (planId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawals_status ON withdrawals (status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawals_createdAt ON withdrawals (createdAt)")

                // Index for exchange_balances table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_balances_exchange ON exchange_balances (exchange)")
            }
        }

        // Migration from version 3 to 4: Add strategy column to dca_plans
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add strategy column with default value 'CLASSIC'
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN strategy TEXT NOT NULL DEFAULT 'CLASSIC'")
            }
        }

        // Migration from version 4 to 5: Add feeAsset column to transactions
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN feeAsset TEXT NOT NULL DEFAULT ''")
            }
        }

        // Migration from version 5 to 6: Add daily_prices table for performance charts
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_prices (
                        crypto TEXT NOT NULL,
                        fiat TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        price TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY (crypto, fiat, dateEpochDay)
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_daily_prices_crypto_fiat ON daily_prices (crypto, fiat)")
            }
        }

        // Migration from version 6 to 7: Add compound indexes for query optimization
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_isEnabled_nextExecutionAt ON dca_plans (isEnabled, nextExecutionAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_planId_status ON transactions (planId, status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_crypto_fiat_status ON transactions (crypto, fiat, status)")
            }
        }

        // Migration from version 7 to 8: Add cronExpression column to dca_plans
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN cronExpression TEXT DEFAULT NULL")
            }
        }

        // Migration from version 8 to 9: Add warningMessage column to transactions
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN warningMessage TEXT DEFAULT NULL")
            }
        }

        // Migration from version 10 to 11: Add isArchived column to notifications
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isArchived ON notifications (isArchived)")
            }
        }

        // Migration from version 11 to 12: Add systemNotificationId column to notifications
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN systemNotificationId INTEGER DEFAULT NULL")
            }
        }

        // Migration from version 12 to 13: Add targetAmount column to dca_plans
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN targetAmount TEXT DEFAULT NULL")
            }
        }

        // Migration from version 13 to 14: Add templateArgs column to notifications
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN templateArgs TEXT DEFAULT NULL")
            }
        }

        // Migration from version 14 to 15: Add [fiat, status] index on transactions
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_fiat_status ON transactions (fiat, status)")
            }
        }

        // Migration from version 15 to 16: Add network retry tracking columns to dca_plans
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN networkRetryCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN nextNetworkRetryAt INTEGER DEFAULT NULL")
            }
        }

        // Migration from version 16 to 17: Add originalScheduledAt for delay tracking across retries
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN originalScheduledAt INTEGER DEFAULT NULL")
            }
        }

        // Migration from version 17 to 18: Add missedPurchaseCount for offline recovery
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN missedPurchaseCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration from version 18 to 19: Multi-credentials per exchange.
        // Adds exchange_connections table, plus connectionId column to dca_plans, transactions,
        // withdrawals, notifications. Recreates withdrawal_thresholds (PK changes to
        // (crypto, connectionId)) and exchange_balances (PK changes to (connectionId, currency)).
        // Auto-creates one empty-named connection per Exchange enum used by existing data so
        // every legacy row gets a valid connectionId.
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1) Create exchange_connections table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exchange_connections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        exchange TEXT NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        displayOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_connections_exchange ON exchange_connections(exchange)")
                // Non-partial unique index on (exchange, name). The empty default name ""
                // counts as a distinct value so each exchange can have AT MOST ONE empty-named
                // connection (the auto-created default), AT MOST ONE per non-empty name.
                // Crucially this lets `INSERT OR IGNORE` from multiple seed sources below
                // converge on a single default row per exchange instead of duplicating.
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_exchange_connections_exchange_name " +
                        "ON exchange_connections(exchange, name)"
                )

                // 2) Auto-create one default connection per exchange used by existing data.
                // The non-partial unique index on (exchange, name) ensures each exchange gets
                // exactly ONE empty-named connection regardless of which seed source(s) reference it.
                val nowMillis = System.currentTimeMillis()
                val seedSources = listOf(
                    "SELECT DISTINCT exchange FROM dca_plans",
                    "SELECT DISTINCT exchange FROM transactions",
                    "SELECT DISTINCT exchange FROM withdrawals",
                    "SELECT DISTINCT exchange FROM withdrawal_thresholds",
                    "SELECT DISTINCT exchange FROM exchange_balances",
                    "SELECT DISTINCT exchange FROM notifications WHERE exchange IS NOT NULL"
                )
                for (src in seedSources) {
                    database.execSQL(
                        "INSERT OR IGNORE INTO exchange_connections (exchange, name, createdAt, displayOrder) " +
                            "SELECT exchange, '', $nowMillis, 0 FROM ($src)"
                    )
                }

                // 3) dca_plans - add connectionId column and backfill from default connection
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN connectionId INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "UPDATE dca_plans SET connectionId = " +
                        "(SELECT id FROM exchange_connections WHERE exchange = dca_plans.exchange LIMIT 1)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dca_plans_connectionId ON dca_plans(connectionId)")

                // Sanity assertion: any plan with connectionId = 0 means an exchange enum
                // existed in dca_plans but no row was created in exchange_connections - bug.
                database.query("SELECT COUNT(*) FROM dca_plans WHERE connectionId = 0").use { c ->
                    if (c.moveToFirst() && c.getInt(0) > 0) {
                        throw IllegalStateException(
                            "Migration 18->19 left ${c.getInt(0)} dca_plans rows with connectionId=0; " +
                                "exchange_connections seeding failed for some Exchange enum"
                        )
                    }
                }

                // 4) transactions - nullable connectionId, no FK
                database.execSQL("ALTER TABLE transactions ADD COLUMN connectionId INTEGER DEFAULT NULL")
                database.execSQL(
                    "UPDATE transactions SET connectionId = " +
                        "(SELECT id FROM exchange_connections WHERE exchange = transactions.exchange LIMIT 1)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_connectionId ON transactions(connectionId)")

                // 5) withdrawals - nullable connectionId, no FK
                database.execSQL("ALTER TABLE withdrawals ADD COLUMN connectionId INTEGER DEFAULT NULL")
                database.execSQL(
                    "UPDATE withdrawals SET connectionId = " +
                        "(SELECT id FROM exchange_connections WHERE exchange = withdrawals.exchange LIMIT 1)"
                )

                // 6) withdrawal_thresholds - recreate with new PK (crypto, connectionId).
                // No FOREIGN KEY constraint here: the entity declaration in Entities.kt
                // doesn't declare one (Room schema validation requires the migrated table
                // to match the entity exactly), and FK enforcement (`PRAGMA foreign_keys`)
                // is disabled anyway. Cascade-on-connection-delete is handled explicitly
                // by [ExchangeConnectionRepository.delete] via WithdrawalThresholdDao.
                database.execSQL(
                    """
                    CREATE TABLE withdrawal_thresholds_new (
                        crypto TEXT NOT NULL,
                        connectionId INTEGER NOT NULL,
                        thresholdAmount TEXT NOT NULL,
                        PRIMARY KEY (crypto, connectionId)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO withdrawal_thresholds_new (crypto, connectionId, thresholdAmount)
                    SELECT wt.crypto, ec.id, wt.thresholdAmount
                    FROM withdrawal_thresholds wt
                    JOIN exchange_connections ec ON ec.exchange = wt.exchange
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE withdrawal_thresholds")
                database.execSQL("ALTER TABLE withdrawal_thresholds_new RENAME TO withdrawal_thresholds")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_withdrawal_thresholds_connectionId ON withdrawal_thresholds(connectionId)")

                // 7) exchange_balances - recreate with new composite PK (connectionId, currency)
                database.execSQL(
                    """
                    CREATE TABLE exchange_balances_new (
                        connectionId INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        exchange TEXT NOT NULL,
                        balance TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY (connectionId, currency)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO exchange_balances_new (connectionId, currency, exchange, balance, lastUpdated)
                    SELECT ec.id, eb.currency, eb.exchange, eb.balance, eb.lastUpdated
                    FROM exchange_balances eb
                    JOIN exchange_connections ec ON ec.exchange = eb.exchange
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE exchange_balances")
                database.execSQL("ALTER TABLE exchange_balances_new RENAME TO exchange_balances")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_balances_connectionId ON exchange_balances(connectionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_exchange_balances_exchange ON exchange_balances(exchange)")

                // 8) notifications - nullable connectionId, no FK
                database.execSQL("ALTER TABLE notifications ADD COLUMN connectionId INTEGER DEFAULT NULL")
                database.execSQL(
                    "UPDATE notifications SET connectionId = " +
                        "(SELECT id FROM exchange_connections WHERE exchange = notifications.exchange LIMIT 1) " +
                        "WHERE exchange IS NOT NULL"
                )
            }
        }

        // Migration from version 19 to 20: Add name and displayOrder columns to dca_plans
        // for custom plan labels and manual ordering on the Dashboard.
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration from version 20 to 21: Add sell extension fields to dca_plans and transactions.
        // Enables opt-in limit sell orders, P&L tracking, and optional profit targets.
        //
        // Defaults: allowSells/side use ColumnInfo defaultValue (must match SQL DEFAULT exactly).
        // Nullable columns intentionally OMIT `DEFAULT NULL` - Room schema validator treats
        // nullable-with-no-Kotlin-default as "no SQL default", and explicit DEFAULT NULL
        // would cause a schema mismatch.
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN allowSells INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE dca_plans ADD COLUMN targetProfitAmount TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN side TEXT NOT NULL DEFAULT 'BUY'")
                database.execSQL("ALTER TABLE transactions ADD COLUMN limitPrice TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN requestedCryptoAmount TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_planId_side_status ON transactions(planId, side, status)")
            }
        }

        // Migration 21 -> 22: backfill transactions.connectionId from the parent plan.
        // Trade-history imports (and pre-v19 rows) left connectionId NULL, which breaks
        // per-connection aggregation and backup-restore dedup. Only backfill real
        // connections (> 0); leave NULL where the plan has no connection.
        internal val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    UPDATE transactions
                    SET connectionId = (SELECT p.connectionId FROM dca_plans p WHERE p.id = transactions.planId)
                    WHERE connectionId IS NULL
                      AND EXISTS (SELECT 1 FROM dca_plans p WHERE p.id = transactions.planId AND p.connectionId > 0)
                    """.trimIndent()
                )
            }
        }

        // Migration from version 9 to 10: Add notifications and withdrawal_thresholds tables
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        planId INTEGER,
                        crypto TEXT,
                        exchange TEXT,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isRead ON notifications (isRead)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_createdAt ON notifications (createdAt)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS withdrawal_thresholds (
                        crypto TEXT NOT NULL,
                        exchange TEXT NOT NULL,
                        thresholdAmount TEXT NOT NULL,
                        PRIMARY KEY (crypto, exchange)
                    )
                """)
            }
        }

        /**
         * Get the database instance for the specified mode.
         * Production and sandbox use separate database files to prevent data mixing.
         *
         * @param context Application context
         * @param isSandbox Whether to use sandbox database
         * @return Database instance for the specified mode
         */
        fun getInstance(context: Context, isSandbox: Boolean = false): DcaDatabase {
            // Migrate legacy database to prod on first access (skip check after first run)
            if (!legacyMigrationDone) {
                migrateLegacyDatabase(context)
                legacyMigrationDone = true
            }

            return if (isSandbox) {
                sandboxInstance ?: synchronized(this) {
                    sandboxInstance ?: buildDatabase(context, SANDBOX_DATABASE_NAME).also {
                        sandboxInstance = it
                    }
                }
            } else {
                prodInstance ?: synchronized(this) {
                    prodInstance ?: buildDatabase(context, PROD_DATABASE_NAME).also {
                        prodInstance = it
                    }
                }
            }
        }

        /**
         * Migrate legacy database (accbot_dca.db) to production database name.
         * This is a one-time migration for existing users.
         */
        private fun migrateLegacyDatabase(context: Context) {
            val legacyDbFile = context.getDatabasePath(LEGACY_DATABASE_NAME)
            val prodDbFile = context.getDatabasePath(PROD_DATABASE_NAME)

            if (legacyDbFile.exists() && !prodDbFile.exists()) {
                // Rename legacy database to production database, fall back to copy
                if (!legacyDbFile.renameTo(prodDbFile)) {
                    legacyDbFile.copyTo(prodDbFile, overwrite = false)
                    legacyDbFile.delete()
                }

                // Also migrate WAL and SHM files if they exist
                val legacyWal = context.getDatabasePath("$LEGACY_DATABASE_NAME-wal")
                val legacyShm = context.getDatabasePath("$LEGACY_DATABASE_NAME-shm")
                val prodWal = context.getDatabasePath("$PROD_DATABASE_NAME-wal")
                val prodShm = context.getDatabasePath("$PROD_DATABASE_NAME-shm")

                if (legacyWal.exists()) {
                    if (!legacyWal.renameTo(prodWal)) {
                        legacyWal.copyTo(prodWal, overwrite = false)
                        legacyWal.delete()
                    }
                }
                if (legacyShm.exists()) {
                    if (!legacyShm.renameTo(prodShm)) {
                        legacyShm.copyTo(prodShm, overwrite = false)
                        legacyShm.delete()
                    }
                }
            }
        }

        private fun buildDatabase(context: Context, databaseName: String): DcaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DcaDatabase::class.java,
                databaseName
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
                // Only allow destructive migration on app downgrade, never on failed upgrade
                // This protects user's transaction history from accidental deletion
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
