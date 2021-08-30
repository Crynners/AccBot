######################################
### GENERAL USER-DEFINED VARIABLES ###
######################################
# Burza, na kterou chcete napojit bota
# (MOŽNÉ HODNOTY: coinmate, huobi, binance, kraken, ftx)
$ExchangeName='coinmate'

# Jméno, které se zobrazuje v Telegram notifikacích
$Name='anonymous'

################################################
########### Nastavení časovače #################
################################################
# Máte možnost vyplnit buďto proměnnou $HourDivider nebo $NCronTabExpression

# Pokud chcete nakupovat každých X hodin (méně než 1x za den), což je i doporučené nastavení (častěji po menších dávkách), vyplňte HourDivider
# HourDivider určuje po kolika hodinách chcete pravidelně nakupovat
# (MOŽNÉ HODNOTY: 1, 2, 3, 4, 6, 8, 12)
$HourDivider='1'

# Pokud chcete nakupovat např. pouze jednou za 2 dny, jednou týdně, nebo např. každé úterý a sobotu, vyplňte $NCronTabExpression
# Formát této proměnné je v NCRONTAB, viz: https://docs.microsoft.com/cs-cz/azure/azure-functions/functions-bindings-timer?tabs=csharp#ncrontab-expressions
# Příklady:
# "0 0 */2 * * *" -> jednou za dvě hodiny
# "0 30 9 * * 1-5" -> v 9:30 každý pracovní den
# Online generátor NCRONTAB hodnoty: https://ncrontab.swimburger.net/
$NCronTabExpression = ''

################################################
########### Nastavení výběru z burzy ###########
################################################

# Příznak, zdali chcete povolit Withdrawal v případě, že je fee menší než 0.1% 
# POVOLENÉ HODNOTY: true / false
$WithdrawalEnabled='false'

# Adresa peněženky pro withdraw (aplikuje se pouze pokud WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit na withdrawal fee v procentech.
# DEFAULT: 0.001 = 0.1 %
$MaxWithdrawalPercentageFee = '0.001'

################################################
########### Nastavení Telegramu ################
################################################

# Adresa telegram kanálu, do kterého chcete dostávat notifikace (ve formátu @NázevKanálu)
$TelegramChannel='@channel_name'

# Privátní klíč telegram bota (POZOR, bot musí být členem kanálu výše)
$TelegramBot='telegram_bot_hash'

################################################
########### Nastavení Azure logu ###############
################################################

# Příznak pro vytvoření logu na Azure. (POVOLENÉ HODNOTY: true / false). 
# DOPORUČENÍ: Standardně mít vypnuté, tedy "false". 
# Log zvyšuje měsíční náklady z cca 0.04 € / měsíc na cca 0.2 € / měsíc. 
# Doporučujeme tedy zapnout pouze pokud Vám bot například nenakupuje jak by měl. 
$CreateAzureLog = 'false'

##################################
### END USER-DEFINED VARIABLES ###
##################################