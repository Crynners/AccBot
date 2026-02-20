# Průvodce vydáním aplikace AccBot na Google Play

Tento dokument slouží jako kompletní tahák pro vyplnění všech potřebných informací při vytváření a nastavování aplikace AccBot v **Google Play Console**. Obsahuje předpřipravené texty a odpovědi na dotazníky, abys mohl většinu věcí jen zkopírovat a vložit.

---

## 1. Základní nastavení (Create App)

Při zakládání nové aplikace v konzoli zadej následující:

- **App name (Název aplikace):** `AccBot`
- **Default language (Výchozí jazyk):** `Czech - čeština – cs` (Případně English, ale následující texty jsou primárně česky)
- **App or game:** `App` (Aplikace)
- **Free or paid:** `Free` (Zdarma)
- **Declarations:** Zaškrtni souhlas s *Developer Program Policies* a *US export laws*.

---

## 2. Hlavní záznam v obchodu (Main Store Listing)

Tyto texty budou veřejně viditelné na profilu aplikace v Google Play.

### Krátký popis (Short description) - max 80 znaků
Zkopíruj tento text:
```text
Open-source DCA bot pro krypto. Plná kontrola nad klíči, žádná registrace.
```

### Úplný popis (Full description) - max 4000 znaků
Zkopíruj tento text:
```text
AccBot je open-source aplikace pro pravidelné investování (DCA - Dollar Cost Averaging) do kryptoměn. Přináší vám plnou kontrolu nad vašimi API klíči a portfoliem přímo z vašeho Android zařízení. Žádná registrace, žádný cloud, žádná telemetrie. Svá data nesdílíte s žádným cizím serverem.

PROČ ACCBOT?
Většina DCA nástrojů jsou uzavřené cloudové služby, kterým musíte svěřit své API klíče. AccBot je plně decentralizovaný. Běží výhradně na vašem telefonu a komunikuje napřímo s burzami pomocí šifrovaného připojení. Vaše API klíče jsou bezpečně uloženy pouze ve vašem zařízení pomocí Android Keystore (AES-256-GCM).

HLAVNÍ FUNKCE:
• Plná kontrola a soukromí: Přihlašovací údaje i data zůstávají pouze u vás.
• Podpora 7 burz: Coinmate, Binance, Kraken, KuCoin, Bitfinex, Huobi, Coinbase.
• 3 pokročilé DCA strategie: 
  - Klasická (fixní částky v pravidelných intervalech)
  - ATH (nakupuje více, když je cena daleko od historického maxima)
  - Fear & Greed (nakupuje více, když je na trhu strach)
• Interaktivní sledování portfolia: Čárové grafy, rozpad podle roku/měsíce, přepínání fiat/crypto denominace.
• Automatické výběry: Možnost automatického přesunu nakoupených kryptoměn na vlastní hardwarovou peněženku (pokud jsou poplatky přijatelné).
• Běh na pozadí: Spolehlivé automatické nákupy bez nutnosti mít aplikaci otevřenou díky WorkManageru a AlarmManageru.
• Široká podpora měn: BTC, ETH, SOL, ADA, DOT, LTC vůči EUR, USD, GBP, CZK, USDT.
• Import historie: Možnost nahrání starých nákupů.
• Sandbox režim: Testování strategií bez rizika ztráty reálných peněz na testovacích sítích.

ZABEZPEČENÍ NA PRVNÍM MÍSTĚ:
Kromě šifrovaného úložiště nabízí AccBot volitelný biometrický zámek aplikace pro maximální ochranu vašich investic před neoprávněným přístupem.

AccBot je vytvořen komunitou pro komunitu. Kód je plně open-source a transparentní na našem GitHubu.
Stack Sats. Stay Humble.
```

### Záznam v obchodu v angličtině (English Store Listing)
Pokud chceš přidat i anglický překlad (velmi doporučeno), přidej jazyk "English (United States)" a použij tyto texty:

#### Krátký popis v angličtině (Short description) - max 80 znaků
```text
Open-source self-custody crypto DCA app. No registration, keys stay on device.
```

#### Úplný popis v angličtině (Full description) - max 4000 znaků
```text
AccBot is an open-source Dollar Cost Averaging (DCA) app for cryptocurrencies. It gives you full control over your API keys and portfolio directly from your Android device. No registration, no cloud, no telemetry. You don't share your data with any external server.

WHY ACCBOT?
Most crypto DCA tools are closed-source cloud services that require you to hand over your API keys. AccBot is different. It is fully decentralized, running entirely on your phone, and communicates directly with exchanges via a secure connection. Your API keys are safely stored only on your device using Android Keystore encryption (AES-256-GCM).

KEY FEATURES:
• Self-custody & privacy: API credentials and data never leave your device.
• 7 supported exchanges: Coinmate, Binance, Kraken, KuCoin, Bitfinex, Huobi, Coinbase.
• 3 advanced DCA strategies:
  - Classic (fixed amount at regular intervals)
  - ATH-Based (buys more when the price is far from the all-time high)
  - Fear & Greed (buys more when the market is fearful)
• Interactive portfolio tracking: Line charts, year/month breakdowns, fiat/crypto denomination toggle.
• Auto-withdrawal: Option to automatically transfer bought crypto to your self-custody hardware wallet (when fees are acceptable).
• Background execution: Reliable background purchases without needing the app open, using WorkManager and AlarmManager.
• Wide currency support: BTC, ETH, SOL, ADA, DOT, LTC against EUR, USD, GBP, CZK, USDT.
• History import: Ability to import your old trades.
• Sandbox mode: Test your strategies without risking real money using exchange testnets.

SECURITY FIRST:
In addition to encrypted storage, AccBot offers an optional biometric app lock for maximum protection of your investments against unauthorized access.

AccBot is built by the community for the community. The code is fully open-source and transparent on our GitHub.
Stack Sats. Stay Humble.
```

---

## 3. Obsah aplikace (App Content)

V menu vlevo úplně dole v sekci **Policy -> App content** (Zásady -> Obsah aplikace) je potřeba vyplnit sérii dotazníků. Zde jsou přesné odpovědi pro AccBot:

### Zásady ochrany soukromí (Privacy Policy)
- **Privacy policy URL:** 
  ```text
  https://crynners.github.io/AccBot/privacy.html
  ```
  *(Poznámka: Ujisti se, že tento odkaz funguje a je dostupný přes GitHub Pages)*

### Přístup k aplikaci (App access)
- **Odpověď:** Zvol možnost `All functionality is available without special access` (Všechny funkce jsou dostupné bez zvláštního přístupu).
- *Vysvětlení:* AccBot nevyžaduje pro spuštění vytváření uživatelských účtů nebo hesel na vašem vlastním serveru. Spojení s burzou si uživatel zajišťuje sám zadáním vlastních API klíčů, což nepředstavuje uzavřený systém vaší aplikace.

### Reklamy (Ads)
- **Odpověď:** Zvol `No, my app does not contain ads` (Ne, moje aplikace neobsahuje reklamy).

### Cílová skupina a obsah (Target audience and content)
- **Cílová věková skupina:** Zvol **POUZE** věkovou kategorii `18 and over` (18 a více). 
- *Vysvětlení:* Kryptoměnové a finanční aplikace nesmí cílit na děti. Pokud bys vybral i mladší, bude Google požadovat další přísná opatření.
- **Appeal to children (Může zaujmout děti):** `No` (Aplikace by nemohla neúmyslně zaujmout děti).

### Zpravodajské aplikace (News apps)
- **Odpověď:** `No` (Ne).

### Aplikace na sledování kontaktů a stavu COVID-19 (COVID-19 contact tracing)
- **Odpověď:** Zvol možnost, že aplikace není veřejně dostupná aplikace pro sledování kontaktů ani informování o stavu COVID-19.

### Finanční funkce (Financial features)
Jelikož je to krypto aplikace, musíte vyplnit tuto sekci.
- **Funkce:** Zaškrtni `Cryptocurrency and non-custodial wallets` (Kryptoměny a non-custodial peněženky), a pravděpodobně také `Trading and investing` (Obchodování a investování).
- *Poznámka:* Pokud Google bude chtít v rámci review detailní doložení finanční licence, odkažte se v doplňujícím políčku na to, že aplikace je pouze open-source "API klient" (nástroj), neprovádí úschovu peněz (non-custodial) a pouze se jménem uživatele připojuje přes jeho API klíče na veřejné licencované burzy (Coinmate, Binance atd.). Aplikace sama o sobě není burza ani broker.

---

## 4. Zabezpečení údajů (Data Safety)

Toto je často nejtěžší část pro vývojáře, ale jelikož je AccBot navržen jako "privacy-first", vyplňování je velmi snadné a Google Play to má rád.

1. **Sběr a sdílení dat (Data collection and security):**
   - **Does your app collect or share any of the required user data types?** Zvol `No` (Ne).
   - *Vysvětlení:* AccBot všechna data (API klíče, historii transakcí) ukládá a šifruje lokálně na zařízení (EncryptedSharedPreferences). Komunikuje **PŘÍMO** s burzami a nesbírá žádná osobní data ani je neposílá na žádné jiné (vaše) servery. Nevyužívá se ani Firebase Analytics, ani Crashlytics. Tvá aplikace data nesbírá ani nesdílí s třetími stranami.
2. Pokud se dotazník zeptá, zda jsou data šifrována při přenosu (Data encrypted in transit): Zvol `Yes` (Ano – vše jde přes standardní HTTPS volání na API burz).

Tímto v podstatě prohlašujete, že jste zcela bezpečná aplikace neshromažďující data.

---

## 5. Záznam v obchodu - Další informace a kontakty (Store settings)

V sekci **Store presence -> Store settings** vyplň:

- **App category (Kategorie aplikace):** `Finance` (případně Tools/Nástroje, ale Finance je přesnější).
- **Tags (Štítky):** Vyhledej a přidej štítky jako `Finance`, `Cryptocurrency` (Kryptoměny) nebo `Investing` (Investice). Maximum je 5 štítků.
- **Contact details (Kontaktní údaje):**
  - **Email:** Kontaktní email na vývojáře (např. tvůj e-mail, případně něco jako `support@accbot.io`).
  - **Website:** (Volitelné) Doporučuji vložit URL GitHub repozitáře: `https://github.com/Crynners/AccBot`

---

## 6. Grafické podklady (Graphics Assets)

Připrav si následující obrázky k nahrání do **Main Store Listing**:

- **Ikona aplikace:** 
  - Velikost: 512 x 512 px (PNG nebo JPEG). 
  - *Tip:* Ve složce repozitáře máte `accbot-android/store-assets/icon-512.svg`. Pro Play Console ho musíte vyexportovat (např. ve Figmě, Inkscapu) do PNG formátu.
- **Obrázek záhlaví (Feature graphic):** 
  - Velikost: 1024 x 500 px. 
  - Bude zobrazen na vrchu profilu aplikace. Měl by obsahovat logo a hezké pozadí.
- **Screenshoty z telefonu (Phone screenshots):** 
  - Minimálně 2 snímky obrazovky. Doporučený poměr stran je 16:9 nebo 9:16 (např. 1080 x 1920 px).
  - *Tip:* Nahraj ideálně 4-5 screenů: (1) Přehled portfolia (Graf), (2) Nastavení DCA plánu, (3) Seznam transakcí, (4) Nastavení burzy (API).
- **Screenshoty z tabletů (Tablet screenshots):**
  - Můžeš nahrát stejné screenshoty jako pro telefon do záložek pro 7-palcové a 10-palcové tablety, pokud nemáte speciální tabletový design.

---

## 7. Checklist před vydáním (Publishing Checklist)

Než aplikaci reálně pošleš na "Production" (nebo "Internal Testing"), zkontroluj si tyto body:

- [ ] Máš vygenerovaný podepsaný **App Bundle (.aab soubor)**. O to se může postarat existující Github Action `android-release.yml` (po nahrání Keystore a hesel do GitHub Secrets), nebo si ho vygeneruješ ručně v Android Studiu přes *Build -> Generate Signed Bundle / APK*.
- [ ] Vytvořil jsi "Release" v sekci Production (nebo Testing) a nahrál jsi tento .aab soubor.
- [ ] Vyplnil jsi kompletně **Main Store Listing** (včetně české lokalizace a všech výše uvedených textů a obrázků).
- [ ] Prošel jsi s čistým štítem všechny dotazníky v sekci **App Content** (Policy).
- [ ] Tvá URL pro zásady ochrany soukromí (`privacy.html`) je aktivní a veřejně načítatelná.

Jakmile máš vše zaškrtnuté, v přehledu vlevo najdi tlačítko pro **Odeslat ke kontrole** (Send for review). Schválení finanční / krypto aplikace může Googlu poprvé trvat až 7 pracovních dnů.
