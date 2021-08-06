
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

##############################
### USER-DEFINED VARIABLES ###
##############################

# Jméno, které se zobrazuje v Telegram notifikacích
$Name='anonymous'

# Crypto, které na Coinmate chcete nakupovat (MOŽNÉ HODNOTY: BTC, LTC, ETH, XRP, DASH)
$Currency='BTC'

# Fiat měna, za kterou chcete na Coinmate nakupovat crypto (MOŽNÉ HODNOTY: CZK, EUR)
$Fiat='CZK'

# Velikost chunku v CZK, resp. EUR, který chcete pravidelně nakupovat (MINIMUM pro CZK: 26; MINIMUM pro EUR: 1)
$ChunkSize='26'

# Jednou za kolik hodin chcete pravidelně nakupovat BTC
$HourDivider='1'

# Příznak, zdali chcete povolit Withdrawal v případě, že je fee menší než 0.1% (POVOLENÉ HODNOTY: true / false)
$WithdrawalEnabled='false'

# Adresa peněženky pro withdraw (aplikuje se pouze pokud WithdrawalEnabled = TRUE)
$WithdrawalAddress=''

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit v procentech, kdy dovolujete botovi zaslat naakumulované krypto do své peněženky
$MaxWithdrawalPercentageFee = '0.001'

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit v absolutní hodnotě (Kč), kdy dovolujete botovi zaslat naakumulované krypto do své peněženky
# Pokud je nastaveno -1, uplatní se pouze podmínka procentuální => $MaxWithdrawalPercentageFee
$MaxWithdrawalAbsoluteFee = -1

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

##################################
###### Kontrola prerekvizit ######
##################################

# Kontrola dostupnosi deployment ZIP souboru
if(![System.IO.File]::Exists($zipDeploymentFileName)){
    $err = "Deployment ZIP file '" + $zipDeploymentFileName+ "' is missing in the same directory as the PowerShell script! Please copy the ZIP file 'AccBot.zip to the same directory as the ps1 script.'"
    throw $err
    exit
}


########################


#Přihlášení do Azure portal
$LoginResult = az login --allow-no-subscriptions --only-show-errors  | ConvertFrom-Json

if (0 -eq $LoginResult.Count){
    $err = "ERROR: Azure subscription does not exists. Please create the new subscription in the Azure portal. (https://https://portal.azure.com/)"
    throw $err
    exit
}


################ RESOURCE GROUP #####################

#Kontrola, zdali již náhodou Resource group "AccBot" neexistuje
$query = "[?name == '" + $resourceGroupName + "']"
$existingResourceGroups = az group list --query $query | ConvertFrom-Json

if ( $existingResourceGroups.Count -gt 0 )
{
    $alreadyExistPrint = "Resource group '" + $resourceGroupName + "' already exists. This step will be skipped."
    Write-Warning $alreadyExistPrint
}else{
    #Vytvoření Resource group
    az group create -l $location -n $resourceGroupName
}

################ COSMOSDB ACCOUNT #####################

#Kontrola, zdali již náhodou CosmosDB v rámci Resource group "AccBot" neexistuje 
$query = "[?contains(name, 'accbotcosmosdbaccount') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az cosmosdb list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -gt 0 )
{
    #Načtení existujícího CosmosDB
    $CosmosDBAccountResult = $existingEntity[0]
    $cosmosDBAccountName = $CosmosDBAccountResult.name

    $alreadyExistPrint = "CosmosDB account '" + $cosmosDBAccountName + "' already exists. This step will be skipped."
    Write-Warning $alreadyExistPrint
}else{
    #Vytvoření CosmosDB
    $CosmosDBAccountResult = az cosmosdb create -n $cosmosDBAccountName -g $resourceGroupName --only-show-errors --default-consistency-level Session | ConvertFrom-Json
    $cosmosDbResult = az cosmosdb sql database create --account-name $cosmosDBAccountName --resource-group $resourceGroupName --only-show-errors --name $cosmosDBName | ConvertFrom-Json
    $containerResult = az cosmosdb sql container create -g $resourceGroupName -a $cosmosDBAccountName -d $cosmosDBName -n $cosmosContainerName --only-show-errors --partition-key-path "/CryptoName" --throughput "400" | ConvertFrom-Json
}



#Vytvoření CosmosDB accountu + DB
$primaryMasterKey = az cosmosdb keys list -n $cosmosDBAccountName -g $resourceGroupName | ConvertFrom-Json

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



########################################################### AZURE STORAGE ACCOUNT ############################################################

#Kontrola, zdali již náhodou Azure storage account v rámci resource group AccBot neexistuje
$query = "[?contains(name, 'accbotsa') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az storage account list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -gt 0 )
{
    #Načtení existujícího CosmosDB
    $StorageAccountResult = $existingEntity[0]
    $storageAccountName = $StorageAccountResult.name

    $alreadyExistPrint = "Storage account '" + $storageAccountName + "' already exists. This step will be skipped."
    Write-Warning $alreadyExistPrint
}else{
    #Tvorba Azure Storage accountu
    az storage account create -n $storageAccountName -g $resourceGroupName --only-show-errors --sku Standard_LRS
}

########################################################### AZURE FUNCTIONAPP ############################################################

#Kontrola, zdali již náhodou Azure functionapp v rámci resource group AccBot neexistuje
$query = "[?contains(name, 'accbotfunction') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az functionapp list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -gt 0 )
{
    #Načtení existujícího CosmosDB
    $AzFunctionAppResult = $existingEntity[0]
    $azFunctionName = $AzFunctionAppResult.name

    $alreadyExistPrint = "Azure functionapp '" + $azFunctionName + "' already exists. This step will be skipped."
    Write-Warning $alreadyExistPrint
}else{
    #Tvorba Azure function
    az functionapp create -g $resourceGroupName  -n $azFunctionName -s $storageAccountName --only-show-errors --functions-version 3 --consumption-plan-location $location
}

#Nastavení proměnných
az functionapp config appsettings set --name $azFunctionName --resource-group $resourceGroupName `
        --settings "Name=$Name" `
                    "Currency=$Currency" `
                    "Fiat=$Fiat" `
                    "ChunkSize=$ChunkSize" `
                    "DayDividerSchedule=$DayDividerSchedule" `
                    "WithdrawalEnabled=$WithdrawalEnabled" `
                    "WithdrawalAddress=$WithdrawalAddress" `
                    "MaxWithdrawalPercentageFee=$MaxWithdrawalPercentageFee" `
                    "MaxWithdrawalAbsoluteFee=$MaxWithdrawalAbsoluteFee" `
                    "TelegramChannel=$TelegramChannel" `
                    "TelegramBot=$TelegramBot" `
                    "CosmosDbEndpointUri=$CosmosDbEndpointUri" `
                    "CosmosDbPrimaryKey=$CosmosDbPrimaryKey" `
                    "CoinMateCredentials_ClientId=$CoinMateCredentials_ClientId" `
                    "CoinMateCredentials_PublicKey=$CoinMateCredentials_PublicKey" `
                    "CoinMateCredentials_PrivateKey=$CoinMateCredentials_PrivateKey"


#Deploy AccBota do Azure function
$env:SCM_DO_BUILD_DURING_DEPLOYMENT='true'
$DeployAzureFunctionResult = az functionapp deployment source config-zip -g $resourceGroupName -n $azFunctionName --src $zipDeploymentFileName --only-show-errors

################################################### Závěrečná kontrola skriptu ############################################################
# Resource group check
$query = "[?name == '" + $resourceGroupName + "']"
$existingEntity = az group list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -eq 0 )
{
    $err = "ERROR: Resource group check failed."
    throw $err
    exit
}

# CosmosDB account check
$query = "[?contains(name, 'accbotcosmosdbaccount') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az cosmosdb list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -eq 0 )
{
    $err = "ERROR: CosmosDB account check failed."
    throw $err
    exit
}

# Azure storage account check
$query = "[?contains(name, 'accbotsa') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az storage account list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -eq 0 )
{
    $err = "ERROR: Azure storage account check failed."
    throw $err
    exit
}

# Azure functionapp check
$query = "[?contains(name, 'accbotfunction') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az functionapp list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -eq 0 )
{
    $err = "ERROR: Azure functionapp check failed."
    throw $err
    exit
}

# Azure functionapp settings check
$query = "[?contains(name, 'CosmosDbPrimaryKey')]"
$existingEntity = az functionapp config appsettings list --query $query -g $resourceGroupName -n $azFunctionName  | ConvertFrom-Json

if ( $existingEntity.Count -eq 0 )
{
    $err = "ERROR: Azure functionapp settings check failed."
    throw $err
    exit
}

if( $null -eq $DeployAzureFunctionResult ){
    $err = "ERROR: Azure function deployment failed."
    throw $err
    exit
}


#Zobrazit výstup co se stalo
$output = "Úspěšně se vytvořily následující entity v Azure: `n`t ResourceGroup: " + $resourceGroupName + "`n"`
                + "`t CosmosDBAccount: " + $cosmosDBAccountName + "`n"`
                + "`t Azure storage: " + $storageAccountName + "`n"`
                + "`t Azure function: " + $azFunctionName + "`n"`
                + " => Bot je již nasazen a nyní by měl začít nakupovat " + $Currency + " v " + $randomMinutes + ". minutu každou " + $NextHour + "hodinu."

Write-Host $output -ForegroundColor green

pause