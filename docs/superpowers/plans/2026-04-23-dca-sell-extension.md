# DCA Sell Extension - implementacni plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Cil:** Rozsirit DCA plany o opt-in limitni prodejni prikazy, P&L tracking a volitelny cil zisku. Globalni Settings toggle + per-plan `allowSells` flag. MVP podpora Coinmate + Binance.

**Architektura:** Minimum invasive na existujici kod. 2 nova pole na `DcaPlan`, 3 pole na `TransactionEntity`, 3 metody v `ExchangeApi`. Sells jsou obycejne transakce s `side=SELL`. Existujici `ResolvePendingTransactionsUseCase` rozsirime na polling BUY i SELL side, polling triggery: app onResume + DcaWorker tick + po user akci + pull-to-refresh + opt-in periodic worker.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, WorkManager, AlarmManager, OkHttp

**Referencni spec:** `docs/superpowers/specs/2026-04-23-dca-sell-extension-design.md`

**Pozn. k testum:** Projekt nema unit test infrastructure (jen `androidTest` pro screenshoty/recording). Manualni verifikace po kazdem tasku: `./gradlew assembleDebug` ze `accbot-android/`. Funkcni testy: sandbox mode + realny run.

---

## Faze 1: Datovy model

### Task 1: Pridat TransactionSide enum a rozsirit TransactionEntity

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/Entities.kt`

- [ ] **Krok 1: Pridat TransactionSide enum**

V `Entities.kt` nad `TransactionEntity`:

```kotlin
enum class TransactionSide {
    BUY,
    SELL
}
```

- [ ] **Krok 2: Pridat 3 nova pole do TransactionEntity**

```kotlin
@Entity(
    tableName = "transactions",
    // ... existujici
)
data class TransactionEntity(
    // ... vsechna existujici pole beze zmeny ...
    val side: TransactionSide = TransactionSide.BUY,
    val limitPrice: BigDecimal? = null,
    val requestedCryptoAmount: BigDecimal? = null
)
```

- [ ] **Krok 3: Pridat TypeConverter pro TransactionSide**

V `Entities.kt` do `Converters` tridy:

```kotlin
@TypeConverter
fun fromTransactionSide(side: TransactionSide): String = side.name

@TypeConverter
fun toTransactionSide(value: String): TransactionSide =
    TransactionSide.valueOf(value)
```

- [ ] **Krok 4: Build check**

```bash
cd accbot-android
./gradlew assembleDebug
```

Expected: SUCCESS

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/data/local/Entities.kt
git commit -m "feat(sell): add TransactionSide enum and sell-specific fields to TransactionEntity"
```

---

### Task 2: Rozsirit DcaPlanEntity o allowSells + targetProfitAmount

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/Entities.kt`

- [ ] **Krok 1: Pridat pole do DcaPlanEntity**

```kotlin
@Entity(
    tableName = "dca_plans",
    // ... existujici
)
data class DcaPlanEntity(
    // ... vsechna existujici pole beze zmeny ...
    val allowSells: Boolean = false,
    val targetProfitAmount: BigDecimal? = null
)
```

- [ ] **Krok 2: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/data/local/Entities.kt
git commit -m "feat(sell): add allowSells + targetProfitAmount to DcaPlanEntity"
```

---

### Task 3: Napsat Room migraci 20 -> 21

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/DcaDatabase.kt`

- [ ] **Krok 1: Pridat MIGRATION_20_21**

Pod `MIGRATION_19_20` v companion objectu:

```kotlin
private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE dca_plans ADD COLUMN allowSells INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE dca_plans ADD COLUMN targetProfitAmount TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE transactions ADD COLUMN side TEXT NOT NULL DEFAULT 'BUY'")
        database.execSQL("ALTER TABLE transactions ADD COLUMN limitPrice TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE transactions ADD COLUMN requestedCryptoAmount TEXT DEFAULT NULL")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_plan_side_status ON transactions(planId, side, status)")
    }
}
```

- [ ] **Krok 2: Upravit @Database version**

Zmenit `version = 20` na `version = 21`:

```kotlin
@Database(
    entities = [...],
    version = 21,
    exportSchema = true
)
```

- [ ] **Krok 3: Zaregistrovat migraci v addMigrations(...)**

V `createDatabase` / builderu pridat na konec listu:

```kotlin
.addMigrations(
    MIGRATION_1_2, MIGRATION_2_3, /* ... */ MIGRATION_19_20, MIGRATION_20_21
)
```

- [ ] **Krok 4: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

Expected: SUCCESS. Pokud failne s "Schema export... mismatch", spustit `./gradlew :app:kspDebugKotlin` a zkontrolovat `schemas/com.accbot.dca.data.local.DcaDatabase/21.json`.

- [ ] **Krok 5: Manualni test migrace**

Nainstaluj pres `./gradlew installDebug` na emulator ktery ma starou DB (version 20). App musi nastartovat bez crashe. Logcat: `adb logcat | grep -i "room\|migration"` nesmi hlasit `IllegalStateException`.

- [ ] **Krok 6: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/data/local/DcaDatabase.kt
git commit -m "feat(sell): Room migration 20->21 for sell extension fields"
```

---

### Task 4: Rozsirit DcaPlan domain model + mapper

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/domain/model/Models.kt` (nebo kde je `DcaPlan` data class)
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/EntityMappers.kt`

- [ ] **Krok 1: Najit DcaPlan domain data class**

```bash
grep -rn 'data class DcaPlan[^E]' accbot-android/app/src/main/java/com/accbot/dca/
```

- [ ] **Krok 2: Pridat pole do DcaPlan domain modelu**

```kotlin
data class DcaPlan(
    // ... existujici pole beze zmeny ...
    val allowSells: Boolean = false,
    val targetProfitAmount: BigDecimal? = null
)
```

- [ ] **Krok 3: Upravit mapper Entity -> Domain**

V `EntityMappers.kt` najit `DcaPlanEntity.toDomain()` a pridat:

```kotlin
fun DcaPlanEntity.toDomain(): DcaPlan = DcaPlan(
    // ... existujici mapping ...
    allowSells = allowSells,
    targetProfitAmount = targetProfitAmount
)
```

- [ ] **Krok 4: Upravit mapper Domain -> Entity**

```kotlin
fun DcaPlan.toEntity(): DcaPlanEntity = DcaPlanEntity(
    // ... existujici mapping ...
    allowSells = allowSells,
    targetProfitAmount = targetProfitAmount
)
```

- [ ] **Krok 5: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 6: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): extend DcaPlan domain model + mappers"
```

---

### Task 5: Rozsirit Transaction domain model + mapper

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/domain/model/Transaction.kt` (nebo kde je `Transaction` data class)
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/EntityMappers.kt`

- [ ] **Krok 1: Najit Transaction domain data class**

```bash
grep -rn 'data class Transaction[^E]' accbot-android/app/src/main/java/com/accbot/dca/
```

- [ ] **Krok 2: Pridat pole do Transaction**

```kotlin
data class Transaction(
    // ... existujici pole ...
    val side: TransactionSide = TransactionSide.BUY,
    val limitPrice: BigDecimal? = null,
    val requestedCryptoAmount: BigDecimal? = null
)
```

- [ ] **Krok 3: Upravit mappery TransactionEntity <-> Transaction**

V `EntityMappers.kt`:

```kotlin
fun TransactionEntity.toDomain(): Transaction = Transaction(
    // ... existujici mapping ...
    side = side,
    limitPrice = limitPrice,
    requestedCryptoAmount = requestedCryptoAmount
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    // ... existujici mapping ...
    side = side,
    limitPrice = limitPrice,
    requestedCryptoAmount = requestedCryptoAmount
)
```

- [ ] **Krok 4: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): extend Transaction domain model + mappers"
```

---

### Task 6: Rozsirit TransactionDao o sell-aware queries

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/Daos.kt`

- [ ] **Krok 1: Pridat query pro resolvable pending + partial transakce**

```kotlin
@Query("""
    SELECT * FROM transactions
    WHERE status IN ('PENDING', 'PARTIAL')
      AND exchangeOrderId IS NOT NULL
""")
suspend fun getResolvablePendingTransactions(): List<TransactionEntity>
```

- [ ] **Krok 2: Pridat query pro pocitani open sells**

```kotlin
@Query("""
    SELECT COUNT(*) FROM transactions
    WHERE side = 'SELL'
      AND status IN ('PENDING', 'PARTIAL')
""")
suspend fun countOpenSells(): Int
```

- [ ] **Krok 3: Pridat query pro open sells konkretniho planu**

```kotlin
@Query("""
    SELECT * FROM transactions
    WHERE planId = :planId
      AND side = 'SELL'
      AND status IN ('PENDING', 'PARTIAL')
    ORDER BY executedAt DESC
""")
fun observeOpenSellsForPlan(planId: Long): Flow<List<TransactionEntity>>
```

- [ ] **Krok 4: Pridat query pro vsechny transakce planu (observe variant, pokud neexistuje)**

Zkontroluj jestli existuje `fun observeTransactionsForPlan(planId: Long): Flow<List<TransactionEntity>>`. Pokud neexistuje:

```kotlin
@Query("SELECT * FROM transactions WHERE planId = :planId ORDER BY executedAt DESC")
fun observeTransactionsForPlan(planId: Long): Flow<List<TransactionEntity>>
```

- [ ] **Krok 5: Concurrency-guarded update pro resolve**

```kotlin
@Query("""
    UPDATE transactions
    SET status = :newStatus,
        cryptoAmount = :cryptoAmount,
        fiatAmount = :fiatAmount,
        price = :price,
        fee = :fee
    WHERE id = :id
      AND status IN ('PENDING', 'PARTIAL')
""")
suspend fun updateResolvedTransaction(
    id: Long,
    newStatus: TransactionStatus,
    cryptoAmount: BigDecimal,
    fiatAmount: BigDecimal,
    price: BigDecimal,
    fee: BigDecimal?
): Int  // returns affected row count
```

- [ ] **Krok 6: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/data/local/Daos.kt
git commit -m "feat(sell): add sell-aware DAO queries (open sells, resolvable, guarded update)"
```

---

### Task 7: Rozsirit BackupPlan + BackupTransaction modely

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/domain/model/BackupModels.kt`
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/BackupDataCollector.kt`
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/BackupDataRestorer.kt`

- [ ] **Krok 1: Rozsirit BackupPlan**

V `BackupModels.kt`:

```kotlin
data class BackupPlan(
    // ... existujici pole ...
    @SerializedName("allowSells") val allowSells: Boolean = false,
    @SerializedName("targetProfitAmount") val targetProfitAmount: String? = null  // BigDecimal jako String
)
```

- [ ] **Krok 2: Rozsirit BackupTransaction**

```kotlin
data class BackupTransaction(
    // ... existujici pole ...
    @SerializedName("side") val side: String = "BUY",  // TransactionSide.name
    @SerializedName("limitPrice") val limitPrice: String? = null,
    @SerializedName("requestedCryptoAmount") val requestedCryptoAmount: String? = null
)
```

- [ ] **Krok 3: Upravit BackupDataCollector - ukladat nova pole**

Najit kde se vytvareji `BackupPlan` a `BackupTransaction` v `BackupDataCollector.kt`, pridat:

```kotlin
BackupPlan(
    // ... existujici ...
    allowSells = plan.allowSells,
    targetProfitAmount = plan.targetProfitAmount?.toPlainString()
)

BackupTransaction(
    // ... existujici ...
    side = tx.side.name,
    limitPrice = tx.limitPrice?.toPlainString(),
    requestedCryptoAmount = tx.requestedCryptoAmount?.toPlainString()
)
```

- [ ] **Krok 4: Upravit BackupDataRestorer - nacitat nova pole**

V `BackupDataRestorer.kt` pri konstrukci entit z backup modelu:

```kotlin
DcaPlanEntity(
    // ... existujici ...
    allowSells = backupPlan.allowSells,
    targetProfitAmount = backupPlan.targetProfitAmount?.let { BigDecimal(it) }
)

TransactionEntity(
    // ... existujici ...
    side = TransactionSide.valueOf(backupTx.side),
    limitPrice = backupTx.limitPrice?.let { BigDecimal(it) },
    requestedCryptoAmount = backupTx.requestedCryptoAmount?.let { BigDecimal(it) }
)
```

- [ ] **Krok 5: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 6: Verifikace ProGuard keep rules**

Ve `proguard-rules.pro` (nebo kde jsou ProGuard rules) overit ze `com.accbot.dca.domain.model.**` je v keep rules. Nova pole maji `@SerializedName` anotaci - release build musi fungovat. Pokud package neni v keep, pridat:

```proguard
-keep class com.accbot.dca.domain.model.** { *; }
```

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): extend backup/restore for sell fields"
```

---

## Faze 2: Exchange API

### Task 8: Definovat OrderStatusResult a refactor ExchangeApi

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/exchange/ExchangeApi.kt`

- [ ] **Krok 1: Pridat OrderStatusResult data class**

Do `ExchangeApi.kt` nebo noveho souboru `OrderStatusResult.kt`:

```kotlin
package com.accbot.dca.exchange

import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal

data class OrderStatusResult(
    val status: TransactionStatus,        // PENDING/PARTIAL/COMPLETED/FAILED
    val filledCryptoAmount: BigDecimal,
    val filledFiatAmount: BigDecimal,
    val avgFillPrice: BigDecimal?,
    val fee: BigDecimal?,
    val feeAsset: String?
)
```

- [ ] **Krok 2: Refactor getOrderStatus signature**

V `ExchangeApi.kt` interface (definitivní signatura, pouzita ve vsech implementacich):

```kotlin
suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = null
```

(Drive vracelo `Transaction?` a bralo jen `orderId` - breaking change.)

- [ ] **Krok 3: Pridat limitSell metodu**

```kotlin
/**
 * Place a limit sell order. Order zustava otevreny na burze.
 * @return DcaResult.Success s exchangeOrderId a status=PENDING.
 *         Failure pokud burza odmitne (insufficient balance, invalid price).
 */
suspend fun limitSell(
    crypto: String,
    fiat: String,
    cryptoAmount: BigDecimal,
    limitPrice: BigDecimal
): DcaResult = throw UnsupportedOperationException(
    "AccBot zatim nepodporuje limit sell pro ${exchange.displayName}"
)
```

- [ ] **Krok 4: Pridat cancelOrder metodu**

**Signature poznamka:** Binance vyzaduje `symbol=${crypto}${fiat}` navic k `orderId`. Aby byla signature konzistentni pro vsechny burzy, pridavame `crypto + fiat` parametry (Coinmate/Coinbase/Kraken je ignoruji).

```kotlin
/**
 * Cancel an open order on the exchange.
 * @param crypto, fiat - nektere burzy (Binance) vyzaduji symbol ke cancelu
 * @return Result.success pokud order byl zrusen (nebo uz byl filled/canceled).
 *         Result.failure pokud burza odmitla / nedostupna.
 */
suspend fun cancelOrder(orderId: String, crypto: String, fiat: String): Result<Unit> =
    Result.failure(UnsupportedOperationException(
        "AccBot zatim nepodporuje cancel order pro ${exchange.displayName}"
    ))
```

Zaroven upravit getOrderStatus signaturu:

```kotlin
suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = null
```

- [ ] **Krok 5: Pridat supportsLimitSell capability flag**

```kotlin
val supportsLimitSell: Boolean get() = false
```

- [ ] **Krok 6: Build check - bude failovat kvuli breaking change**

```bash
cd accbot-android && ./gradlew assembleDebug
```

Expected: FAIL na callers `getOrderStatus` (CoinbaseApi, KrakenApi, ResolvePendingTransactionsUseCase). Fix prichazi v nasledujicich tascich.

- [ ] **Krok 7: Commit (broken build, fix v dalsich tascich)**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/exchange/
git commit -m "feat(sell): add limitSell/cancelOrder/supportsLimitSell + refactor getOrderStatus to OrderStatusResult"
```

---

### Task 9: Refactor existujicich getOrderStatus implementaci (Coinbase + Kraken)

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/exchange/CoinbaseApi.kt`
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/exchange/OtherExchanges.kt` (KrakenApi)

- [ ] **Krok 1: Precti aktualni CoinbaseApi.getOrderStatus**

```bash
grep -n -A 30 'getOrderStatus' accbot-android/app/src/main/java/com/accbot/dca/exchange/CoinbaseApi.kt
```

- [ ] **Krok 2: Refactor CoinbaseApi.getOrderStatus na OrderStatusResult**

Stavajici kod vraci `Transaction?` - prepsat aby vracelo `OrderStatusResult?`. Nova signature: `(orderId, crypto, fiat)` (crypto+fiat se pro Coinbase ignoruji, ale interface je sjednoceny).

```kotlin
override suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = withContext(Dispatchers.IO) {
    // ... existujici API call ...
    // Misto vraceni Transaction(...) vrat OrderStatusResult(...)

    val status = when (coinbaseStatusString) {
        "OPEN" -> TransactionStatus.PENDING
        "PARTIALLY_FILLED" -> TransactionStatus.PARTIAL
        "FILLED" -> TransactionStatus.COMPLETED
        "CANCELLED", "EXPIRED" -> if (filledSize > BigDecimal.ZERO)
            TransactionStatus.PARTIAL else TransactionStatus.FAILED
        else -> return@withContext null
    }

    OrderStatusResult(
        status = status,
        filledCryptoAmount = filledSize,
        filledFiatAmount = filledValue,
        avgFillPrice = if (filledSize > BigDecimal.ZERO)
            filledValue.divide(filledSize, 8, RoundingMode.HALF_UP) else null,
        fee = fee,
        feeAsset = feeAsset
    )
}
```

(Detail mapovani Coinbase statusu si dohledat v dokumentaci / existujicim kodu.)

- [ ] **Krok 3: Refactor KrakenApi.getOrderStatus v OtherExchanges.kt**

Obdobne - stejna nova signature `(orderId, crypto, fiat)` (crypto+fiat pro Kraken ignoruji). `status` Kraken pouziva `open/closed/canceled/expired`:

```kotlin
override suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = withContext(Dispatchers.IO) {
    // ... API call ...
    val status = when (krakenStatus) {
    "open" -> if (vol_exec > BigDecimal.ZERO) TransactionStatus.PARTIAL else TransactionStatus.PENDING
    "closed" -> TransactionStatus.COMPLETED
    "canceled", "expired" -> if (vol_exec > BigDecimal.ZERO)
        TransactionStatus.PARTIAL else TransactionStatus.FAILED
    else -> return@withContext null
}

OrderStatusResult(
    status = status,
    filledCryptoAmount = vol_exec,
    filledFiatAmount = cost,
    avgFillPrice = if (vol_exec > BigDecimal.ZERO)
        cost.divide(vol_exec, 8, RoundingMode.HALF_UP) else null,
    fee = fee,
    feeAsset = null
)
```

- [ ] **Krok 4: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

Expected: stale failne na `ResolvePendingTransactionsUseCase` (fix v Task 12).

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/exchange/
git commit -m "refactor(sell): adapt CoinbaseApi + KrakenApi to OrderStatusResult"
```

---

### Task 10: Implementovat CoinmateApi.limitSell + cancelOrder + getOrderStatus

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/exchange/CoinmateApi.kt`

- [ ] **Krok 1: Override supportsLimitSell**

V `CoinmateApi` classi:

```kotlin
override val supportsLimitSell: Boolean = true
```

- [ ] **Krok 2: Implementovat limitSell**

Coinmate API endpoint: `POST /api/sellLimit` s body: `amount, price, currencyPair, clientOrderId (optional)`.

```kotlin
override suspend fun limitSell(
    crypto: String,
    fiat: String,
    cryptoAmount: BigDecimal,
    limitPrice: BigDecimal
): DcaResult = withContext(Dispatchers.IO) {
    val currencyPair = "${crypto}_${fiat}"
    val params = buildSignedParams(
        "amount" to cryptoAmount.stripTrailingZeros().toPlainString(),
        "price" to limitPrice.stripTrailingZeros().toPlainString(),
        "currencyPair" to currencyPair
    )
    val response = executePostRequest("/api/sellLimit", params)

    if (response.error) {
        return@withContext DcaResult.Failure(
            exchange = Exchange.COINMATE,
            reason = mapCoinmateErrorToReason(response.errorMessage),
            message = response.errorMessage ?: "Coinmate sell limit failed"
        )
    }

    val orderId = response.data?.toString() ?: return@withContext DcaResult.Failure(...)

    DcaResult.Success(
        transaction = Transaction(
            exchange = Exchange.COINMATE,
            crypto = crypto,
            fiat = fiat,
            cryptoAmount = BigDecimal.ZERO,            // not filled yet
            fiatAmount = BigDecimal.ZERO,              // not filled yet
            price = limitPrice,
            fee = null,
            feeAsset = null,
            status = TransactionStatus.PENDING,
            exchangeOrderId = orderId,
            side = TransactionSide.SELL,
            limitPrice = limitPrice,
            requestedCryptoAmount = cryptoAmount,
            executedAt = Instant.now()
        )
    )
}
```

**Pozn.:** `buildSignedParams` / `executePostRequest` jsou existujici helpery v `CoinmateApi` - reuse.

- [ ] **Krok 3: Implementovat cancelOrder**

Coinmate: `POST /api/cancelOrder` s `orderId`. Parametry `crypto + fiat` se ignoruji (jsou tam kvuli interface konzistenci s Binance).

```kotlin
override suspend fun cancelOrder(orderId: String, crypto: String, fiat: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val params = buildSignedParams("orderId" to orderId)
        val response = executePostRequest("/api/cancelOrder", params)
        if (response.error) {
            return@withContext Result.failure(
                IOException("Coinmate cancel failed: ${response.errorMessage}")
            )
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

- [ ] **Krok 4: Implementovat getOrderStatus**

Coinmate: `POST /api/orderById` s `orderId`, vraci objekt s `status, remainingAmount, originalAmount, orderType, price, avgPrice, ...`. Parametry `crypto + fiat` se ignoruji.

```kotlin
override suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = withContext(Dispatchers.IO) {
    try {
        val params = buildSignedParams("orderId" to orderId)
        val response = executePostRequest("/api/orderById", params)
        if (response.error) return@withContext null

        val order = response.data as? JSONObject ?: return@withContext null
        val originalAmount = BigDecimal(order.getString("originalAmount"))
        val remainingAmount = BigDecimal(order.getString("remainingAmount"))
        val filledAmount = originalAmount - remainingAmount
        val avgPrice = order.optString("avgPrice").takeIf { it.isNotBlank() }?.let { BigDecimal(it) }
        val filledFiat = avgPrice?.let { filledAmount * it } ?: BigDecimal.ZERO

        val coinmateStatus = order.getString("status")
        val txStatus = when (coinmateStatus) {
            "OPEN" -> if (filledAmount > BigDecimal.ZERO) TransactionStatus.PARTIAL else TransactionStatus.PENDING
            "FILLED" -> TransactionStatus.COMPLETED
            "PARTIALLY_FILLED" -> TransactionStatus.PARTIAL
            "CANCELLED", "EXPIRED" -> if (filledAmount > BigDecimal.ZERO)
                TransactionStatus.PARTIAL else TransactionStatus.FAILED
            else -> return@withContext null
        }

        OrderStatusResult(
            status = txStatus,
            filledCryptoAmount = filledAmount,
            filledFiatAmount = filledFiat,
            avgFillPrice = avgPrice,
            fee = null,  // Coinmate order endpoint fee TBD - vracet null pokud endpoint nevraci
            feeAsset = null
        )
    } catch (e: Exception) {
        Log.w("CoinmateApi", "getOrderStatus failed for $orderId", e)
        null
    }
}
```

- [ ] **Krok 5: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 6: Manualni sandbox test**

Spustit app v sandbox mode (UserPreferences sandbox=true). Pres DEBUG UI (nebo adb shell) zavolat `limitSell` s malou castkou, pak `getOrderStatus`, pak `cancelOrder`. Verifikovat ze orderID prichazi, status se meni, cancel funguje. Logy pres `adb logcat | grep CoinmateApi`.

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/exchange/CoinmateApi.kt
git commit -m "feat(sell): implement CoinmateApi limitSell + cancelOrder + getOrderStatus"
```

---

### Task 11: Implementovat BinanceApi.limitSell + cancelOrder + getOrderStatus

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/exchange/BinanceApi.kt`

- [ ] **Krok 1: Override supportsLimitSell**

```kotlin
override val supportsLimitSell: Boolean = true
```

- [ ] **Krok 2: Implementovat limitSell**

Binance: `POST /api/v3/order?symbol=BTCEUR&side=SELL&type=LIMIT&timeInForce=GTC&quantity=0.01&price=1200000`.

```kotlin
override suspend fun limitSell(
    crypto: String,
    fiat: String,
    cryptoAmount: BigDecimal,
    limitPrice: BigDecimal
): DcaResult = withContext(Dispatchers.IO) {
    val symbol = "${crypto}${fiat}"
    val params = mapOf(
        "symbol" to symbol,
        "side" to "SELL",
        "type" to "LIMIT",
        "timeInForce" to "GTC",
        "quantity" to cryptoAmount.stripTrailingZeros().toPlainString(),
        "price" to limitPrice.stripTrailingZeros().toPlainString()
    )

    try {
        val response = executeSignedRequest("POST", "/api/v3/order", params)
        val orderId = response.getLong("orderId").toString()

        DcaResult.Success(
            transaction = Transaction(
                exchange = Exchange.BINANCE,
                crypto = crypto,
                fiat = fiat,
                cryptoAmount = BigDecimal.ZERO,
                fiatAmount = BigDecimal.ZERO,
                price = limitPrice,
                fee = null,
                feeAsset = null,
                status = TransactionStatus.PENDING,
                exchangeOrderId = orderId,
                side = TransactionSide.SELL,
                limitPrice = limitPrice,
                requestedCryptoAmount = cryptoAmount,
                executedAt = Instant.now()
            )
        )
    } catch (e: BinanceApiException) {
        DcaResult.Failure(
            exchange = Exchange.BINANCE,
            reason = mapBinanceErrorToReason(e.code),
            message = e.message ?: "Binance limit sell failed"
        )
    }
}
```

- [ ] **Krok 3: Implementovat cancelOrder**

Binance: `DELETE /api/v3/order?symbol=BTCEUR&orderId=XXX`. Signature uz je `(orderId, crypto, fiat)` definovane v Task 8.

```kotlin
override suspend fun cancelOrder(orderId: String, crypto: String, fiat: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val symbol = "${crypto}${fiat}"
        val params = mapOf("symbol" to symbol, "orderId" to orderId)
        executeSignedRequest("DELETE", "/api/v3/order", params)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

- [ ] **Krok 4: Implementovat getOrderStatus**

Binance: `GET /api/v3/order?symbol=BTCEUR&orderId=XXX`.

```kotlin
override suspend fun getOrderStatus(orderId: String, crypto: String, fiat: String): OrderStatusResult? = withContext(Dispatchers.IO) {
    try {
        val symbol = "${crypto}${fiat}"
        val params = mapOf("symbol" to symbol, "orderId" to orderId)
        val response = executeSignedRequest("GET", "/api/v3/order", params)

        val binanceStatus = response.getString("status")
        val executedQty = BigDecimal(response.getString("executedQty"))
        val cumQuoteQty = BigDecimal(response.getString("cummulativeQuoteQty"))
        val avgPrice = if (executedQty > BigDecimal.ZERO)
            cumQuoteQty.divide(executedQty, 8, RoundingMode.HALF_UP) else null

        val txStatus = when (binanceStatus) {
            "NEW" -> TransactionStatus.PENDING
            "PARTIALLY_FILLED" -> TransactionStatus.PARTIAL
            "FILLED" -> TransactionStatus.COMPLETED
            "CANCELED", "EXPIRED", "REJECTED" -> if (executedQty > BigDecimal.ZERO)
                TransactionStatus.PARTIAL else TransactionStatus.FAILED
            else -> return@withContext null
        }

        OrderStatusResult(
            status = txStatus,
            filledCryptoAmount = executedQty,
            filledFiatAmount = cumQuoteQty,
            avgFillPrice = avgPrice,
            fee = null,  // fee je per-fill, zjisti se z /myTrades endpoint - MVP: skip
            feeAsset = null
        )
    } catch (e: Exception) {
        Log.w("BinanceApi", "getOrderStatus failed for $orderId", e)
        null
    }
}
```

- [ ] **Krok 5: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 6: Manualni Binance testnet test**

Prepnout app na sandbox mode (uses Binance testnet). Zalozit plan na BTC/EUR, pak pres DEBUG UI vyvolat limitSell s malou castkou. Verifikovat orderID, status check, cancel. Logy `adb logcat | grep BinanceApi`.

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/exchange/
git commit -m "feat(sell): implement BinanceApi limitSell + cancel + getOrderStatus"
```

---

## Faze 3: Use cases a business logic

### Task 12: Rozsirit ResolvePendingTransactionsUseCase

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ResolvePendingTransactionsUseCase.kt`

- [ ] **Krok 1: Zmenit query z getPendingTransactionsWithOrderId na getResolvablePendingTransactions**

```kotlin
val pendingTransactions = database.transactionDao().getResolvablePendingTransactions()
```

- [ ] **Krok 2: Zmenit volani getOrderStatus na novy signaturu**

```kotlin
val filledOrder = api.getOrderStatus(orderId, tx.crypto, tx.fiat) ?: continue
```

- [ ] **Krok 3: Zmenit update logiku - pouzit guarded update**

```kotlin
val rowsUpdated = database.transactionDao().updateResolvedTransaction(
    id = tx.id,
    newStatus = filledOrder.status,
    cryptoAmount = filledOrder.filledCryptoAmount,
    fiatAmount = filledOrder.filledFiatAmount,
    price = filledOrder.avgFillPrice ?: tx.price,
    fee = filledOrder.fee
)
if (rowsUpdated > 0) resolvedCount++
```

- [ ] **Krok 4: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ResolvePendingTransactionsUseCase.kt
git commit -m "feat(sell): extend ResolvePendingTransactionsUseCase for SELL-side + PARTIAL resolution"
```

---

### Task 13: PlaceLimitSellUseCase

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/PlaceLimitSellUseCase.kt`

- [ ] **Krok 1: Vytvorit use case**

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.EntityMappers.toEntity
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaResult
import com.accbot.dca.exchange.ExchangeApiFactory
import java.math.BigDecimal
import javax.inject.Inject

class PlaceLimitSellUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) {
    suspend operator fun invoke(
        planId: Long,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal
    ): Result<Long> {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: return Result.failure(IllegalArgumentException("Plan $planId neexistuje"))

        val credentials = plan.connectionId?.let {
            credentialsStore.getCredentials(it, userPreferences.isSandboxMode())
        } ?: return Result.failure(IllegalStateException("Chybi credentials pro plan"))

        val api = exchangeApiFactory.create(credentials)
        val result = api.limitSell(plan.crypto, plan.fiat, cryptoAmount, limitPrice)

        return when (result) {
            is DcaResult.Success -> {
                val tx = result.transaction.copy(
                    planId = planId,
                    connectionId = plan.connectionId
                )
                val txId = database.transactionDao().insertTransaction(tx.toEntity())
                resolvePendingTransactionsUseCase()  // okamzity poll (edge: instant fill)
                Result.success(txId)
            }
            is DcaResult.Failure -> Result.failure(
                IllegalStateException("${result.reason}: ${result.message}")
            )
        }
    }
}
```

- [ ] **Krok 2: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/PlaceLimitSellUseCase.kt
git commit -m "feat(sell): add PlaceLimitSellUseCase"
```

---

### Task 14: CancelSellOrderUseCase

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/CancelSellOrderUseCase.kt`

- [ ] **Krok 1: Vytvorit use case**

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.TransactionStatus
import com.accbot.dca.exchange.ExchangeApiFactory
import java.math.BigDecimal
import javax.inject.Inject

class CancelSellOrderUseCase @Inject constructor(
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) {
    suspend operator fun invoke(txId: Long): Result<Unit> {
        val tx = database.transactionDao().getTransactionById(txId)
            ?: return Result.failure(IllegalArgumentException("Transakce $txId neexistuje"))

        val orderId = tx.exchangeOrderId
            ?: return Result.failure(IllegalStateException("Transakce nema exchangeOrderId"))

        val credentials = tx.connectionId?.let {
            credentialsStore.getCredentials(it, userPreferences.isSandboxMode())
        } ?: return Result.failure(IllegalStateException("Chybi credentials"))

        val api = exchangeApiFactory.create(credentials)
        val cancelResult = api.cancelOrder(orderId, tx.crypto, tx.fiat)

        return if (cancelResult.isSuccess) {
            val newStatus = if (tx.cryptoAmount > BigDecimal.ZERO)
                TransactionStatus.PARTIAL else TransactionStatus.FAILED
            database.transactionDao().updateResolvedTransaction(
                id = txId,
                newStatus = newStatus,
                cryptoAmount = tx.cryptoAmount,
                fiatAmount = tx.fiatAmount,
                price = tx.price,
                fee = tx.fee
            )
            Result.success(Unit)
        } else {
            resolvePendingTransactionsUseCase()  // mozna se mezitim zfilovalo
            cancelResult
        }
    }
}
```

- [ ] **Krok 2: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/CancelSellOrderUseCase.kt
git commit -m "feat(sell): add CancelSellOrderUseCase"
```

---

### Task 15: CalculatePlanPnLUseCase

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/domain/model/PlanPnL.kt`
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/CalculatePlanPnLUseCase.kt`

- [ ] **Krok 1: Vytvorit PlanPnL data class**

`PlanPnL.kt`:

```kotlin
package com.accbot.dca.domain.model

import java.math.BigDecimal

data class PlanPnL(
    val totalBoughtFiat: BigDecimal,
    val totalBoughtCrypto: BigDecimal,
    val totalSoldFiat: BigDecimal,
    val totalSoldCrypto: BigDecimal,
    val currentCryptoHeld: BigDecimal,
    val avgBuyPrice: BigDecimal?,
    val currentValueFiat: BigDecimal?,
    val realizedPnL: BigDecimal?,
    val unrealizedPnL: BigDecimal?,
    val netPnL: BigDecimal?,
    val targetProgressPct: Double?
)
```

- [ ] **Krok 2: Vytvorit CalculatePlanPnLUseCase**

`CalculatePlanPnLUseCase.kt`:

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.TransactionSide
import com.accbot.dca.domain.model.PlanPnL
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class CalculatePlanPnLUseCase @Inject constructor(
    private val database: DcaDatabase
) {
    suspend operator fun invoke(
        planId: Long,
        currentMarketPrice: BigDecimal?
    ): PlanPnL {
        val plan = database.dcaPlanDao().getPlanById(planId)
            ?: error("Plan $planId neexistuje")

        val transactions = database.transactionDao()
            .getTransactionsByPlanId(planId)
            .filter { it.status == TransactionStatus.COMPLETED || it.status == TransactionStatus.PARTIAL }

        val buys = transactions.filter { it.side == TransactionSide.BUY }
        val sells = transactions.filter { it.side == TransactionSide.SELL }

        val totalBoughtFiat = buys.sumOf { it.fiatAmount }
        val totalBoughtCrypto = buys.sumOf { it.cryptoAmount }
        val totalSoldFiat = sells.sumOf { it.fiatAmount }
        val totalSoldCrypto = sells.sumOf { it.cryptoAmount }
        val currentCryptoHeld = totalBoughtCrypto - totalSoldCrypto

        val avgBuyPrice = if (totalBoughtCrypto > BigDecimal.ZERO)
            totalBoughtFiat.divide(totalBoughtCrypto, 8, RoundingMode.HALF_UP)
        else null

        val currentValueFiat = currentMarketPrice?.let { currentCryptoHeld * it }

        val realizedPnL = avgBuyPrice?.let {
            totalSoldFiat - (totalSoldCrypto * it)
        }

        val unrealizedPnL = if (avgBuyPrice != null && currentValueFiat != null)
            currentValueFiat - (currentCryptoHeld * avgBuyPrice)
        else null

        val netPnL = if (realizedPnL != null && unrealizedPnL != null)
            realizedPnL + unrealizedPnL
        else null

        val targetProgressPct = if (netPnL != null && plan.targetProfitAmount != null && plan.targetProfitAmount > BigDecimal.ZERO)
            netPnL.toDouble() / plan.targetProfitAmount.toDouble()
        else null

        return PlanPnL(
            totalBoughtFiat, totalBoughtCrypto,
            totalSoldFiat, totalSoldCrypto,
            currentCryptoHeld, avgBuyPrice, currentValueFiat,
            realizedPnL, unrealizedPnL, netPnL, targetProgressPct
        )
    }
}
```

- [ ] **Krok 3: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): add PlanPnL model + CalculatePlanPnLUseCase"
```

---

### Task 16: ValidateSellOrderUseCase (business validace wizardu)

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCase.kt`

- [ ] **Krok 1: Vytvorit use case**

```kotlin
package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.data.local.TransactionSide
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import javax.inject.Inject

sealed class SellValidation {
    object Ok : SellValidation()
    data class HardError(val message: String) : SellValidation()
    data class InstantFillInfo(val spot: BigDecimal) : SellValidation()
    data class FarFromMarketWarning(val spot: BigDecimal) : SellValidation()
}

class ValidateSellOrderUseCase @Inject constructor(
    private val database: DcaDatabase
) {
    suspend operator fun invoke(
        planId: Long,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal,
        minOrderSize: BigDecimal,
        currentSpot: BigDecimal?
    ): List<SellValidation> {
        val result = mutableListOf<SellValidation>()

        if (cryptoAmount <= BigDecimal.ZERO) {
            result += SellValidation.HardError("Mnozstvi musi byt vetsi nez 0")
            return result
        }

        if (limitPrice <= BigDecimal.ZERO) {
            result += SellValidation.HardError("Limitni cena musi byt vetsi nez 0")
            return result
        }

        if (cryptoAmount < minOrderSize) {
            result += SellValidation.HardError("Minimalni order je $minOrderSize")
        }

        val tx = database.transactionDao().getTransactionsByPlanId(planId)
        val completedOrPartial = tx.filter { it.status == TransactionStatus.COMPLETED || it.status == TransactionStatus.PARTIAL }
        val heldBought = completedOrPartial.filter { it.side == TransactionSide.BUY }.sumOf { it.cryptoAmount }
        val heldSold = completedOrPartial.filter { it.side == TransactionSide.SELL }.sumOf { it.cryptoAmount }
        val held = heldBought - heldSold

        val openSellsRequested = tx
            .filter { it.side == TransactionSide.SELL && it.status in setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL) }
            .sumOf { (it.requestedCryptoAmount ?: BigDecimal.ZERO) - it.cryptoAmount }

        val available = held - openSellsRequested
        if (cryptoAmount > available) {
            result += SellValidation.HardError("Nemas tolik BTC k dispozici (k dispozici $available)")
        }

        if (currentSpot != null) {
            if (limitPrice <= currentSpot) {
                result += SellValidation.InstantFillInfo(currentSpot)
            }
            if (limitPrice > currentSpot.multiply(BigDecimal("3"))) {
                result += SellValidation.FarFromMarketWarning(currentSpot)
            }
        }

        if (result.isEmpty()) result += SellValidation.Ok
        return result
    }
}
```

- [ ] **Krok 2: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/ValidateSellOrderUseCase.kt
git commit -m "feat(sell): add ValidateSellOrderUseCase with instant-fill + far-from-market checks"
```

---

## Faze 4: UserPreferences + periodic polling

### Task 17: Rozsirit UserPreferences o trading flagy

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/data/local/UserPreferences.kt`

- [ ] **Krok 1: Pridat klice a gettery/settery**

```kotlin
// Klice (companion object nebo top-level const):
private const val KEY_TRADING_ENABLED = "trading_enabled"
private const val KEY_SELL_POLLING_ENABLED = "sell_polling_enabled"
private const val KEY_SELL_POLLING_FREQUENCY = "sell_polling_frequency"
private const val KEY_SELL_POLLING_CRON = "sell_polling_cron"
private const val KEY_SELL_POLLING_SCHEDULE_CONFIG = "sell_polling_schedule_config"

// Metody:
fun isTradingEnabled(): Boolean = prefs.getBoolean(KEY_TRADING_ENABLED, false)
fun setTradingEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_TRADING_ENABLED, enabled).apply() }

fun isPeriodicSellPollingEnabled(): Boolean = prefs.getBoolean(KEY_SELL_POLLING_ENABLED, false)
fun getSellPollingFrequency(): DcaFrequency =
    prefs.getString(KEY_SELL_POLLING_FREQUENCY, null)?.let { DcaFrequency.valueOf(it) } ?: DcaFrequency.HOURLY
fun getSellPollingCronExpression(): String? = prefs.getString(KEY_SELL_POLLING_CRON, null)
fun getSellPollingScheduleConfig(): String? = prefs.getString(KEY_SELL_POLLING_SCHEDULE_CONFIG, null)

fun setPeriodicSellPolling(enabled: Boolean, frequency: DcaFrequency, cron: String?, scheduleConfig: String?) {
    prefs.edit()
        .putBoolean(KEY_SELL_POLLING_ENABLED, enabled)
        .putString(KEY_SELL_POLLING_FREQUENCY, frequency.name)
        .putString(KEY_SELL_POLLING_CRON, cron)
        .putString(KEY_SELL_POLLING_SCHEDULE_CONFIG, scheduleConfig)
        .apply()
}
```

- [ ] **Krok 2: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 3: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/data/local/UserPreferences.kt
git commit -m "feat(sell): add trading + sell polling flags to UserPreferences"
```

---

### Task 18: SellPollingWorker + SellPollingScheduler

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/worker/SellPollingWorker.kt`
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/worker/SellPollingScheduler.kt`

- [ ] **Krok 1: Prozkoumat DcaWorker pattern**

```bash
head -100 accbot-android/app/src/main/java/com/accbot/dca/worker/DcaWorker.kt
```

Najit jak se schedule vypocitava `nextExecutionAt` z `cronExpression` / `DcaFrequency`. Bude sdilena utility funkce.

- [ ] **Krok 2: Vytvorit SellPollingWorker**

```kotlin
package com.accbot.dca.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.accbot.dca.data.local.DcaDatabase
import com.accbot.dca.domain.usecase.ResolvePendingTransactionsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SellPollingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: DcaDatabase,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val openSells = database.transactionDao().countOpenSells()
            if (openSells == 0) return Result.success()

            resolvePendingTransactionsUseCase()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sell_polling"
    }
}
```

- [ ] **Krok 3: Vytvorit SellPollingScheduler**

```kotlin
package com.accbot.dca.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.DcaFrequency
import com.accbot.dca.domain.util.calculateNextFireTime
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SellPollingScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val userPreferences: UserPreferences
) {
    fun rescheduleIfEnabled() {
        if (!userPreferences.isPeriodicSellPollingEnabled()) {
            cancel()
            return
        }

        val frequency = userPreferences.getSellPollingFrequency()
        val cronOrConfig = userPreferences.getSellPollingCronExpression()
            ?: userPreferences.getSellPollingScheduleConfig()

        val nextFire = calculateNextFireTime(frequency, cronOrConfig, Instant.now())
        val delayMs = (nextFire.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<SellPollingWorker>()
            .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            SellPollingWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(SellPollingWorker.WORK_NAME)
    }
}
```

**Pozn.:** `calculateNextFireTime` existuje nekde v codebase (pouziva ji DcaWorker). Overit `grep -rn "calculateNextFireTime\|fun.*nextFire\|toCronExpression" accbot-android/app/src/main/java/com/accbot/dca/domain/`. Reuse.

- [ ] **Krok 4: Zaretez rescheduling - po kazdem worker doWork() naplanovat dalsi**

V `SellPollingWorker` injectovat `SellPollingScheduler`:

```kotlin
@HiltWorker
class SellPollingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: DcaDatabase,
    private val resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase,
    private val sellPollingScheduler: SellPollingScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val openSells = database.transactionDao().countOpenSells()
            if (openSells > 0) {
                resolvePendingTransactionsUseCase()
            }
            sellPollingScheduler.rescheduleIfEnabled()  // naplanovat dalsi spusteni
            Result.success()
        } catch (e: Exception) {
            sellPollingScheduler.rescheduleIfEnabled()  // naplanovat i pri failu
            Result.retry()
        }
    }

    companion object { const val WORK_NAME = "sell_polling" }
}
```

- [ ] **Krok 5: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 6: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/worker/
git commit -m "feat(sell): add SellPollingWorker + SellPollingScheduler"
```

---

### Task 19: ProcessLifecycle observer pro onResume polling

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/AppLifecycleObserver.kt` (nebo do existujiciho Application tridy)

- [ ] **Krok 1: Najit Application tridu**

```bash
grep -rn "class.*Application\|@HiltAndroidApp" accbot-android/app/src/main/java/com/accbot/dca/ | head -5
```

- [ ] **Krok 2: Vytvorit lifecycle observer**

V `AccBotApplication.kt` (nebo jak se jmenuje):

```kotlin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.accbot.dca.domain.usecase.ResolvePendingTransactionsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AccBotApplication : Application() {

    @Inject lateinit var resolvePendingTransactionsUseCase: ResolvePendingTransactionsUseCase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // ... existujici ...

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch {
                    try {
                        resolvePendingTransactionsUseCase()
                    } catch (e: Exception) {
                        android.util.Log.w("AppLifecycle", "Polling on app start failed", e)
                    }
                }
            }
        })
    }
}
```

- [ ] **Krok 3: Build check**

```bash
cd accbot-android && ./gradlew assembleDebug
```

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): trigger pending tx resolution on app foreground"
```

---

## Faze 5: UI - Settings

### Task 20: Rozsirit SettingsScreen o Pokrocile sekci

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/SettingsScreen.kt`
- Upravit: ViewModel pro SettingsScreen (grep pro `SettingsViewModel`)

- [ ] **Krok 1: Najit SettingsViewModel**

```bash
grep -rn "class SettingsViewModel" accbot-android/app/src/main/java/com/accbot/dca/
```

- [ ] **Krok 2: Pridat state pro trading toggle a periodic polling**

Do `SettingsUiState`:

```kotlin
val tradingEnabled: Boolean = false,
val periodicSellPollingEnabled: Boolean = false,
val sellPollingFrequency: DcaFrequency = DcaFrequency.HOURLY,
val sellPollingScheduleState: ScheduleBuilderState = ScheduleBuilderState()
```

- [ ] **Krok 3: Pridat ViewModel akce**

```kotlin
fun setTradingEnabled(enabled: Boolean) {
    userPreferences.setTradingEnabled(enabled)
    if (!enabled) {
        userPreferences.setPeriodicSellPolling(false, DcaFrequency.HOURLY, null, null)
        sellPollingScheduler.cancel()
    }
    refreshState()
}

fun setPeriodicSellPolling(
    enabled: Boolean,
    frequency: DcaFrequency,
    cron: String?,
    scheduleConfig: String?
) {
    userPreferences.setPeriodicSellPolling(enabled, frequency, cron, scheduleConfig)
    if (enabled) sellPollingScheduler.rescheduleIfEnabled()
    else sellPollingScheduler.cancel()
    refreshState()
}
```

- [ ] **Krok 4: Pridat Compose sekci do SettingsScreen**

Najit konec existujiciho settings formu a pridat:

```kotlin
SettingsSection(title = "Pokrocile") {
    SwitchRow(
        title = "Povolit prodeje",
        subtitle = "Umozni u vybranych planu zadavat limitni prodejni prikazy a sledovat P&L.",
        checked = uiState.tradingEnabled,
        onCheckedChange = viewModel::setTradingEnabled
    )

    if (uiState.tradingEnabled) {
        Divider()

        SwitchRow(
            title = "Kontrolovat sell ordery na pozadi",
            subtitle = "Periodicka kontrola stavu orderu. Zvysuje spotrebu baterie.",
            checked = uiState.periodicSellPollingEnabled,
            onCheckedChange = { enabled ->
                viewModel.setPeriodicSellPolling(
                    enabled = enabled,
                    frequency = uiState.sellPollingFrequency,
                    cron = uiState.sellPollingScheduleState.toCronExpression().takeIf { uiState.sellPollingFrequency == DcaFrequency.CUSTOM },
                    scheduleConfig = null
                )
            }
        )

        if (uiState.periodicSellPollingEnabled) {
            // Reuse schedule builder z AddPlanScreen
            FrequencyPickerAndScheduleBuilder(
                frequency = uiState.sellPollingFrequency,
                state = uiState.sellPollingScheduleState,
                onFrequencyChange = { freq ->
                    viewModel.setPeriodicSellPolling(
                        enabled = true,
                        frequency = freq,
                        cron = null,
                        scheduleConfig = null
                    )
                },
                onStateChange = { state ->
                    // Uloz state pro CUSTOM/DAILY/WEEKLY
                }
            )

            Text(
                text = "Caste kontroly zvysuji spotrebu baterie a pocitaji se do API limitu burzy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
```

- [ ] **Krok 5: Pokud neexistuje sdilena komponenta FrequencyPickerAndScheduleBuilder**

Extrahovat existujici Compose logiku z `AddPlanScreen.kt` (kde je ScheduleBuilder) do sdilene komponenty `accbot-android/app/src/main/java/com/accbot/dca/presentation/components/ScheduleBuilder.kt`. Pouzit stejny Compose kod v SettingsScreen.

- [ ] **Krok 6: Build check + instalace**

```bash
cd accbot-android && ./gradlew assembleDebug && ./gradlew installDebug
```

Otevrit app, jit do Settings, overit ze:
- Toggle "Povolit prodeje" funguje
- Pri ON se odkryje "Kontrolovat na pozadi"
- Pri zapnuti periodic se odkryje frequency picker + schedule builder

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): add Advanced section to SettingsScreen with trading + polling toggles"
```

---

## Faze 6: UI - Plan creation/edit

### Task 21: Rozsirit AddPlanScreen o sell sekci (gated by global trading)

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/AddPlanScreen.kt`
- Upravit: AddPlan ViewModel

- [ ] **Krok 1: Injektovat UserPreferences do AddPlanViewModel a expose trading flag**

```kotlin
val tradingEnabled: Boolean = userPreferences.isTradingEnabled()
```

- [ ] **Krok 2: Pridat state pro allowSells + targetProfitAmount**

```kotlin
val allowSells: Boolean = false,
val targetProfitAmount: String = "",  // raw input
val targetProfitAmountError: String? = null
```

Validace `targetProfitAmount`: musi byt prazdny nebo kladne cislo parsovatelne BigDecimal.

- [ ] **Krok 3: Update CreateDcaPlanUseCase (pokud je potreba)**

Zkontrolovat `CreateDcaPlanUseCase`, pokud akceptuje `DcaPlan`, automaticky dostane nova pole. Pokud ma explicitni parametry, pridat:

```kotlin
suspend operator fun invoke(
    // ... existujici ...
    allowSells: Boolean = false,
    targetProfitAmount: BigDecimal? = null
): Result<Long>
```

- [ ] **Krok 4: Pridat Compose sekci v AddPlanScreen**

Na konec formu, pred submit button, pridat:

```kotlin
if (uiState.tradingEnabled) {
    SectionHeader(text = "Prodeje (volitelne)")

    SwitchRow(
        title = "Povolit prodeje pro tento plan",
        subtitle = null,
        checked = uiState.allowSells,
        onCheckedChange = viewModel::setAllowSells
    )

    if (uiState.allowSells) {
        OutlinedTextField(
            value = uiState.targetProfitAmount,
            onValueChange = viewModel::setTargetProfitAmount,
            label = { Text("Cil zisku (volitelne, v ${uiState.fiat})") },
            supportingText = {
                Text("Plan-detail zobrazi progress bar k tomuto cili.")
            },
            isError = uiState.targetProfitAmountError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

- [ ] **Krok 5: Wire submit - predat nova pole do CreateDcaPlanUseCase**

```kotlin
createDcaPlanUseCase(
    // ... existujici ...
    allowSells = uiState.allowSells,
    targetProfitAmount = uiState.targetProfitAmount.takeIf { it.isNotBlank() }?.let { BigDecimal(it) }
)
```

- [ ] **Krok 6: Build check + manualni test**

```bash
cd accbot-android && ./gradlew installDebug
```

Settings -> zapnout Povolit prodeje. AddPlan screen -> overit ze se zobrazi Prodeje sekce. Vypnout trading -> sekce mizi.

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/AddPlanScreen.kt
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): add Prodeje section to AddPlanScreen (gated by trading_enabled)"
```

---

### Task 22: Rozsirit EditPlanScreen o sell sekci + confirm dialog

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/EditPlanScreen.kt`
- Upravit: EditPlan ViewModel

- [ ] **Krok 1: Stejna logika jako AddPlanScreen - pridat state + sekci**

Identicke kroky jako Task 21 Kroky 1-4, jen v EditPlanScreen kontextu. Nacita existujici hodnoty `plan.allowSells`, `plan.targetProfitAmount` do state.

- [ ] **Krok 2: Pridat confirm dialog pri vypnuti allowSells pokud jsou open ordery**

V onClick `Ulozit` (nebo onChange allowSells toggle off):

```kotlin
fun onToggleAllowSells(newValue: Boolean) {
    if (!newValue && uiState.currentAllowSells) {
        viewModelScope.launch {
            val openSells = database.transactionDao().observeOpenSellsForPlan(planId).first().size
            if (openSells > 0) {
                _uiState.update { it.copy(showDisableSellsDialog = openSells) }
            } else {
                _uiState.update { it.copy(allowSells = false) }
            }
        }
    } else {
        _uiState.update { it.copy(allowSells = newValue) }
    }
}
```

Compose:

```kotlin
uiState.showDisableSellsDialog?.let { count ->
    AlertDialog(
        onDismissRequest = { viewModel.dismissDisableSellsDialog() },
        title = { Text("Vypnout prodeje?") },
        text = { Text("Mas $count otevrenych sell orderu. Vypnutim prodeju se skryje sell sekce, ale ordery na burze zustavaji. Musis je zrusit rucne pres burzu, nebo zapnutim prodeju a kliknutim Cancel.") },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmDisableSells() }) {
                Text("Vypnout")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissDisableSellsDialog() }) {
                Text("Zrusit")
            }
        }
    )
}
```

- [ ] **Krok 3: Build check + manualni test**

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/EditPlanScreen.kt
git commit -m "feat(sell): add Prodeje section + disable confirm dialog to EditPlanScreen"
```

---

## Faze 7: UI - Plan detail + sell wizard

### Task 23: Plan detail - P&L card + open orders list

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/PlanDetailsScreen.kt`
- Upravit: PlanDetails ViewModel
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/components/PnLCard.kt`
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/components/OpenSellsList.kt`

- [ ] **Krok 1: ViewModel expose PlanPnL + open sells**

```kotlin
val planPnL: StateFlow<PlanPnL?> = combine(
    transactionFlow,
    spotPriceFlow
) { txs, spot ->
    calculatePlanPnLUseCase(planId, spot)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

val openSells: StateFlow<List<Transaction>> =
    transactionDao.observeOpenSellsForPlan(planId)
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
```

- [ ] **Krok 2: Vytvorit PnLCard composable**

```kotlin
@Composable
fun PnLCard(
    pnl: PlanPnL,
    fiat: String,
    targetAmount: BigDecimal?,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("P&L", style = MaterialTheme.typography.titleMedium)
            PnLRow("Drzeno:", "${pnl.currentCryptoHeld.stripTrailingZeros().toPlainString()} BTC")
            pnl.currentValueFiat?.let {
                PnLRow("  = hodnota:", "${formatFiat(it, fiat)}")
            }
            pnl.avgBuyPrice?.let {
                PnLRow("Prum. nakup:", "${formatFiat(it, fiat)}")
            }
            pnl.realizedPnL?.let {
                PnLRow("Realizovany:", formatPnL(it, fiat), pnlColor(it))
            } ?: PnLRow("Realizovany:", "-")

            pnl.unrealizedPnL?.let {
                PnLRow("Nerealizovany:", formatPnL(it, fiat), pnlColor(it))
            } ?: PnLRow("Nerealizovany:", "-")

            pnl.netPnL?.let {
                PnLRow("Net:", formatPnL(it, fiat), pnlColor(it), bold = true)
            }

            if (targetAmount != null && pnl.targetProgressPct != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = pnl.targetProgressPct.toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Cil: ${formatFiat(targetAmount, fiat)} (${(pnl.targetProgressPct * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PnLRow(label: String, value: String, color: Color? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = color ?: LocalContentColor.current,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun pnlColor(value: BigDecimal): Color = when {
    value > BigDecimal.ZERO -> Color(0xFF2E7D32)  // green
    value < BigDecimal.ZERO -> Color(0xFFC62828)  // red
    else -> Color.Unspecified
}

private fun formatPnL(value: BigDecimal, fiat: String): String =
    (if (value >= BigDecimal.ZERO) "+" else "") + formatFiat(value, fiat)
```

- [ ] **Krok 3: Vytvorit OpenSellsList composable**

```kotlin
@Composable
fun OpenSellsList(
    openSells: List<Transaction>,
    onCancelClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (openSells.isEmpty()) return

    Card(modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Otevrene sell ordery (${openSells.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            openSells.forEach { tx ->
                OpenSellRow(tx, onCancelClick)
            }
        }
    }
}

@Composable
private fun OpenSellRow(tx: Transaction, onCancelClick: (Long) -> Unit) {
    val requested = tx.requestedCryptoAmount ?: BigDecimal.ZERO
    val filled = tx.cryptoAmount
    val progressPct = if (requested > BigDecimal.ZERO)
        filled.divide(requested, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toInt() else 0

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${requested.toPlainString()} ${tx.crypto} @ ${tx.limitPrice?.toPlainString() ?: "-"} ${tx.fiat}")
            if (tx.status == TransactionStatus.PARTIAL) {
                Text("Partial: $progressPct% (${filled.toPlainString()} / ${requested.toPlainString()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text("Pending", style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { onCancelClick(tx.id) }) {
            Icon(Icons.Default.Close, contentDescription = "Zrusit order")
        }
    }
}
```

- [ ] **Krok 4: Vlozit komponenty do PlanDetailsScreen**

V mistem layoutu (mezi buy info a transaction history):

```kotlin
val sellUiVisible = remember(plan, userPrefs, exchangeApi) {
    plan.allowSells && userPrefs.isTradingEnabled() && exchangeApi.supportsLimitSell
}

if (sellUiVisible) {
    pnl?.let { PnLCard(it, plan.fiat, plan.targetProfitAmount) }
    OpenSellsList(openSells, onCancelClick = { viewModel.cancelSell(it) })
    Button(
        onClick = { viewModel.openSellWizard() },
        enabled = (pnl?.currentCryptoHeld ?: BigDecimal.ZERO) > BigDecimal.ZERO
    ) {
        Text("+ Vytvorit prodejni prikaz")
    }
}
```

- [ ] **Krok 5: Wire cancelSell akci ve ViewModelu**

```kotlin
fun cancelSell(txId: Long) = viewModelScope.launch {
    val result = cancelSellOrderUseCase(txId)
    if (result.isFailure) {
        _snackbar.emit("Zruseni orderu selhalo: ${result.exceptionOrNull()?.message}")
    }
}
```

- [ ] **Krok 6: Build check + manualni test**

```bash
cd accbot-android && ./gradlew installDebug
```

Otevrit plan-detail planu s `allowSells=true`. Overit:
- P&L card se zobrazuje (i s null hodnotami jako "-")
- Open sells list je prazdny (zatim nejsou sell transakce)
- Button "Vytvorit prodejni prikaz" je disabled pokud held=0, inak enabled

- [ ] **Krok 7: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/
git commit -m "feat(sell): add P&L card + open sells list to PlanDetailsScreen"
```

---

### Task 24: Sell wizard Krok 1 - zadani objednavky

**Soubory:**
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardBottomSheet.kt`
- Vytvorit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt`

- [ ] **Krok 1: Vytvorit SellWizardViewModel**

```kotlin
@HiltViewModel
class SellWizardViewModel @Inject constructor(
    private val validateUseCase: ValidateSellOrderUseCase,
    private val placeSellUseCase: PlaceLimitSellUseCase,
    private val calculatePnLUseCase: CalculatePlanPnLUseCase,
    private val database: DcaDatabase,
    private val credentialsStore: CredentialsStore,
    private val exchangeApiFactory: ExchangeApiFactory,
    private val userPreferences: UserPreferences
) : ViewModel() {

    data class UiState(
        val planId: Long = 0,
        val crypto: String = "",
        val fiat: String = "",
        val held: BigDecimal = BigDecimal.ZERO,
        val spotPrice: BigDecimal? = null,
        val avgBuyPrice: BigDecimal? = null,
        val amountInput: String = "",       // raw, v crypto
        val priceInput: String = "",         // raw, ve fiatu
        val minOrderSize: BigDecimal = BigDecimal("0.0001"),
        val validations: List<SellValidation> = emptyList(),
        val step: WizardStep = WizardStep.INPUT,
        val submitting: Boolean = false,
        val submitError: String? = null,
        val showTimeoutDialog: Boolean = false
    ) {
        val canProceed: Boolean
            get() = validations.none { it is SellValidation.HardError } &&
                    amountInput.isNotBlank() && priceInput.isNotBlank()
    }

    enum class WizardStep { INPUT, CONFIRM }

    // setters: setAmount, setPrice, chipActions (25/50/75/all, spot/breakeven/+10/+25)
    // validate() - re-runs validateUseCase on every input change
    // proceedToConfirm() - step = CONFIRM
    // submit() - calls placeSellUseCase
    // back() - step = INPUT
}
```

- [ ] **Krok 2: Vytvorit bottom sheet UI (zadani)**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellWizardBottomSheet(
    planId: Long,
    onDismiss: () -> Unit,
    viewModel: SellWizardViewModel = hiltViewModel()
) {
    LaunchedEffect(planId) { viewModel.init(planId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        when (state.step) {
            SellWizardViewModel.WizardStep.INPUT -> SellInputStep(state, viewModel, onDismiss)
            SellWizardViewModel.WizardStep.CONFIRM -> SellConfirmStep(state, viewModel)
        }
    }
}

@Composable
private fun SellInputStep(
    state: SellWizardViewModel.UiState,
    vm: SellWizardViewModel,
    onDismiss: () -> Unit
) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("Limit sell ${state.crypto}/${state.fiat}") },
            navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.Close, null) } }
        )

        InfoRow("Aktualni cena:", state.spotPrice?.toPlainString() ?: "-")
        InfoRow("Prum. nakup:", state.avgBuyPrice?.toPlainString() ?: "-")
        InfoRow("K dispozici:", "${state.held.toPlainString()} ${state.crypto}")

        SectionHeader("Mnozstvi")
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = vm::setAmount,
            trailingIcon = { Text(state.crypto) },
            modifier = Modifier.fillMaxWidth()
        )
        Row { listOf(25, 50, 75, 100).forEach { pct ->
            AssistChip(onClick = { vm.setAmountPct(pct) }, label = { Text(if (pct == 100) "Vse" else "$pct%") })
        } }

        SectionHeader("Limitni cena")
        OutlinedTextField(
            value = state.priceInput,
            onValueChange = vm::setPrice,
            trailingIcon = { Text(state.fiat) },
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            AssistChip(onClick = vm::setPriceSpot, label = { Text("Trzni") })
            AssistChip(onClick = vm::setPriceBreakeven, label = { Text("Breakeven") })
            AssistChip(onClick = { vm.setPricePct(10) }, label = { Text("+10%") })
            AssistChip(onClick = { vm.setPricePct(25) }, label = { Text("+25%") })
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Souhrn")
        val amountBD = state.amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val priceBD = state.priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
        InfoRow("Ziskate:", "${(amountBD * priceBD).toPlainString()} ${state.fiat}")
        state.avgBuyPrice?.let { avg ->
            val profit = (priceBD - avg) * amountBD
            InfoRow("Zisk vs prum:", formatPnL(profit, state.fiat), pnlColor(profit))
        }

        // validation messages
        state.validations.forEach { v ->
            when (v) {
                is SellValidation.HardError ->
                    Text(v.message, color = MaterialTheme.colorScheme.error)
                is SellValidation.InstantFillInfo ->
                    InfoBanner("Prodej probehne okamzite. Limitni cena je pod aktualni trzni (${v.spot.toPlainString()} ${state.fiat}). Prikaz se zfilluje ihned za nejvyssi nabidku na burze.")
                is SellValidation.FarFromMarketWarning ->
                    WarningBanner("Cena vysoko nad trhem - prodej se nemusi zfillovat dlouho.")
                is SellValidation.Ok -> { /* noop */ }
            }
        }

        Button(
            onClick = vm::proceedToConfirm,
            enabled = state.canProceed,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pokracovat") }
    }
}
```

- [ ] **Krok 3: Implementovat chip actions ve ViewModelu**

```kotlin
fun setAmountPct(pct: Int) {
    val available = state.held - sumOpenSellRequested(state.planId)
    val amount = available.multiply(BigDecimal(pct)).divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
    setAmount(amount.stripTrailingZeros().toPlainString())
}

fun setPriceSpot() = state.spotPrice?.let { setPrice(it.toPlainString()) }
fun setPriceBreakeven() = state.avgBuyPrice?.let { setPrice(it.toPlainString()) }
fun setPricePct(pct: Int) = state.avgBuyPrice?.let { avg ->
    val price = avg.multiply(BigDecimal("1.${pct.toString().padStart(2, '0')}"))
    setPrice(price.setScale(2, RoundingMode.HALF_UP).toPlainString())
}
```

- [ ] **Krok 4: Build check + manualni test**

```bash
cd accbot-android && ./gradlew installDebug
```

Overit ze bottom sheet otevre, inputy funguji, chipy pocitaji spravne, validace se zobrazuji (hlavne instant-fill banner kdyz limit < spot).

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/
git commit -m "feat(sell): add SellWizardBottomSheet Step 1 (input)"
```

---

### Task 25: Sell wizard Krok 2 - potvrzeni + submit

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardBottomSheet.kt`
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/SellWizardViewModel.kt`

- [ ] **Krok 1: Pridat SellConfirmStep composable**

```kotlin
@Composable
private fun SellConfirmStep(
    state: SellWizardViewModel.UiState,
    vm: SellWizardViewModel
) {
    val amountBD = state.amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val priceBD = state.priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO

    Column(Modifier.padding(16.dp)) {
        TopAppBar(
            title = { Text("Potvrdit prodej") },
            navigationIcon = { IconButton(vm::back) { Icon(Icons.Default.ArrowBack, null) } }
        )

        SummaryRow("Burza:", state.exchangeName)
        SummaryRow("Plan:", state.planName)
        SummaryRow("Side:", "PRODEJ")
        SummaryRow("Mnozstvi:", "${amountBD.toPlainString()} ${state.crypto}")
        SummaryRow("Limitni cena:", "${priceBD.toPlainString()} ${state.fiat}")
        SummaryRow("Ziskate:", "${(amountBD * priceBD).toPlainString()} ${state.fiat}")

        WarningBanner("Tato akce odesle prikaz na ${state.exchangeName} a nelze ji vratit. Prikaz lze pote zrusit, dokud neni castecne/celkem zfillovan.")

        state.submitError?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error)
        }

        Row {
            OutlinedButton(onClick = vm::back, enabled = !state.submitting) { Text("Zpet") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { vm.submit() },
                enabled = !state.submitting
            ) {
                if (state.submitting) CircularProgressIndicator() else Text("Odeslat")
            }
        }
    }

    if (state.showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissTimeoutDialog,
            title = { Text("Nelze overit stav prikazu") },
            text = { Text("Spojeni s burzou selhalo. Zkontroluj otevrene ordery na burze pres web a v pripade potreby zrus duplicitu.") },
            confirmButton = { Button(onClick = vm::dismissTimeoutDialog) { Text("OK") } }
        )
    }
}
```

- [ ] **Krok 2: Implementovat submit ve ViewModelu**

```kotlin
fun submit() = viewModelScope.launch {
    _uiState.update { it.copy(submitting = true, submitError = null) }

    try {
        val amount = state.amountInput.toBigDecimal()
        val price = state.priceInput.toBigDecimal()

        val result = withTimeoutOrNull(10_000L) {
            placeSellUseCase(state.planId, amount, price)
        }

        when {
            result == null -> {
                _uiState.update { it.copy(submitting = false, showTimeoutDialog = true) }
            }
            result.isSuccess -> {
                _navEvents.emit(NavEvent.Dismiss)
                _snackbar.emit("Prikaz vytvoren")
            }
            result.isFailure -> {
                val msg = result.exceptionOrNull()?.message ?: "Neznama chyba"
                _uiState.update { it.copy(submitting = false, submitError = msg) }
            }
        }
    } catch (e: Exception) {
        _uiState.update { it.copy(submitting = false, submitError = e.message ?: "Neznama chyba") }
    }
}

fun dismissTimeoutDialog() {
    _uiState.update { it.copy(showTimeoutDialog = false) }
}
```

- [ ] **Krok 3: Wire bottom sheet ve Plan detail screen**

V PlanDetailsScreen:

```kotlin
var sellWizardOpen by rememberSaveable { mutableStateOf(false) }

if (sellWizardOpen) {
    SellWizardBottomSheet(
        planId = planId,
        onDismiss = { sellWizardOpen = false }
    )
}

Button(onClick = { sellWizardOpen = true }) { Text("+ Vytvorit prodejni prikaz") }
```

- [ ] **Krok 4: Build check + manualni sandbox test**

```bash
cd accbot-android && ./gradlew installDebug
```

Sandbox mode, plan s allowSells a koupene BTC. Otevrit wizard, zadat 0.0001 BTC @ 1 200 000. Pokracovat. Potvrdit. Overit ze:
- API call prosel (logy)
- Transakce v DB ma status=PENDING, side=SELL, requestedCryptoAmount=0.0001
- Bottom sheet se zavrel
- Plan-detail ukazuje open order

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/sell/
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/PlanDetailsScreen.kt
git commit -m "feat(sell): add SellWizardBottomSheet Step 2 (confirm + submit) + wire into plan-detail"
```

---

## Faze 8: UI - Chart, History, Portfolio, Dashboard

### Task 26: Chart sell markery

**Soubory:**
- Upravit: existujici chart komponenta na plan-detail (grep `PlanDetailChart\|chart` v presentation/screens/plans/)

- [ ] **Krok 1: Najit chart komponentu**

```bash
grep -rn "Canvas\|drawLine\|Chart" accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/
```

- [ ] **Krok 2: Rozsirit chart o BUY/SELL markery**

Pro kazdou transakci s `status IN (COMPLETED, PARTIAL)`:

```kotlin
// v Canvas drawScope:
transactions.filter { it.status in setOf(TransactionStatus.COMPLETED, TransactionStatus.PARTIAL) }.forEach { tx ->
    val x = xForTime(tx.executedAt)
    val y = yForValue(tx.price)  // nebo y = size.height - 20.dp.toPx() pro fixed bottom axis

    val color = when (tx.side) {
        TransactionSide.BUY -> Color(0xFF2E7D32)
        TransactionSide.SELL -> Color(0xFFC62828)
    }
    val sizePx = 6.dp.toPx()

    val path = androidx.compose.ui.graphics.Path().apply {
        if (tx.side == TransactionSide.BUY) {
            // Trojuhelnik nahoru ^
            moveTo(x, y - sizePx)
            lineTo(x - sizePx, y + sizePx)
            lineTo(x + sizePx, y + sizePx)
        } else {
            // Trojuhelnik dolu v
            moveTo(x, y + sizePx)
            lineTo(x - sizePx, y - sizePx)
            lineTo(x + sizePx, y - sizePx)
        }
        close()
    }
    drawPath(path, color)
}
```

- [ ] **Krok 3: Tooltip na tap**

Rozsirit existujici tap handler aby detect klik na marker a zobrazit tooltip se detaily tx (mnozstvi, cena, status, side).

- [ ] **Krok 4: Build check + manualni test**

Test: plan s aspon 1 COMPLETED buy a 1 COMPLETED sell -> v chartu vidim zeleny trojuhelnik nahoru a cerveny dolu.

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/
git commit -m "feat(sell): add BUY/SELL markers to plan chart"
```

---

### Task 27: HistoryScreen - BUY/SELL icons + filter

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/HistoryScreen.kt`

- [ ] **Krok 1: Pridat filter chip**

```kotlin
enum class HistoryFilter { ALL, BUYS, SELLS, PENDING }

var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }

Row {
    FilterChip(filter == HistoryFilter.ALL, { filter = HistoryFilter.ALL }, { Text("Vse") })
    FilterChip(filter == HistoryFilter.BUYS, { filter = HistoryFilter.BUYS }, { Text("Nakupy") })
    FilterChip(filter == HistoryFilter.SELLS, { filter = HistoryFilter.SELLS }, { Text("Prodeje") })
    FilterChip(filter == HistoryFilter.PENDING, { filter = HistoryFilter.PENDING }, { Text("Pending") })
}

val filtered = transactions.filter { tx ->
    when (filter) {
        HistoryFilter.ALL -> true
        HistoryFilter.BUYS -> tx.side == TransactionSide.BUY
        HistoryFilter.SELLS -> tx.side == TransactionSide.SELL
        HistoryFilter.PENDING -> tx.status in setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL)
    }
}
```

- [ ] **Krok 2: Upravit item rendering**

```kotlin
@Composable
fun TransactionRow(tx: Transaction) {
    val (icon, color, sign) = when (tx.side) {
        TransactionSide.BUY -> Triple(Icons.Default.ArrowDownward, Color(0xFF2E7D32), "+")
        TransactionSide.SELL -> Triple(Icons.Default.ArrowUpward, Color(0xFFC62828), "-")
    }
    Row {
        Icon(icon, contentDescription = null, tint = color)
        Column {
            Text("${sign}${tx.cryptoAmount} ${tx.crypto}")
            Text("${if (tx.side == TransactionSide.BUY) "-" else "+"}${tx.fiatAmount} ${tx.fiat}")
        }
    }
}
```

- [ ] **Krok 3: Build check + manualni test**

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/HistoryScreen.kt
git commit -m "feat(sell): add BUY/SELL icons + filter chips to HistoryScreen"
```

---

### Task 28: TransactionDetailsScreen - sell-specific fields

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/history/TransactionDetailsScreen.kt`

- [ ] **Krok 1: Pridat rendering pro SELL pole**

```kotlin
if (tx.side == TransactionSide.SELL) {
    DetailRow("Limitni cena:", tx.limitPrice?.toPlainString() ?: "-")
    val requested = tx.requestedCryptoAmount ?: BigDecimal.ZERO
    DetailRow("Vyplneno:", "${tx.cryptoAmount} / $requested ${tx.crypto} (${progressPct}%)")
    tx.price?.let { DetailRow("Avg fill price:", it.toPlainString()) }

    if (tx.status in setOf(TransactionStatus.PENDING, TransactionStatus.PARTIAL)) {
        Button(onClick = { viewModel.cancelOrder(tx.id) }) {
            Text("Zrusit order")
        }
    }
}
```

- [ ] **Krok 2: Wire cancel ve ViewModelu**

```kotlin
fun cancelOrder(txId: Long) = viewModelScope.launch {
    cancelSellOrderUseCase(txId).onFailure { e ->
        _snackbar.emit("Zruseni selhalo: ${e.message}")
    }
}
```

- [ ] **Krok 3: Build check + manualni test**

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/history/
git commit -m "feat(sell): add sell-specific fields + cancel to TransactionDetailsScreen"
```

---

### Task 29: PortfolioScreen - realized + net P&L

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/portfolio/PortfolioScreen.kt`
- Upravit: PortfolioViewModel + CalculatePortfolioUseCase

- [ ] **Krok 1: Pridat realized + net P&L do CalculatePortfolioUseCase**

Najit kde se pocita portfolio summary (`CalculatePortfolioUseCase` nebo inline v ViewModelu). Pridat:

```kotlin
val totalRealizedFiat = completedOrPartialTxs
    .filter { it.side == TransactionSide.SELL }
    .sumOf { it.fiatAmount }

val currentHeldValueFiat = /* existing calc */
val totalInvestedFiat = completedOrPartialTxs
    .filter { it.side == TransactionSide.BUY }
    .sumOf { it.fiatAmount }

val netPnLFiat = currentHeldValueFiat + totalRealizedFiat - totalInvestedFiat
```

- [ ] **Krok 2: Expose v UiState (gated by isTradingEnabled)**

```kotlin
val totalRealized: BigDecimal = BigDecimal.ZERO,
val netPnL: BigDecimal? = null,
val showTradingMetrics: Boolean = false  // = userPreferences.isTradingEnabled()
```

- [ ] **Krok 3: Render v Compose**

V PortfolioScreen:

```kotlin
if (uiState.showTradingMetrics && uiState.totalRealized > BigDecimal.ZERO) {
    SummaryRow("Celkem realizovano:", "${uiState.totalRealized} ${uiState.fiat}")
}
uiState.netPnL?.takeIf { uiState.showTradingMetrics }?.let {
    SummaryRow("Net P&L:", formatPnL(it, uiState.fiat), pnlColor(it))
}
```

- [ ] **Krok 4: Build check + manualni test**

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/portfolio/
git commit -m "feat(sell): add realized + net P&L to PortfolioScreen"
```

---

### Task 30: DashboardScreen - open sells card

**Soubory:**
- Upravit: `accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/DashboardScreen.kt`
- Upravit: Dashboard ViewModel

- [ ] **Krok 1: Expose open sells per plan ve ViewModelu**

```kotlin
val openSellsByPlan: StateFlow<Map<Long, List<Transaction>>> =
    transactionDao.observeOpenSells()
        .map { list -> list.groupBy { it.planId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())
```

(`observeOpenSells()` - pridat do DAO pokud neexistuje: `SELECT * FROM transactions WHERE side='SELL' AND status IN ('PENDING','PARTIAL')`)

- [ ] **Krok 2: Render cards v DashboardScreen**

Pro kazdy plan s openSells:

```kotlin
uiState.openSellsByPlan.forEach { (planId, sells) ->
    val plan = planLookup[planId] ?: return@forEach
    Card(onClick = { nav.navigate("plan/$planId") }) {
        Column(Modifier.padding(16.dp)) {
            Text("${plan.name}: ${sells.size} open sell${if (sells.size > 1) "s" else ""}")
            sells.firstOrNull()?.let { tx ->
                Text("${tx.requestedCryptoAmount ?: tx.cryptoAmount} ${tx.crypto} @ ${tx.limitPrice} ${tx.fiat}")
            }
            uiState.spotPrices[plan.crypto]?.let {
                Text("Aktualni trzni: $it ${plan.fiat}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Krok 3: Build check + manualni test**

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/DashboardScreen.kt
git commit -m "feat(sell): add open sells card to DashboardScreen"
```

---

## Faze 9: Edge cases, polish, testing

### Task 31: Block plan delete pokud jsou open ordery

**Soubory:**
- Upravit: `DeleteDcaPlanUseCase` (grep pro tento nebo obdobny)
- Upravit: UI kde se spousti delete (pravdepodobne PlanDetailsScreen nebo EditPlanScreen)

- [ ] **Krok 1: Najit DeletePlanUseCase**

```bash
grep -rn "DeleteDcaPlan\|deletePlan" accbot-android/app/src/main/java/com/accbot/dca/domain/usecase/
```

- [ ] **Krok 2: Pridat check v use case**

```kotlin
suspend operator fun invoke(planId: Long): Result<Unit> {
    val openSells = database.transactionDao().observeOpenSellsForPlan(planId).first()
    if (openSells.isNotEmpty()) {
        return Result.failure(
            IllegalStateException("Plan ma ${openSells.size} open sell orderu. Zrus je nejdrive.")
        )
    }
    database.dcaPlanDao().deletePlan(planId)
    return Result.success(Unit)
}
```

- [ ] **Krok 3: V UI zobrazit alert pri failure**

V delete handler (pravdepodobne ve ViewModelu):

```kotlin
fun deletePlan(planId: Long) = viewModelScope.launch {
    val result = deletePlanUseCase(planId)
    if (result.isFailure) {
        _dialog.emit(Dialog.CannotDelete(result.exceptionOrNull()?.message))
    }
}
```

A Compose:

```kotlin
uiState.dialog?.let { d ->
    when (d) {
        is Dialog.CannotDelete -> AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            title = { Text("Nelze smazat plan") },
            text = { Text(d.message ?: "Plan ma otevrene ordery.") },
            confirmButton = { Button(vm::dismissDialog) { Text("OK") } }
        )
    }
}
```

- [ ] **Krok 4: Build check + manualni test**

Smaz plan s open sell order -> alert. Cancel order. Smaz znovu -> uspech.

- [ ] **Krok 5: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/
git commit -m "feat(sell): block plan delete when open sell orders exist"
```

---

### Task 32: Pull-to-refresh integration

**Soubory:**
- Upravit: `PlanDetailsScreen.kt`

- [ ] **Krok 1: Wire pull-to-refresh**

Pokud existuje `PullToRefreshBox` nebo `SwipeRefresh` v projektu, reuse. Jinak:

```kotlin
val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
val state = rememberPullToRefreshState()

Box(
    Modifier.pullToRefresh(state, refreshing, { viewModel.refresh() })
) {
    // existujici content
    PullToRefreshContainer(state = state, modifier = Modifier.align(Alignment.TopCenter))
}
```

- [ ] **Krok 2: V ViewModelu**

```kotlin
private val _refreshing = MutableStateFlow(false)
val refreshing = _refreshing.asStateFlow()

fun refresh() = viewModelScope.launch {
    _refreshing.value = true
    try {
        resolvePendingTransactionsUseCase()
    } finally {
        _refreshing.value = false
    }
}
```

- [ ] **Krok 3: Build check + manualni test**

Pull-down -> spinner -> pokud byly pending/partial ordery, aktualizovat.

- [ ] **Krok 4: Commit**

```bash
git add accbot-android/app/src/main/java/com/accbot/dca/presentation/screens/plans/
git commit -m "feat(sell): add pull-to-refresh to plan-detail for order status polling"
```

---

### Task 33: Manualni sandbox E2E test - Coinmate

**Zadne soubory ke zmene - ciste testovaci task.**

- [ ] **Krok 1: Pripravit sandbox**

Zapnout sandbox mode v Settings. Overit ze Coinmate credentials sandbox jsou platne.

- [ ] **Krok 2: Setup**

- Vytvorit plan BTC/CZK s `allowSells=true`, `targetProfitAmount=10000`
- Spustit 3 rucni buys po 100 CZK aby byl v planu nejaky BTC (pres "Run Now")
- Overit ze buy transakce jsou status=COMPLETED, held > 0

- [ ] **Krok 3: Scenare**

**Scenar A - standardni limit sell nad trhem:**
- Otevrit wizard, 25% z held, price = spot × 1.1
- Pokracovat -> Potvrdit -> Odeslat
- Overit:
  - V plan-detail open orders: 1 order, status=PENDING
  - Pred pull-to-refresh: status se nemeni
  - Po chvili / pull-to-refresh: status stale PENDING (order se nefilluje nad trhem)

**Scenar B - instant fill (limit pod trhem):**
- Wizard, 10% z held, price = spot × 0.5
- Validace: instant-fill banner zobrazen
- Odeslat
- Po pull-to-refresh (1-2s): status = COMPLETED, cryptoAmount = requested

**Scenar C - cancel:**
- Wizard, price = spot × 2 (nad trhem)
- Odeslat -> PENDING
- V plan-detail kliknout cancel ikonku
- Overit:
  - Tlacitko cancel trigger
  - Transakce: status = FAILED
  - Na burze (pres web): order canceled

**Scenar D - plan delete block:**
- Plan s open sell orderem
- Smazat plan -> alert "Nelze smazat"
- Cancel order
- Smazat plan -> uspech

- [ ] **Krok 4: Verifikace migrace**

Backup aktualni DB (export pres existujici backup flow). Pokud byly v backupu plany, zkusit restore na cistou instalaci -> plany + transakce projdou mapovanim, vcetne `allowSells`, `side`, atd.

- [ ] **Krok 5: Poznamky k chybam pripadne opravy**

Pokud scenare selzou, opravit konkretni bug (identifikovat task) a projit scenare znovu.

- [ ] **Krok 6: Commit (pokud byly opravy)**

```bash
git commit -m "fix(sell): ..."
```

---

### Task 34: Manualni sandbox E2E test - Binance

**Zadne soubory ke zmene - ciste testovaci task.**

- [ ] **Krok 1: Setup Binance testnet credentials**

Dle dokumentace aplikace. Zapnout sandbox mode.

- [ ] **Krok 2: Opakovat scenare A-D z Task 33, tentokrat na Binance**

Plan BTC/USDT nebo BTC/EUR. Krome orderu pres Binance endpointu `/api/v3/order`, overit:
- `limitSell` zakonci s numerickym orderId (Binance vraci long)
- `cancelOrder` vyzaduje `symbol + orderId` - funguje
- `getOrderStatus` vraci `executedQty` a `cummulativeQuoteQty` ktere se mapuji spravne

- [ ] **Krok 3: Verifikace - vydal jsi appku s oboustrannou podporou**

Po obou E2E testech (Coinmate + Binance) je MVP ready.

- [ ] **Krok 4: Final commit (pokud opravy)**

---

## Summary

**Celkem tasku:** 34
**Predpokladany rozsah:** 3-5 dnu pro experienced Kotlin/Compose dev, vice pro nezkusene s Room / WorkManager / Hilt patterns.

**Kriticke zavislosti v poradi:**
- Tasky 1-7 (datovy model) MUSI byt hotove pred 8+ (Exchange API)
- Task 8 je breaking change, opravy v 9-11
- Task 12 zavisi na 6 (DAO queries) a 8-11 (API refactor)
- Tasky 20+ (UI) zavisi na 12-16 (use cases)
- Tasky 33-34 (E2E) zavisi na vsem

**Vedlejsi dulezite:**
- ProGuard keep rules: Task 7 Krok 6 - overit ze Gson modely v `domain.model` package nejsou shrinknute
- DAO `observeOpenSells` (bez planId filter) pro Dashboard - Task 30
- Reuse `ScheduleBuilderState` Compose komponenty - Task 20 Krok 5 (pripadne extract do shared)
