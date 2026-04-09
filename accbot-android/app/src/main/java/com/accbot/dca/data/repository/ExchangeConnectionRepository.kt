package com.accbot.dca.data.repository

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.ExchangeBalanceDao
import com.accbot.dca.data.local.ExchangeConnectionDao
import com.accbot.dca.data.local.ExchangeConnectionEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.local.WithdrawalThresholdDao
import com.accbot.dca.domain.model.Exchange
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for exchange connections (envelopes). Wraps [ExchangeConnectionDao] with
 * convenience operations and side effects (cascade-clean credentials/balances on delete).
 *
 * A "connection" is one set of API credentials targeting a specific [Exchange]. Multiple
 * connections can exist for the same exchange (e.g. two Coinmate sub-accounts named
 * "Hlavní" and "Spoření"). Each connection has its own credentials (in [CredentialsStore]),
 * its own balance cache, and its own withdrawal thresholds.
 *
 * Production and sandbox connections live in separate Room databases — this repository
 * operates against whichever DB is currently active for the running app.
 */
@Singleton
class ExchangeConnectionRepository @Inject constructor(
    private val connectionDao: ExchangeConnectionDao,
    private val dcaPlanDao: DcaPlanDao,
    private val exchangeBalanceDao: ExchangeBalanceDao,
    private val withdrawalThresholdDao: WithdrawalThresholdDao,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences
) {
    fun observeAll(): Flow<List<ExchangeConnectionEntity>> = connectionDao.getAllFlow()

    suspend fun getAll(): List<ExchangeConnectionEntity> = connectionDao.getAll()

    suspend fun getById(id: Long): ExchangeConnectionEntity? = connectionDao.getById(id)

    suspend fun getByExchange(exchange: Exchange): List<ExchangeConnectionEntity> =
        connectionDao.getByExchange(exchange)

    suspend fun getDefaultByExchange(exchange: Exchange): ExchangeConnectionEntity? =
        connectionDao.getDefaultByExchange(exchange)

    suspend fun countByExchange(exchange: Exchange): Int =
        connectionDao.countByExchange(exchange)

    /**
     * Create a new connection. The DB-level partial unique index on `(exchange, name)`
     * (where name != '') prevents duplicate non-empty names per exchange; an empty name
     * is allowed only when no other connection on the same exchange already has empty
     * name. Caller is expected to validate the name uniqueness before calling for a
     * better UX (Phase 7 [AddExchangeViewModel] does this).
     */
    suspend fun create(exchange: Exchange, name: String): Long {
        return connectionDao.insert(
            ExchangeConnectionEntity(
                exchange = exchange,
                name = name,
                createdAt = Instant.now()
            )
        )
    }

    suspend fun rename(connectionId: Long, newName: String) {
        val existing = connectionDao.getById(connectionId) ?: return
        connectionDao.update(existing.copy(name = newName))
    }

    /**
     * Delete a connection and manually cascade to all dependent rows. Cleanup order:
     *  1. (optional) DCA plans referencing this connection
     *  2. Withdrawal thresholds (manual — `PRAGMA foreign_keys` is disabled in Room
     *     so the schema-level `ON DELETE CASCADE` is a no-op)
     *  3. Balance cache rows
     *  4. Encrypted credentials in [CredentialsStore]
     *  5. The connection row itself
     *
     * Transaction history is *not* deleted — its `connectionId` becomes orphaned
     * (nullable, no FK), and the UI falls back to the [Exchange] enum for the label.
     *
     * @param deletePlans if true, also deletes any DCA plans tied to this connection.
     *  If false and plans still reference this connection, throws to prevent orphaning
     *  plans (which would loop in DcaWorker with "no credentials" errors).
     * @throws IllegalStateException when [deletePlans] is false but active plans exist.
     */
    suspend fun delete(connectionId: Long, deletePlans: Boolean) {
        val planCount = dcaPlanDao.countPlansByConnection(connectionId)
        if (planCount > 0 && !deletePlans) {
            throw IllegalStateException(
                "Cannot delete connection $connectionId: $planCount active plan(s) reference it. " +
                    "Pass deletePlans=true to remove them, or delete the plans first."
            )
        }
        val isSandbox = userPreferences.isSandboxMode()
        if (deletePlans && planCount > 0) {
            dcaPlanDao.deletePlansByConnection(connectionId)
        }
        // Manual cascade — FK enforcement is currently disabled.
        withdrawalThresholdDao.deleteByConnection(connectionId)
        exchangeBalanceDao.deleteBalancesByConnection(connectionId)
        credentialsStore.deleteCredentials(connectionId, isSandbox)
        connectionDao.deleteById(connectionId)
    }

    /**
     * Compute a "display label" for a connection — exchange name plus optional custom name.
     * E.g. "Coinmate" (no name) or "Coinmate — Spoření".
     */
    fun displayLabel(connection: ExchangeConnectionEntity): String {
        return if (connection.name.isNotBlank()) {
            "${connection.exchange.displayName} — ${connection.name}"
        } else {
            connection.exchange.displayName
        }
    }
}
