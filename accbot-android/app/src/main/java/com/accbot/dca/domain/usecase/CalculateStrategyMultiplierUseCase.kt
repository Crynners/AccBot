package com.accbot.dca.domain.usecase

import android.util.Log
import com.accbot.dca.data.remote.MarketDataService
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.MarketData
import com.accbot.dca.domain.model.StrategyMultiplierResult
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Use case for calculating DCA purchase multiplier based on strategy
 */
class CalculateStrategyMultiplierUseCase @Inject constructor(
    private val marketDataService: MarketDataService
) {
    companion object {
        private const val TAG = "StrategyMultiplier"
    }

    /**
     * Calculate the purchase amount multiplier based on strategy and current market conditions
     *
     * @param strategy The DCA strategy to use
     * @param crypto The cryptocurrency symbol (BTC, ETH, etc.)
     * @param fiat The fiat currency (EUR, USD, etc.)
     * @return StrategyMultiplierResult containing the multiplier and explanation
     */
    suspend operator fun invoke(
        strategy: DcaStrategy,
        crypto: String,
        fiat: String
    ): StrategyMultiplierResult {
        return when (strategy) {
            is DcaStrategy.Classic -> calculateClassic()
            is DcaStrategy.AthBased -> calculateAthBased(strategy, crypto, fiat)
            is DcaStrategy.FearAndGreed -> calculateFearAndGreed(strategy)
        }
    }

    private fun calculateClassic(): StrategyMultiplierResult {
        return StrategyMultiplierResult(
            multiplier = 1.0f,
            reason = "Classic DCA: Fixed amount purchase"
        )
    }

    private suspend fun calculateAthBased(
        strategy: DcaStrategy.AthBased,
        crypto: String,
        fiat: String
    ): StrategyMultiplierResult {
        val cryptoData = marketDataService.getCryptoData(crypto, fiat)

        if (cryptoData == null) {
            Log.w(TAG, "Could not fetch ATH data for $crypto/$fiat, using default multiplier")
            return StrategyMultiplierResult(
                multiplier = 1.0f,
                reason = "ATH data unavailable, using default amount"
            )
        }

        val athDistance = cryptoData.athDistance

        // Find the appropriate tier
        val multiplier = strategy.tiers
            .sortedBy { it.maxDistancePercent }
            .firstOrNull { athDistance <= it.maxDistancePercent }
            ?.multiplier ?: 1.0f

        val distancePercent = (athDistance * 100).toInt()

        Log.d(TAG, "ATH-based: $crypto is $distancePercent% below ATH, multiplier: $multiplier")

        return StrategyMultiplierResult(
            multiplier = multiplier,
            reason = "$crypto is $distancePercent% below ATH → ${formatMultiplier(multiplier)}",
            marketData = MarketData(
                currentPrice = cryptoData.currentPrice,
                allTimeHigh = cryptoData.allTimeHigh
            )
        )
    }

    private suspend fun calculateFearAndGreed(
        strategy: DcaStrategy.FearAndGreed
    ): StrategyMultiplierResult {
        val fngData = marketDataService.getFearGreedIndex()

        if (fngData == null) {
            Log.w(TAG, "Could not fetch Fear & Greed data, using default multiplier")
            return StrategyMultiplierResult(
                multiplier = 1.0f,
                reason = "Fear & Greed data unavailable, using default amount"
            )
        }

        val index = fngData.value

        // Find the appropriate tier
        val multiplier = strategy.tiers
            .sortedBy { it.maxIndex }
            .firstOrNull { index <= it.maxIndex }
            ?.multiplier ?: 1.0f

        Log.d(TAG, "Fear & Greed: Index is $index (${fngData.classification}), multiplier: $multiplier")

        return StrategyMultiplierResult(
            multiplier = multiplier,
            reason = "${fngData.classification} ($index) → ${formatMultiplier(multiplier)}",
            marketData = MarketData(
                currentPrice = BigDecimal.ZERO,
                fearGreedIndex = index
            )
        )
    }

    private fun formatMultiplier(multiplier: Float): String {
        return when {
            multiplier < 1.0f -> "${(multiplier * 100).toInt()}% of base"
            multiplier == 1.0f -> "normal amount"
            else -> "${(multiplier * 100).toInt()}% of base"
        }
    }
}
