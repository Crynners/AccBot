package com.accbot.dca.domain.share

import com.accbot.dca.data.local.DcaPlanEntity
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.model.DcaStrategy
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.TransactionSide
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** Locale-free decode failure; the UI maps each reason to a string resource. */
enum class SharedPlanError {
    NOT_A_PLAN_SHARE, UNSUPPORTED_VERSION, TOO_LARGE, INVALID, PASSWORD_REQUIRED, WRONG_PASSWORD
}

class SharedPlanException(val reason: SharedPlanError) : Exception(reason.name)

/** A received share: the plan plus, for live-update shares, the read-only exchange key. */
data class DecodedShare(val plan: SharedPlan, val credentials: ExchangeCredentials?)

object SharedPlanCodec {
    const val FORMAT = "accbot-plan-share"
    const val CURRENT_VERSION = 1
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    const val MAX_TRADES = 50_000
    private const val MAX_NAME_LENGTH = 100

    private val SHARE_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val ASSET = Regex("^[A-Z0-9]{2,10}$")
    private val REF = Regex("^[0-9a-f]{32}$")
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    fun isValidShareId(value: String): Boolean = SHARE_ID.matches(value)

    /** First 128 bits of SHA-256("accbot-order:<EXCHANGE>:<orderId>") as hex; same on iOS. */
    fun orderRef(exchange: String, orderId: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("accbot-order:$exchange:$orderId".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Build a snapshot of [plan] for sharing. Only confirmed fills are included and,
     * unless [includeAmounts] is set, all absolute amounts are left out.
     */
    fun createSnapshot(
        plan: DcaPlanEntity,
        transactions: List<TransactionEntity>,
        shareId: String,
        isSandbox: Boolean,
        includeAmounts: Boolean,
        now: Instant = Instant.now()
    ): SharedPlanSnapshotDto {
        require(isValidShareId(shareId))
        val fills = transactions
            .filter {
                it.planId == plan.id && it.status.hasFilledPortion &&
                    it.cryptoAmount.signum() > 0 && it.fiatAmount.signum() > 0 && it.price.signum() > 0
            }
            .sortedBy { it.executedAt }
        val buys = fills.filter { it.side == TransactionSide.BUY }
        val boughtCrypto = buys.sumOf { it.cryptoAmount }
        val avgBuyPrice = if (boughtCrypto.signum() > 0) {
            buys.sumOf { it.fiatAmount }.divide(boughtCrypto, 8, RoundingMode.HALF_UP)
        } else null

        return SharedPlanSnapshotDto(
            format = FORMAT,
            version = CURRENT_VERSION,
            shareId = shareId,
            sharedAt = now.toEpochMilli(),
            environment = if (isSandbox) "sandbox" else "prod",
            includesAmounts = includeAmounts,
            plan = SharedPlanInfoDto(
                name = plan.name.take(MAX_NAME_LENGTH),
                exchange = plan.exchange.name,
                crypto = plan.crypto,
                fiat = plan.fiat,
                amount = if (includeAmounts) plan.amount.toPlainString() else null,
                frequency = plan.frequency.name,
                cronExpression = plan.cronExpression,
                strategy = DcaStrategy.toDbString(plan.strategy),
                isEnabled = plan.isEnabled,
                createdAt = plan.createdAt.toEpochMilli()
            ),
            summary = SharedPlanSummaryDto(avgBuyPrice = avgBuyPrice?.stripTrailingZeros()?.toPlainString()),
            transactions = fills.map { tx ->
                SharedPlanTradeDto(
                    executedAt = tx.executedAt.toEpochMilli(),
                    side = tx.side.name,
                    price = tx.price.stripTrailingZeros().toPlainString(),
                    cryptoAmount = if (includeAmounts) tx.cryptoAmount.stripTrailingZeros().toPlainString() else null,
                    fiatAmount = if (includeAmounts) tx.fiatAmount.stripTrailingZeros().toPlainString() else null,
                    fee = if (includeAmounts) tx.fee.stripTrailingZeros().toPlainString() else null,
                    feeAsset = if (includeAmounts) tx.feeAsset.ifBlank { null } else null,
                    ref = tx.exchangeOrderId?.takeIf { it.isNotBlank() }?.let { orderRef(plan.exchange.name, it) }
                )
            }
        )
    }

    fun encode(snapshot: SharedPlanSnapshotDto): String = gson.toJson(snapshot)

    /** Re-encode a validated plan for local storage. Never contains credentials. */
    fun encode(plan: SharedPlan): String = encode(toDto(plan))

    /**
     * Produce the file content to send. Without [access] the snapshot is plain JSON.
     * With [access] (live updates) the snapshot and key are encrypted with [password].
     */
    fun encodeShare(
        snapshot: SharedPlanSnapshotDto,
        access: SharedPlanAccessDto?,
        password: String?,
        encrypt: (plaintext: ByteArray, password: String) -> String
    ): String {
        if (access == null) return encode(snapshot.copy(access = null, encrypted = null, data = null, local = null))
        require(!password.isNullOrEmpty()) { "A password is required to share a key" }
        require(snapshot.includesAmounts == true) { "A shared key exposes amounts" }
        val inner = encode(snapshot.copy(access = access, encrypted = null, data = null, local = null))
        return encode(
            SharedPlanSnapshotDto(
                format = FORMAT,
                version = CURRENT_VERSION,
                encrypted = true,
                data = encrypt(inner.toByteArray(Charsets.UTF_8), password)
            )
        )
    }

    /**
     * Decode a received file. A live-update share needs [password]; without it this throws
     * [SharedPlanError.PASSWORD_REQUIRED].
     */
    fun decodeShare(
        json: String,
        password: String?,
        decrypt: (data: String, password: String) -> ByteArray
    ): DecodedShare {
        val outer = parseEnvelope(json)
        if (outer.encrypted != true) {
            if (outer.access != null || outer.data != null) invalid()
            return DecodedShare(convert(outer, trustLocal = false, live = null), null)
        }
        val data = outer.data ?: invalid()
        if (password.isNullOrEmpty()) throw SharedPlanException(SharedPlanError.PASSWORD_REQUIRED)
        val innerJson = try {
            decrypt(data, password).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            throw SharedPlanException(SharedPlanError.WRONG_PASSWORD)
        }
        val inner = parseEnvelope(innerJson)
        if (inner.encrypted == true || inner.includesAmounts != true) invalid()
        val access = inner.access ?: invalid()
        val credentials = try {
            ExchangeCredentials(
                exchange = Exchange.valueOf(access.exchange ?: invalid()),
                apiKey = access.apiKey?.trim()?.takeIf { it.isNotEmpty() && it.length <= 512 } ?: invalid(),
                apiSecret = access.apiSecret?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 } ?: invalid(),
                passphrase = access.passphrase?.takeIf { it.isNotEmpty() },
                clientId = access.clientId?.takeIf { it.isNotEmpty() }
            )
        } catch (e: SharedPlanException) {
            throw e
        } catch (_: Exception) {
            invalid()
        }
        val verifiedReadOnly = access.verifiedReadOnly == true
        val plan = convert(inner, trustLocal = false, live = verifiedReadOnly)
            .copy(fullAccessKey = access.fullAccess == true && !verifiedReadOnly)
        if (plan.exchange != credentials.exchange) invalid()
        return DecodedShare(plan, credentials)
    }

    /** Decode a stored share (written by [encode]). @throws SharedPlanException */
    fun decode(json: String): SharedPlan {
        val dto = parseEnvelope(json)
        if (dto.encrypted == true || dto.access != null) invalid()
        return convert(dto, trustLocal = true, live = null)
    }

    private fun parseEnvelope(json: String): SharedPlanSnapshotDto {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) throw SharedPlanException(SharedPlanError.TOO_LARGE)
        val dto = try {
            gson.fromJson(json, SharedPlanSnapshotDto::class.java)
        } catch (_: Exception) {
            null
        } ?: throw SharedPlanException(SharedPlanError.NOT_A_PLAN_SHARE)
        if (dto.format != FORMAT) throw SharedPlanException(SharedPlanError.NOT_A_PLAN_SHARE)
        val version = dto.version ?: invalid()
        if (version !in 1..CURRENT_VERSION) throw SharedPlanException(SharedPlanError.UNSUPPORTED_VERSION)
        return dto
    }

    /** @param live null for a snapshot without key, otherwise whether the key was verified read-only. */
    private fun convert(dto: SharedPlanSnapshotDto, trustLocal: Boolean, live: Boolean?): SharedPlan {
        val plan = try {
            fromDto(dto)
        } catch (e: SharedPlanException) {
            throw e
        } catch (_: Exception) {
            invalid()
        }
        val local = dto.local.takeIf { trustLocal }
        return when {
            live != null -> plan.copy(liveUpdates = true, verifiedReadOnly = live)
            local != null -> plan.copy(
                liveUpdates = local.liveUpdates == true,
                verifiedReadOnly = local.verifiedReadOnly == true,
                fullAccessKey = local.fullAccessKey == true,
                refreshedAt = local.refreshedAt?.let(Instant::ofEpochMilli),
                refreshFailed = local.refreshFailed == true
            )
            else -> plan
        }
    }

    private fun fromDto(dto: SharedPlanSnapshotDto): SharedPlan {
        val shareId = dto.shareId?.takeIf(::isValidShareId) ?: invalid()
        val includesAmounts = dto.includesAmounts ?: invalid()
        val plan = dto.plan ?: invalid()
        val crypto = plan.crypto?.takeIf { ASSET.matches(it) } ?: invalid()
        val fiat = plan.fiat?.takeIf { ASSET.matches(it) } ?: invalid()
        val exchangeName = plan.exchange?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 } ?: invalid()
        val rawTrades = dto.transactions ?: emptyList()
        if (rawTrades.size > MAX_TRADES) throw SharedPlanException(SharedPlanError.TOO_LARGE)

        val trades = rawTrades.map { t ->
            val side = when (t.side) {
                "BUY" -> TransactionSide.BUY
                "SELL" -> TransactionSide.SELL
                else -> invalid()
            }
            SharedPlanTrade(
                executedAt = Instant.ofEpochMilli(t.executedAt ?: invalid()),
                side = side,
                price = positive(t.price) ?: invalid(),
                cryptoAmount = if (includesAmounts) positive(t.cryptoAmount) ?: invalid() else null,
                fiatAmount = if (includesAmounts) positive(t.fiatAmount) ?: invalid() else null,
                fee = if (includesAmounts) nonNegative(t.fee) else null,
                feeAsset = if (includesAmounts) t.feeAsset?.takeIf { ASSET.matches(it) } else null,
                ref = t.ref?.takeIf { REF.matches(it) }
            )
        }.sortedBy { it.executedAt }

        return SharedPlan(
            shareId = shareId.lowercase(),
            sharedAt = Instant.ofEpochMilli(dto.sharedAt ?: invalid()),
            isSandbox = dto.environment == "sandbox",
            includesAmounts = includesAmounts,
            name = plan.name.orEmpty().trim().take(MAX_NAME_LENGTH),
            exchange = Exchange.entries.firstOrNull { it.name == exchangeName },
            exchangeName = exchangeName,
            crypto = crypto,
            fiat = fiat,
            amount = if (includesAmounts) positive(plan.amount) else null,
            frequency = DcaFrequency.entries.firstOrNull { it.name == plan.frequency },
            cronExpression = plan.cronExpression?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 },
            strategy = DcaStrategy.fromString(plan.strategy.orEmpty()),
            isEnabled = plan.isEnabled ?: false,
            createdAt = Instant.ofEpochMilli(plan.createdAt ?: 0L),
            statedAvgBuyPrice = positive(dto.summary?.avgBuyPrice),
            trades = trades
        )
    }

    private fun toDto(plan: SharedPlan) = SharedPlanSnapshotDto(
        format = FORMAT,
        version = CURRENT_VERSION,
        shareId = plan.shareId,
        sharedAt = plan.sharedAt.toEpochMilli(),
        environment = if (plan.isSandbox) "sandbox" else "prod",
        includesAmounts = plan.includesAmounts,
        plan = SharedPlanInfoDto(
            name = plan.name,
            exchange = plan.exchangeName,
            crypto = plan.crypto,
            fiat = plan.fiat,
            amount = plan.amount?.toPlainString(),
            frequency = plan.frequency?.name,
            cronExpression = plan.cronExpression,
            strategy = DcaStrategy.toDbString(plan.strategy),
            isEnabled = plan.isEnabled,
            createdAt = plan.createdAt.toEpochMilli()
        ),
        summary = SharedPlanSummaryDto(avgBuyPrice = plan.statedAvgBuyPrice?.toPlainString()),
        transactions = plan.trades.map {
            SharedPlanTradeDto(
                executedAt = it.executedAt.toEpochMilli(),
                side = it.side.name,
                price = it.price.toPlainString(),
                cryptoAmount = it.cryptoAmount?.toPlainString(),
                fiatAmount = it.fiatAmount?.toPlainString(),
                fee = it.fee?.toPlainString(),
                feeAsset = it.feeAsset,
                ref = it.ref
            )
        },
        local = SharedPlanLocalDto(
            liveUpdates = plan.liveUpdates,
            verifiedReadOnly = plan.verifiedReadOnly,
            fullAccessKey = plan.fullAccessKey.takeIf { it },
            refreshedAt = plan.refreshedAt?.toEpochMilli(),
            refreshFailed = plan.refreshFailed
        )
    )

    private fun positive(value: String?): BigDecimal? =
        value?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    private fun nonNegative(value: String?): BigDecimal? =
        value?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    private fun invalid(): Nothing = throw SharedPlanException(SharedPlanError.INVALID)
}
