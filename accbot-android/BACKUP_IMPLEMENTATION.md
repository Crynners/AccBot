# Backup & Restore — Implementační plán

## Přehled
Jednorázový manuální export/import konfigurace a dat AccBot. Umožňuje přenos na nový telefon nebo zálohu proti ztrátě.

## Stav implementace

### Hotové
- [x] **1. BackupModels.kt** — datové třídy (envelope, payload, options, preview, backup plan/settings/credentials)

### Rozpracované
- [ ] **2. Bip39WordList.kt** — BIP39 wordlist (2048 slov) + generátor 12-slovného seedu

### Čeká na implementaci
- [ ] **3. BackupCryptoUseCase.kt** — AES-256-GCM + PBKDF2-HMAC-SHA256 (600k iterací)
- [ ] **4. DAO rozšíření** — `getAllNotificationsOnce()`, `getAllWithdrawalsOnce()`, `getWithdrawalCount()`, `getAllThresholdsOnce()` v Daos.kt
- [ ] **5. BackupDataCollector.kt** — čtení dat z DB + preferences do BackupPayload
- [ ] **6. BackupDataRestorer.kt** — zápis BackupPayload do DB + prefs (Room transakce, ID remap)
- [ ] **7. CreateBackupUseCase.kt** — export pipeline: collect → serialize → compress → encrypt
- [ ] **8. RestoreBackupUseCase.kt** — import pipeline: parse → decrypt → decompress → preview → restore
- [ ] **9. build.gradle.kts** — přidat `com.google.zxing:core` dependency
- [ ] **10. Screen.kt + MainActivity.kt** — navigační routes `BackupExport`, `BackupImport`
- [ ] **11. BackupExportViewModel.kt + BackupExportScreen.kt** — export wizard UI
- [ ] **12. BackupImportViewModel.kt + BackupImportScreen.kt** — import wizard UI
- [ ] **13. SettingsScreen.kt** — přidat sekci "Backup & Restore" s 2 kartami
- [ ] **14. strings.xml + strings-cs.xml** — ~40 nových lokalizačních stringů
- [ ] **15. Build verification** — `./gradlew assembleDebug`

---

## Architektura

### Formát zálohy

**Obálka (vždy plaintext JSON):**
```json
{
  "format": "accbot-backup",
  "version": 1,
  "createdAt": 1740000000000,
  "appVersion": "2.2.1",
  "platform": "android",
  "environment": "prod",
  "encrypted": true,
  "compressed": true,
  "sections": ["plans", "settings", "credentials", "transactions"],
  "data": "<base64(salt ‖ IV ‖ ciphertext ‖ GCM-tag)>"
}
```

**Payload (po dešifrování/dekompresi):**
```json
{
  "plans": [...],
  "settings": {...},
  "withdrawalThresholds": [...],
  "credentials": [...],
  "transactions": [...],
  "notifications": [...],
  "withdrawals": [...]
}
```

### Šifrování
- **Cipher:** AES-256-GCM (autentizované šifrování)
- **KDF:** PBKDF2-HMAC-SHA256, 600 000 iterací
- **Formát binárních dat:** `salt(16B) ‖ IV(12B) ‖ ciphertext ‖ GCM-tag(16B)` → base64
- **Seed mód:** 128 bitů entropie → 12 BIP39 slov → passphrase pro PBKDF2
- Pokud záloha obsahuje credentials → šifrování je **povinné**

### QR kód (config-only)
- Kapacita: ~1850 B (verze 25, ECC-L)
- Payload: GZip → AES-GCM → binární QR
- Generování: `com.google.zxing:core`
- Skenování: existující `com.google.mlkit:barcode-scanning`

### ID remapping při importu
```
oldPlanId → dao.insertPlan(plan.copy(id = 0)) → newPlanId
transactions.forEach { it.copy(planId = idMap[it.planId]) }
```
Celý import v jedné Room transakci.

---

## Nové soubory

```
app/src/main/java/com/accbot/dca/
├── domain/
│   ├── model/
│   │   └── BackupModels.kt              ✅ HOTOVO
│   └── usecase/
│       ├── CreateBackupUseCase.kt        — orchestrace exportu
│       ├── RestoreBackupUseCase.kt       — orchestrace importu
│       └── BackupCryptoUseCase.kt        — AES-256-GCM + PBKDF2
├── data/
│   └── local/
│       ├── BackupDataCollector.kt        — sběr dat z DB + preferences
│       ├── BackupDataRestorer.kt         — zápis dat s ID remappingem
│       └── Bip39WordList.kt              — BIP39 slov + seed generátor
└── presentation/
    └── screens/
        └── backup/
            ├── BackupExportScreen.kt     — export wizard UI
            ├── BackupExportViewModel.kt  — export state management
            ├── BackupImportScreen.kt     — import wizard UI
            └── BackupImportViewModel.kt  — import state management
```

## Existující soubory k úpravě

| Soubor | Změna |
|--------|-------|
| `data/local/Daos.kt` | Nové suspend query metody |
| `presentation/navigation/Screen.kt` | `BackupExport` + `BackupImport` routes |
| `MainActivity.kt` | Composable routes pro backup screens |
| `presentation/screens/SettingsScreen.kt` | Sekce "Backup & Restore" |
| `res/values/strings.xml` | ~40 EN stringů |
| `res/values-cs/strings.xml` | ~40 CS stringů |
| `app/build.gradle.kts` | ZXing dependency |

---

## UX Flow

### Export
```
Settings → Export Backup → [vybrat data] → [heslo/seed] → [Exportovat soubor / QR kód]
```

### Import
```
Settings → Import Backup → [Vybrat soubor / Skenovat QR] → [zadat heslo/seed] → [preview] → [Obnovit] → restart
```

## Klíčové existující vzory
- **CSV export:** `ExportTransactionsToCsvUseCase.kt` — FileProvider + ACTION_SEND
- **CSV import:** `ImportCsvScreen.kt` — `ActivityResultContracts.OpenDocument()`
- **QR skenování:** `QrScanner.kt` — ML Kit
- **App restart:** `SettingsViewModel.restartApp()`
- **JSON:** Gson (již v projektu)
- **Room transakce:** `database.withTransaction { }`
- **DI:** Hilt `@HiltViewModel`, `@Inject constructor`
