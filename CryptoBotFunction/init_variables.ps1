######################################
### GENERAL USER-DEFINED VARIABLES ###
######################################
# Burza, na kterou chcete napojit bota
# (MOŽNÉ HODNOTY: coinmate, huobi, binance, kraken, ftx, coinbase, kucoin, bitfinex, bittrex)
# -------------------------------------------------------
# Exchange you want to connect the bot to
# (POSSIBLE VALUES: coinmate, huobi, binance, kraken, ftx, coinbase, kucoin, bitfinex, bittrex)
$ExchangeName='coinmate'

# Jméno, které se zobrazuje v Telegram notifikacích
# -------------------------------------------------------
# Name that appears in Telegram notifications
$Name='anonymous'

# Jméno AccBota, kterého chcete nasadit. Využije se v momentě, kdy chcete akumulovat více párů najednou.
# Použití: 
# 1. Spustíte skript s např. AccBotName='BTC-AccBot' s konfigurací pro prvního bota
# 2. Spustíte skript s např. AccBotName='ETH-AccBot' s konfigurací pro druhého bota
# (POVOLENÉ HODNOTY: "a-z", "0-9", "-")
# -------------------------------------------------------
# Name of the AccBot you want to deploy. Used when you want to accumulate multiple pairs at once.
# Usage: 
# 1. Run the script with e.g. AccBotName='BTC-AccBot' with the configuration for the first bot
# 2. Run a script with e.g. AccBotName='ETH-AccBot' with the configuration for the second bot
# (ALLOWED VALUES: "a-z", "0-9", "-")
$AccBotName='BTC-AccBot'

################################################
########### Nastavení časovače #################
################################################
# Máte možnost vyplnit buďto proměnnou $HourDivider nebo $NCronTabExpression

# Pokud chcete nakupovat každých X hodin (méně než 1x za den), což je i doporučené nastavení (častěji po menších dávkách), vyplňte HourDivider
# HourDivider určuje po kolika hodinách chcete pravidelně nakupovat
# (MOŽNÉ HODNOTY: 1, 2, 3, 4, 6, 8, 12)
# -------------------------------------------------------
################################################
########### Timer settings #####################
################################################
# You have the option to fill in either the $HourDivider or $NCronTabExpression variable

# If you want to shop every X hours (less than once per day), which is also the recommended setting (more often in smaller batches), fill in HourDivider
# HourDivider specifies how many hours you want to shop regularly
# (POSSIBLE VALUES: 1, 2, 3, 4, 6, 8, 12)

$HourDivider='1'

# Pokud chcete nakupovat např. pouze jednou za 2 dny, jednou týdně, nebo např. každé úterý a sobotu, vyplňte $NCronTabExpression
# Formát této proměnné je v NCRONTAB, viz: https://docs.microsoft.com/cs-cz/azure/azure-functions/functions-bindings-timer?tabs=csharp#ncrontab-expressions
# Příklady:
# "0 0 */2 * * *" -> jednou za dvě hodiny
# "0 30 9 * * 1-5" -> v 9:30 každý pracovní den
# Online generátor NCRONTAB hodnoty: https://ncrontab.swimburger.net/
# -------------------------------------------------------
# If you only want to buy e.g. once every 2 days, once a week, or e.g. every Tuesday and Saturday, fill in $NCronTabExpression
# The format of this variable is in NCRONTAB, see: https://docs.microsoft.com/azure/azure-functions/functions-bindings-timer?tabs=csharp#ncrontab-expressions
# Examples:
# "0 0 */2 * * * *" -> every two hours
# "0 30 9 * * * 1-5" -> at 9:30 every working day
# Online NCRONTAB value generator: https://ncrontab.swimburger.net/

$NCronTabExpression = ''

################################################
########### Nastavení výběru z burzy ###########
################################################

# Příznak, zdali chcete povolit Withdrawal v případě, že je fee menší než 0.1% 
# POVOLENÉ HODNOTY: true / false
# -------------------------------------------------------
################################################
########### Exchange Withdrawal Settings #######
################################################

# Flag if you want to enable Withdrawal if the fee is less than 0.1% 
# ALLOWED VALUES: true / false
$WithdrawalEnabled='false'

# Adresa peněženky pro withdraw (aplikuje se pouze pokud WithdrawalEnabled = TRUE)
# -------------------------------------------------------
# Wallet address for withdraw (only applies if WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit na withdrawal fee v procentech.
# DEFAULT: 0.001 = 0.1 %
# -------------------------------------------------------
# (Only used if $WithdrawalEnabled='true'). 
# Maximum withdrawal fee limit in percentage.
# DEFAULT: 0.001 = 0.1 %
$MaxWithdrawalPercentageFee = '0.001'

################################################
########### Nastavení Telegramu ################
################################################

# Adresa telegram kanálu, do kterého chcete dostávat notifikace (ve formátu @NázevKanálu)
# -------------------------------------------------------
################################################
########### Telegram Settings ##################
################################################

# Address of the Telegram channel you want to receive notifications (in @ChannelName format)
$TelegramChannel='@channel_name'

# Privátní klíč telegram bota (POZOR, bot musí být členem kanálu výše)
# -------------------------------------------------------
# Private key of telegram bot (ATTENTION, bot must be a member of the channel above)
$TelegramBot='telegram_bot_hash'

################################################
########### Nastavení Azure logu ###############
################################################

# Příznak pro vytvoření logu na Azure. (POVOLENÉ HODNOTY: true / false). 
# DOPORUČENÍ: Standardně mít vypnuté, tedy "false". 
# Log zvyšuje měsíční náklady z cca 0.04 € / měsíc na cca 0.2 € / měsíc. 
# Doporučujeme tedy zapnout pouze pokud Vám bot například nenakupuje jak by měl. 
# -------------------------------------------------------
################################################
########### Azure log settings ###############
################################################

# Flag to create a log on Azure. (OPTIONAL VALUES: true / false). 
# RECOMMENDATION: By default have it disabled, i.e. "false". 
# Log increases the monthly cost from approx 0.04€/month to approx 0.2€/month. 
# So it is recommended to turn it on only if your bot for example does not purchase as it should. 

$CreateAzureLog = 'false'

##################################
### END USER-DEFINED VARIABLES ###
##################################