# Google Play Store Publishing Guide for AccBot

A comprehensive checklist for publishing AccBot DCA on Google Play Store.

---

## 1. Google Play Developer Account

- [ ] Register at [play.google.com/console](https://play.google.com/console) ($25 one-time fee)
- [ ] Complete identity verification (government-issued ID + address)
- [ ] Choose account type: **Individual** (recommended for open-source projects; no D-U-N-S number needed)
- [ ] For organization accounts: obtain a D-U-N-S number (free from Dun & Bradstreet, takes 5-10 business days)

---

## 2. App Signing

### 2.1 Generate Upload Keystore

```bash
keytool -genkey -v \
  -keystore accbot-upload.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias accbot-upload
```

You will be prompted for:
- Keystore password
- Key password
- Your name, organization, city, state, country code

**CRITICAL: Store the keystore file and passwords securely. NEVER commit to git.**

### 2.2 Configure Signing in `app/build.gradle.kts`

Add `signingConfigs` inside the `android` block:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../accbot-upload.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "accbot-upload"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 2.3 Google Play App Signing

- Google manages the **app signing key** (most secure approach)
- You upload with your **upload key** (the keystore generated above)
- If your upload key is ever compromised, Google can issue a new one
- Enroll in Google Play App Signing during first upload in Play Console

### 2.4 GitHub Actions Secrets

Add these secrets in GitHub repo settings (`Settings > Secrets and variables > Actions`):

| Secret Name | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded keystore file (`base64 -i accbot-upload.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `accbot-upload` |
| `KEY_PASSWORD` | Key password |

---

## 3. Store Listing Assets

### 3.1 Required Graphics

| Asset | Dimensions | Format | Notes |
|---|---|---|---|
| **Hi-res icon** | 512 x 512 | PNG (32-bit) | Source: `ic_launcher_foreground.xml` |
| **Feature graphic** | 1024 x 500 | PNG or JPEG | Displayed at top of store listing |
| **Phone screenshots** | min 2, recommended 8 | PNG or JPEG | 16:9 or 9:16, min 320px, max 3840px |
| **Tablet screenshots** | optional but recommended | PNG or JPEG | 7-inch and 10-inch |

### 3.2 Recommended Screenshots

1. Dashboard with active plan and portfolio summary
2. Portfolio performance chart with ROI
3. Strategy selection (Classic, ATH, Fear & Greed)
4. Transaction history with filters
5. Exchange setup with QR scanning
6. Security onboarding screen
7. Sandbox mode demonstration
8. Settings with exchange management

### 3.3 Short Description (80 chars max)

**English:**
> Self-custody Bitcoin DCA. Stack sats automatically with 7 exchanges.

**Czech:**
> Bitcoin DCA bez třetí strany. Automatické hromadění satoshi na 7 burzách.

### 3.4 Full Description (4000 chars max)

**English:**
```
AccBot is a self-custody Dollar Cost Averaging (DCA) tool for Bitcoin and other
cryptocurrencies. Your API keys never leave your device — no cloud, no tracking,
no middlemen.

KEY FEATURES

- Automatic DCA: Set your amount, frequency, and strategy — AccBot handles the rest
- 3 Smart Strategies: Classic DCA, ATH-based (buy more during dips), and Fear &
  Greed Index-based (buy more when market is fearful)
- 7 Supported Exchanges: Coinmate, Binance, Kraken, KuCoin, Bitfinex, Huobi,
  Coinbase
- Portfolio Analytics: Track your holdings, ROI, and performance with interactive
  charts
- Transaction History: Full history with filtering, sorting, and CSV export
- Auto-Withdrawal: Automatically send purchased crypto to your own wallet
- QR Code Setup: Scan API keys and wallet addresses with your camera
- Sandbox Mode: Test your strategies risk-free with testnet exchanges

SECURITY FIRST

AccBot is built with a security-first, decentralized architecture:
- API credentials encrypted with AES-256-GCM via Android Keystore
- All data stored locally on your device only
- No cloud sync, no analytics, no tracking
- Direct HTTPS communication with exchanges
- Optional biometric lock (fingerprint/face)
- Open source — audit the code yourself

HOW IT WORKS

1. Connect your exchange with API keys (trading permission only)
2. Configure your DCA plan: crypto, amount, frequency, strategy
3. AccBot automatically executes purchases on your schedule

SUPPORTED CRYPTOCURRENCIES

Bitcoin (BTC), Ethereum (ETH), Litecoin (LTC), and more — depends on your
exchange's available trading pairs.

IMPORTANT DISCLAIMERS

AccBot is a DCA automation tool, not a financial advisor. Past performance does
not guarantee future results. Cryptocurrency investments carry risk. Always do
your own research. AccBot does not have access to withdraw your funds — API keys
should be created with trading permissions only.

Open source under MIT License.
```

**Czech:**
```
AccBot je nástroj pro automatické pravidelné nákupy (DCA) Bitcoinu a dalších
kryptoměn. Vaše API klíče nikdy neopustí vaše zařízení — žádný cloud, žádné
sledování, žádní prostředníci.

HLAVNÍ FUNKCE

- Automatické DCA: Nastavte částku, frekvenci a strategii — AccBot se postará o
  zbytek
- 3 chytré strategie: Klasické DCA, DCA dle ATH (nakupujte více při propadech) a
  DCA dle indexu Fear & Greed (nakupujte více, když na trhu panuje strach)
- 7 podporovaných burz: Coinmate, Binance, Kraken, KuCoin, Bitfinex, Huobi,
  Coinbase
- Analýza portfolia: Sledujte své držby, ROI a výkonnost s interaktivními grafy
- Historie transakcí: Kompletní historie s filtrováním, řazením a CSV exportem
- Automatický výběr: Automaticky odesílejte nakoupené krypto do vlastní peněženky
- Nastavení přes QR kód: Skenujte API klíče a adresy peněženek fotoaparátem
- Sandbox režim: Testujte strategie bez rizika s testovacími burzami

BEZPEČNOST NA PRVNÍM MÍSTĚ

AccBot je postaven na bezpečnostní decentralizované architektuře:
- API přihlašovací údaje šifrovány AES-256-GCM přes Android Keystore
- Veškerá data uložena pouze lokálně na vašem zařízení
- Žádná synchronizace s cloudem, žádná analytika, žádné sledování
- Přímá HTTPS komunikace s burzami
- Volitelný biometrický zámek (otisk prstu/obličej)
- Open source — zkontrolujte kód sami

JAK TO FUNGUJE

1. Připojte burzu pomocí API klíčů (pouze oprávnění k obchodování)
2. Nastavte DCA plán: kryptoměna, částka, frekvence, strategie
3. AccBot automaticky provádí nákupy podle vašeho plánu

PODPOROVANÉ KRYPTOMĚNY

Bitcoin (BTC), Ethereum (ETH), Litecoin (LTC) a další — závisí na dostupných
obchodních párech vaší burzy.

DŮLEŽITÁ UPOZORNĚNÍ

AccBot je nástroj pro automatizaci DCA, není finančním poradcem. Minulá
výkonnost nezaručuje budoucí výsledky. Investice do kryptoměn nesou riziko.
Vždy provádějte vlastní výzkum. AccBot nemá přístup k výběru vašich prostředků
— API klíče by měly být vytvořeny pouze s oprávněním k obchodování.

Open source pod licencí MIT.
```

---

## 4. Privacy Policy

**Required** for apps that access the internet and use sensitive permissions.

Host at: `https://crynners.github.io/AccBot/privacy.html`

Key points to cover:
- **Data collected:** No personal data is collected or sent externally
- **Local storage:** API keys encrypted with AES-256-GCM via Android Keystore
- **Permissions:** Camera (QR scanning), Internet (exchange API), Background (DCA execution)
- **No analytics, no tracking, no ads**
- **Data deletion:** Uninstalling the app removes all data
- **Contact:** Link to GitHub issues

See `docs/privacy.html` for the full privacy policy page.

---

## 5. Sensitive Permissions Declarations

These permissions may trigger manual review by Google. Prepare justifications:

### `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

> **Justification:** AccBot is a DCA (Dollar Cost Averaging) automation tool that executes scheduled cryptocurrency purchases at user-defined intervals (15 minutes to weekly). Battery optimization restrictions can prevent timely execution of these financial transactions, potentially causing users to miss their scheduled purchases. The core functionality of the app depends on reliable background execution.

### `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`

> **Justification:** AccBot schedules precise DCA purchase executions at user-defined intervals. Financial transactions must execute at the scheduled time to maintain the user's dollar-cost averaging strategy. Inexact timing could result in missed purchases or incorrect averaging.

### `CAMERA`

> **Justification:** Used exclusively for QR code scanning to import exchange API keys and cryptocurrency wallet addresses. This provides a convenient and error-free way to enter long alphanumeric credentials. The camera is only activated when the user explicitly taps the QR scan button.

### `FOREGROUND_SERVICE_DATA_SYNC`

> **Justification:** The foreground service monitors active DCA plans and executes scheduled cryptocurrency purchases via exchange APIs. It must run continuously to ensure timely execution of the user's investment strategy. The service shows a persistent notification informing the user that DCA monitoring is active.

---

## 6. Data Safety Section

Fill in the Google Play Data Safety form:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS for all exchange API calls) |
| Do you provide a way for users to request that their data is deleted? | **Yes** (uninstall removes all data; in-app "Delete All Data" option in Settings) |
| Does this app collect data? | **No data is collected** |
| Does this app share data with third parties? | **No data is shared** |
| Does this app handle financial data? | App facilitates trades via user's own exchange accounts; no financial data is stored on external servers |

### Data encrypted at rest
- API credentials: AES-256-GCM via Android Keystore
- Transaction history: Local Room database (app-private storage)
- Settings/preferences: EncryptedSharedPreferences

---

## 7. Release Management

### 7.1 Testing Tracks (recommended progression)

1. **Internal testing** — up to 100 testers, immediate publishing
2. **Closed testing** — invite-only, 20+ testers recommended for meaningful feedback
3. **Open testing** — anyone can join, acts as public beta
4. **Production** — full public release

### 7.2 Update CI/CD for Signed AAB

Update `.github/workflows/android-release.yml` to produce a signed Android App Bundle:

```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > accbot-android/accbot-upload.jks

- name: Build signed AAB
  env:
    KEYSTORE_PATH: ${{ github.workspace }}/accbot-android/accbot-upload.jks
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: |
    cd accbot-android
    ./gradlew bundleRelease

- name: Upload AAB to release
  uses: softprops/action-gh-release@v1
  with:
    files: accbot-android/app/build/outputs/bundle/release/app-release.aab
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 7.3 Build Commands

```bash
# Local signed AAB build
./gradlew bundleRelease

# Local signed APK build (for direct distribution)
./gradlew assembleRelease
```

---

## 8. Financial App Compliance

Google has strict policies for financial and cryptocurrency apps:

- [ ] **No misleading claims** — do not promise returns or profits
- [ ] **Disclose risks** — cryptocurrency investments carry risk
- [ ] **Not a financial advisor** — clearly state AccBot is a DCA automation tool
- [ ] **Self-custody disclosure** — user is responsible for their own exchange accounts
- [ ] **Category compliance** — may need to declare "Cryptocurrency" category under Finance
- [ ] Review [Google Play Financial Services policy](https://support.google.com/googleplay/android-developer/answer/9876821)
- [ ] Review [Google Play Cryptocurrency policy](https://support.google.com/googleplay/android-developer/answer/12253906)

### Recommended App Description Disclaimers

Include in the store listing:
> "AccBot is not a financial advisor. Past performance does not guarantee future results. Cryptocurrency investments carry significant risk. Always do your own research."

---

## 9. Content Rating

Complete the IARC (International Age Rating Coalition) questionnaire in Play Console:

- **Violence:** None
- **Sexual content:** None
- **Language:** None
- **Controlled substances:** None
- **User interaction:** None (no user-to-user interaction)
- **Shares location:** No
- **In-app purchases:** No
- **Digital goods/services purchases:** No

Expected rating: **Rated for all ages** or **PEGI 3**

---

## 10. Pre-launch Checklist

### Account & Setup
- [ ] Developer account registered and verified
- [ ] Upload keystore generated and stored securely
- [ ] Signing config added to `app/build.gradle.kts`
- [ ] GitHub Actions secrets configured
- [ ] CI/CD updated for signed AAB builds

### Store Listing
- [ ] App name: "AccBot - Bitcoin DCA"
- [ ] Short description (EN + CS)
- [ ] Full description (EN + CS)
- [ ] Category: Finance
- [ ] Content rating questionnaire completed

### Graphics
- [ ] Hi-res icon (512 x 512 PNG)
- [ ] Feature graphic (1024 x 500 PNG)
- [ ] Phone screenshots (minimum 2, recommended 8)
- [ ] Tablet screenshots (optional)

### Compliance
- [ ] Privacy policy published at `https://crynners.github.io/AccBot/privacy.html`
- [ ] Permission declarations prepared (battery, alarm, camera, foreground service)
- [ ] Data safety form completed
- [ ] Financial compliance disclosures reviewed

### Release
- [ ] Internal testing track created
- [ ] Minimum 20 closed testers invited
- [ ] App tested on multiple devices/API levels
- [ ] ProGuard/R8 verified (no crashes in release build)
- [ ] Version code and version name updated

---

## Quick Reference

| Item | Value |
|---|---|
| Package name | `com.accbot.dca` |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| Category | Finance |
| Pricing | Free |
| License | MIT |
| Privacy policy URL | `https://crynners.github.io/AccBot/privacy.html` |
| Source code | `https://github.com/crynners/AccBot` |
