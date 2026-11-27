package com.accbot.dca.domain.share

import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionSide
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Wire format of a read-only plan share (`accbot-plan-share`).
 *
 * A share is a static snapshot: it never contains API credentials, exchange order IDs,
 * connection names or withdrawal addresses, so it cannot be used to run the plan.
 * Amounts are serialized as plain decimal strings and timestamps as epoch milliseconds,
 * matching the backup format. Every field is nullable because Gson ignores Kotlin
 * null-safety; [SharedPlanCodec.decode] validates and converts to [SharedPlan].
 *
 * The same JSON is produced and read by the iOS app (`SharedPlanCodec.swift`).
 */
data class SharedPlanSnapshotDto(
    @SerializedName("format") val format: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("shareId") val shareId: String? = null,
    @SerializedName("sharedAt") val sharedAt: Long? = null,
    @SerializedName("environment") val environment: String? = null,
    @SerializedName("includesAmounts") val includesAmounts: Boolean? = null,
    @SerializedName("plan") val plan: SharedPlanInfoDto? = null,
    @SerializedName("summary") val summary: SharedPlanSummaryDto? = null,
    @SerializedName("transactions") val transactions: List<SharedPlanTradeDto>? = null,
    /**
     * True for a live-update share: the whole snapshot including [access] is AES-GCM
     * encrypted with a password into [data] and only format/version/encrypted are clear.
     */
    @SerializedName("encrypted") val encrypted: Boolean? = null,
    @SerializedName("data") val data: String? = null,
    /** Exchange key for live updates; only ever present inside the encrypted payload. */
    @SerializedName("access") val access: SharedPlanAccessDto? = null,
    /** Local state of a stored share; ignored in received files. */
    @SerializedName("local") val local: SharedPlanLocalDto? = null
)

data class SharedPlanAccessDto(
    @SerializedName("exchange") val exchange: String? = null,
    @SerializedName("apiKey") val apiKey: String? = null,
    @SerializedName("apiSecret") val apiSecret: String? = null,
    @SerializedName("passphrase") val passphrase: String? = null,
    @SerializedName("clientId") val clientId: String? = null,
    /** True when the sender's exchange confirmed the key cannot trade or withdraw. */
    @SerializedName("verifiedReadOnly") val verifiedReadOnly: Boolean? = null,
    /**
     * True when the sender knowingly shared the key the plan trades with instead of a
     * separate read-only key. The recipient then accepts a key that can trade, but still
     * uses it only for reads.
     */
    @SerializedName("fullAccess") val fullAccess: Boolean? = null
)

data class SharedPlanLocalDto(
    @SerializedName("liveUpdates") val liveUpdates: Boolean? = null,
    @SerializedName("verifiedReadOnly") val verifiedReadOnly: Boolean? = null,
    @SerializedName("fullAccessKey") val fullAccessKey: Boolean? = null,
    @SerializedName("refreshedAt") val refreshedAt: Long? = null,
    @SerializedName("refreshFailed") val refreshFailed: Boolean? = null,
    /** The sender's trades come from the exchange rather than from AccBot's own purchases. */
    @SerializedName("tradesFromExchange") val tradesFromExchange: Boolean? = null
)

data class SharedPlanInfoDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("exchange") val exchange: String? = null,
    @SerializedName("crypto") val crypto: String? = null,
    @SerializedName("fiat") val fiat: String? = null,
    /** Base purchase amount per execution. Null when the share hides amounts. */
    @SerializedName("amount") val amount: String? = null,
    @SerializedName("frequency") val frequency: String? = null,
    @SerializedName("cronExpression") val cronExpression: String? = null,
    @SerializedName("strategy") val strategy: String? = null,
    @SerializedName("isEnabled") val isEnabled: Boolean? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    /** Plan start: the earlier of its creation and its first trade. Optional in older files. */
    @SerializedName("startedAt") val startedAt: Long? = null
)

/** Values the recipient cannot derive when amounts are hidden. */
data class SharedPlanSummaryDto(
    @SerializedName("avgBuyPrice") val avgBuyPrice: String? = null
)

data class SharedPlanTradeDto(
    @SerializedName("executedAt") val executedAt: Long? = null,
    @SerializedName("side") val side: String? = null,
    @SerializedName("price") val price: String? = null,
    @SerializedName("cryptoAmount") val cryptoAmount: String? = null,
    @SerializedName("fiatAmount") val fiatAmount: String? = null,
    @SerializedName("fee") val fee: String? = null,
    @SerializedName("feeAsset") val feeAsset: String? = null,
    /**
     * One-way reference of the exchange order (see [SharedPlanCodec.orderRef]) so live
     * updates can skip trades already in the snapshot without revealing the order ID.
     */
    @SerializedName("ref") val ref: String? = null
)

/** Validated, read-only plan snapshot received from someone else. */
data class SharedPlan(
    val shareId: String,
    val sharedAt: Instant,
    val isSandbox: Boolean,
    val includesAmounts: Boolean,
    val name: String,
    /** Null when the sender used an exchange this app version does not know. */
    val exchange: Exchange?,
    val exchangeName: String,
    val crypto: String,
    val fiat: String,
    val amount: BigDecimal?,
    val frequency: DcaFrequency?,
    val cronExpression: String?,
    val strategy: DcaStrategy,
    val isEnabled: Boolean,
    val createdAt: Instant,
    /** Plan start (earlier of creation and first trade); derived when an old share lacks it. */
    val startedAt: Instant,
    /** Average buy price stated by the sender (used when amounts are hidden). */
    val statedAvgBuyPrice: BigDecimal?,
    /** Oldest first. */
    val trades: List<SharedPlanTrade>,
    /** The sender included a read-only key, so new trades can be fetched from the exchange. */
    val liveUpdates: Boolean = false,
    val verifiedReadOnly: Boolean = false,
    /** The sender shared the plan's own trading key (see [SharedPlanAccessDto.fullAccess]). */
    val fullAccessKey: Boolean = false,
    val refreshedAt: Instant? = null,
    val refreshFailed: Boolean = false,
    /** Locally stored only: the trades were fetched from the exchange, not AccBot purchases. */
    val tradesFromExchange: Boolean = false
) {
    val displayName: String get() = name.ifBlank { "$crypto/$fiat" }

    val summary: SharedPlanSummary by lazy { SharedPlanSummary.from(this) }
}

data class SharedPlanTrade(
    val executedAt: Instant,
    val side: TransactionSide,
    val price: BigDecimal,
    val cryptoAmount: BigDecimal?,
    val fiatAmount: BigDecimal?,
    val fee: BigDecimal?,
    val feeAsset: String?,
    val ref: String? = null
)

/** Figures shown on the read-only detail. Amount-based values are null when hidden. */
data class SharedPlanSummary(
    val buyCount: Int,
    val sellCount: Int,
    val firstTradeAt: Instant?,
    val lastTradeAt: Instant?,
    val avgBuyPrice: BigDecimal?,
    val totalInvested: BigDecimal?,
    val totalBought: BigDecimal?,
    val totalSold: BigDecimal?,
    val totalSoldFiat: BigDecimal?,
    val heldCrypto: BigDecimal?
) {
    /** Performance of [currentPrice] against the average buy price, in percent. */
    fun performancePercent(currentPrice: BigDecimal?): BigDecimal? {
        val avg = avgBuyPrice ?: return null
        if (currentPrice == null || currentPrice <= BigDecimal.ZERO || avg <= BigDecimal.ZERO) return null
        return currentPrice.subtract(avg)
            .multiply(BigDecimal(100))
            .divide(avg, 2, RoundingMode.HALF_UP)
    }

    fun currentValue(currentPrice: BigDecimal?): BigDecimal? {
        val held = heldCrypto ?: return null
        if (currentPrice == null || currentPrice <= BigDecimal.ZERO) return null
        return held.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP)
    }

    companion object {
        fun from(plan: SharedPlan): SharedPlanSummary {
            val buys = plan.trades.filter { it.side == TransactionSide.BUY }
            val sells = plan.trades.filter { it.side == TransactionSide.SELL }
            val bought = if (plan.includesAmounts) buys.sumOf { it.cryptoAmount ?: BigDecimal.ZERO } else null
            val invested = if (plan.includesAmounts) buys.sumOf { it.fiatAmount ?: BigDecimal.ZERO } else null
            val sold = if (plan.includesAmounts) sells.sumOf { it.cryptoAmount ?: BigDecimal.ZERO } else null
            val soldFiat = if (plan.includesAmounts) sells.sumOf { it.fiatAmount ?: BigDecimal.ZERO } else null
            val computedAvg = if (bought != null && invested != null && bought > BigDecimal.ZERO) {
                invested.divide(bought, 8, RoundingMode.HALF_UP)
            } else null
            return SharedPlanSummary(
                buyCount = buys.size,
                sellCount = sells.size,
                firstTradeAt = plan.trades.firstOrNull()?.executedAt,
                lastTradeAt = plan.trades.lastOrNull()?.executedAt,
                avgBuyPrice = computedAvg ?: plan.statedAvgBuyPrice,
                totalInvested = invested,
                totalBought = bought,
                totalSold = sold,
                totalSoldFiat = soldFiat,
                heldCrypto = if (bought != null && sold != null) (bought - sold).max(BigDecimal.ZERO) else null
            )
        }
    }
}
