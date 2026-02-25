package com.accbot.dca.data.local

import androidx.room.withTransaction
import com.accbot.dca.domain.model.*
import java.math.BigDecimal
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
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences
) {
    suspend fun restore(payload: BackupPayload): BackupResult {
        return try {
            // DB operations inside a single transaction
            val planIdMap = mutableMapOf<Long, Long>()

            database.withTransaction {
                // 1. Insert plans with id=0 → Room auto-generates new ID
                for (plan in payload.plans) {
                    val entity = plan.toEntity()
                    val newId = dcaPlanDao.insertPlan(entity)
                    planIdMap[plan.id] = newId
                }

                // 2. Insert transactions with remapped planId
                for (tx in payload.transactions) {
                    val remappedPlanId = planIdMap[tx.planId] ?: tx.planId
                    transactionDao.insertTransaction(tx.toEntity(remappedPlanId))
                }

                // 3. Insert withdrawals with remapped planId
                for (w in payload.withdrawals) {
                    val remappedPlanId = planIdMap[w.planId] ?: w.planId
                    withdrawalDao.insertWithdrawal(w.toEntity(remappedPlanId))
                }

                // 4. Insert notifications with remapped planId
                for (n in payload.notifications) {
                    val remappedPlanId = n.planId?.let { planIdMap[it] ?: it }
                    notificationDao.insert(n.toEntity(remappedPlanId))
                }

                // 5. Upsert withdrawal thresholds
                for (t in payload.withdrawalThresholds) {
                    withdrawalThresholdDao.upsert(t.toEntity())
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

            // Outside transaction: restore credentials
            val isSandbox = userPreferences.isSandboxMode()
            for (cred in payload.credentials) {
                try {
                    val exchange = Exchange.valueOf(cred.exchange)
                    credentialsStore.saveCredentials(
                        ExchangeCredentials(
                            exchange = exchange,
                            apiKey = cred.apiKey,
                            apiSecret = cred.apiSecret,
                            passphrase = cred.passphrase,
                            clientId = cred.clientId
                        ),
                        isSandbox = isSandbox
                    )
                } catch (_: Exception) {
                    // Skip unknown exchanges
                }
            }

            BackupResult.Success()
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Unknown error during restore")
        }
    }

    // Backup → Entity mapping (all with id=0 for Room auto-generate)

    private fun BackupPlan.toEntity() = DcaPlanEntity(
        id = 0,
        exchange = Exchange.valueOf(exchange),
        crypto = crypto,
        fiat = fiat,
        amount = BigDecimal(amount),
        frequency = DcaFrequency.valueOf(frequency),
        cronExpression = cronExpression,
        strategy = DcaStrategy.fromString(strategy),
        isEnabled = isEnabled,
        withdrawalEnabled = withdrawalEnabled,
        withdrawalAddress = withdrawalAddress,
        createdAt = Instant.ofEpochMilli(createdAt),
        lastExecutedAt = lastExecutedAt?.let { Instant.ofEpochMilli(it) },
        nextExecutionAt = nextExecutionAt?.let { Instant.ofEpochMilli(it) }
    )

    private fun BackupTransaction.toEntity(remappedPlanId: Long) = TransactionEntity(
        id = 0,
        planId = remappedPlanId,
        exchange = Exchange.valueOf(exchange),
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

    private fun BackupWithdrawal.toEntity(remappedPlanId: Long) = WithdrawalEntity(
        id = 0,
        planId = remappedPlanId,
        exchange = Exchange.valueOf(exchange),
        crypto = crypto,
        amount = BigDecimal(amount),
        address = address,
        txHash = txHash,
        fee = BigDecimal(fee),
        status = WithdrawalStatus.valueOf(status),
        errorMessage = errorMessage,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun BackupNotification.toEntity(remappedPlanId: Long?) = NotificationEntity(
        id = 0,
        type = NotificationType.valueOf(type),
        title = title,
        message = message,
        planId = remappedPlanId,
        crypto = crypto,
        exchange = exchange?.let { try { Exchange.valueOf(it) } catch (_: Exception) { null } },
        isRead = isRead,
        isArchived = isArchived,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun BackupWithdrawalThreshold.toEntity() = WithdrawalThresholdEntity(
        crypto = crypto,
        exchange = Exchange.valueOf(exchange),
        thresholdAmount = BigDecimal(thresholdAmount)
    )
}
