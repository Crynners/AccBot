package com.accbot.dca.domain.model

import androidx.annotation.StringRes
import com.accbot.dca.R
import java.math.BigDecimal

/**
 * DCA Strategy types with their configurations
 */
sealed class DcaStrategy {
    @get:StringRes
    abstract val displayNameRes: Int
    @get:StringRes
    abstract val descriptionRes: Int

    /**
     * Classic DCA - fixed amount at fixed intervals
     */
    object Classic : DcaStrategy() {
        override val displayNameRes = R.string.strategy_classic_name
        override val descriptionRes = R.string.strategy_classic_description
    }

    /**
     * ATH-based DCA - buy more when price is far from All-Time High
     */
    data class AthBased(
        val tiers: List<AthTier> = defaultAthTiers
    ) : DcaStrategy() {
        override val displayNameRes = R.string.strategy_ath_name
        override val descriptionRes = R.string.strategy_ath_description
    }

    /**
     * Fear & Greed DCA - buy more during market fear
     */
    data class FearAndGreed(
        val tiers: List<FearGreedTier> = defaultFearGreedTiers
    ) : DcaStrategy() {
        override val displayNameRes = R.string.strategy_fear_greed_name
        override val descriptionRes = R.string.strategy_fear_greed_description
    }

    companion object {
        fun fromString(value: String): DcaStrategy = when (value) {
            "CLASSIC" -> Classic
            "ATH_BASED" -> AthBased()
            "FEAR_AND_GREED" -> FearAndGreed()
            else -> Classic
        }

        fun toDbString(strategy: DcaStrategy): String = when (strategy) {
            is Classic -> "CLASSIC"
            is AthBased -> "ATH_BASED"
            is FearAndGreed -> "FEAR_AND_GREED"
        }

        val allStrategies: List<DcaStrategy> = listOf(
            Classic,
            AthBased(),
            FearAndGreed()
        )
    }
}

/**
 * ATH distance tier configuration
 * @param maxDistancePercent Maximum distance from ATH for this tier (0.0 to 1.0)
 * @param multiplier Purchase amount multiplier
 */
data class AthTier(
    val maxDistancePercent: Float,
    val multiplier: Float
)

/**
 * Default ATH tiers (even 20% bands):
 * - 0-20% below ATH: buy 50% (near ATH)
 * - 20-40% below: buy 100% (normal)
 * - 40-60% below: buy 150%
 * - 60-80% below: buy 200%
 * - 80-100% below: buy 300% (maximum opportunity)
 */
val defaultAthTiers = listOf(
    AthTier(0.20f, 0.5f),
    AthTier(0.40f, 1.0f),
    AthTier(0.60f, 1.5f),
    AthTier(0.80f, 2.0f),
    AthTier(1.00f, 3.0f)
)

/**
 * Fear & Greed index tier configuration
 * @param maxIndex Maximum Fear & Greed index value for this tier (0-100)
 * @param multiplier Purchase amount multiplier
 */
data class FearGreedTier(
    val maxIndex: Int,
    val multiplier: Float
)

/**
 * Default Fear & Greed tiers (even 20-point bands):
 * - Extreme Fear (0-19): buy 250%
 * - Fear (20-39): buy 150%
 * - Neutral (40-59): buy 100%
 * - Greed (60-79): buy 50%
 * - Extreme Greed (80-100): buy 25%
 */
val defaultFearGreedTiers = listOf(
    FearGreedTier(19, 2.5f),
    FearGreedTier(39, 1.5f),
    FearGreedTier(59, 1.0f),
    FearGreedTier(79, 0.5f),
    FearGreedTier(100, 0.25f)
)

/**
 * Market data required for strategy calculations
 */
data class MarketData(
    val currentPrice: BigDecimal,
    val allTimeHigh: BigDecimal? = null,
    val fearGreedIndex: Int? = null
)

/**
 * Result of strategy multiplier calculation
 */
data class StrategyMultiplierResult(
    val multiplier: Float,
    val reason: String,
    val marketData: MarketData? = null
)
