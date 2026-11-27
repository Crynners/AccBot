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
import com.accbot.dca.domain.util.QrLimits
import com.accbot.dca.exchange.ReadOnlyExchangeClient
import com.accbot.dca.exchange.ReadOnlyExchangeClient.KeyCheck
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * [qrContent] is a compact variant of a live share (no trades) for a QR code; null for plain
 * snapshots and when it would exceed [QrLimits.MAX_PAYLOAD_BYTES].
 */
data class PlanShareFile(val fileName: String, val content: String, val qrContent: String? = null)

/** Which exchange key a live-update share carries. */
sealed interface LiveShareKey {
    /**
     * A separate key the user created for sharing (the default). [keyCheck] is the result
     * of [VerifyShareKeyUseCase]; UNVERIFIABLE requires the user's explicit confirmation.
     */
    data class Separate(val credentials: ExchangeCredentials, val keyCheck: KeyCheck) : LiveShareKey

    /**
     * The key the plan itself trades with. The user accepted a warning that the recipient
     * gets the key with all its exchange permissions. [CreatePlanShareUseCase] loads the key
     * itself, so its secret never passes through the UI.
     */
    data object PlanKey : LiveShareKey
}

/** Exchange key and file password for a live-update share. */
data class LiveShareAccess(val key: LiveShareKey, val password: String)

/** Locale-free reasons a key cannot be shared; the UI maps them to strings. */
enum class ShareKeyProblem {
    LIVE_UPDATES_UNSUPPORTED, WRONG_EXCHANGE, SAME_AS_PLAN_KEY, NOT_READ_ONLY, CHECK_FAILED, PLAN_KEY_MISSING
}

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
    private val backupCrypto: BackupCryptoUseCase,
    private val credentialsStore: CredentialsStore,
    private val readOnlyClients: ReadOnlyExchangeClient.Factory
) {
    /** @throws ShareKeyException when [LiveShareKey.PlanKey] is requested but cannot be shared. */
    suspend operator fun invoke(planId: Long, includeAmounts: Boolean, live: LiveShareAccess? = null): PlanShareFile {
        val plan = requireNotNull(database.dcaPlanDao().getPlanById(planId)) { "Plan $planId not found" }
        val isSandbox = userPreferences.processSandboxMode
        val access = when (val key = live?.key) {
            null -> null
            is LiveShareKey.Separate -> {
                require(key.credentials.exchange == plan.exchange)
                require(key.keyCheck != KeyCheck.NOT_READ_ONLY)
                accessDto(key.credentials, verifiedReadOnly = key.keyCheck == KeyCheck.READ_ONLY, fullAccess = false)
            }
            LiveShareKey.PlanKey -> {
                val planKey = credentialsStore.getCredentials(plan.connectionId, isSandbox)
                    ?.takeIf { it.exchange == plan.exchange }
                    ?: throw ShareKeyException(ShareKeyProblem.PLAN_KEY_MISSING)
                readOnlyClients.create(planKey, isSandbox)
                    ?: throw ShareKeyException(ShareKeyProblem.LIVE_UPDATES_UNSUPPORTED)
                accessDto(planKey, verifiedReadOnly = false, fullAccess = true)
            }
        }
        val transactions = database.transactionDao().getTransactionsByPlanSync(planId)
        val snapshot = SharedPlanCodec.createSnapshot(
            plan = plan,
            transactions = transactions,
            shareId = sharedPlanStore.shareIdForOwnPlan(planId, isSandbox),
            isSandbox = isSandbox,
            // A shared key reveals amounts anyway, so live shares always include them.
            includeAmounts = includeAmounts || live != null
        )
        val content = SharedPlanCodec.encodeShare(snapshot, access, live?.password, backupCrypto::encrypt)
        // Same snapshot (shareId, sharedAt), so the QR and the file describe one share.
        val qrContent = if (live != null) {
            SharedPlanCodec.encodeShare(
                SharedPlanCodec.qrSnapshot(snapshot), access, live.password, backupCrypto::encrypt, compact = true
            ).takeIf { it.toByteArray(Charsets.UTF_8).size <= QrLimits.MAX_PAYLOAD_BYTES }
        } else null
        val date = LocalDate.now(ZoneId.systemDefault())
        val fileName = "accbot_plan_${plan.crypto.lowercase()}_${plan.fiat.lowercase()}_$date.json"
        return PlanShareFile(fileName, content, qrContent)
    }

    private fun accessDto(credentials: ExchangeCredentials, verifiedReadOnly: Boolean, fullAccess: Boolean) =
        SharedPlanAccessDto(
            exchange = credentials.exchange.name,
            apiKey = credentials.apiKey.trim(),
            apiSecret = credentials.apiSecret.trim(),
            passphrase = credentials.passphrase?.trim()?.ifEmpty { null },
            clientId = credentials.clientId?.trim()?.ifEmpty { null },
            verifiedReadOnly = verifiedReadOnly,
            fullAccess = fullAccess.takeIf { it }
        )
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
            // A key the sender knowingly shared with full access is expected to allow trading.
            if (check == KeyCheck.NOT_READ_ONLY && !plan.fullAccessKey) {
                throw ShareKeyException(ShareKeyProblem.NOT_READ_ONLY)
            }
            if (check == KeyCheck.READ_ONLY) plan = plan.copy(verifiedReadOnly = true, fullAccessKey = false)
        }
        // Serialized with refresh/download, so a concurrent refresh never writes back a stale copy.
        return sharedPlanStore.withPlanLock(plan.shareId) { sharedPlanStore.save(plan, credentials) }
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
        /** [newTrades]: trades appended, or for a replacing download the trades now in its range. */
        data class Updated(val plan: SharedPlan, val newTrades: Int) : Result()
        /** The share has no key (plain snapshot). */
        object NotLive : Result()
        /** The key now allows trading or withdrawals; it was removed and updates stopped. */
        object KeyNotReadOnly : Result()
        /**
         * The exchange rejected the key or could not be reached. [historyTooLong]: a replacing
         * download stopped because the exchange history exceeds the download limit.
         */
        data class Failed(val plan: SharedPlan, val historyTooLong: Boolean = false) : Result()
    }

    /** A plan with downloaded trades and the count reported in [Result.Updated.newTrades]. */
    internal data class Download(val plan: SharedPlan, val newTrades: Int)

    suspend operator fun invoke(shareId: String, now: Instant = Instant.now()): Result =
        withVerifiedKey(sharedPlanStore, readOnlyClients, shareId, now) { plan, client ->
            appendNew(client, plan, onProgress = {})
        }

    companion object {
        private val OVERLAP: Duration = Duration.ofHours(1)
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 50

        /** Trades this close after the last snapshot trade are treated as that trade. */
        private val SNAPSHOT_MARGIN: Duration = Duration.ofMinutes(5)

        /**
         * Loads the share's key, checks its permissions again and stores the plan [download]
         * returns, marked as refreshed at [now]. Shared by refresh and history download, so
         * both drop a key that started to allow trading and record exchange errors alike.
         */
        internal suspend fun withVerifiedKey(
            store: SharedPlanStore,
            clients: ReadOnlyExchangeClient.Factory,
            shareId: String,
            now: Instant,
            download: suspend (SharedPlan, ReadOnlyExchangeClient) -> Download
        ): Result = store.withPlanLock(shareId) {
            withVerifiedKeyLocked(store, clients, shareId, now, download)
        }

        private suspend fun withVerifiedKeyLocked(
            store: SharedPlanStore,
            clients: ReadOnlyExchangeClient.Factory,
            shareId: String,
            now: Instant,
            download: suspend (SharedPlan, ReadOnlyExchangeClient) -> Download
        ): Result {
            val plan = store.get(shareId) ?: return Result.NotLive
            val credentials = store.credentials(plan.shareId) ?: return Result.NotLive
            val client = clients.create(credentials, plan.isSandbox) ?: return Result.NotLive
            return try {
                when (client.verifyKey()) {
                    // Expected for a key shared with full access; it is still used only for reads.
                    KeyCheck.NOT_READ_ONLY -> if (!plan.fullAccessKey) {
                        store.dropKey(plan.shareId)
                        return Result.KeyNotReadOnly
                    }
                    else -> Unit
                }
                val result = download(plan, client)
                val updated = result.plan.copy(refreshedAt = now, refreshFailed = false)
                store.update(updated)
                Result.Updated(updated, result.newTrades)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val failed = plan.copy(refreshFailed = true)
                store.update(failed)
                Result.Failed(failed, historyTooLong = e is HistoryTruncatedException)
            }
        }

        /** Appends trades made after the plan's known ones (a refresh, or "since last purchase"). */
        internal suspend fun appendNew(
            client: ReadOnlyExchangeClient,
            plan: SharedPlan,
            onProgress: (ApiImportProgress) -> Unit
        ): Download {
            val lastTrade = plan.trades.lastOrNull()?.executedAt
            // Exchange-based trades carry exchange refs, so fetching from the last one cannot
            // duplicate it. A file snapshot leaves everything up to its share time to itself.
            val from = if (plan.tradesFromExchange) {
                lastTrade ?: plan.startedAt
            } else {
                maxOf(lastTrade ?: plan.createdAt, plan.sharedAt)
            }
            // Stopping at MAX_PAGES is fine here: the next refresh continues from the last trade.
            val merged = merge(plan, fetchSince(client, plan, from, MAX_PAGES, onProgress).trades)
            return Download(plan.copy(trades = merged), merged.size - plan.trades.size)
        }

        /** [truncated]: the exchange had more history after [maxPages] pages. */
        internal class Fetched(val trades: List<HistoricalTrade>, val truncated: Boolean)

        internal suspend fun fetchSince(
            client: ReadOnlyExchangeClient,
            plan: SharedPlan,
            since: Instant,
            maxPages: Int,
            onProgress: (ApiImportProgress) -> Unit
        ): Fetched {
            // Overlap a little so a trade at the boundary is not missed; refs remove duplicates.
            var cursor: Instant = since.minus(OVERLAP)
            val trades = mutableListOf<HistoricalTrade>()
            for (page in 1..maxPages) {
                val result = client.tradeHistory(plan.crypto, plan.fiat, cursor, PAGE_SIZE)
                trades += result.trades
                onProgress(ApiImportProgress.Fetching(page, trades.size))
                if (!result.hasMore || result.trades.isEmpty()) return Fetched(trades, truncated = false)
                val next = result.trades.maxOf { it.timestamp }
                // A full page that does not move forward cannot be paged further.
                if (!next.isAfter(cursor)) return Fetched(trades, truncated = result.hasMore)
                cursor = next
            }
            return Fetched(trades, truncated = true)
        }

        /**
         * Converts fetched fills into the plan's trades, oldest first. Fills reported under
         * the same ID are grouped into one trade; fills of another pair, of other sides,
         * without amounts or from before the plan started are dropped.
         */
        internal fun toSharedTrades(plan: SharedPlan, fetched: List<HistoricalTrade>): List<SharedPlanTrade> =
            fetched
                .filter { it.crypto == plan.crypto && it.fiat == plan.fiat && it.orderId.isNotBlank() }
                .filter { it.side == "BUY" || it.side == "SELL" }
                .groupBy { it.orderId }
                .mapNotNull { (orderId, fills) ->
                    val crypto = fills.sumOf { it.cryptoAmount }
                    val fiat = fills.sumOf { it.fiatAmount }
                    if (crypto.signum() <= 0 || fiat.signum() <= 0) return@mapNotNull null
                    val executedAt = fills.minOf { it.timestamp }
                    if (executedAt.isBefore(plan.startedAt)) return@mapNotNull null
                    SharedPlanTrade(
                        executedAt = executedAt,
                        side = if (fills.first().side == "SELL") TransactionSide.SELL else TransactionSide.BUY,
                        price = fiat.divide(crypto, 8, RoundingMode.HALF_UP),
                        cryptoAmount = crypto,
                        fiatAmount = fiat,
                        fee = fills.sumOf { it.fee },
                        feeAsset = fills.first().feeAsset.takeIf { it.matches(Regex("^[A-Z0-9]{2,10}$")) },
                        ref = SharedPlanCodec.orderRef(plan.exchangeName, orderId)
                    )
                }
                .sortedBy { it.executedAt }

        /**
         * Appends fetched trades that are not yet known.
         *
         * A file snapshot identifies trades by their order ID, but some exchanges (Binance,
         * Coinbase) report history by trade ID, so the two cannot be matched by ref. Instead
         * everything up to the snapshot (its share time, or shortly after its last trade) is
         * left to the snapshot, and refs only dedupe trades added by earlier refreshes. The
         * trades of an exchange-based plan carry exchange refs from [SharedPlan.exchangeHistoryFrom]
         * (or its start) on, so there refs alone dedupe and older fetched trades are dropped:
         * those may be snapshot trades named differently.
         */
        internal fun merge(plan: SharedPlan, fetched: List<HistoricalTrade>): List<SharedPlanTrade> {
            val known = plan.trades.mapNotNull { it.ref }.toSet()
            val unknown = toSharedTrades(plan, fetched).filter { it.ref !in known }
            val added = if (plan.tradesFromExchange) {
                val historyFrom = plan.exchangeHistoryFrom ?: plan.startedAt
                unknown.filter { !it.executedAt.isBefore(historyFrom) }
            } else {
                val lastSnapshotTrade = plan.trades
                    .filter { !it.executedAt.isAfter(plan.sharedAt) }
                    .maxOfOrNull { it.executedAt }
                val cutoff = maxOf(plan.sharedAt, lastSnapshotTrade?.plus(SNAPSHOT_MARGIN) ?: plan.sharedAt)
                unknown.filter { it.executedAt.isAfter(cutoff) }
            }
            return (plan.trades + added).sortedBy { it.executedAt }
        }

        /**
         * The plan's trades before [from] followed by the fetched ones from [from] on.
         * Replacing the range instead of merging avoids duplicates when the snapshot and the
         * exchange name the same trades differently (order ID vs trade ID).
         */
        internal fun replaceFrom(plan: SharedPlan, from: Instant, fetched: List<HistoricalTrade>): List<SharedPlanTrade> {
            val kept = plan.trades.filter { it.executedAt.isBefore(from) }
            val downloaded = toSharedTrades(plan, fetched).filter { !it.executedAt.isBefore(from) }
            return (kept + downloaded).sortedBy { it.executedAt }
        }
    }
}

/**
 * Downloads a shared plan's trade history from the exchange on request, using only the
 * [ReadOnlyExchangeClient]. Nothing older than the plan's start is kept from the exchange.
 */
class DownloadSharedPlanHistoryUseCase internal constructor(
    private val sharedPlanStore: SharedPlanStore,
    private val readOnlyClients: ReadOnlyExchangeClient.Factory,
    private val maxDownloadPages: Int
) {
    @Inject constructor(sharedPlanStore: SharedPlanStore, readOnlyClients: ReadOnlyExchangeClient.Factory) :
        this(sharedPlanStore, readOnlyClients, MAX_DOWNLOAD_PAGES)

    /**
     * [ImportRange.SinceLastPurchase] appends new trades like a refresh. [ImportRange.SinceDate]
     * (clamped to the plan start) and [ImportRange.AllHistory] (from the plan start) replace
     * the plan's trades in that range with the exchange's; the plan's history then counts as
     * coming from the exchange.
     */
    suspend operator fun invoke(
        shareId: String,
        range: ImportRange,
        onProgress: (ApiImportProgress) -> Unit = {},
        now: Instant = Instant.now()
    ): RefreshSharedPlanUseCase.Result =
        RefreshSharedPlanUseCase.withVerifiedKey(sharedPlanStore, readOnlyClients, shareId, now) { plan, client ->
            val from = when (range) {
                ImportRange.SinceLastPurchase ->
                    return@withVerifiedKey RefreshSharedPlanUseCase.appendNew(client, plan, onProgress)
                is ImportRange.SinceDate -> maxOf(Instant.ofEpochMilli(range.millis), plan.startedAt)
                ImportRange.AllHistory -> plan.startedAt
            }
            val fetched = RefreshSharedPlanUseCase.fetchSince(client, plan, from, maxDownloadPages, onProgress)
            // Replacing with a partial range would silently drop newer stored trades.
            if (fetched.truncated) throw HistoryTruncatedException()
            val trades = RefreshSharedPlanUseCase.replaceFrom(plan, from, fetched.trades)
            RefreshSharedPlanUseCase.Download(
                plan.copy(trades = trades, tradesFromExchange = true, exchangeHistoryFrom = from),
                newTrades = trades.count { !it.executedAt.isBefore(from) }
            )
        }

    companion object {
        /** A replacing download must see the whole range, so it may page much further than a refresh. */
        internal const val MAX_DOWNLOAD_PAGES = 1000
    }
}

/**
 * A replacing download could not see the whole range. Reported as
 * [RefreshSharedPlanUseCase.Result.Failed] with `historyTooLong`; the stored trades stay unchanged.
 */
internal class HistoryTruncatedException : Exception("Trade history exceeds the download limit")
