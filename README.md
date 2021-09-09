[![MIT License](https://img.shields.io/apm/l/atomic-design-ui.svg?)](https://github.com/Crynners/AccBot/blob/main/LICENSE)
[![PR's Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat)](http://makeapullrequest.com) 
[![GitHub Release](https://img.shields.io/github/release/crynners/accbot.svg?style=flat)](https://github.com/Crynners/AccBot/releases/latest)
[![Github All Releases](https://img.shields.io/github/downloads/crynners/accbot/total.svg)](https://github.com/Crynners/AccBot/releases/latest)

_Přečíst README v [češtině <img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 />](https://github.com/Crynners/AccBot/blob/main/README.cs.md)_ 

# Introduction
Welcome to AccBot. AccBot is an open-source accumulation bot that buys [BTC](https://cs.wikipedia.org/wiki/Bitcoin) _(possibly LTC, ETH, XMR or DASH and others)_ at regular intervals in small amounts of FIAT _(CZK, EUR, USD and others)_ on the most popular exchanges (see the list below) according to the [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html) strategy.

<a name="exchangelist"></a>
List of supported exchanges:
 - [Coinmate](https://coinmate.io/)
 - [Huobi](https://www.huobi.com/en-us/)
 - [Kraken](https://www.kraken.com/)
 - [Binance](https://www.binance.com/)
 - [FTX](https://ftx.com/)
 - [Bittrex](https://global.bittrex.com/)
 - [Bitfinex](https://www.bitfinex.com/)
 - [Coinbase](https://www.coinbase.com/)
 - [KuCoin](https://www.kucoin.com/)

# Why DCA?
 - [<img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 /> 📝Explanation in Facebook Bitcoin Community CZ & SK by Josef Tětek ](https://www.facebook.com/groups/bitcoincz/posts/1758068064378420)
 - [<img src="https://cdn.countryflags.com/thumbs/czech-republic/flag-400.png" width=25 height=16 /> 🎥Video on "Bitcoinovej kanál"](https://youtu.be/4y2VCEpiPQA)
 - [<img src="https://www.countryflags.com/wp-content/uploads/united-states-of-america-flag-png-large.png" width=25 height=16 /> 📝Even God Couldn’t Beat Dollar-Cost Averaging](https://ofdollarsanddata.com/even-god-couldnt-beat-dollar-cost-averaging/)

# Why AccBot?
There are many different bots for buying cryptocurrencies, but quite often they are closed applications where you need to register, fill in API keys and the bot then buys/trade for you according to the rules. The downside is that the app probably collects data and statistics about your purchases, the code is closed, so you don't have full control over what the bot will actually do.
Our solution is fully decentralized in that everyone installs their own bot in their own environment. Thus, individual user bots are fully decoupled and no data is collected centrally anywhere. Everyone's statistics are stored in their own DB, which are then dumped at will to private Telegram channels.

# A simple description of how the AccBot works
* It buys a user-defined amount in Czech crowns _(typically tens of CZK)_ / Euros _(typically units of Euros)_ every user-defined hours _(ideally divisible by 24, so that it always buys at the same time, e.g. -> every hour, 1x in 2h, 1x in 4h, 1x in 8h, etc.)_.
* It runs autonomously without the need to manage it somehow over time, you only need to keep track of your CZK account balance and replenish it regularly on the exchange _(e.g. once a month)_.
* **Operating costs are practically zero** (it comes out to about 0.04 €/month for Azure hosting); the bot is implemented as [Azure function](https://azure.microsoft.com/cs-cz/services/functions/) for now, which runs at regular intervals and the whole solution is thus hosted on [Azure](https://azure.microsoft.com/cs-cz/). 
* (Optional functionality) After each purchase, the Telegram channel informs you of the amount of the purchase. This information is supplemented with statistics, what is the current average accumulated price, etc. See example:
  * ![image](https://user-images.githubusercontent.com/87997650/127355720-fe73c0b5-5fd4-4d31-98dc-b569975f8a9e.png)
* (Optional functionality) If a sufficient amount of BTC is accumulated, then if the withdrawal fee from the total amount is less than the user defined limit (e.g. 0.1%), the bot will send the accumulated amount of BTC from the exchange to the defined BTC wallet (note: if you want to use this functionality, we recommend enabling API send only to your specific BTC wallet, see settings when creating an API key on Coinmate)
  * ![image](https://user-images.githubusercontent.com/87997650/127356371-6a9d1493-55f0-41cc-ab03-4a67cf610f42.png)

# Pre-requisites
1. **Installed [PowerShell](https://docs.microsoft.com/cs-cz/powershell/scripting/install/installing-powershell?view=powershell-7.1)**
2. **Installed [Azure CLI](https://docs.microsoft.com/cs-cz/cli/azure/install-azure-cli)**
3. **An established account on [any supported exchange](#exchangelist)**
    - If you would like to support us and sign up through our referral link, you can click on the banners below

    <a href="https://coinmate.io?referral=ZWw4NVlXbDRVbTFVT0dKS1ZHczBZMXB1VEhKTlVRPT0"><img src="https://coinmate.io/static/img/banner/CoinMate_Banner_02.png" alt="Registration link via referral" border="0"></a>


4. **An account on [Azure](https://azure.microsoft.com/cs-cz/)** (the account is free; you only pay for the funds used, which comes out to about $0.04/month)

# Installation procedure
1. Generate API keys on your exchange. E.g. for Coinmate is [instructions for generating keys here](https://coinmate.io/blog/using-the-coinmate-io-api/). This step is important to allow AccBot to access funds on the exchange and perform its accumulation activity. Write down the generated ClientId, PublicKey and PrivateKey in your notepad -> you will need them in step 5.
   - ATTENTION: You need to add Trading permissions to the API keys, see: 

   ![image](https://user-images.githubusercontent.com/87997650/127633515-b5828914-6183-4c60-8208-4e78d262f62e.png). 
   - If you'd like to use the autowithdrawal feature, check the "Enable for Withdrawal" option as well. In this case, we recommend that you also check "Enable for withdrawals to template addresses only", which means that the bot will only be able to send accumulated BTC to the addresses you define, see: 

   ![image](https://user-images.githubusercontent.com/87997650/127633656-a6698455-03b6-4b23-902d-e5642dbe4988.png)

3. Download the [ZIP from the current RELEASE](https://github.com/Crynners/AccBot/releases/latest/download/AccBot_installation.zip), which contains the PowerShell installation script and the built bot.
4. Unzip the ZIP from the previous point anywhere on your filesystem.
5.(Optional) Set up [Telegram notifications](#telegramnotifications). _(If, despite the recommendation, you do not want to use Telegram notifications, do not fill in the Telegram variables in the next step)_
6. In Notepad (or another text editor), first open the **init_variables.ps1** file, which contains the general settings for the bot
7. Edit the variables in the **## USER-DEFINED VARIABLES ###** section.
```powershell
######################################
### GENERAL USER-DEFINED VARIABLES ###
######################################
# Exchange you want to link the bot to
# (POSSIBLE VALUES: coinmate, huobi, binance, kraken, ftx, coinbase, kucoin, bitfinex, bittrex)
$ExchangeName='coinmate'

#Name that appears in Telegram notifications
$Name='anonymous'

# The name of the AccBot you want to deploy. Used when you want to accumulate multiple pairs at once.
# Usage: 
# 1. Run the script with e.g. AccBotName='BTC-AccBot' with the configuration for the first bot
# 2. Run a script with e.g. AccBotName='ETH-AccBot' with the configuration for the second bot
# (ALLOWED VALUES: "a-z", "0-9", "-")

$AccBotName='BTC-AccBot'

################################################
########### Timer settings #####################
################################################
# You have the option to fill in either the $HourDivider or $NCronTabExpression variable

# If you want to buy every X hours (less than once per day), which is also the recommended setting (more often in smaller batches), fill in HourDivider
# HourDivider specifies how many hours you want to shop regularly
# (POSSIBLE VALUES: 1, 2, 3, 4, 6, 8, 12)

$HourDivider='1'

# If you only want to shop once every 2 days, once a week, or e.g. every Tuesday and Saturday, fill in $NCronTabExpression
# The format of this variable is in NCRONTAB, see: https://docs.microsoft.com/cs-cz/azure/azure-functions/functions-bindings-timer?tabs=csharp#ncrontab-expressions
# Examples:
# "0 0 */2 * * * *" -> once every two hours
# "0 30 9 * * * 1-5" -> at 9:30 every working day
# Online NCRONTAB value generator: https://ncrontab.swimburger.net/

$NCronTabExpression = ''

################################################
########### Exchange selection settings ########
################################################

# Flag if you want to enable Withdrawal if the fee is less than 0.1% 
# ALLOWED VALUES: true / false
$WithdrawalEnabled='false'

# Wallet address for withdraw (only applies if WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# (Only applied if $WithdrawalEnabled='true'). 
# Maximum withdrawal fee limit in percentage.
# DEFAULT: 0.001 = 0.1 %
$MaxWithdrawalPercentageFee = '0.001'

################################################
########### Telegram Settings ##################
################################################

# Address of the telegram channel you want to receive notifications to (in @ChannelName format)
$TelegramChannel='@channel_name'

# Private key of the Telegram bot (ATTENTION, the bot must be a member of the channel above)
$TelegramBot='telegram_bot_hash'

################################################
########### Azure log settings #################
################################################

The # flag to create a log on Azure. (ALLOWED VALUES: true / false). 
# RECOMMENDATION: By default have it disabled, i.e. "false". 
# Log increases the monthly cost from approx 0.04€/month to approx 0.2€/month. 
# So it is recommended to turn it on only if your bot for example does not purchase as it should. 

$CreateAzureLog = 'false'

##################################
### END USER-DEFINED VARIABLES ###
##################################
```
8. After saving the general configuration, open the configuration file **coinmate_variables.ps1** or **huobi_variables.ps1** depending on which exchange you want to accumulate on.
  - For **Coinmate**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Coinmate (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Coinmate (POSSIBLE VALUES: CZK, EUR)
  $Fiat='CZK'

  # Size of the chunk in CZK or EUR you want to buy regularly (MINIMUM for CZK: 26; MINIMUM for EUR: 1)
  $ChunkSize='26'

  # ClientId from Coinmate API
  $CoinMateCredentials_ClientId='111'

  # Public key from Coinmate API
  $CoinMateCredentials_PublicKey='XXX'

  # Private key from Coinmate API
  $CoinMateCredentials_PrivateKey='XXX'

  # (Only used when $WithdrawalEnabled='true'). 
  # Maximum withdrawal fee limit in absolute value (CZK)
  # If set to -1, only the percentage condition is applied => $MaxWithdrawalPercentageFee
  $MaxWithdrawalAbsoluteFee = -1
  ```
  - For **Huobi**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Huobi (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Huobi (POSSIBLE VALUES: USDT, HUSD)
  $Fiat='USDT'

  # The size of the USDT or HUSD chunk you want to buy regularly (MINIMUM: 5)
  $ChunkSize='5'

  # API Key from Huobi API
  $HuobiCredentials_Key='XXX'

  # API Secret from Huobi API
  $HuobiCredentials_Secret='XXX'
  ```
  - For **Kraken**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Kraken (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Kraken (POSSIBLE VALUES: USDT)
  $Fiat='USDT'

  # The size of the USDT (or $Fiat) chunk you want to buy regularly (MINIMUM: by exchange)
  $ChunkSize='5'

  # Name of the wallet you want to send the accumulated crypto to
  $WithdrawalKeyName = ''

  # API Key from Kraken API
  $KrakenCredentials_Key='XXX'

  # API Secret from Kraken API
  $KrakenCredentials_Secret='XXX'
  ```
  - In case of **Binance** fill in the following values:
  ```powershell
  # Crypto you want to buy on Binance (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH, ...)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Binance (POSSIBLE VALUES: USDT, BUSD, USDC, DAI)
  $Fiat='USDT'

  # The size of the USDT (or $Fiat) chunk you want to buy regularly (MINIMUM: by exchange)
  $ChunkSize='10'

  # API Key from Binance API
  $BinanceCredentials_Key='XXX'

  # API Secret from Binance API
  $BinanceCredentials_Secret='XXX'
  ```
   - For **FTX**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Binance (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH, ...)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Binance (POSSIBLE VALUES: USDT, BUSD, USDC, DAI)
  $Fiat='USDT'

  # The size of the USDT (or $Fiat) chunk you want to buy regularly (MINIMUM: by exchange)
  $ChunkSize='5'

  # API Key from Binance API
  $FTXCredentials_Key='XXX'

  # API Secret from Binance API
  $FTXCredentials_Secret='XXX'
  ``` 
   - For **Bitfinex**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Kraken (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Kraken (POSSIBLE VALUES: USDT)
  $Fiat='USDT'

  # The size of the USDT (or $Fiat) chunk you want to buy regularly (MINIMUM: by exchange)
  $ChunkSize='5'

  # Name of the wallet you want to send the accumulated crypto to
  $WithdrawalKeyName = ''

  # API Key from Bitfinex API
  $BitfinexCredentials_Key='XXX'

  # API Secret from Bitfinex API
  $BitfinexCredentials_Secret='XXX'
  ```
   - For **KuCoin**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Huobi (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Huobi (POSSIBLE VALUES: USDT, HUSD)
  $Fiat='USDT'

  # The size of the USDT or HUSD chunk you want to buy regularly (MINIMUM: 5)
  $ChunkSize='5'

  # API Key from KuCoin API
  $KuCoinCredentials_Key='XXX'

  # API Secret from KuCoin API
  $KuCoinCredentials_Secret='XXX'

  # API PassPhrase from KuCoin API
  $KuCoinCredentials_PassPhrase='XXX'
  ``` 
   - For **Coinbase**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Kraken (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Kraken (POSSIBLE VALUES: USDT)
  $Fiat='USDT'

  # The size of the USDT (or $Fiat) chunk you want to buy regularly (MINIMUM: by exchange)
  $ChunkSize='5'

  # Name of the wallet you want to send the accumulated crypto to
  $WithdrawalKeyName = ''

  # API Key from Coinbase API
  $CoinbaseCredentials_Key='XXX'

  # API Secret from Coinbase API
  $CoinbaseCredentials_Secret='XXX'
  ```
   - For **Bittrex**, fill in the following values:
  ```powershell
  # Crypto you want to buy on Kraken (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
  $Currency='BTC'

  # Fiat currency you want to buy crypto with on Kraken (POSSIBLE VALUES: USDT)
  $Fiat='USDT'

  # The size of the USDT (or $Fiat) chunk you want to buy regularly (MINIMUM: by exchange)
  $ChunkSize='5'

  # Name of the wallet you want to send the accumulated crypto to
  $WithdrawalKeyName = ''

  # API Key from Bittrex API
  $BittrexCredentials_Key='XXX'

  # API Secret from Bittrex API
  $BittrexCredentials_Secret='XXX'
  ``` 
<a name="installscript"></a>
9. 
  - <img src="https://user-images.githubusercontent.com/87997650/128522417-9bd02e68-a4d6-48bd-8661-81ec43ee3a47.png" width="25" height="25" />: Double-click to run **run.bat** file _(For Windows OS)._
  - <img src="https://user-images.githubusercontent.com/87997650/128523326-a7456256-4f01-41ef-9c21-1fe5968923cf.png" width="25" height="25" /> / <img src="https://user-images.githubusercontent.com/87997650/128523557-566d738d-67f5-43ac-a65e-080105f92abb.png" width="25" height="25" />: Run PowerShell and execute the command in PowerShell 
    ```powershell 
    powershell.exe -executionpolicy bypass -file .\install_script.ps1
    ``` 
The script will automatically prepare all the resources you need on Azure. It should also pop up the Azure portal login window at the beginning. 

** WARNING: The installation takes a few minutes, please wait for it to complete.** The following message should appear at the end:

![image](https://user-images.githubusercontent.com/87997650/128522145-3acfef81-ede6-4e40-95f0-627a532ca5d2.png)

<a name="telegramnotifications"></a>
# (Optional) Telegram notifications settings

This part is not mandatory for the bot to work, however it is a great added value as the bot will periodically inform you after each purchase what your average BTC accrued price is and how many BTC you have already accrued. You will also be able to verify in real time that the bot is working.

1. Create an account on [Telegram](https://telegram.org/)
2. Create a bot via BotFather according to [instructions](https://sendpulse.com/knowledge-base/chatbot/create-telegram-chatbot).
   - The token from the tutorial is then inserted into the **$TelegramBot** variable from the PowerShell script
3. Create a new channel ([video tutorial](https://youtu.be/q6-k_LGbw_k) to create from the mobile app -> [Android](https://play.google.com/store/apps/details?id=org.telegram.messenger&hl=cs&gl=US) or [iOS](https://apps.apple.com/us/app/telegram-messenger/id686449807) version). 
Alternatively, follow the following printscreen -> create via [Telegram desktop](https://desktop.telegram.org/).
   - In the top left corner, click on settings
   
   ![image](https://user-images.githubusercontent.com/87997650/127706308-0ca1aead-f5a8-42eb-b740-6463d820636f.png)
   - Click on the `New Channel` button
   
   ![image](https://user-images.githubusercontent.com/87997650/127706363-c10948dd-2d97-4dc1-9028-718d1f802153.png)
   - Name your channel and confirm the creation with the button
   
   ![image](https://user-images.githubusercontent.com/87997650/127706441-52c861f9-3f76-49a0-8d42-9c5d48c657cc.png)
   - Mark the channel as **Public** and come up with a unique name for it. This name is then filled in the format `@MyAccBotChannel` (in the case of the example below) in the **$TelegramChannel** variable in the powershell script
   ![image](https://user-images.githubusercontent.com/87997650/127706976-591cb415-4bc2-444b-95fc-56aaa9d58e73.png)
   - To set the created channel as **Private** do the following:
     - Find out the Id of your private channel, e.g. by opening the channel in the [Telegram web interface](https://web.telegram.org)
     - The URL address will be in the format https://web.telegram.org/z/#{ChannelId}
     - ⚠️Attention: to send messages via the created bot you have to add -100 before the Id. So if your address was e.g. https://web.telegram.org/z/#-123456789, the resulting Id would be -100123456789.
     - Put this resulting Id into the **$TelegramChannel** variable instead of the channel name.
4. Invite your bot (search for it by name) that you created in step 2 via BotFather to the channel.
   ![image](https://user-images.githubusercontent.com/87997650/127707214-174f6dd0-a990-49d8-8cb0-6c9c9e290102.png)
   - Confirm the bot as a channel administrator
   
   ![image](https://user-images.githubusercontent.com/87997650/127707275-af26e4f8-3c8b-46ff-b437-1e0d29a9ce77.png)
   - Leave the default permissions of the bot
   
   ![image](https://user-images.githubusercontent.com/87997650/127707327-faa3fa84-56ab-4fce-be0f-7a3f81cadf38.png)
5. Done, the bot should now write information about purchases with statistics to the created channel.

# FAQ
**Q:** How can I change the settings of a running AccBot?

**A:** If AccBot is already running successfully and you want to change some settings over time _(frequency or amount of individual purchases, withdrawal permissions, etc.)_, the easiest way is to edit **USER-DEFINED VARIABLES** in the installation script **install_script.ps1** and run the script again according to step 9 [of the installation instructions](#installscript).
##
**Q:** How to deploy a new version of AccBot?

**A:** Download the [ZIP from the current RELEASE](https://github.com/Crynners/AccBot/releases/latest/download/AccBot_installation.zip), set up the configuration files **init_variables.ps1** and _{burst you want to accumulate}_ **variables.ps1** and re-run the script according to step 7 of the [installation instructions](#installscript).
##
**Q:** Can I accumulate BTC and ETH at the same time?

**A:** Yes. Just run the script according to step 7 [of the installation guide](#installscript) with the configuration of the 1st bot (i.e. accumulation to BTC), and then run the script with the configuration of the 2nd bot. <br />⚠️CAUTION: You need to change the **$AccBotName** variable (AccBot name) in the **init_variables.ps1** configuration file.

# Donate
![heart_donate](https://user-images.githubusercontent.com/87997650/127650190-188e401a-9942-4511-847e-d1010628777a.png)

We decided to provide AccBot completely free of charge, as we want to give as many people as possible the easiest and cheapest way to apply the [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html) strategy. We believe that saving regularly in Bitcoin is the best way to secure for the future. In fact, investing in BTC with emotions involved and with ambitions to predict the market does not pay off in most cases.

If you'd like to support us, we certainly won't stop you from doing so. Below are the individual wallets where you can send us a donation, for example for a beer. :) Thank you <3

- **BTC ❤️**: bc1q2hz79m4csklecqgusu9e2yjnrr6e9ca6nhu0at
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


