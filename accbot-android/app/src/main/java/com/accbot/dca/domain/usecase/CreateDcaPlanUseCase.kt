package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaPlanDao
import com.accbot.dca.data.local.DcaPlanEntity
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
    private val userPreferences: UserPreferences
) {
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
        targetAmount: BigDecimal? = null
    ) {
        val now = Instant.now()
        val nextExecution = if (frequency == DcaFrequency.CUSTOM && cronExpression != null) {
            CronUtils.getNextExecution(cronExpression, now)
                ?: now.plus(Duration.ofMinutes(1440))
        } else {
            now.plus(Duration.ofMinutes(frequency.intervalMinutes))
        }

        val plan = DcaPlanEntity(
            exchange = exchange,
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
            targetAmount = targetAmount
        )

        dcaPlanDao.insertPlan(plan)

        // Auto-enable Market Pulse when creating a plan with market-aware strategy
        if (strategy is DcaStrategy.AthBased || strategy is DcaStrategy.FearAndGreed) {
            userPreferences.setMarketPulseEnabled(true)
        }
    }
}
