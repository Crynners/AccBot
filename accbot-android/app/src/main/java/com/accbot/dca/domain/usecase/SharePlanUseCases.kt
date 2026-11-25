package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.SharedPlanStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.domain.model.HistoricalTrade
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.domain.share.SharedPlan
import com.accbot.dca.domain.share.SharedPlanAccessDto
import com.accbot.dca.domain.share.SharedPlanCodec
import com.accbot.dca.domain.share.SharedPlanError
import com.accbot.dca.domain.share.SharedPlanException
import com.accbot.dca.domain.share.SharedPlanTrade
import com.accbot.dca.exchange.ReadOnlyExchangeClient
import com.accbot.dca.exchange.ReadOnlyExchangeClient.KeyCheck
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class PlanShareFile(val fileName: String, val content: String)

/** Read-only key and file password for a live-update share. */
data class LiveShareAccess(
    val credentials: ExchangeCredentials,
    val password: String,
    /** Result of [VerifyShareKeyUseCase]; UNVERIFIABLE requires the user's explicit confirmation. */
    val keyCheck: KeyCheck
)

/** Locale-free reasons a key cannot be shared; the UI maps them to strings. */
enum class ShareKeyProblem { LIVE_UPDATES_UNSUPPORTED, WRONG_EXCHANGE, SAME_AS_PLAN_KEY, NOT_READ_ONLY, CHECK_FAILED }

class ShareKeyException(val problem: ShareKeyProblem) : Exception(problem.name)

/**
 * Checks a key the user wants to share. It must be a separate key for the plan's exchange,
 * different from the key the plan trades with, and read-only where the exchange can tell.
 */
class VerifyShareKeyUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val userPreferences: UserPreferences,
    private val readOnlyClients: ReadOnlyExchangeClient.Factory
) {
    /** @throws ShareKeyException */
    suspend operator fun invoke(planId: Long, credentials: ExchangeCredentials): KeyCheck {
        val plan = requireNotNull(database.dcaPlanDao().getPlanById(planId)) { "Plan $planId not found" }
        if (credentials.exchange != plan.exchange) throw ShareKeyException(ShareKeyProblem.WRONG_EXCHANGE)
        val isSandbox = userPreferences.processSandboxMode
        val tradingKey = credentialsStore.getCredentials(plan.connectionId, isSandbox)
        if (tradingKey != null && tradingKey.apiKey.trim() == credentials.apiKey.trim()) {
            throw ShareKeyException(ShareKeyProblem.SAME_AS_PLAN_KEY)
        }
        val client = readOnlyClients.create(credentials, isSandbox)
            ?: throw ShareKeyException(ShareKeyProblem.LIVE_UPDATES_UNSUPPORTED)
        val check = try {
            client.verifyKey()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw ShareKeyException(ShareKeyProblem.CHECK_FAILED)
        }
        if (check == KeyCheck.NOT_READ_ONLY) throw ShareKeyException(ShareKeyProblem.NOT_READ_ONLY)
        if (check == KeyCheck.UNVERIFIABLE) {
            // Still prove the key works before it is handed to someone else.
            val valid = try {
                client.validateCredentials()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!valid) throw ShareKeyException(ShareKeyProblem.CHECK_FAILED)
        }
        return check
    }
}

/** Creates a read-only snapshot file of one of the user's own plans. */
class CreatePlanShareUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val sharedPlanStore: SharedPlanStore,
    private val userPreferences: UserPreferences,
    private val backupCrypto: BackupCryptoUseCase
) {
    suspend operator fun invoke(planId: Long, includeAmounts: Boolean, live: LiveShareAccess? = null): PlanShareFile {
        val plan = requireNotNull(database.dcaPlanDao().getPlanById(planId)) { "Plan $planId not found" }
        require(live == null || live.credentials.exchange == plan.exchange)
        require(live == null || live.keyCheck != KeyCheck.NOT_READ_ONLY)
        val transactions = database.transactionDao().getTransactionsByPlanSync(planId)
        val isSandbox = userPreferences.processSandboxMode
        val snapshot = SharedPlanCodec.createSnapshot(
            plan = plan,
            transactions = transactions,
            shareId = sharedPlanStore.shareIdForOwnPlan(planId, isSandbox),
            isSandbox = isSandbox,
            // A shared key reveals amounts anyway, so live shares always include them.
            includeAmounts = includeAmounts || live != null
        )
        val access = live?.let {
            SharedPlanAccessDto(
                exchange = it.credentials.exchange.name,
                apiKey = it.credentials.apiKey.trim(),
                apiSecret = it.credentials.apiSecret.trim(),
                passphrase = it.credentials.passphrase?.trim()?.ifEmpty { null },
                clientId = it.credentials.clientId?.trim()?.ifEmpty { null },
                verifiedReadOnly = it.keyCheck == KeyCheck.READ_ONLY
            )
        }
        val content = SharedPlanCodec.encodeShare(snapshot, access, live?.password, backupCrypto::encrypt)
        val date = LocalDate.now(ZoneId.systemDefault())
        val fileName = "accbot_plan_${plan.crypto.lowercase()}_${plan.fiat.lowercase()}_$date.json"
        return PlanShareFile(fileName, content)
    }
}

/** Validates a received plan share and stores it as a read-only snapshot. */
class ImportSharedPlanUseCase @Inject constructor(
    private val sharedPlanStore: SharedPlanStore,
    private val backupCrypto: BackupCryptoUseCase,
    private val readOnlyClients: ReadOnlyExchangeClient.Factory
) {
    /**
     * @throws SharedPlanException when [json] is not a valid plan share, or
     * [SharedPlanError.PASSWORD_REQUIRED] for a live-update share without [password].
     * @throws ShareKeyException when the shared key turns out to allow trading or withdrawals.
     */
    suspend operator fun invoke(json: String, password: String? = null): SharedPlanStore.SaveResult {
        val decoded = SharedPlanCodec.decodeShare(json, password, backupCrypto::decrypt)
        val credentials = decoded.credentials
        var plan = decoded.plan
        if (credentials != null) {
            val client = readOnlyClients.create(credentials, decoded.plan.isSandbox)
                ?: throw ShareKeyException(ShareKeyProblem.LIVE_UPDATES_UNSUPPORTED)
            val check = try {
                client.verifyKey()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null // Offline: keep the key, the next refresh checks again.
            }
            if (check == KeyCheck.NOT_READ_ONLY) throw ShareKeyException(ShareKeyProblem.NOT_READ_ONLY)
            if (check == KeyCheck.READ_ONLY) plan = plan.copy(verifiedReadOnly = true)
        }
        return sharedPlanStore.save(plan, credentials)
    }

    /** Reads at most [SharedPlanCodec.MAX_FILE_BYTES] from [input]. */
    fun readBounded(input: java.io.InputStream): String = try {
        RestoreBackupUseCase.readBounded(input, SharedPlanCodec.MAX_FILE_BYTES).toString(Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        throw SharedPlanException(SharedPlanError.TOO_LARGE)
    }
}

/**
 * Fetches trades made since the last known trade of a live-update share, using only the
 * [ReadOnlyExchangeClient]. The exchange reports history per account and pair, so trades
 * of other plans or manual trades of the same pair on that account appear as well.
 */
class RefreshSharedPlanUseCase @Inject constructor(
    private val sharedPlanStore: SharedPlanStore,
    private val readOnlyClients: ReadOnlyExchangeClient.Factory
) {
    sealed class Result {
        data class Updated(val plan: SharedPlan, val newTrades: Int) : Result()
        /** The share has no key (plain snapshot). */
        object NotLive : Result()
        /** The key now allows trading or withdrawals; it was removed and updates stopped. */
        object KeyNotReadOnly : Result()
        /** The exchange rejected the key or could not be reached. */
        data class Failed(val plan: SharedPlan) : Result()
    }

    suspend operator fun invoke(shareId: String, now: Instant = Instant.now()): Result {
        val plan = sharedPlanStore.get(shareId) ?: return Result.NotLive
        val credentials = sharedPlanStore.credentials(plan.shareId) ?: return Result.NotLive
        val client = readOnlyClients.create(credentials, plan.isSandbox) ?: return Result.NotLive
        return try {
            when (client.verifyKey()) {
                KeyCheck.NOT_READ_ONLY -> {
                    sharedPlanStore.save(plan, credentials = null)
                    return Result.KeyNotReadOnly
                }
                else -> Unit
            }
            val lastKnown = plan.trades.lastOrNull()?.executedAt ?: plan.createdAt
            val fetched = fetchSince(client, plan, maxOf(lastKnown, plan.sharedAt))
            val merged = merge(plan, fetched)
            val updated = plan.copy(
                trades = merged,
                refreshedAt = now,
                refreshFailed = false
            )
            sharedPlanStore.update(updated)
            Result.Updated(updated, merged.size - plan.trades.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            val failed = plan.copy(refreshFailed = true)
            sharedPlanStore.update(failed)
            Result.Failed(failed)
        }
    }

    private suspend fun fetchSince(
        client: ReadOnlyExchangeClient,
        plan: SharedPlan,
        lastKnown: Instant
    ): List<HistoricalTrade> {
        // Overlap a little so a trade at the boundary is not missed; refs remove duplicates.
        var cursor: Instant? = lastKnown.minus(OVERLAP)
        val trades = mutableListOf<HistoricalTrade>()
        repeat(MAX_PAGES) {
            val page = client.tradeHistory(plan.crypto, plan.fiat, cursor, PAGE_SIZE)
            trades += page.trades
            if (!page.hasMore || page.trades.isEmpty()) return trades
            val next = page.trades.maxOf { it.timestamp }
            if (cursor != null && !next.isAfter(cursor)) return trades
            cursor = next
        }
        return trades
    }

    companion object {
        private val OVERLAP: Duration = Duration.ofHours(1)
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 50

        /** Trades this close after the last snapshot trade are treated as that trade. */
        private val SNAPSHOT_MARGIN: Duration = Duration.ofMinutes(5)

        /**
         * Appends fetched trades that are newer than the snapshot and not yet known.
         *
         * The snapshot identifies trades by their order ID, but some exchanges (Binance,
         * Coinbase) report history by trade ID, so the two cannot be matched by ref. Instead
         * everything up to the snapshot (its share time, or shortly after its last trade) is
         * left to the snapshot, and refs only dedupe trades added by earlier refreshes.
         * Fills reported under the same ID are grouped into one trade.
         */
        internal fun merge(plan: SharedPlan, fetched: List<HistoricalTrade>): List<SharedPlanTrade> {
            val exchangeName = plan.exchangeName
            val known = plan.trades.mapNotNull { it.ref }.toMutableSet()
            val lastSnapshotTrade = plan.trades
                .filter { !it.executedAt.isAfter(plan.sharedAt) }
                .maxOfOrNull { it.executedAt }
            val cutoff = maxOf(plan.sharedAt, lastSnapshotTrade?.plus(SNAPSHOT_MARGIN) ?: plan.sharedAt)
            val added = fetched
                .filter { it.crypto == plan.crypto && it.fiat == plan.fiat && it.orderId.isNotBlank() }
                .filter { it.side == "BUY" || it.side == "SELL" }
                .groupBy { it.orderId }
                .mapNotNull { (orderId, fills) ->
                    val ref = SharedPlanCodec.orderRef(exchangeName, orderId)
                    if (ref in known) return@mapNotNull null
                    val crypto = fills.sumOf { it.cryptoAmount }
                    val fiat = fills.sumOf { it.fiatAmount }
                    if (crypto.signum() <= 0 || fiat.signum() <= 0) return@mapNotNull null
                    val executedAt = fills.minOf { it.timestamp }
                    if (!executedAt.isAfter(cutoff)) return@mapNotNull null
                    known += ref
                    SharedPlanTrade(
                        executedAt = executedAt,
                        side = if (fills.first().side == "SELL") TransactionSide.SELL else TransactionSide.BUY,
                        price = fiat.divide(crypto, 8, RoundingMode.HALF_UP),
                        cryptoAmount = crypto,
                        fiatAmount = fiat,
                        fee = fills.sumOf { it.fee },
                        feeAsset = fills.first().feeAsset.takeIf { it.matches(Regex("^[A-Z0-9]{2,10}$")) },
                        ref = ref
                    )
                }
            return (plan.trades + added).sortedBy { it.executedAt }
        }
    }
}
