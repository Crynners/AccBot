# DCA Sell Extension - Design Spec

**Datum:** 2026-04-23
**Status:** Draft - waiting user review
**Scope:** Android app (`accbot-android/`)

## 1. Cíl a kontext

Rozšířit existující DCA plány o opt-in **trading mode** - schopnost zadávat limitní prodejní příkazy na burzu, sledovat jejich stav, zobrazovat sell transakce v historii a grafu, a počítat realizovaný i nerealizovaný P&L vůči volitelnému cíli zisku.

Funkce je primárně určena pro pokročilé uživatele, kteří kromě akumulace občas realizují část pozice. Skrývá se za globální Settings toggle a per-plán opt-in - běžný DCA uživatel nezažije žádnou změnu.

### Mimo scope MVP

- Auto-sell triggery (plán prodá sám při dosažení ceny)
- Online price watcher
- Stop-loss
- Ladder sells (více limit orderů najednou)
- Sell wizard s doporučenou cenou / profit preview kalkulátorem nad rámec quick-select chipů
- Limit BUY (DCA zůstává market buy)
- Loan tracking (Firefish nebo jiné půjčky)
- Push notifikace o filled orderech

## 2. Architektura

```
┌──────────────────────────────────────────────────────────┐
│                     Plan Detail Screen                    │
│  ┌──────────────────┐  ┌─────────────────────────────┐   │
│  │ Buy side         │  │ Sell side (opt-in)          │   │
│  │ - DCA schedule   │  │ - "Place limit sell" button │   │
│  │ - Next buy       │  │ - Open orders list          │   │
│  │ - Buy history    │  │ - P&L card + target progress│   │
│  └──────────────────┘  └─────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
               │                           │
               ▼                           ▼
        ┌─────────────┐            ┌──────────────────┐
        │  DcaWorker  │            │  ExchangeApi     │
        │  (buy exec) │            │  - marketBuy()   │
        └─────────────┘            │  - limitSell()   │ ← nový
               │                   │  - getOrderStatus│ ← refactor
               │                   │  - cancelOrder() │ ← nový
               ▼                   └──────────────────┘
        ┌──────────────────────────────────────┐
        │  ResolvePendingTransactionsUseCase   │ ← rozšíří se
        │  (řeší PENDING + PARTIAL na BUY+SELL)│
        └──────────────────────────────────────┘
                       ▲
                       │ triggers
                       ├── App onResume
                       ├── DcaWorker tick (buy exec)
                       ├── Po placement / cancel
                       ├── Pull-to-refresh
                       └── SellPollingWorker (opt-in)
```

### Klíčové principy

1. **Minimum invasive na existující kód.** Buy-side logika beze změn. Přidají se 2 pole na `DcaPlan`, 3 pole na `TransactionEntity`, 3 nové metody v `ExchangeApi`.
2. **Znovuužití existujícího pending-tx flow.** Limit sell = transakce s `side=SELL`, `status=PENDING`. Existující `ResolvePendingTransactionsUseCase` řeší fill resolution pro buy i sell.
3. **Dvouvrstvý opt-in.** Globální Settings toggle (default OFF) → per-plán `allowSells` flag. Bez globálního toggle žádné nové UI nikde.
4. **Polling, ne websocket.** Žádné real-time updaty. Triggery: app open, DCA worker tick, po user akci, pull-to-refresh, opt-in periodic worker.

## 3. Datový model

### UserPreferences (nové flagy)

```kotlin
fun isTradingEnabled(): Boolean                          // default false (master gate)
fun setTradingEnabled(enabled: Boolean)

fun isPeriodicSellPollingEnabled(): Boolean              // default false
fun getSellPollingFrequency(): DcaFrequency              // default HOURLY
fun getSellPollingCronExpression(): String?              // jen pro CUSTOM
fun getSellPollingScheduleConfig(): String?              // serialized ScheduleBuilderState
fun setPeriodicSellPolling(enabled: Boolean, frequency: DcaFrequency, ...)
```

SharedPreferences keys: `trading_enabled`, `sell_polling_enabled`, `sell_polling_frequency`, `sell_polling_cron`, `sell_polling_schedule_config`. Per-device, ne v backupu (advanced opt-in se přijatelně re-enabluje po restore).

### DcaPlan (rozšíření)

```kotlin
data class DcaPlan(
    // existující pole beze změn
    val allowSells: Boolean = false,
    val targetProfitAmount: BigDecimal? = null   // jednotka = plan.fiat
)
```

### TransactionEntity (rozšíření)

```kotlin
data class TransactionEntity(
    // existující pole beze změn
    val side: TransactionSide = TransactionSide.BUY,
    val limitPrice: BigDecimal? = null,
    val requestedCryptoAmount: BigDecimal? = null
)

enum class TransactionSide { BUY, SELL }
```

**Sémantika polí pro různé stavy:**

| Pole | BUY market | SELL limit |
|---|---|---|
| `cryptoAmount` | filled (final) | filled so far (0 → requested) |
| `fiatAmount` | spent (final) | received so far (0 → requested × avg fill) |
| `requestedCryptoAmount` | `null` | `0.01` (zadáno při založení, fixed) |
| `limitPrice` | `null` | `1 200 000` (fixed) |

Progress fill v UI = `cryptoAmount / requestedCryptoAmount`.

### Lifecycle limit sell orderu

| Fáze | status | cryptoAmount | fiatAmount |
|---|---|---|---|
| Order zadán | PENDING | 0 | 0 |
| Partially filled | PARTIAL | 0.005 | 6 000 |
| Fully filled | COMPLETED | 0.01 (= requested) | 12 000 |
| Canceled bez fillu | FAILED | 0 | 0 |
| Canceled po partial fillu | PARTIAL | filled-so-far | filled-so-far |
| Expired bez fillu | FAILED | 0 | 0 |
| Expired po partial fillu | PARTIAL | filled-so-far | filled-so-far |

`requestedCryptoAmount` zůstává fixní napříč všemi stavy.

### Room migrace v19 → v20

```sql
ALTER TABLE dca_plans ADD COLUMN allowSells INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dca_plans ADD COLUMN targetProfitAmount TEXT DEFAULT NULL;

ALTER TABLE transactions ADD COLUMN side TEXT NOT NULL DEFAULT 'BUY';
ALTER TABLE transactions ADD COLUMN limitPrice TEXT DEFAULT NULL;
ALTER TABLE transactions ADD COLUMN requestedCryptoAmount TEXT DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_tx_plan_side_status
    ON transactions(planId, side, status);
```

Vše backward-kompatibilní.

### Backup / Restore

`BackupPlan` rozšířen o `allowSells` + `targetProfitAmount`. `BackupTransaction` rozšířen o `side` + `limitPrice` + `requestedCryptoAmount`. Všechna nová pole nepovinná s defaulty (BUY, null) pro starší verze.

### P&L (derivovaný, neperzistuje se)

```kotlin
data class PlanPnL(
    val totalBoughtFiat: BigDecimal,
    val totalBoughtCrypto: BigDecimal,
    val totalSoldFiat: BigDecimal,
    val totalSoldCrypto: BigDecimal,
    val currentCryptoHeld: BigDecimal,        // bought - sold
    val avgBuyPrice: BigDecimal?,             // null pokud nic nenakoupeno
    val currentValueFiat: BigDecimal?,        // null pokud spot není dostupný
    val realizedPnL: BigDecimal?,             // soldFiat - (soldCrypto * avgBuyPrice)
    val unrealizedPnL: BigDecimal?,           // currentValueFiat - (held * avgBuyPrice)
    val netPnL: BigDecimal?,                  // realized + unrealized
    val targetProgressPct: Double?            // netPnL / targetProfitAmount
)
```

Počítá se on-the-fly v ViewModelu. Žádná perzistence.

## 4. Exchange API rozšíření

### Nové metody na `ExchangeApi`

```kotlin
interface ExchangeApi {
    // existující metody beze změn

    suspend fun limitSell(
        crypto: String,
        fiat: String,
        cryptoAmount: BigDecimal,
        limitPrice: BigDecimal
    ): DcaResult = throw UnsupportedOperationException(
        "AccBot zatím nepodporuje limit sell pro ${exchange.displayName}"
    )

    suspend fun cancelOrder(orderId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(
            "AccBot zatím nepodporuje cancel order pro ${exchange.displayName}"
        ))

    val supportsLimitSell: Boolean get() = false
}
```

### Refactor `getOrderStatus`

Stávající signature `Transaction?` nepokrývá partial fill. Refactor na:

```kotlin
data class OrderStatusResult(
    val status: TransactionStatus,             // PENDING/PARTIAL/COMPLETED/FAILED
    val filledCryptoAmount: BigDecimal,
    val filledFiatAmount: BigDecimal,
    val avgFillPrice: BigDecimal?,
    val fee: BigDecimal?,
    val feeAsset: String?
)

suspend fun getOrderStatus(orderId: String): OrderStatusResult? = null
```

**Breaking change** pro existující callery - migrace kódu:
- `CoinbaseApi.getOrderStatus` - přemapovat z `Transaction?` na `OrderStatusResult?`
- `OtherExchanges.kt` (KrakenApi má getOrderStatus) - dtto
- `ResolvePendingTransactionsUseCase` - update mapování

### MVP support matrix

| Burza | `limitSell` | `cancelOrder` | `getOrderStatus` |
|---|---|---|---|
| Coinmate | ANO | ANO | ANO (nový/refactor) |
| Binance | ANO | ANO | ANO (nový) |
| Coinbase | NE (default) | NE | refactor existujícího |
| Kraken | NE | NE | refactor existujícího |
| KuCoin / Bitfinex / Huobi | NE | NE | NE |

Coinmate i Binance přepíšou `supportsLimitSell = true` v override; ostatní burzy ho nechávají na default `false`.

UI gating přes `supportsLimitSell` - tlačítka pro nepodporované burzy nejsou viditelná, případné existující plány s `allowSells=true` na nepodporované burze zobrazí warning místo sell sekce.

### REST endpointy

**Coinmate:**
- `POST /api/sellLimit` - založení
- `POST /api/cancelOrder` - cancel
- `POST /api/orderById` - status (`status: OPEN/FILLED/PARTIALLY_FILLED/CANCELLED`, `remainingAmount`, `originalAmount`)

**Binance:**
- `POST /api/v3/order` (`type=LIMIT, side=SELL, timeInForce=GTC`)
- `DELETE /api/v3/order`
- `GET /api/v3/order` (`status`, `executedQty`, `cummulativeQuoteQty`)

Sandbox: Coinmate sandbox + Binance testnet podporují limit ordery, fungují identicky s produkcí.

## 5. Sell flow (UX)

### Wizard - 2-step bottom-sheet

**Krok 1: Zadání objednávky**

Inputy:
- **Množství** (crypto, defaultně focused). Quick-select chipy `25% / 50% / 75% / Vše` z `currentCryptoHeld - sum(open sell requested)`. Toggle na vstup ve fiatu (přepočet podle limitní ceny).
- **Limitní cena** (fiat). Quick-select chipy:
  - `Tržní` = aktuální spot price
  - `Breakeven` = `avgBuyPrice` plánu
  - `+10%`, `+25%` = relativně k `avgBuyPrice`

Live souhrn:
- Získáte: `množství × limitní cena`
- Zisk vs prům: `(limitní - avgBuy) × množství` (zelená/červená, fiat + %)
- Cílová cena: `(limitní - spot) / spot` (informativní)

**Validace inline:**
- Množství > `currentCryptoHeld - sum(open sell requested)` → red error "Nemáte tolik BTC k dispozici (k dispozici X)"
- Množství < min order size burzy → red error
- Limit price <= spot → ⚡ info banner "Prodej proběhne okamžitě - příkaz se zfilluje ihned za nejvyšší nabídku na burze (obvykle blízko tržní ceny minus spread). Není to chyba." (ne-blokující)
- Limit price > spot × 3 → ⚠ warning "Cena vysoko nad trhem - prodej se nemusí zfillovat dlouho"
- Limit price <= 0 → red error

**Krok 2: Potvrzení**

Souhrn (burza, plán, side, množství, limit, získáte) + warning text "Akce odešle příkaz na {burzu} a nelze ji vrátit. Příkaz lze poté zrušit, dokud není zfillován."

`Odeslat`:
1. Disable wizard, show spinner
2. `exchangeApi.limitSell(...)`
3. Success → zápis `TransactionEntity(side=SELL, status=PENDING, exchangeOrderId, limitPrice, requestedCryptoAmount=množství, cryptoAmount=0, fiatAmount=0)` → toast "Příkaz vytvořen" → close wizard → trigger immediate poll
4. Failure → inline error v Krok 2 + button Zpět + Zkusit znovu (žádný DB zápis)
5. Network timeout → dialog "Nelze ověřit stav. Zkontroluj na burze." (žádný DB zápis)

### Sell sekce na plan-detailu

Mezi existující buy info a transaction history:

```
[Buy info]
─────────
P&L card (drženo, prům. nákup, realizovaný, nerealizovaný, net, cíl progress)
Otevřené sell ordery (list s cancel ikonkou, partial fill progress)
[ + Vytvořit prodejní příkaz ]
─────────
[Transaction history]
```

Cancel ikonka u open orderu → confirm dialog → `cancelOrder()` → DB update na `status=FAILED` (nebo `PARTIAL` pokud filled > 0).

## 6. Order tracking + polling

### Polling triggery

1. **App onResume** - `ProcessLifecycle` observer volá `ResolvePendingTransactionsUseCase` jednou
2. **DcaWorker tick** - piggyback (už dnes)
3. **Po placement / cancel sell orderu** - okamžitý poll (free, ověří propsání)
4. **Pull-to-refresh na plan-detail** - explicit user action s loading spinnerem
5. **Periodic worker (opt-in)** - `SellPollingWorker` reuse pattern z `DcaWorker` (AlarmManager + cron next-fire)

### UC query rozšíření

```kotlin
@Query("""
    SELECT * FROM transactions
    WHERE status IN ('PENDING', 'PARTIAL')
      AND exchangeOrderId IS NOT NULL
""")
suspend fun getResolvablePendingTransactions(): List<TransactionEntity>
```

PARTIAL stav se taky pollluje (může se postupně doplnit do COMPLETED).

### UC update logika

Pro každou transakci:
- Načti credentials (existující path s connectionId)
- `api.getOrderStatus(orderId)` → `OrderStatusResult?`
- Mapování:
  - `OPEN` → no change
  - `PARTIALLY_FILLED` → update `cryptoAmount=filled`, `fiatAmount=filled*avg`, `status=PARTIAL`
  - `FILLED` → update na final, `status=COMPLETED`
  - `CANCELED` / `EXPIRED` → `status=FAILED` (nebo `PARTIAL` pokud filled > 0)
  - `null` (order neznámý burze) → log warning, no change
- UPDATE query musí mít `WHERE status IN ('PENDING', 'PARTIAL')` jako concurrency guard (cancel mezitím nemění)

### Reaktivní propagace

Plan-detail ViewModel čte transakce přes Room Flow (existující `observeTransactionsForPlan(planId)` - ověřit; pokud chybí, přidáme `observe` variantu standardním Room patternem). UC update → Flow emit → UI re-render.

### Periodic SellPollingWorker

- Reuse `DcaFrequency` enum (`EVERY_15_MIN`, `HOURLY`, `EVERY_4_HOURS`, `EVERY_8_HOURS`, `DAILY`, `WEEKLY`, `CUSTOM`)
- Reuse `ScheduleBuilderState` Compose komponenta pro DAILY/WEEKLY/CUSTOM (extrahovat do shared composable pokud ještě není)
- AlarmManager pattern stejný jako `DcaWorker`
- Auto-skip když `transactionDao().countOpenSells() == 0`
- Constraints: `NetworkType.CONNECTED`
- Cancel při vypnutí toggle: `WorkManager.cancelUniqueWork("sell_polling")`
- Default frequency: `HOURLY`

## 7. UI placement

### 7.1 SettingsScreen

Nová sekce "Pokročilé":
- `[ ]` Povolit prodeje (master gate, default OFF)
- Dimmed dokud master OFF:
  - `[ ]` Kontrolovat sell ordery na pozadí
  - Frekvence dropdown (`DcaFrequency` options) + visual schedule builder pro DAILY/WEEKLY/CUSTOM
  - Warning text o spotřebě baterie / API limitech

Při vypnutí master toggle: periodic sell polling auto-disable, worker cancel. Plány s `allowSells=true` zachovány v DB, jen UI sell sekce se skryje.

### 7.2 AddPlanScreen / EditPlanScreen

Když `isTradingEnabled = false` → žádné nové UI (skryté).

Když `true`, nová sekce "Prodeje (volitelné)" na konci formuláře:
- `[ ]` Povolit prodeje pro tento plán
- Cíl zisku (volitelné, pouze pokud allowSells ON) - input v `plan.fiat`

V edit-mode pokud user vypne `allowSells` a má open sell ordery → confirm dialog s informací o orderech na burze. Allow continue.

### 7.3 PlanDetailsScreen

Sell sekce zobrazená pouze když `plan.allowSells && global.isTradingEnabled && exchangeApi.supportsLimitSell`:

- P&L card (Drženo, Prům. nákup, Realizovaný, Nerealizovaný, Net, Cíl progress bar pokud `targetProfitAmount` set)
- Open orders list s cancel button (a partial fill progress bar pokud filled > 0)
- "Vytvořit prodejní příkaz" button (disabled pokud held = 0 nebo pod min order size)

### 7.4 Chart sell markery

Existující chart na plan-detailu:
- BUY = malý zelený trojúhelník nahoru ▲
- SELL = malý červený trojúhelník dolů ▼
- Klik/long-press → tooltip (množství, cena, status)

Sells **nemění historickou křivku invested** (= sum buy fiat). **Mění** křivku held value (klesne v čase sellu).

### 7.5 HistoryScreen + TransactionDetailsScreen

HistoryScreen:
- Item dostane směrovou ikonu/badge (BUY ↓ zelená, SELL ↑ červená)
- Filter chip: `Vše | Nákupy | Prodeje | Pending`
- Amounty s znaménkem (`-0.01 BTC / +12 500 CZK` pro SELL)

TransactionDetailsScreen pro SELL navíc:
- Limitní cena
- Vyplněno: X / Y BTC (Z%)
- Avg fill price
- Cancel button (status v PENDING/PARTIAL)

### 7.6 PortfolioScreen

- **Sumární BTC drženo** = `sum(buy crypto) - sum(sell crypto)`
- **Celkem investováno** = `sum(buy fiat)` (beze změny)
- **Celkem realizováno** (nové, jen pokud > 0) = `sum(sell fiat)`
- **Net P&L portfolia** (nové, jen pokud trading enabled) = `currentValue + realized - invested`
- Agregátní křivka: sells "ujídají" z held value křivky, invested zůstává monotónně rostoucí

### 7.7 DashboardScreen

Nová karta (jen pro plány s `allowSells=true` a aspoň jedním open sell orderem):
```
📤 BTC stack: 1 open sell
0.01 BTC @ 1 250 000 CZK
Aktuální tržní: 1 180 245
```
Klik → plan-detail.

## 8. Edge cases & error handling

### 8.1 Insufficient balance

Server-side fail (Coinmate `ERROR_INSUFFICIENT_FUNDS`, Binance `-2010`) → `DcaResult.Failure(INSUFFICIENT_BALANCE)` → wizard inline error → no DB write.

### 8.2 Partial fill + cancel

User cancel po partial fill: `status=PARTIAL`, filled hodnoty zachovány. P&L bere `cryptoAmount` (filled), ne requested.

### 8.3 Out-of-band cancel (web)

Polling detekuje `CANCELED` → status=FAILED nebo PARTIAL. Žádné notifikace.

### 8.4 Placement timeout

Dialog "Nelze ověřit stav. Zkontroluj na burze." Žádný DB write. Lepší false negative než duplicitní order.

### 8.5 Multiple open sells

Validace amount = `held - sum(open sell requested)`. Server-side error zachytí race.

### 8.6 Sandbox mode

Existující `isSandboxMode()` orthogonal. Limit sells jdou na Coinmate sandbox / Binance testnet. Manuální sandbox sell loop před release.

### 8.7 Concurrency: polling vs cancel

UPDATE query s `WHERE status IN ('PENDING', 'PARTIAL')` slouží jako optimistic lock. Cancel mění na FAILED → následující polling update nic neudělá.

### 8.8 Plán delete s open ordery

**Block delete** s alertem "Plán má X open sell ordery. Zruš je nejdřív." User musí explicitně cancelovat.

### 8.9 Target overshoot

Žádný side effect. Progress bar capped vizuálně na 100% s textem (např. "130%" nebo "Cíl dosažen"). User pokračuje normálně.

### 8.10 P&L NaN edge cases

- Bez buy tx → `avgBuyPrice = null` → realized/unrealized = null → UI "—"
- Bez spot price → `unrealizedPnL = null` → UI "—"
- `realized` se počítá jen pokud `totalBoughtCrypto > 0` (zero-div guard)

## 9. Testing

### Unit
- `PlanPnL` kalkulace pro různé scénáře (no buys, no sells, partial fills, missing spot)
- `ResolvePendingTransactionsUseCase` mapování `OrderStatusResult` → DB update pro každý status
- Validace v sell wizardu (amount, price thresholds)
- Multi-open-sell validace (`held - sum(open sell requested)`)

### Integration
- Coinmate sandbox: place limit sell pod tržní (instant fill), nad tržní (open), partial fill simulation, cancel
- Binance testnet: stejný matrix
- Migration v19 → v20 idempotence
- Backup roundtrip s/bez nových polí

### Manual (před release)
- Full E2E na Coinmate sandbox: vytvoření trading plánu, buy několik tx, založit sell, sledovat polling, cancel
- Network timeout simulace při placement (DB ne-zápis verifikace)
- Plán delete s open ordery (block alert)
- Settings master toggle off → sell UI mizí, plány nedotčené

## 10. Rollout

- Feature gated globálním Settings toggle (default OFF) - safe to ship
- Coinmate + Binance support na release; ostatní burzy zobrazí "AccBot zatím nepodporuje" hlášku
- Periodic sell polling default OFF - user musí explicit opt-in
- Před release: manuální sandbox sell loop na Coinmate i Binance
