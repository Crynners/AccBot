[![Community Chat](https://img.shields.io/badge/community%20chat-telegram-%235351FB)](https://t.me/accbotsupport)
[![MIT License](https://img.shields.io/apm/l/atomic-design-ui.svg?)](https://github.com/Crynners/AccBot/blob/main/LICENSE)
[![PR's Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat)](http://makeapullrequest.com)
[![GitHub Release](https://img.shields.io/github/release/crynners/accbot.svg?style=flat)](https://github.com/Crynners/AccBot/releases/latest)
[![Github All Releases](https://img.shields.io/github/downloads/crynners/accbot/total.svg)](https://github.com/Crynners/AccBot/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android)](https://github.com/Crynners/AccBot/releases/latest)

_Read README in [Czech <img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 />](https://github.com/Crynners/AccBot/blob/main/README.cs.md)_

# AccBot

**Stack Sats. Stay Humble.**

Open-source, self-custody crypto DCA app for Android. iOS coming soon.

- **Self-custody** — API keys never leave your device
- **7 exchanges** supported
- **3 DCA strategies** — Classic, ATH-Based, Fear & Greed
- **Portfolio tracking** with interactive charts
- **No registration, no cloud, no telemetry**

# Why DCA?

- [<img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 /> Post by Josef Tetek in Bitcoin CZ & SK community](https://www.facebook.com/groups/bitcoincz/posts/1758068064378420)
- [<img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 /> Video on "Bitcoinovej kanal"](https://youtu.be/4y2VCEpiPQA)
- [<img src="https://www.countryflags.com/wp-content/uploads/united-states-of-america-flag-png-large.png" width=25 height=16 /> Even God Couldn't Beat Dollar-Cost Averaging](https://ofdollarsanddata.com/even-god-couldnt-beat-dollar-cost-averaging/)

# Why AccBot?

There are many crypto DCA tools out there, but most are closed-source cloud services that require registration and collect your data. You hand over your API keys and trust someone else's server.

AccBot is different:

- **Fully decentralized** — runs entirely on your phone, no backend server
- **API credentials stored locally** with Android Keystore encryption (AES-256-GCM)
- **Direct HTTPS communication** with exchanges — no middleman
- **Open source** — fully auditable code, no hidden telemetry

# Features

### Supported Exchanges
[Coinmate](https://coinmate.io/) | [Binance](https://www.binance.com/) | [Kraken](https://www.kraken.com/) | [KuCoin](https://www.kucoin.com/) | [Bitfinex](https://www.bitfinex.com/) | [Huobi](https://www.huobi.com/en-us/) | [Coinbase](https://www.coinbase.com/)

### Supported Crypto
BTC | ETH | SOL | ADA | DOT | LTC

### Supported Fiat
EUR | USD | GBP | CZK | USDT

### DCA Strategies

| Strategy | Description |
|---|---|
| **Classic** | Buy a fixed amount at regular intervals |
| **ATH-Based** | Buy more when price is far from all-time high, less when near it |
| **Fear & Greed** | Buy more when the market is fearful, less when greedy |

### Portfolio & Charts
Interactive line charts with drill-down by year/month, landscape mode, and fiat/crypto denomination toggle.

### Transaction History
Full transaction log with filtering by crypto, exchange, and status. Sort, search, swipe-to-delete, and export to CSV.

### Auto-Withdrawal
Automatic withdrawal to your self-custody wallet after purchases when the fee is below your configured threshold.

### QR Scanner
Scan API credentials and wallet addresses using QR code or OCR text recognition.

### Security
- EncryptedSharedPreferences with Android Keystore (AES-256-GCM)
- Optional biometric app lock
- No cloud backup of sensitive data

### Background Execution
Reliable DCA execution via AlarmManager + WorkManager + Foreground Service with persistent notification. Survives Doze mode and device reboots.

### Notifications
Purchase confirmations, error alerts, and low balance warnings.

### Sandbox Mode
Test with exchange testnets without risking real funds.

### Import
Import trade history from exchange API (Coinmate, Binance) or CSV file.

### Localization
English, Czech

# Installation

1. Download the latest APK from [GitHub Releases](https://github.com/Crynners/AccBot/releases/latest)
2. Install and open the app
3. Follow the onboarding wizard:
   - **Security** — set up biometric lock (optional)
   - **Exchange** — connect your exchange with API keys (scan QR or enter manually)
   - **First Plan** — configure your first DCA plan (crypto, fiat, amount, interval, strategy)
   - **Done** — AccBot starts accumulating automatically

> iOS version is in preparation.

# Building from Source

```bash
git clone https://github.com/Crynners/AccBot.git
cd AccBot/accbot-android
```

Open in Android Studio, or build from the command line:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

# Contributing

Pull requests are welcome! Feel free to open an [issue](https://github.com/Crynners/AccBot/issues) to discuss ideas or report bugs.

# Donate

![heart_donate](https://user-images.githubusercontent.com/87997650/127650190-188e401a-9942-4511-847e-d1010628777a.png)

AccBot is completely free. We believe regular saving in Bitcoin is the best way to secure your future, and we want to make the DCA strategy as accessible as possible.

If you'd like to support us, we certainly won't stop you. Below are wallets where you can send us a donation — for example, for a beer. :) Thank you <3

- **BTC**: bc1q2hz79m4csklecqgusu9e2yjnrr6e9ca6nhu0at
     - <img src="https://user-images.githubusercontent.com/87997650/127651099-d9e1b381-adcf-46a5-9d17-59f87176304d.png" width="150" height="150" />
- **LTC**: LTXdCFBYHgVLa8cBNBqwEvaQLi8tENY5R3
     - <img src="https://user-images.githubusercontent.com/87997650/127651223-0abe025d-950b-445e-8196-7c113853b313.png" width="150" height="150" />
- **XMR**: 49QBko3UdegAkx6g8foqjs9efQD6rrhsPEoTqP9HmA2LCUZsJ8xBD2JZSMEdzhA5NJ9SrVhzu2uJXRUvL2kAiV45LyDBCUt
     - <img src="https://user-images.githubusercontent.com/87997650/127651801-cc35dfc0-f1ce-4dd0-ae0f-211fab41e2fb.png" width="150" height="150" />
- **DOGE**: DR9mEaVLmx3gxqiqffwYQcLsT1upRL3xe9
     - <img src="https://user-images.githubusercontent.com/87997650/127651630-2bb06de7-3b7a-42af-86b0-8b8fa0a6d59a.png" width="150" height="150" />
- **ETH**: 0x8A944bcb5919dF04C5207939749C40A61f88188C
     - <img src="https://user-images.githubusercontent.com/87997650/127653313-e989e607-f1db-40e9-a341-7cbdbd9fdfd0.png" width="150" height="150" />
- **DOT**: 15sBCVyWu5Gy9VnzQpid4ggC1MmguBBd1xotUVbsbbRWddun
     - <img src="https://user-images.githubusercontent.com/87997650/127651761-6484c9f4-547c-475e-a5ec-2029e6ee1699.png" width="150" height="150" />
- **BNB**: bnb1lwcgq8emrjgptxg4hm37d5tf2yunrph842awrh
     - <img src="https://user-images.githubusercontent.com/87997650/127651542-1fa0b32b-ed30-4a9a-b1cd-1622ef1044cf.png" width="150" height="150" />
- **ADA**: addr1qxgfp7xf8rpg7laque78queavpfdztajgl3hr8kuanuqgdysjruvjwxz3al6penuwpen6czj6yhmy3lrwx0dem8cqs6qr8y8fj
     - <img src="https://user-images.githubusercontent.com/87997650/127651500-df50eaee-15aa-415e-8a0b-044f22d89493.png" width="150" height="150" />

# License

[MIT](https://github.com/Crynners/AccBot/blob/main/LICENSE)
