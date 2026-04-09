package com.accbot.dca.data.local

import androidx.room.withTransaction
import com.accbot.dca.domain.model.*
import com.accbot.dca.domain.util.CronUtils
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores a BackupPayload into DB + preferences, remapping plan IDs.
 */
@Singleton
class BackupDataRestorer @Inject constructor(
    private val database: DcaDatabase,
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val notificationDao: NotificationDao,
    private val withdrawalDao: WithdrawalDao,
    private val withdrawalThresholdDao: WithdrawalThresholdDao,
    private val exchangeConnectionDao: ExchangeConnectionDao,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences
) {

    /**
     * Get-or-create the default empty-named connection for an exchange. Used for legacy v1
     * backups (no connection metadata) and as a fallback when v2 backup connectionId can't
     * be remapped.
     *
     * Race-safe: re-checks after insert in case a parallel restore (or the v2 connections
     * loop) raced and inserted a row with the same `(exchange, "")` key. The unique index
     * on `(exchange, name)` would otherwise raise a constraint violation.
     */
    private suspend fun resolveOrCreateDefaultConnection(exchange: Exchange): Long {
        exchangeConnectionDao.getDefaultByExchange(exchange)?.let { return it.id }
        return try {
            exchangeConnectionDao.insert(
                ExchangeConnectionEntity(exchange = exchange, name = "")
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Concurrent insert won the race — re-fetch and use the existing row.
            exchangeConnectionDao.getDefaultByExchange(exchange)?.id
                ?: throw IllegalStateException("Failed to resolve default connection for $exchange", e)
        }
    }
    suspend fun restore(payload: BackupPayload, restoreMode: RestoreMode = RestoreMode.Merge): BackupResult {
        return try {
            // PRE-VALIDATE credentials before touching the DB. If any credential has an
            // unknown exchange enum, abort with an error WITHOUT modifying any DB state.
            // This guards against the half-restored scenario where plans are committed but
            // their credentials silently fail to save (leading to "no credentials" loops in
            // DcaWorker for every restored plan).
            val parsedCredentials = mutableListOf<ParsedCredential>()
            for (cred in payload.credentials) {
                val exchange = try {
                    Exchange.valueOf(cred.exchange)
                } catch (e: Exception) {
                    return BackupResult.Error("Backup contains credentials for unknown exchange '${cred.exchange}'")
                }
                parsedCredentials += ParsedCredential(
                    exchange = exchange,
                    backupConnectionId = cred.connectionId,
                    credentials = ExchangeCredentials(
                        exchange = exchange,
                        apiKey = cred.apiKey,
                        apiSecret = cred.apiSecret,
                        passphrase = cred.passphrase,
                        clientId = cred.clientId
                    )
                )
            }

            // DB operations inside a single transaction
            val planIdMap = mutableMapOf<Long, Long>()
            // v2: map backup-local connection ids → newly assigned local ids.
            val connectionIdMap = mutableMapOf<Long, Long>()

            database.withTransaction {
                // Replace mode: wipe all existing DB data first.
                // NOTE: deleting plans last (after transactions) preserves any existing FK
                // assumptions. Connections are NOT wiped — we preserve them and let merge
                // dedupe by (exchange, name).
                if (restoreMode == RestoreMode.Replace) {
                    transactionDao.deleteAllTransactions()
                    withdrawalDao.deleteAllWithdrawals()
                    notificationDao.deleteAllNotifications()
                    withdrawalThresholdDao.deleteAll()
                    dcaPlanDao.deleteAllPlans()
                }

                // 0. Connections (v2): create or dedupe by (exchange, name).
                // The unique index on (exchange, name) means duplicate inserts raise
                // SQLiteConstraintException — we catch and re-fetch.
                for (conn in payload.connections) {
                    val exchange = try { Exchange.valueOf(conn.exchange) } catch (_: Exception) { continue }
                    val existing = exchangeConnectionDao.getByExchange(exchange)
                        .firstOrNull { it.name == conn.name }
                    val targetId = existing?.id ?: try {
                        exchangeConnectionDao.insert(
                            ExchangeConnectionEntity(
                                exchange = exchange,
                                name = conn.name,
                                createdAt = if (conn.createdAt > 0) Instant.ofEpochMilli(conn.createdAt) else Instant.now(),
                                displayOrder = conn.displayOrder
                            )
                        )
                    } catch (_: android.database.sqlite.SQLiteConstraintException) {
                        exchangeConnectionDao.getByExchange(exchange)
                            .firstOrNull { it.name == conn.name }?.id
                            ?: continue
                    }
                    connectionIdMap[conn.id] = targetId
                }

                // Helper: resolve a backup connectionId (v2) or fall back to default
                // connection per exchange (v1 legacy).
                suspend fun resolveConnectionForRestore(backupConnectionId: Long?, exchange: Exchange): Long {
                    if (backupConnectionId != null) {
                        connectionIdMap[backupConnectionId]?.let { return it }
                    }
                    return resolveOrCreateDefaultConnection(exchange)
                }

                // 1. Plans: merge with dedup or insert after wipe
                if (restoreMode == RestoreMode.Merge) {
                    val existingPlans = dcaPlanDao.getAllPlansOnce()
                    for (plan in payload.plans) {
                        val planExchange = Exchange.valueOf(plan.exchange)
                        val connectionId = resolveConnectionForRestore(plan.connectionId, planExchange)
                        val entity = plan.toEntity(connectionId)
                        // Match must include connectionId so that two plans with identical
                        // crypto/fiat/amount on different envelopes ("Hlavní" vs "Spoření")
                        // don't collapse to one during merge restore.
                        val match = existingPlans.find { existing ->
                            existing.connectionId == connectionId &&
                                existing.exchange.name == plan.exchange &&
                                existing.crypto == plan.crypto &&
                                existing.fiat == plan.fiat &&
                                existing.amount.compareTo(entity.amount) == 0 &&
                                existing.frequency == entity.frequency
                        }
                        if (match != null) {
                            // Update existing plan with backup values, keep existing ID
                            dcaPlanDao.updatePlan(match.copy(
                                strategy = entity.strategy,
                                isEnabled = entity.isEnabled,
                                withdrawalEnabled = entity.withdrawalEnabled,
                                withdrawalAddress = entity.withdrawalAddress,
                                cronExpression = entity.cronExpression,
                                lastExecutedAt = entity.lastExecutedAt,
                                nextExecutionAt = entity.nextExecutionAt,
                                targetAmount = entity.targetAmount
                            ))
                            planIdMap[plan.id] = match.id
                        } else {
                            val newId = dcaPlanDao.insertPlan(entity)
                            planIdMap[plan.id] = newId
                        }
                    }
                } else {
                    for (plan in payload.plans) {
                        val planExchange = Exchange.valueOf(plan.exchange)
                        val connectionId = resolveConnectionForRestore(plan.connectionId, planExchange)
                        val entity = plan.toEntity(connectionId)
                        val newId = dcaPlanDao.insertPlan(entity)
                        planIdMap[plan.id] = newId
                    }
                }

                // 2. Transactions with remapped planId (merge: skip duplicates by exchangeOrderId)
                for (tx in payload.transactions) {
                    val remappedPlanId = planIdMap[tx.planId] ?: tx.planId
                    if (restoreMode == RestoreMode.Merge && !tx.exchangeOrderId.isNullOrEmpty()) {
                        val existing = transactionDao.getByExchangeOrderId(tx.exchangeOrderId)
                        if (existing != null) continue // Already imported, skip
                    }
                    val txExchange = Exchange.valueOf(tx.exchange)
                    val connectionId = resolveConnectionForRestore(tx.connectionId, txExchange)
                    transactionDao.insertTransaction(tx.toEntity(remappedPlanId, connectionId))
                }

                // 3. Insert withdrawals with remapped planId
                for (w in payload.withdrawals) {
                    val remappedPlanId = planIdMap[w.planId] ?: w.planId
                    val wExchange = Exchange.valueOf(w.exchange)
                    val connectionId = resolveConnectionForRestore(w.connectionId, wExchange)
                    withdrawalDao.insertWithdrawal(w.toEntity(remappedPlanId, connectionId))
                }

                // 4. Insert notifications with remapped planId
                for (n in payload.notifications) {
                    val remappedPlanId = n.planId?.let { planIdMap[it] ?: it }
                    val connectionId = n.exchange?.let { name ->
                        try {
                            resolveConnectionForRestore(n.connectionId, Exchange.valueOf(name))
                        } catch (_: Exception) { null }
                    }
                    notificationDao.insert(n.toEntity(remappedPlanId, connectionId))
                }

                // 5. Upsert withdrawal thresholds
                for (t in payload.withdrawalThresholds) {
                    val tExchange = Exchange.valueOf(t.exchange)
                    val connectionId = resolveConnectionForRestore(t.connectionId, tExchange)
                    withdrawalThresholdDao.upsert(t.toEntity(connectionId))
                }
            }

            // Outside transaction: restore settings
            payload.settings?.let { settings ->
                try { userPreferences.setAppTheme(AppTheme.valueOf(settings.appTheme)) } catch (_: Exception) {}
                userPreferences.setNotificationsEnabled(settings.notificationsEnabled)
                userPreferences.setPurchaseNotificationsEnabled(settings.purchaseNotifications)
                userPreferences.setErrorNotificationsEnabled(settings.errorNotifications)
                userPreferences.setWeeklySummaryNotificationsEnabled(settings.weeklySummaryNotifications)
                if (settings.languageTag.isNotEmpty()) {
                    userPreferences.setLanguageTag(settings.languageTag)
                }
                userPreferences.setBiometricLockEnabled(settings.biometricLockEnabled)
                userPreferences.setLowBalanceThresholdDays(settings.lowBalanceThresholdDays)
            }

            // Outside transaction: restore credentials.
            // Pre-validation above guarantees all entries parse cleanly. Remap each
            // backup-local connectionId to the freshly inserted local one and save.
            // Failures here are logged but don't roll back the DB — at this point the
            // restore is "best effort committed" and partial credentials is recoverable
            // (user can re-enter API keys via AddExchange).
            val isSandbox = userPreferences.isSandboxMode()
            if (restoreMode == RestoreMode.Replace) {
                credentialsStore.clearAllCredentials(isSandbox)
            }
            var failedCredentials = 0
            for (cred in parsedCredentials) {
                try {
                    val targetConnectionId = cred.backupConnectionId
                        ?.let { connectionIdMap[it] }
                        ?: resolveOrCreateDefaultConnection(cred.exchange)
                    credentialsStore.saveCredentials(targetConnectionId, cred.credentials, isSandbox)
                } catch (e: Exception) {
                    failedCredentials++
                }
            }

            if (failedCredentials > 0) {
                BackupResult.Success("Restored, but $failedCredentials credential set(s) could not be saved")
            } else {
                BackupResult.Success()
            }
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Unknown error during restore")
        }
    }

    /**
     * Internal pre-parsed credential record. Built BEFORE the DB transaction so any
     * malformed backup credential aborts the restore upfront, before plans are committed.
     */
    private data class ParsedCredential(
        val exchange: Exchange,
        val backupConnectionId: Long?,
        val credentials: ExchangeCredentials
    )

    // Backup → Entity mapping (all with id=0 for Room auto-generate)

    private fun BackupPlan.toEntity(connectionId: Long): DcaPlanEntity {
        val now = Instant.now()
        val freq = DcaFrequency.valueOf(frequency)
        val restoredNext = nextExecutionAt?.let { Instant.ofEpochMilli(it) }

        val effectiveNext = if (restoredNext != null && restoredNext.isAfter(now)) {
            restoredNext // still in the future – keep it
        } else if (cronExpression != null) {
            CronUtils.getNextExecution(cronExpression, now)
                ?: now.plus(Duration.ofMinutes(freq.intervalMinutes.takeIf { it > 0 } ?: 1440))
        } else {
            now.plus(Duration.ofMinutes(freq.intervalMinutes))
        }

        return DcaPlanEntity(
            id = 0,
            exchange = Exchange.valueOf(exchange),
            connectionId = connectionId,
            crypto = crypto,
            fiat = fiat,
            amount = BigDecimal(amount),
            frequency = freq,
            cronExpression = cronExpression,
            strategy = DcaStrategy.fromString(strategy),
            isEnabled = isEnabled,
            withdrawalEnabled = withdrawalEnabled,
            withdrawalAddress = withdrawalAddress,
            createdAt = Instant.ofEpochMilli(createdAt),
            lastExecutedAt = lastExecutedAt?.let { Instant.ofEpochMilli(it) },
            nextExecutionAt = effectiveNext,
            targetAmount = targetAmount?.let { BigDecimal(it) }
        )
    }

    private fun BackupTransaction.toEntity(remappedPlanId: Long, connectionId: Long?) = TransactionEntity(
        id = 0,
        planId = remappedPlanId,
        exchange = Exchange.valueOf(exchange),
        connectionId = connectionId,
        crypto = crypto,
        fiat = fiat,
        fiatAmount = BigDecimal(fiatAmount),
        cryptoAmount = BigDecimal(cryptoAmount),
        price = BigDecimal(price),
        fee = BigDecimal(fee),
        feeAsset = feeAsset,
        status = TransactionStatus.valueOf(status),
        exchangeOrderId = exchangeOrderId,
        errorMessage = errorMessage,
        warningMessage = warningMessage,
        executedAt = Instant.ofEpochMilli(executedAt)
    )

    private fun BackupWithdrawal.toEntity(remappedPlanId: Long, connectionId: Long?) = WithdrawalEntity(
        id = 0,
        planId = remappedPlanId,
        exchange = Exchange.valueOf(exchange),
        connectionId = connectionId,
        crypto = crypto,
        amount = BigDecimal(amount),
        address = address,
        txHash = txHash,
        fee = BigDecimal(fee),
        status = WithdrawalStatus.valueOf(status),
        errorMessage = errorMessage,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun BackupNotification.toEntity(remappedPlanId: Long?, connectionId: Long?) = NotificationEntity(
        id = 0,
        type = NotificationType.valueOf(type),
        title = title,
        message = message,
        planId = remappedPlanId,
        crypto = crypto,
        exchange = exchange?.let { try { Exchange.valueOf(it) } catch (_: Exception) { null } },
        connectionId = connectionId,
        isRead = isRead,
        isArchived = isArchived,
        templateArgs = templateArgs,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun BackupWithdrawalThreshold.toEntity(connectionId: Long) = WithdrawalThresholdEntity(
        crypto = crypto,
        connectionId = connectionId,
        thresholdAmount = BigDecimal(thresholdAmount)
    )
}
