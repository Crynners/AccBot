package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.ExchangeConnectionDao
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.util.CronUtils
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class CreateDcaPlanUseCase @Inject constructor(
    private val dcaPlanDao: DcaPlanDao,
    private val exchangeConnectionDao: ExchangeConnectionDao,
    private val userPreferences: UserPreferences
) {
    /**
     * @param connectionId optional explicit connection. If null, the use case picks the
     *  default (first) connection of [exchange]. If no connection exists for that
     *  exchange, throws [IllegalStateException] - callers must ensure credentials are set
     *  up first (the AddPlan/AddExchange flow does this via [ValidateAndSaveCredentialsUseCase]
     *  which creates the connection alongside the credentials).
     *
     *  Auto-creation of an empty connection here was removed: it produced "ghost"
     *  connections without credentials, which then made the next AddExchange flow
     *  unnecessarily prompt for a name (since the ghost counted as the 1st connection).
     *
     * @throws IllegalStateException when no connection exists for [exchange] and
     *  [connectionId] is null.
     */
    suspend fun execute(
        exchange: Exchange,
        crypto: String,
        fiat: String,
        amount: BigDecimal,
        frequency: DcaFrequency,
        cronExpression: String?,
        strategy: DcaStrategy,
        withdrawalEnabled: Boolean = false,
        withdrawalAddress: String? = null,
        targetAmount: BigDecimal? = null,
        connectionId: Long? = null
    ) {
        val now = Instant.now()
        val nextExecution = if (frequency == DcaFrequency.CUSTOM && cronExpression != null) {
            CronUtils.getNextExecution(cronExpression, now)
                ?: now.plus(Duration.ofMinutes(1440))
        } else {
            now.plus(Duration.ofMinutes(frequency.intervalMinutes))
        }

        val resolvedConnectionId = connectionId
            ?: exchangeConnectionDao.getDefaultByExchange(exchange)?.id
            ?: throw IllegalStateException(
                "No connection exists for $exchange - set up credentials first via AddExchange flow"
            )

        val nextDisplayOrder = dcaPlanDao.getMaxDisplayOrder() + 1

        val plan = DcaPlanEntity(
            exchange = exchange,
            connectionId = resolvedConnectionId,
            crypto = crypto,
            fiat = fiat,
            amount = amount,
            frequency = frequency,
            cronExpression = if (frequency == DcaFrequency.CUSTOM) cronExpression else null,
            strategy = strategy,
            isEnabled = true,
            withdrawalEnabled = withdrawalEnabled,
            withdrawalAddress = withdrawalAddress,
            createdAt = now,
            nextExecutionAt = nextExecution,
            targetAmount = targetAmount,
            displayOrder = nextDisplayOrder
        )

        dcaPlanDao.insertPlan(plan)

        // Auto-enable Market Pulse when creating a plan with market-aware strategy
        if (strategy is DcaStrategy.AthBased || strategy is DcaStrategy.FearAndGreed) {
            userPreferences.setMarketPulseEnabled(true)
        }
    }
}
