######################################
### GENERAL USER-DEFINED VARIABLES ###
######################################
# Burza, na kterou chcete napojit bota
# (MOŽNÉ HODNOTY: coinmate, huobi, binance, kraken, ftx)
$ExchangeName='coinmate'

# Jméno, které se zobrazuje v Telegram notifikacích
$Name='anonymous'

# Jednou za kolik hodin chcete pravidelně nakupovat BTC
# (MOŽNÉ HODNOTY: 1, 2, 3, 4, 6, 8, 12)
$HourDivider='1'

# Příznak, zdali chcete povolit Withdrawal v případě, že je fee menší než 0.1% (POVOLENÉ HODNOTY: true / false)
$WithdrawalEnabled='false'

# Adresa peněženky pro withdraw (aplikuje se pouze pokud WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit na withdrawal fee v procentech. (DEFAULT: 0.001 = 0.1 %) 
$MaxWithdrawalPercentageFee = '0.001'

# Adresa telegram kanálu, do kterého chcete dostávat notifikace (ve formátu @NázevKanálu)
$TelegramChannel='@channel_name'

# Privátní klíč telegram bota (POZOR, bot musí být členem kanálu výše)
$TelegramBot='telegram_bot_hash'

# Příznak pro vytvoření logu na Azure. (POVOLENÉ HODNOTY: true / false). DOPORUČENÍ: Standardně mít vypnuté, tedy "false". 
# Log zvyšuje měsíční náklady z cca 0.04 € / měsíc na cca 0.2 € / měsíc. Doporučujeme tedy zapnout pouze pokud Vám bot například nenakupuje jak by měl. 
$CreateAzureLog = 'false'

##################################
### END USER-DEFINED VARIABLES ###
##################################