package com.accbot.dca.data.local

import com.accbot.dca.domain.model.AppNotification
import com.accbot.dca.domain.model.DcaPlan
import com.accbot.dca.domain.model.Transaction
import com.accbot.dca.domain.model.WithdrawalThreshold

fun DcaPlanEntity.toDomain() = DcaPlan(
    id = id,
    exchange = exchange,
    connectionId = connectionId,
    crypto = crypto,
    fiat = fiat,
    amount = amount,
    frequency = frequency,
    cronExpression = cronExpression,
    strategy = strategy,
    isEnabled = isEnabled,
    withdrawalEnabled = withdrawalEnabled,
    withdrawalAddress = withdrawalAddress,
    createdAt = createdAt,
    lastExecutedAt = lastExecutedAt,
    nextExecutionAt = nextExecutionAt,
    targetAmount = targetAmount
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    planId = planId,
    exchange = exchange,
    connectionId = connectionId,
    crypto = crypto,
    fiat = fiat,
    fiatAmount = fiatAmount,
    cryptoAmount = cryptoAmount,
    price = price,
    fee = fee,
    feeAsset = feeAsset,
    status = status,
    exchangeOrderId = exchangeOrderId,
    errorMessage = errorMessage,
    warningMessage = warningMessage,
    executedAt = executedAt
)

fun NotificationEntity.toDomain() = AppNotification(
    id = id,
    type = type,
    title = title,
    message = message,
    planId = planId,
    crypto = crypto,
    exchange = exchange,
    connectionId = connectionId,
    isRead = isRead,
    isArchived = isArchived,
    createdAt = createdAt
)

/**
 * Convert a [WithdrawalThresholdEntity] to its domain model. Requires the parent
 * [ExchangeConnectionEntity] for the denormalized `exchange` and `connectionName`
 * fields that the UI needs without a JOIN at the consumer.
 */
fun WithdrawalThresholdEntity.toDomain(connection: ExchangeConnectionEntity) = WithdrawalThreshold(
    crypto = crypto,
    connectionId = connectionId,
    exchange = connection.exchange,
    connectionName = connection.name,
    thresholdAmount = thresholdAmount
)
