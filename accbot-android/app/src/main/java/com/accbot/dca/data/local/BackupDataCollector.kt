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

        val thresholds = withdrawalThresholdDao.getAllThresholdsOnce().map { it.toBackup() }

        val credentials = if (options.includeCredentials) {
            val isSandbox = userPreferences.isSandboxMode()
            credentialsStore.getConfiguredExchanges(isSandbox).mapNotNull { exchange ->
                credentialsStore.getCredentials(exchange, isSandbox)?.toBackup()
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
            withdrawals = withdrawals
        )
    }

    fun isSandbox(): Boolean = userPreferences.isSandboxMode()

    suspend fun getDataCounts(): BackupDataCounts {
        val isSandbox = userPreferences.isSandboxMode()
        return BackupDataCounts(
            planCount = dcaPlanDao.getPlanCount(),
            thresholdCount = withdrawalThresholdDao.getAllThresholdsOnce().size,
            credentialCount = credentialsStore.getConfiguredExchanges(isSandbox).size,
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
        targetAmount = targetAmount?.toPlainString()
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
        executedAt = executedAt.toEpochMilli()
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
        createdAt = createdAt.toEpochMilli()
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
        createdAt = createdAt.toEpochMilli()
    )

    private fun WithdrawalThresholdEntity.toBackup() = BackupWithdrawalThreshold(
        crypto = crypto,
        exchange = exchange.name,
        thresholdAmount = thresholdAmount.toPlainString()
    )

    private fun ExchangeCredentials.toBackup() = BackupCredentials(
        exchange = exchange.name,
        apiKey = apiKey,
        apiSecret = apiSecret,
        passphrase = passphrase,
        clientId = clientId
    )
}
