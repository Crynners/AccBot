# Introduction
Welcome to AccBot. AccBot is an open-source accumulation bot that buys [BTC](https://cs.wikipedia.org/wiki/Bitcoin) _(possibly LTC, ETH, XMR or DASH)_ on the [Coinmate](https://coinmate.io/) exchange at regular intervals in small amounts of CZK or EUR according to the [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html) strategy.

# A simple description of how the bot works
* Purchases a user-defined amount in Czech crowns _(typically tens of CZK)_ / Euros _(typically units of Eur)_ every user-defined hours _(ideally divisible by 24, so that it always purchases at the same time, e.g. -> every hour, 1x in 2h, 1x in 4h, 1x in 8h, etc.)_.
* It runs autonomously without the need to manage it somehow over time, you only need to keep track of your CZK account balance and regularly top it up on Coinmate _(e.g. once a month)_.
* **Operating costs are practically zero** (it comes out to about 0.04 €/month for Azure hosting); the bot is implemented as [Azure function](https://azure.microsoft.com/cs-cz/services/functions/) for now, which runs at regular intervals and the whole solution is hosted on [Azure](https://azure.microsoft.com/cs-cz/). 
* (Optional functionality) After each purchase, the Telegram channel informs you of the amount of the purchase. This information is supplemented with statistics, what is the current average accumulated price, etc. See example:
  * ![image](https://user-images.githubusercontent.com/87997650/127355720-fe73c0b5-5fd4-4d31-98dc-b569975f8a9e.png)
* (Optional functionality) If a sufficient amount of BTC is accumulated, then if the withdrawal fee from the total amount is less than the user defined limit (e.g. 0.1%), the bot will send the accumulated amount of BTC from the exchange to the defined BTC wallet (note: if you want to use this functionality, we recommend enabling API send only to your specific BTC wallet, see settings when creating an API key on Coinmate)
  * ![image](https://user-images.githubusercontent.com/87997650/127356371-6a9d1493-55f0-41cc-ab03-4a67cf610f42.png)

# Pre-requisites
1. **Installed [PowerShell](https://docs.microsoft.com/cs-cz/powershell/scripting/install/installing-powershell?view=powershell-7.1)**
2. **Installed [Azure CLI](https://docs.microsoft.com/cs-cz/cli/azure/install-azure-cli)**
3. **Established account on the exchange [Coinmate](https://coinmate.io/)** (account is free; [KYC](https://en.wikipedia.org/wiki/Know_your_customer) verification is required to send fiat to the exchange)
    - If you would like to support us and sign up through our referral link, you can click on the banner below

    <a href="https://coinmate.io?referral=ZWw4NVlXbDRVbTFVT0dKS1ZHczBZMXB1VEhKTlVRPT0"><img src="https://coinmate.io/static/img/banner/CoinMate_Banner_02.png" alt="Registration link via referral" border="0"></a>


4. **An account on [Azure](https://azure.microsoft.com/cs-cz/)** (account is free; you only pay for the funds used, which works out to about $0.04/month)

# Installation procedure
1. On Coinmate, [generate API keys](https://coinmate.io/blog/using-the-coinmate-io-api/) (so that the BOT can access the funds on the exchange and perform its accumulation activity). Write down the generated ClientId, PublicKey and PrivateKey in a notepad -> you will need them in step 5.
   - ATTENTION: You need to add Trading permissions to the API keys, see: 

   ![image](https://user-images.githubusercontent.com/87997650/127633515-b5828914-6183-4c60-8208-4e78d262f62e.png). 
   - If you'd like to use the autowithdrawal feature, check the "Enable for Withdrawal" option as well. In this case, we recommend that you also check "Enable for withdrawals to template addresses only", which means that the bot will only be able to send accumulated BTC to the addresses you define, see: 

   ![image](https://user-images.githubusercontent.com/87997650/127633656-a6698455-03b6-4b23-902d-e5642dbe4988.png)

3. Download the [ZIP from the current RELEASE](https://github.com/Crynners/AccBot/releases/latest/download/AccBot_installation.zip), which contains the PowerShell installation script and the built bot.
4. Unzip the ZIP from the previous point anywhere on your filesystem.
5. In Notepad (or another text editor) open the **install_script.ps1** file
6. (Optional) Set your [Telegram notifications](#telegramnotifications). _(If, despite the recommendation, you do not want to use Telegram notifications, do not fill in the Telegram variables in the next step)_
7. Edit the variables in the **## USER-DEFINED VARIABLES section ###**
```
##############################
### USER-DEFINED VARIABLES ###
##############################

# Name that appears in Telegram notifications
$Name='anonymous'

# Crypto you want to buy on Coinmate (POSSIBLE VALUES: BTC, LTC, ETH, XRP, DASH)
$Currency='BTC'

# Fiat currency you want to buy crypto with on Coinmate (POSSIBLE VALUES: CZK, EUR)
$Fiat='CZK'

# The size of the chunk in CZK or EUR you want to buy regularly (MINIMUM for CZK: 26; MINIMUM for EUR: 1)
$ChunkSize='26'

# Once every how many hours you want to buy BTC regularly
$HourDivider='1'

# Flag if you want to enable Withdrawal if the fee is less than 0.1% (ALLOWED VALUES: true / false)
$WithdrawalEnabled='false'

# Wallet address for withdraw (only applies if WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# (Only applied if $WithdrawalEnabled='true'). 
# Maximum withdrawal fee limit in percentage. (DEFAULT: 0.001 = 0.1%) 
$MaxWithdrawalPercentageFee = '0.001'

# (Only used when $WithdrawalEnabled='true'). 
# Maximum withdrawal fee limit in absolute value (CZK)
# If set to -1, only the percentage condition is applied => $MaxWithdrawalPercentageFee
$MaxWithdrawalAbsoluteFee = -1

# Address of the telegram channel you want to receive notifications (in @ChannelName format)
$TelegramChannel='@channel_name'

# Private key of the telegram bot (ATTENTION, the bot must be a member of the channel above)
$TelegramBot='telegram_bot_hash'

# ClientId from Coinmate API
$CoinMateCredentials_ClientId='111'

# Public key from Coinmate API
$CoinMateCredentials_PublicKey='XXX'

# Private key from Coinmate API
$CoinMateCredentials_PrivateKey='XXX'

##############################
```
7. Save the **install_script.ps1** file with the values from the previous step filled in.
  - <img src="https://user-images.githubusercontent.com/87997650/128522417-9bd02e68-a4d6-48bd-8661-81ec43ee3a47.png" width="25" height="25" />: Double-click to run the **run.bat** file _(For Windows OS)._ 
  - <img src="https://user-images.githubusercontent.com/87997650/128523326-a7456256-4f01-41ef-9c21-1fe5968923cf.png" width="25" height="25" /> / <img src="https://user-images.githubusercontent.com/87997650/128523557-566d738d-67f5-43ac-a65e-080105f92abb.png" width="25" height="25" />: Run PowerShell and execute `powershell.exe -executionpolicy bypass -file .\install_script.ps1`. 

The script will automatically prepare all the necessary resources on Azure. A window should also pop up at the beginning with the Azure portal login. 

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
4. Invite your bot (search for it by name) that you created in step 2 via BotFather to the channel.
   ![image](https://user-images.githubusercontent.com/87997650/127707214-174f6dd0-a990-49d8-8cb0-6c9c9e290102.png)
   - Confirm the bot as a channel administrator
   
   ![image](https://user-images.githubusercontent.com/87997650/127707275-af26e4f8-3c8b-46ff-b437-1e0d29a9ce77.png)
   - Leave the default permissions of the bot
   
   ![image](https://user-images.githubusercontent.com/87997650/127707327-faa3fa84-56ab-4fce-be0f-7a3f81cadf38.png)
5. Done, the bot should now write information about purchases with statistics to the created channel.

# Donate
![heart_donate](https://user-images.githubusercontent.com/87997650/127650190-188e401a-9942-4511-847e-d1010628777a.png)

We decided to provide AccBot completely free of charge because we want to give as many people as possible the easiest and cheapest way to apply the [DCA](https://www.fxstreet.cz/jiri-makovsky-co-je-dollar-cost-averaging-a-jak-funguje.html) strategy. We believe that saving regularly in Bitcoin is the best way to secure for the future. In fact, investing in BTC with emotions involved and with ambitions to predict the market does not pay off in most cases.

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
