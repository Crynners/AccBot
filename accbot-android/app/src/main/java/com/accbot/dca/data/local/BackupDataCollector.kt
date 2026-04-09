package com.accbot.dca.data.local

import com.accbot.dca.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects all app data (DB + preferences) into a BackupPayload.
 */
@Singleton
class BackupDataCollector @Inject constructor(
    private val dcaPlanDao: DcaPlanDao,
    private val transactionDao: TransactionDao,
    private val notificationDao: NotificationDao,
    private val withdrawalDao: WithdrawalDao,
    private val withdrawalThresholdDao: WithdrawalThresholdDao,
    private val exchangeConnectionDao: ExchangeConnectionDao,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences
) {
    suspend fun collect(options: BackupExportOptions): BackupPayload {
        val plans = dcaPlanDao.getAllPlansOnce().map { it.toBackup() }

        val settings = BackupSettings(
            appTheme = userPreferences.getAppTheme().name,
            notificationsEnabled = userPreferences.areNotificationsEnabled(),
            purchaseNotifications = userPreferences.arePurchaseNotificationsEnabled(),
            errorNotifications = userPreferences.areErrorNotificationsEnabled(),
            weeklySummaryNotifications = userPreferences.areWeeklySummaryNotificationsEnabled(),
            languageTag = userPreferences.getLanguageTag(),
            biometricLockEnabled = userPreferences.isBiometricLockEnabled(),
            lowBalanceThresholdDays = userPreferences.getLowBalanceThresholdDays()
        )

        // v2: snapshot all connections so the restorer can recreate the envelope structure.
        val connections = exchangeConnectionDao.getAll().map { it.toBackup() }

        // Thresholds carry both exchange enum (v1 compat) and connectionId (v2).
        val thresholds = withdrawalThresholdDao.getAllThresholdsOnce().mapNotNull { entity ->
            val connection = exchangeConnectionDao.getById(entity.connectionId) ?: return@mapNotNull null
            BackupWithdrawalThreshold(
                crypto = entity.crypto,
                exchange = connection.exchange.name,
                connectionId = entity.connectionId,
                thresholdAmount = entity.thresholdAmount.toPlainString()
            )
        }

        // Credentials: iterate all connections (not just exchanges) so multiple envelopes
        // on the same exchange roundtrip cleanly. Each backup credential row carries the
        // source connectionId.
        val credentials = if (options.includeCredentials) {
            val isSandbox = userPreferences.isSandboxMode()
            connections.mapNotNull { conn ->
                val source = credentialsStore.getCredentials(conn.id, isSandbox) ?: return@mapNotNull null
                BackupCredentials(
                    exchange = source.exchange.name,
                    apiKey = source.apiKey,
                    apiSecret = source.apiSecret,
                    passphrase = source.passphrase,
                    clientId = source.clientId,
                    connectionId = conn.id
                )
            }
        } else {
            emptyList()
        }

        val transactions = if (options.includeTransactions) {
            transactionDao.getAllTransactionsOnce().map { it.toBackup() }
        } else {
            emptyList()
        }

        val notifications = if (options.includeNotifications) {
            notificationDao.getAllNotificationsOnce().map { it.toBackup() }
        } else {
            emptyList()
        }

        val withdrawals = if (options.includeWithdrawals) {
            withdrawalDao.getAllWithdrawalsOnce().map { it.toBackup() }
        } else {
            emptyList()
        }

        return BackupPayload(
            plans = plans,
            settings = settings,
            withdrawalThresholds = thresholds,
            credentials = credentials,
            transactions = transactions,
            notifications = notifications,
            withdrawals = withdrawals,
            connections = connections
        )
    }

    fun isSandbox(): Boolean = userPreferences.isSandboxMode()

    suspend fun getDataCounts(): BackupDataCounts {
        val isSandbox = userPreferences.isSandboxMode()
        @Suppress("DEPRECATION")
        val credentialCount = credentialsStore.getConfiguredExchanges(isSandbox).size
        return BackupDataCounts(
            planCount = dcaPlanDao.getPlanCount(),
            thresholdCount = withdrawalThresholdDao.getAllThresholdsOnce().size,
            credentialCount = credentialCount,
            transactionCount = transactionDao.getTransactionCount(),
            notificationCount = notificationDao.getNotificationCount(),
            withdrawalCount = withdrawalDao.getWithdrawalCount()
        )
    }

    // Entity → Backup mapping

    private fun DcaPlanEntity.toBackup() = BackupPlan(
        id = id,
        exchange = exchange.name,
        crypto = crypto,
        fiat = fiat,
        amount = amount.toPlainString(),
        frequency = frequency.name,
        cronExpression = cronExpression,
        strategy = DcaStrategy.toDbString(strategy),
        isEnabled = isEnabled,
        withdrawalEnabled = withdrawalEnabled,
        withdrawalAddress = withdrawalAddress,
        createdAt = createdAt.toEpochMilli(),
        lastExecutedAt = lastExecutedAt?.toEpochMilli(),
        nextExecutionAt = nextExecutionAt?.toEpochMilli(),
        targetAmount = targetAmount?.toPlainString(),
        connectionId = connectionId
    )

    private fun TransactionEntity.toBackup() = BackupTransaction(
        id = id,
        planId = planId,
        exchange = exchange.name,
        crypto = crypto,
        fiat = fiat,
        fiatAmount = fiatAmount.toPlainString(),
        cryptoAmount = cryptoAmount.toPlainString(),
        price = price.toPlainString(),
        fee = fee.toPlainString(),
        feeAsset = feeAsset,
        status = status.name,
        exchangeOrderId = exchangeOrderId,
        errorMessage = errorMessage,
        warningMessage = warningMessage,
        executedAt = executedAt.toEpochMilli(),
        connectionId = connectionId
    )

    private fun NotificationEntity.toBackup() = BackupNotification(
        id = id,
        type = type.name,
        title = title,
        message = message,
        planId = planId,
        crypto = crypto,
        exchange = exchange?.name,
        isRead = isRead,
        isArchived = isArchived,
        templateArgs = templateArgs,
        createdAt = createdAt.toEpochMilli(),
        connectionId = connectionId
    )

    private fun WithdrawalEntity.toBackup() = BackupWithdrawal(
        id = id,
        planId = planId,
        exchange = exchange.name,
        crypto = crypto,
        amount = amount.toPlainString(),
        address = address,
        txHash = txHash,
        fee = fee.toPlainString(),
        status = status.name,
        errorMessage = errorMessage,
        createdAt = createdAt.toEpochMilli(),
        connectionId = connectionId
    )

    private fun ExchangeConnectionEntity.toBackup() = BackupExchangeConnection(
        id = id,
        exchange = exchange.name,
        name = name,
        createdAt = createdAt.toEpochMilli(),
        displayOrder = displayOrder
    )

    // Note: WithdrawalThresholdEntity → BackupWithdrawalThreshold conversion is inlined in
    // collect() above because it requires looking up the parent connection's exchange.
}
