# Úvod
Vítejte na stránkách AccBota. AccBot je open-source akumulační bot, který v pravidelných intervalech po malých částkách v Kč nakupuje [BTC](https://cs.wikipedia.org/wiki/Bitcoin) na burze [Coinmate](https://coinmate.io/) dle strategie [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html).

# Jednoduchý popis fungování bota
* Nakupuje uživatelem definovanou částku v českých korunách _(typicky desítky Kč)_ jednou za několik hodin _(typicky jednou za x hodin -> 1x za 2h / 1x za 4h / 1x za 8h)_.
* Běží autonomně nutnosti jej nějak v čase spravovat, je zapotřebí si pouze hlídat stav svého Kč účtu a pravidelně jej na Coinmate doplňovat _(např. jednou za měsíc)_.
* **Náklady na provoz jsou prakticky nulové** (vychází to cca na 0.04 $ / měsíčně); bot je implementován zatím jako [Azure function](https://azure.microsoft.com/cs-cz/services/functions/), která se spouští v pravidelných intervalech a celé řešení je tedy hostované na [Azure](https://azure.microsoft.com/cs-cz/). 
* (Volitelná funkcionalita) Po každém nákupu Vás informuje na Telegramovém kanále o tom, za jakou částku nakoupil. Tuto informaci doplní o statistiky, jaká je aktuální průměrná akumulovaná cena, etc. Viz příklad:
  * ![image](https://user-images.githubusercontent.com/87997650/127355720-fe73c0b5-5fd4-4d31-98dc-b569975f8a9e.png)
* (Volitelná funkcionalita) Pokud je naakumulované dostatečné množství BTC, pak pokud je widthrawal poplatek z celkové částky menší, než uživatelsky stanovený limit (např. 0.1 %), bot pošle naakumulované množství BTC z burzy do definované BTC peněženky (poznámka: pokud chcete využívat tuto funkcionalitu, doporučujeme povolit API odeslání pouze na Vaši konkrétní BTC peněženku, viz nastavení při vytváření API klíče na Coinmate)
  * ![image](https://user-images.githubusercontent.com/87997650/127356371-6a9d1493-55f0-41cc-ab03-4a67cf610f42.png)

# Postup instalace
1. **Založte si účet na [Azure](https://azure.microsoft.com/cs-cz/)** (účet je zdarma; platí se pouze za využité prostředky, které vychází na cca 0.04$ / měsíc)
2. **Založte si účet na [Coinmate](https://coinmate.io/)** (účet je zdarma; k tomu, abyste mohli na burzu zasílat fiat, je zapotřebí provést ověření [KYC](https://en.wikipedia.org/wiki/Know_your_customer))
3. **Na Coinmate si [vygenerujte API klíče](https://coinmate.io/blog/using-the-coinmate-io-api/)** (aby měl BOT přístup k prostředkům na burze a mohl provádět svoji akumulační činnost)
4. Stáhněte si ZIP s powershell skriptem, který Vám automaticky do Azure prostředí nainstaluje AccBota
5. ZIP rozbalte do Vašeho souborového systému
6. Upravte soubor **local.settings.json**, kde je potřeba vyplnit proměnné:
  ```
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "dotnet",
    "Currency": "BTC",
    "ChunkSize": 30,
    "WithdrawalEnabled": false, //true - zapnout pravidelné withdrawal, false - vypnout pravidelné withdrawal
    "WithdrawalAddress": null, //BTC adresa, kam chcete zasílat pravidelně withdrawal
    "TelegramChannel": "@43", //telegram kanál, na který chcete pravidelně zasílat informace o nákupech a dalších statistikách
    "TelegramBot": "44095555WBM8Hk", //jméno telegram bota, který jste si vytvořili přes BotFather
    "CosmosDbEndpointUri": "https://.docuiments.azure.com:443/",
    "CosmosDbPrimaryKey": "54443==",
    "CoinMateCredentials_ClientId": 44, //Coinmate API Client ID
    "CoinMateCredentials_PublicKey": "44-4", //Coinmate Public key
    "CoinMateCredentials_PrivateKey": "45555" //Coinmate Private key
  }
}
  ```
