# AccBot — Twitter/X vlákno pro Ankap piráti

---

Už delší dobu nás štvalo, že když chcete pravidelně nakupovat Bitcoin, musíte buď věřit nějaké službě, která to dělá za vás (a má vaše API klíče), nebo si to ručně hlídat sami.

Tak jsme udělali AccBot — open-source appku, která DCA řeší přímo na vašem telefonu. DCA patří vám, ne burze ani žádné třetí straně. 🧵👇

---

Jak to funguje? Připojíte si burzu přes API, nastavíte strategii a AccBot nakupuje automaticky podle vašeho plánu. Celé to běží lokálně na zařízení — žádná data neodesíláme na žádný svůj server, komunikace probíhá výhradně mezi vaším telefonem a burzou.

---

Proč nám na tom záleží: většina DCA služeb funguje tak, že jim dáte API klíče a oni nakupují za vás. Jenže to znamená, že někde na serveru leží hromadně klíče tisíců uživatelů. Jeden breach a je problém.

U AccBotu klíče nikdy neopustí vaše zařízení. Jsou šifrované přes Android Keystore (AES-256-GCM) a komunikace jde přímo na burzu přes HTTPS. Žádný mezičlánek.

---

Není to žádná novinka — první verzi AccBotu jsme provozovali roky přes Azure Functions a fungovala spolehlivě. Ale pořád to znamenalo mít někde server.

Nová verze je nativní mobilní appka. Žádný cloud, žádná závislost. Prostě to běží na telefonu.

👉 https://crynners.github.io/AccBot/#cs

---

Momentálně jsou na výběr 3 DCA strategie:

• Classic — prostě pravidelný nákup
• ATH-Based — nakupuje víc, když je cena dál od maxima
• Fear & Greed — nakupuje podle sentimentu trhu

Podporujeme víc burz a seznam postupně rozšiřujeme.

---

Jo, některé burzy samy nabízí pravidelné nákupy. Jenže s AccBotem si můžete štosovat satoshi každých 15 minut, máte přehledné grafy z celého období a veškerá data zůstávají u vás — dají se i zálohovat. Nemusíte se spoléhat na to, že vám třetí strana data nesmaže nebo nezmění podmínky.

---

Celé je to open-source pod MIT licencí. Žádné skryté poplatky — platíte jen fee na burze jako při normálním obchodu. Žádná telemetrie, žádné reklamy, žádný catch.

Kód si můžete projít, forkovat, upravit. Nebo přispět — jakákoliv pomoc je vítaná.

---

Jasně, bez KYC burzy se neobejdete — to je kompromis, který zatím existuje. Ale nejste závislí na jedné jediné burze, můžete kdykoliv zmigrovat jinam a data o svých transakcích máte neustále u sebe.

Je to kompromis mezi pohodlím a kontrolou — #OwnYourDCA. Vaše klíče, vaše data, vaše pravidla — a když se vám něco přestane líbit, přepojíte se jinam bez ztráty historie.

---

Mimochodem, koukáme na Evolu (@evaborzen) — local-first platformu pro sync dat bez serveru. Filozoficky to sedí k AccBotu úplně přesně.

@steidacz — je Evolu ready na production? Rádi bychom přidali sync napříč zařízeními.

---

Jestli vás to zaujalo:

⭐ https://crynners.github.io/AccBot/#cs

Na stránce najdete odkaz na Google Play i na zdrojový kód na GitHubu. Je to projekt pro komunitu, žádná firma za tím nestojí — jakákoliv pomoc je vítaná.

#Bitcoin #OwnYourDCA #OpenSource #SelfCustody

---

**Poznámky:**
- Ověřit aktuální handle @steidacz a @evaborzen před publikací
- K tweetu 1 přidat screenshot appky nebo logo
- Ideální čas: pracovní den, 10:00–12:00 CET
