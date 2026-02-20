package com.accbot.dca.domain.usecase

import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.util.CronUtils
import com.accbot.dca.presentation.model.MonthlyCostEstimate
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculateMonthlyCostUseCase @Inject constructor(
    private val marketDataService: MarketDataService
) {
    fun getEffectiveIntervalMinutes(frequency: DcaFrequency, cronExpression: String?): Long {
        return if (frequency == DcaFrequency.CUSTOM) {
            CronUtils.getIntervalMinutesEstimate(cronExpression ?: "") ?: 1440L
        } else {
            frequency.intervalMinutes
        }
    }

    suspend fun computeEstimate(
        amount: BigDecimal,
        frequency: DcaFrequency,
        cronExpression: String?,
        strategy: DcaStrategy,
        crypto: String,
        fiat: String
    ): MonthlyCostEstimate? {
        if (amount <= BigDecimal.ZERO) return null

        val intervalMinutes = getEffectiveIntervalMinutes(frequency, cronExpression)
        if (intervalMinutes <= 0) return null

        val runsPerMonth = BigDecimal(30 * 24 * 60).divide(
            BigDecimal(intervalMinutes), 2, RoundingMode.HALF_UP
        )

        return when (strategy) {
            is DcaStrategy.Classic -> {
                val monthly = amount.multiply(runsPerMonth)
                MonthlyCostEstimate(
                    minMonthly = monthly,
                    maxMonthly = monthly,
                    currentMonthly = monthly,
                    currentInfo = null
                )
            }
            is DcaStrategy.AthBased -> {
                computeStrategyEstimate(
                    amount = amount,
                    runsPerMonth = runsPerMonth,
                    minMult = strategy.tiers.minOf { it.multiplier },
                    maxMult = strategy.tiers.maxOf { it.multiplier },
                    fetchCurrentMultiplier = {
                        val cryptoData = marketDataService.getCryptoData(crypto, fiat)
                        if (cryptoData != null) {
                            val mult = strategy.tiers.sortedBy { it.maxDistancePercent }
                                .firstOrNull { cryptoData.athDistance <= it.maxDistancePercent }
                                ?.multiplier ?: 1.0f
                            mult to "$crypto is ${cryptoData.athDistancePercent}% below ATH"
                        } else null
                    }
                )
            }
            is DcaStrategy.FearAndGreed -> {
                computeStrategyEstimate(
                    amount = amount,
                    runsPerMonth = runsPerMonth,
                    minMult = strategy.tiers.minOf { it.multiplier },
                    maxMult = strategy.tiers.maxOf { it.multiplier },
                    fetchCurrentMultiplier = {
                        val fngData = marketDataService.getFearGreedIndex()
                        if (fngData != null) {
                            val mult = strategy.tiers.sortedBy { it.maxIndex }
                                .firstOrNull { fngData.value <= it.maxIndex }
                                ?.multiplier ?: 1.0f
                            mult to "Fear & Greed: ${fngData.value} (${fngData.classification})"
                        } else null
                    }
                )
            }
        }
    }

    private suspend fun computeStrategyEstimate(
        amount: BigDecimal,
        runsPerMonth: BigDecimal,
        minMult: Float,
        maxMult: Float,
        fetchCurrentMultiplier: suspend () -> Pair<Float, String>?
    ): MonthlyCostEstimate {
        val minMonthly = amount.multiply(BigDecimal(minMult.toString())).multiply(runsPerMonth)
        val maxMonthly = amount.multiply(BigDecimal(maxMult.toString())).multiply(runsPerMonth)

        val result = fetchCurrentMultiplier()
        val currentMonthly = result?.let { (mult, _) ->
            amount.multiply(BigDecimal(mult.toString())).multiply(runsPerMonth)
        }

        return MonthlyCostEstimate(
            minMonthly = minMonthly,
            maxMonthly = maxMonthly,
            currentMonthly = currentMonthly,
            currentInfo = result?.second
        )
    }
}
