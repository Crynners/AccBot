######################################
### COINMATE USER-DEFINED VARIABLES #####
######################################

# Crypto, které na Coinmate chcete nakupovat (MOŽNÉ HODNOTY: BTC, LTC, ETH, XRP, DASH)
$Currency='BTC'

# Fiat měna, za kterou chcete na Coinmate nakupovat crypto (MOŽNÉ HODNOTY: CZK, EUR)
$Fiat='CZK'

# Velikost chunku v CZK, resp. EUR, který chcete pravidelně nakupovat (MINIMUM pro CZK: 26; MINIMUM pro EUR: 1)
$ChunkSize='26'

# ClientId z Coinmate API
$CoinMateCredentials_ClientId='111'

# Public key z Coinmate API
$CoinMateCredentials_PublicKey='XXX'

# Private key z Coinmate API
$CoinMateCredentials_PrivateKey='XXX'

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit na withdrawal fee v absolutní hodnotě (Kč)
# Pokud je nastaveno -1, uplatní se pouze podmínka procentuální => $MaxWithdrawalPercentageFee
$MaxWithdrawalAbsoluteFee = -1

######################################
### END USER-DEFINED VARIABLES #######
######################################