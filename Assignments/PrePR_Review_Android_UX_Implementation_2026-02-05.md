# Pre-PR Review: AccBot Android UX Implementation

**Datum:** 2026-02-05
**Reviewer:** Claude Code
**Rozsah:** Kompletní Android UX implementace (Onboarding, Navigation, Portfolio, History, Plans, Exchanges)

---

## Status: 🟢 Připraveno k PR

Všechny kritické, vysoké a většina středních priorit byla opravena.

**Opraveno:** 18 položek (1 kritická, 7 vysokých, 10 středních)
**Zbývá:** 2 položky (1 střední I18N, 4 nízké priority) - lze řešit v následujících PR

---

## Assignments Checklist

### Kritické (musí být opraveno)
- [x] **DB-001:** ~~Odstranit `.fallbackToDestructiveMigration()` v `DcaDatabase.kt:79`~~ - OPRAVENO: změněno na `.fallbackToDestructiveMigrationOnDowngrade()`

### Vysoká priorita
- [x] **VAL-001:** ~~Přidat validaci passphrase pro KuCoin/Coinbase~~ - OPRAVENO: podmínka + supporting text
- [x] **VAL-002:** ~~Přidat validaci minimální velikosti orderu~~ - OPRAVENO: minOrderSize check v `FirstPlanScreen.kt`
- [x] **VAL-003:** ~~Přidat validaci formátu wallet adresy~~ - OPRAVENO: BTC/LTC/generic address validators v `EditPlanViewModel.kt`
- [x] **SOLID-001:** ~~Rozdělit `EncryptedPreferences.kt` na 3 třídy~~ - OPRAVENO: vytvořeny CredentialsStore, OnboardingPreferences, UserPreferences
- [x] **SOLID-002:** ~~Extrahovat exchange instructions z `AddExchangeViewModel.kt:123-204`~~ - OPRAVENO: vytvořen `ExchangeInstructionsProvider.kt`
- [x] **EDGE-001:** ~~Opravit race condition v `OnboardingViewModel.kt`~~ - OPRAVENO: guard pro concurrent validation
- [x] **EDGE-002:** ~~Přidat exception handling do type converterů~~ - OPRAVENO: try-catch s fallback hodnotami
- [x] **PREC-001:** ~~Opravit precision loss v DAO queries~~ - OPRAVENO: String return types

### Střední priorita
- [x] **DUP-001:** ~~Extrahovat `DateTimeFormatter` do constants objektu~~ - OPRAVENO: vytvořen `DateFormatters.kt`
- [x] **DUP-002:** ~~Vytvořit reusable `ExchangeAvatar` composable~~ - OPRAVENO: vytvořen v `ReusableComponents.kt`
- [x] **DUP-003:** ~~Vytvořit reusable `OnboardingHeader` composable~~ - OPRAVENO: vytvořen v `ReusableComponents.kt`
- [x] **DUP-004:** ~~Extrahovat `TransactionStatusIcon` mapping~~ - OPRAVENO: `getTransactionStatusStyle()` v `ReusableComponents.kt`
- [x] **DUP-005:** ~~Vytvořit reusable `IconBadge` composable~~ - OPRAVENO: vytvořen v `ReusableComponents.kt`
- [x] **EFF-001:** ~~Přidat `remember` pro `bottomNavRoutes`~~ - OPRAVENO: extrahováno do `bottomNavRoutes` Set na module level
- [x] **EFF-002:** ~~Přidat pagination do `getAllTransactions()`~~ - OPRAVENO: přidány `getTransactionsPaged()`, `getFilteredTransactionsPaged()` a count queries
- [x] **EFF-003:** ~~Přesunout calculations do domain layer~~ - OPRAVENO: vytvořen `CalculatePortfolioUseCase.kt`
- [ ] **I18N-001:** Přesunout hardcoded strings do `strings.xml` (30+ stringů)
- [x] **SEC-001:** ~~Použít `.commit()` místo `.apply()` pro credentials~~ - OPRAVENO: změněno v `EncryptedPreferences.kt`
- [x] **IDX-001:** ~~Přidat database indexy~~ - OPRAVENO: indexy + migrace v3 v `DcaDatabase.kt`

### Nízká priorita
- [ ] **CLEAN-001:** Odstranit unused `isSuccess` flag v `AddExchangeViewModel.kt:88`
- [ ] **CLEAN-002:** Přidat komentář k empty TopAppBar title v `SecurityScreen.kt:29`
- [ ] **CLEAN-003:** Extrahovat magic numbers do konstant (animation delays, font scales)
- [ ] **CLEAN-004:** Implementovat "View all transactions" button v `PlanDetailsScreen.kt:269`

---

## Kritické nálezy

### 1. 🔴 Destruktivní migrace databáze (CRITICAL)

**Soubor:** `DcaDatabase.kt:79`

```kotlin
.fallbackToDestructiveMigration()
.build()
```

**Problém:** Při jakémkoliv selhání migrace se SMAŽE CELÁ DATABÁZE včetně historie transakcí uživatele.

**Řešení:**
```kotlin
// Odstranit fallbackToDestructiveMigration() nebo použít pouze pro downgrade:
.fallbackToDestructiveMigrationOnDowngrade()
.build()
```

---

### 2. 🔴 Chybějící validace passphrase (HIGH)

**Soubor:** `AddExchangeScreen.kt:485`

```kotlin
enabled = uiState.selectedExchange != null &&
        uiState.apiKey.isNotBlank() &&
        uiState.apiSecret.isNotBlank() &&
        !uiState.isValidatingCredentials
// CHYBÍ: && (!needsPassphrase || uiState.passphrase.isNotBlank())
```

**Problém:** Uživatel může kliknout "Connect Exchange" bez passphrase pro KuCoin/Coinbase, které ji vyžadují.

**Řešení:**
```kotlin
val needsPassphrase = uiState.selectedExchange in listOf(Exchange.KUCOIN, Exchange.COINBASE)
enabled = uiState.selectedExchange != null &&
        uiState.apiKey.isNotBlank() &&
        uiState.apiSecret.isNotBlank() &&
        (!needsPassphrase || uiState.passphrase.isNotBlank()) &&
        !uiState.isValidatingCredentials
```

---

### 3. 🔴 Race condition při validaci credentials (HIGH)

**Soubor:** `OnboardingViewModel.kt:88-127`

**Problém:** Pokud uživatel rychle klikne "Validate" 2x, obě async operace běží současně a mohou uložit nesprávné credentials.

**Řešení:**
```kotlin
fun validateAndSaveCredentials() {
    val state = _uiState.value
    if (state.isValidatingCredentials) return // Guard

    viewModelScope.launch {
        _uiState.update { it.copy(isValidatingCredentials = true) }
        // ... rest of validation
    }
}
```

---

### 4. 🔴 Precision loss v monetary calculations (HIGH)

**Soubor:** `Daos.kt:96-108`

```kotlin
data class MonthlyStatsResult(
    val totalFiat: Double,    // ŠPATNĚ - ztráta přesnosti
    val totalCrypto: Double   // ŠPATNĚ - ztráta přesnosti
)
```

**Problém:** Agregační queries vrací `Double`, ale entity používají `BigDecimal`. Při konverzi dochází ke ztrátě přesnosti u peněžních hodnot.

**Řešení:** Použít String v SQL a konvertovat na BigDecimal:
```kotlin
@Query("""
    SELECT CAST(SUM(fiatAmount) AS TEXT) as totalFiat,
           CAST(SUM(cryptoAmount) AS TEXT) as totalCrypto
    FROM transactions WHERE ...
""")
suspend fun getMonthlyStats(): MonthlyStatsStringResult

// Pak konvertovat: BigDecimal(result.totalFiat)
```

---

### 5. 🟠 SRP Violation - EncryptedPreferences (HIGH)

**Soubor:** `EncryptedPreferences.kt`

**Problém:** Třída má 3 různé odpovědnosti:
1. Správa API credentials (security-critical)
2. Onboarding state
3. User preferences (theme, notifications)

**Dopad:**
- Změny v notification preferencích vyžadují úpravu security-sensitive třídy
- Těžké testování
- Porušení SRP

**Řešení:** Rozdělit na 3 třídy:
```kotlin
class CredentialsStore @Inject constructor(context: Context) { ... }
class OnboardingPreferences @Inject constructor(context: Context) { ... }
class UserPreferences @Inject constructor(context: Context) { ... }
```

---

### 6. 🟠 Missing exception handling v Type Converters (HIGH)

**Soubor:** `Entities.kt:34,40,46,52`

```kotlin
@TypeConverter
fun toExchange(value: String): Exchange = Exchange.valueOf(value)
// Pokud value není platný enum name, vyhodí IllegalArgumentException
```

**Řešení:**
```kotlin
@TypeConverter
fun toExchange(value: String): Exchange = try {
    Exchange.valueOf(value)
} catch (e: IllegalArgumentException) {
    Exchange.COINMATE // fallback
}
```

---

## Návrhy na zlepšení

### 1. Extrakce duplicitních UI patterns

Vytvořit reusable komponenty:

```kotlin
// components/OnboardingHeader.kt
@Composable
fun OnboardingHeader(
    title: String,
    subtitle: String,
    progress: Float? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Primary
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
```

### 2. DateTimeFormatter singleton

```kotlin
// utils/DateFormatters.kt
object DateFormatters {
    val transactionDateTime: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM d, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
}
```

### 3. Domain layer pro kalkulace

```kotlin
// domain/usecase/CalculatePortfolioUseCase.kt
class CalculatePortfolioUseCase @Inject constructor() {
    fun calculateCryptoHoldings(transactions: List<Transaction>): List<CryptoHolding> { ... }
    fun calculateExchangeHoldings(transactions: List<Transaction>): List<ExchangeHolding> { ... }
    fun calculateMonthlyPerformance(transactions: List<Transaction>): List<MonthlyPerformance> { ... }
}
```

### 4. Pagination pro velké datasety

```kotlin
// Daos.kt
@Query("SELECT * FROM transactions ORDER BY executedAt DESC LIMIT :limit OFFSET :offset")
fun getTransactionsPaged(limit: Int, offset: Int): Flow<List<TransactionEntity>>
```

### 5. Database indexy

```kotlin
@Entity(
    tableName = "transactions",
    indices = [
        Index("planId"),
        Index("exchange"),
        Index("crypto"),
        Index("status"),
        Index("executedAt")
    ]
)
data class TransactionEntity(...)
```

---

## Výkonnostní poznámka

### Časová složitost klíčových operací

| Operace | Složitost | Poznámka |
|---------|-----------|----------|
| `calculateCryptoHoldings()` | O(n) | Jeden průchod přes transakce + groupBy |
| `calculateExchangeHoldings()` | O(n) | Nested groupBy, ale stále lineární |
| `calculateMonthlyPerformance()` | O(n) | GroupBy + map |
| `getAllTransactions()` | O(n) | ⚠️ Načte VŠECHNY záznamy do paměti |
| `exportToCsv()` | O(n) | Lineární, ale bez streamování |

### Paměťová náročnost

| Komponenta | Riziko | Poznámka |
|------------|--------|----------|
| `HistoryScreen` | 🟡 Střední | LazyColumn, ale všechna data v paměti |
| `PortfolioViewModel` | 🟡 Střední | 3 kopie dat (crypto, exchange, monthly) |
| `TransactionDao.getAllTransactions()` | 🔴 Vysoké | Bez pagination - OOM při tisících záznamech |

### Doporučení pro optimalizaci

1. **Pagination** - Implementovat pro `getAllTransactions()` a `getAllPlans()`
2. **Incremental updates** - Použít Room `@Query` s `WHERE` pro inkrementální aktualizace místo přepočtu všeho
3. **Lazy loading** - Pro portfolio statistics používat lazy computed properties
4. **Remember** - Všechny `DateTimeFormatter` a seznamy v composables obalit do `remember {}`

---

## Souhrnná tabulka nálezů

| Kategorie | Kritické | Vysoké | Střední | Nízké |
|-----------|----------|--------|---------|-------|
| Validace | 0 | 3 | 0 | 0 |
| SOLID | 0 | 2 | 1 | 0 |
| Edge Cases | 0 | 2 | 2 | 0 |
| Duplikace | 0 | 0 | 5 | 0 |
| Efektivita | 0 | 1 | 3 | 0 |
| Security | 1 | 0 | 1 | 0 |
| I18n | 0 | 0 | 1 | 0 |
| Clean Code | 0 | 0 | 0 | 4 |
| **Celkem** | **1** | **8** | **13** | **4** |

---

## Závěr

Implementace je solidní a funkční. Před PR doporučuji:

1. **Okamžitě opravit:** DB-001 (destruktivní migrace) - kritické riziko ztráty dat
2. **Opravit před PR:** VAL-001, VAL-002, EDGE-001, PREC-001
3. **Ideálně opravit:** Duplikace a SRP violations pro lepší maintainability
4. **Odložit:** I18n a nízké priority lze řešit v následujících PR

Odhadovaný čas na kritické + vysoké priority: **4-6 hodin**
