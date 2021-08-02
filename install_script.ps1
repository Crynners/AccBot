
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

##############################
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
$TelegramBot='telegram_bot_hash'

# ClientId z Coinmate API
$CoinMateCredentials_ClientId='111'

# Public key z Coinmate API
$CoinMateCredentials_PublicKey='XXX'

# Private key z Coinmate API
$CoinMateCredentials_PrivateKey='XXX'

##############################

########################
### SYSTEM VARIABLES ###
########################

$scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
$zipFile = 'AccBot.zip'

$resourceGroupName='AccBot'

$cosmosDBAccountName='accbotcosmosdbaccount-'+$([System.Guid]::NewGuid().ToString())
$cosmosDBAccountName = $cosmosDBAccountName.Substring(0,44)

$azFunctionName ='accbotfunction-'+$([System.Guid]::NewGuid().ToString())
$azFunctionName = $azFunctionName.Substring(0,44)

$randomNumber = Get-Random -Maximum 1000000000
$storageAccountName ='accbotsa' + $randomNumber

$location = 'germanywestcentral'

$cosmosDBName='AccBotDatabase'
$cosmosContainerName='AccBotContainer'

$zipDeploymentFileName = join-path -path $scriptPath -childpath $zipFile
$zipDeploymentFileName

##################################
###### Kontrola prerekvizit ######
##################################

# Kontrola dostupnosi deployment ZIP souboru
if(![System.IO.File]::Exists($zipDeploymentFileName)){
    throw "Deployment ZIP file '" + $zipDeploymentFileName+ "' is missing in the same directory as the PowerShell script! Please copy the ZIP file 'AccBot.zip to the same directory as the ps1 script.'"
    exit
}


########################


#Přihlášení do Azure portal
az login

#Kontrola, zdali již náhodou Resource group "AccBot" neexistuje
$query = "[?name == '" + $resourceGroupName + "']"
$existingResourceGroups = az group list --query $query | ConvertFrom-Json

if ( $existingResourceGroups.Count -gt 0 )
{
    $alreadyExistPrint = "Resource group '" + $resourceGroupName + "' is already exists. This step will be skipped."
    $alreadyExistPrint
}else{
    #Vytvoření Resource group
    az group create -l $location -n $resourceGroupName
}


$CosmosDBAccountResult = az cosmosdb create -n $cosmosDBAccountName -g $resourceGroupName --default-consistency-level Session | ConvertFrom-Json

#Vytvoření CosmosDB accountu + DB
$primaryMasterKey = az cosmosdb keys list -n $cosmosDBAccountName -g $resourceGroupName | ConvertFrom-Json

$cosmosDbResult = az cosmosdb sql database create --account-name $cosmosDBAccountName --resource-group $resourceGroupName --name $cosmosDBName | ConvertFrom-Json
$containerResult = az cosmosdb sql container create -g $resourceGroupName -a $cosmosDBAccountName -d $cosmosDBName -n $cosmosContainerName --partition-key-path "/CryptoName" --throughput "400" | ConvertFrom-Json

$CosmosDbPrimaryKey = $primaryMasterKey.primaryMasterKey
$CosmosDbEndpointUri = 'https://' + $CosmosDBAccountResult.name + '.documents.azure.com:443/'

$randomMinutes = Get-Random -Maximum 60

if ( 1 -eq $HourDivider )
{
    $HourDivider = '*'
    $NextHour = ""
}else{
    $NextHour = $HourDivider + ". "
    $HourDivider = '*/' + $HourDivider
}

$DayDividerSchedule = '0 ' + $randomMinutes + ' ' + $HourDivider + ' * * *'


#Tvorba Azure Storage accountu
az storage account create -n $storageAccountName -g $resourceGroupName --sku Standard_LRS

#Tvorba Azure function
az functionapp create -g $resourceGroupName  -n $azFunctionName -s $storageAccountName --functions-version 3 --consumption-plan-location $location

#Nastavení proměnných
az functionapp config appsettings set --name $azFunctionName --resource-group $resourceGroupName `
      --settings "Name=$Name" `
                 "Currency=$Currency" `
                 "ChunkSize=$ChunkSize" `
                 "DayDividerSchedule=$DayDividerSchedule" `
                 "WithdrawalEnabled=$WithdrawalEnabled" `
                 "WithdrawalAddress=$WithdrawalAddress" `
                 "TelegramChannel=$TelegramChannel" `
                 "TelegramBot=$TelegramBot" `
                 "CosmosDbEndpointUri=$CosmosDbEndpointUri" `
                 "CosmosDbPrimaryKey=$CosmosDbPrimaryKey" `
                 "CoinMateCredentials_ClientId=$CoinMateCredentials_ClientId" `
                 "CoinMateCredentials_PublicKey=$CoinMateCredentials_PublicKey" `
                 "CoinMateCredentials_PrivateKey=$CoinMateCredentials_PrivateKey"

#Deploy AccBota do Azure function
az functionapp deployment source config-zip -g $resourceGroupName -n $azFunctionName --src $zipDeploymentFileName

#Zobrazit výstup co se stalo
$output = "Úspěšně se vytvořily následující entity v Azure: `n`t ResourceGroup: " + $resourceGroupName + "`n"`
                + "`t CosmosDBAccount: " + $cosmosDBAccountName + "`n"`
                + "`t Azure storage: " + $storageAccountName + "`n"`
                + "`t Azure function: " + $azFunctionName + "`n"`
                + " => Bot je již nasazen a nyní by měl začít nakupovat " + $Currency + " v " + $randomMinutes + ". minutu každou " + $NextHour + "hodinu."

$output

pause
