# AccBot Android - Native Kotlin DCA App

Native Android aplikace pro automatické DCA (Dollar Cost Averaging) nákupy kryptoměn.

## Klíčové vlastnosti

- **100% Nativní Kotlin** - Maximální výkon a plný přístup k Android API
- **Decentralizovaný** - Žádné API klíče nejsou ukládány na třetí strany
- **Spolehlivé pozadí** - WorkManager + Foreground Service pro garantované spuštění
- **Šifrované úložiště** - API credentials jsou šifrovány pomocí Android Keystore
- **Offline-first** - Veškerá data zůstávají na zařízení

## Podporované burzy

- Coinmate (CZK, EUR)
- Binance
- Kraken
- KuCoin
- Bitfinex
- Huobi
- Coinbase

## Architektura

```
accbot-android/
├── app/
│   └── src/main/
│       ├── java/com/accbot/dca/
│       │   ├── data/          # Room database, DAO, Encrypted storage
│       │   ├── domain/        # Business models
│       │   ├── exchange/      # Exchange API implementations
│       │   ├── presentation/  # Jetpack Compose UI
│       │   ├── service/       # Foreground service, notifications
│       │   ├── worker/        # WorkManager DCA worker
│       │   └── di/            # Hilt dependency injection
│       └── res/               # Android resources
└── gradle/                    # Gradle wrapper
```

## Technologie

- **Kotlin 1.9** - Moderní Kotlin s coroutines
- **Jetpack Compose** - Deklarativní UI
- **Room** - Lokální SQLite databáze
- **Hilt** - Dependency injection
- **WorkManager** - Spolehlivé background tasks
- **EncryptedSharedPreferences** - Šifrované úložiště
- **OkHttp + Retrofit** - HTTP klient

## Background Execution

Aplikace využívá několik mechanismů pro spolehlivé spouštění DCA:

1. **WorkManager** - Periodic work s 15minutovým intervalem (minimum Android)
2. **Foreground Service** - Persistent notification pro vyšší prioritu
3. **Battery Optimization Exemption** - Žádost o vyjmutí z úspory baterie
4. **Boot Receiver** - Automatický restart po restartu zařízení

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Požadavky

- Android Studio Hedgehog (2023.1.1) nebo novější
- JDK 17
- Android SDK 34
- Kotlin 1.9.22

## Bezpečnost

- API credentials jsou šifrovány pomocí Android Keystore (AES-256-GCM)
- Databáze a preferences jsou vyloučeny z cloud backup
- Žádná data nejsou odesílána na externí servery
- Veškerá komunikace probíhá přímo s burzami přes HTTPS

## Licence

MIT License - viz hlavní repozitář AccBot
