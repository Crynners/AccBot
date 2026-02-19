[![Community Chat](https://img.shields.io/badge/community%20chat-telegram-%235351FB)](https://t.me/accbotsupport)
[![MIT License](https://img.shields.io/apm/l/atomic-design-ui.svg?)](https://github.com/Crynners/AccBot/blob/main/LICENSE)
[![PR's Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat)](http://makeapullrequest.com)
[![GitHub Release](https://img.shields.io/github/release/crynners/accbot.svg?style=flat)](https://github.com/Crynners/AccBot/releases/latest)
[![Github All Releases](https://img.shields.io/github/downloads/crynners/accbot/total.svg)](https://github.com/Crynners/AccBot/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android)](https://github.com/Crynners/AccBot/releases/latest)

_Read README in [English <img src="https://www.countryflags.com/wp-content/uploads/united-states-of-america-flag-png-large.png" width=25 height=16 />](https://github.com/Crynners/AccBot/blob/main/README.md)_

# AccBot

**Stack Sats. Stay Humble.**

Open-source aplikace pro DCA do kryptoměn s plnou kontrolou nad klíči pro Android. iOS již brzy.

- **Plná kontrola** — API klíče nikdy neopustí vaše zařízení
- **7 podporovaných burz**
- **3 DCA strategie** — Klasická, ATH, Fear & Greed
- **Sledování portfolia** s interaktivními grafy
- **Žádná registrace, žádný cloud, žádná telemetrie**

# Proč DCA?

- [<img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 /> Post Josefa Tětka na facebookové skupině Bitcoin CZ & SK](https://www.facebook.com/groups/bitcoincz/posts/1758068064378420)
- [<img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 /> Video na Bitcoinovém kanálu](https://youtu.be/4y2VCEpiPQA)
- [<img src="https://www.countryflags.com/wp-content/uploads/united-states-of-america-flag-png-large.png" width=25 height=16 /> Even God Couldn't Beat Dollar-Cost Averaging](https://ofdollarsanddata.com/even-god-couldnt-beat-dollar-cost-averaging/)

# Proč AccBot?

Různých nástrojů na DCA kryptoměn existuje celá řada, ale většina z nich jsou uzavřené cloudové služby, kde se musíte zaregistrovat a svěřit jim své API klíče. Svá data předáváte cizímu serveru.

AccBot je jiný:

- **Plně decentralizovaný** — běží výhradně na vašem telefonu, žádný backend server
- **API přihlašovací údaje uloženy lokálně** s šifrováním Android Keystore (AES-256-GCM)
- **Přímá HTTPS komunikace** s burzami — žádný prostředník
- **Open source** — plně auditovatelný kód, žádná skrytá telemetrie

# Funkce

### Podporované burzy
[Coinmate](https://coinmate.io/) | [Binance](https://www.binance.com/) | [Kraken](https://www.kraken.com/) | [KuCoin](https://www.kucoin.com/) | [Bitfinex](https://www.bitfinex.com/) | [Huobi](https://www.huobi.com/en-us/) | [Coinbase](https://www.coinbase.com/)

### Podporované kryptoměny
BTC | ETH | SOL | ADA | DOT | LTC

### Podporované fiat měny
EUR | USD | GBP | CZK | USDT

### DCA strategie

| Strategie | Popis |
|---|---|
| **Klasická** | Nákup fixní částky v pravidelných intervalech |
| **ATH** | Nakupuje více, když je cena daleko od historického maxima, méně když je blízko |
| **Fear & Greed** | Nakupuje více, když je trh ve strachu, méně když je chamtivý |

### Portfolio a grafy
Interaktivní čárové grafy s rozpadem dle roku/měsíce, režim na šířku a přepínání mezi fiat/crypto denominací.

### Historie transakcí
Kompletní přehled transakcí s filtrováním dle kryptoměny, burzy a stavu. Řazení, vyhledávání, smazání swipem a export do CSV.

### Automatický výběr
Automatický výběr na vaši vlastní peněženku po nákupu, pokud je poplatek pod nastaveným limitem.

### QR skener
Skenování API přihlašovacích údajů a adres peněženek pomocí QR kódu nebo OCR rozpoznávání textu.

### Zabezpečení
- EncryptedSharedPreferences s Android Keystore (AES-256-GCM)
- Volitelný biometrický zámek aplikace
- Žádné cloudové zálohy citlivých dat

### Běh na pozadí
Spolehlivé DCA spouštění přes AlarmManager + WorkManager + Foreground Service s trvalou notifikací. Přežije režim Doze i restart zařízení.

### Notifikace
Potvrzení nákupů, upozornění na chyby a varování při nízkém zůstatku.

### Sandbox režim
Testování s testovacími sítěmi burz bez rizika ztráty reálných prostředků.

### Import
Import historie obchodů z API burzy (Coinmate, Binance) nebo CSV souboru.

### Lokalizace
Angličtina, čeština

# Instalace

1. Stáhněte si nejnovější APK z [GitHub Releases](https://github.com/Crynners/AccBot/releases/latest)
2. Nainstalujte a otevřete aplikaci
3. Projděte průvodcem nastavení:
   - **Zabezpečení** — nastavení biometrického zámku (volitelné)
   - **Burza** — připojení burzy pomocí API klíčů (sken QR nebo ruční zadání)
   - **První plán** — konfigurace prvního DCA plánu (kryptoměna, fiat, částka, interval, strategie)
   - **Hotovo** — AccBot začne automaticky akumulovat

> iOS verze je v přípravě.

# Sestavení ze zdrojového kódu

```bash
git clone https://github.com/Crynners/AccBot.git
cd AccBot/accbot-android
```

Otevřete v Android Studio, nebo sestavte z příkazové řádky:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew assembleDebug
```

Debug APK najdete v `app/build/outputs/apk/debug/app-debug.apk`.

# Přispívání

Pull requesty jsou vítány! Neváhejte otevřít [issue](https://github.com/Crynners/AccBot/issues) pro diskuzi nápadů nebo hlášení chyb.

# Donate

![heart_donate](https://user-images.githubusercontent.com/87997650/127650190-188e401a-9942-4511-847e-d1010628777a.png)

AccBota jsme se rozhodli poskytnout zcela zdarma, neboť chceme co nejvíce lidem umožnit co nejjednodušší a nejlevnější cestu k aplikaci strategie [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html). Věříme, že pravidelné spoření v Bitcoinu je tím nejlepším způsobem k zajištění do budoucna.

Pokud byste nás chtěli podpořit, rozhodně se tomu bránit nebudeme. Níže jsou uvedeny jednotlivé peněženky, kam nám můžete zaslat příspěvek třeba na pivo. :) Děkujeme <3

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

# Licence

[MIT](https://github.com/Crynners/AccBot/blob/main/LICENSE)
