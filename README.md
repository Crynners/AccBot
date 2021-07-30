# Úvod
Vítejte na stránkách AccBota. AccBot je open-source akumulační bot, který v pravidelných intervalech po malých částkách v Kč nakupuje [BTC](https://cs.wikipedia.org/wiki/Bitcoin) na burze [Coinmate](https://coinmate.io/) dle strategie [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html).

# Jednoduchý popis fungování bota
* Nakupuje uživatelem definovanou částku v českých korunách _(typicky desítky Kč)_ každých uživatelsky definovaných hodin _(ideálně dělitelných 24, aby nakupoval vždy ve stejný čas, tedy např. -> každou hodinu, 1x za 2h, 1x za 4h, 1x za 8h, etc.)_.
* Běží autonomně nutnosti jej nějak v čase spravovat, je zapotřebí si pouze hlídat stav svého Kč účtu a pravidelně jej na Coinmate doplňovat _(např. jednou za měsíc)_.
* **Náklady na provoz jsou prakticky nulové** (vychází to cca na 0.04 $ / měsíčně); bot je implementován zatím jako [Azure function](https://azure.microsoft.com/cs-cz/services/functions/), která se spouští v pravidelných intervalech a celé řešení je tedy hostované na [Azure](https://azure.microsoft.com/cs-cz/). 
* (Volitelná funkcionalita) Po každém nákupu Vás informuje na Telegramovém kanále o tom, za jakou částku nakoupil. Tuto informaci doplní o statistiky, jaká je aktuální průměrná akumulovaná cena, etc. Viz příklad:
  * ![image](https://user-images.githubusercontent.com/87997650/127355720-fe73c0b5-5fd4-4d31-98dc-b569975f8a9e.png)
* (Volitelná funkcionalita) Pokud je naakumulované dostatečné množství BTC, pak pokud je widthrawal poplatek z celkové částky menší, než uživatelsky stanovený limit (např. 0.1 %), bot pošle naakumulované množství BTC z burzy do definované BTC peněženky (poznámka: pokud chcete využívat tuto funkcionalitu, doporučujeme povolit API odeslání pouze na Vaši konkrétní BTC peněženku, viz nastavení při vytváření API klíče na Coinmate)
  * ![image](https://user-images.githubusercontent.com/87997650/127356371-6a9d1493-55f0-41cc-ab03-4a67cf610f42.png)

# Prerekvizity
1. **Nainstalovaný [PowerShell](https://docs.microsoft.com/cs-cz/powershell/scripting/install/installing-powershell?view=powershell-7.1)**
2. **Nainstalovaný [Azure CLI](https://docs.microsoft.com/cs-cz/cli/azure/install-azure-cli)**
3. **Založený účet na burze [Coinmate](https://coinmate.io/)** (účet je zdarma; k tomu, abyste mohli na burzu zasílat fiat, je zapotřebí provést ověření [KYC](https://en.wikipedia.org/wiki/Know_your_customer))
4. **Založený účet na [Azure](https://azure.microsoft.com/cs-cz/)** (účet je zdarma; platí se pouze za využité prostředky, které vychází na cca 0.04$ / měsíc)

# Postup instalace
1. Na Coinmate si [vygenerujte API klíče](https://coinmate.io/blog/using-the-coinmate-io-api/) (aby měl BOT přístup k prostředkům na burze a mohl provádět svoji akumulační činnost). Do poznámkového bloku si zapište vygenerovaný ClientId, PublicKey a PrivateKey -> budete je potřebovat v bodu 5.
2. Stáhněte si [ZIP v aktuálním RELEASE](https://github.com/Crynners/AccBot/releases/tag/v1.0), který obsahuje instalační PowerShell skript a zbuilděného bota.
3. ZIP z předchozího bodu rozbalte kamkoliv do Vašeho souborového systému
4. V poznámkovém bloku (nebo jiném textovém editoru) otevřte soubor **install_script.ps1**
5. Upravte proměnné v sekci **### USER-DEFINED VARIABLES ###**
```
### USER-DEFINED VARIABLES ###
##############################

# Jméno, které se zobrazuje v Telegram notifikacích
$Name='anonymous'

# Měnový pár, který na Coinmate chcete nakupovat
$Currency='BTC'

# Velikost chunku, který chcete pravidelně nakupovat (MINIMUM: 26)
$ChunkSize='26'

# Jednou za kolik hodin chcete pravidelně nakupovat BTC
$HourDivider='1'

# Příznak, zdali chcete povolit Withdrawal v případě, že je fee menší než 0.1% (POVOLENÉ HODNOTY: true / false)
$WithdrawalEnabled='false'

# Adresa peněženky pro withdraw (aplikuje se pouze pokud WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# Adresa telegram kanálu, do kterého chcete dostávat notifikace (ve formátu @NázevKanálu)
$TelegramChannel='@channel_name'

# Privátní klíč telegram bota (POZOR, bot musí být členem kanálu výše)
$TelegramBot='<telegram_bot_hash>'

# ClientId z Coinmate API
$CoinMateCredentials_ClientId='111'

# Public key z Coinmate API
$CoinMateCredentials_PublicKey='XXX'

# Private key z Coinmate API
$CoinMateCredentials_PrivateKey='XXX'

##############################
```
6. Uložte soubor a spusťte zmiňovaný skript, který Vám automaticky připraví všechny potřebné resources na Azure. Na začátku by mělo vyskočit i okno s přihlášením do Azure portal. **POZOR: Instalace trvá několik minut, vyčkejte prosím na její dokončení.** Na konci by se měla objevit následující hláška:

![image](https://user-images.githubusercontent.com/87997650/127630772-334fb454-d1f9-4847-ae5a-0ce3757800cc.png)
