
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
# Maximální limit na withdrawal fee v procentech. (DEFAULT: 0.001 = 0.1 %) 
$MaxWithdrawalPercentageFee = '0.001'

# (Využije se pouze v případě, kdy $WithdrawalEnabled='true'). 
# Maximální limit na withdrawal fee v absolutní hodnotě (Kč)
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

# Příznak pro vytvoření logu na Azure. (POVOLENÉ HODNOTY: true / false). DOPORUČENÍ: Standardně mít vypnuté, tedy "false". 
# Log zvyšuje měsíční náklady z cca 0.04 € / měsíc na cca 0.2 € / měsíc. Doporučujeme tedy zapnout pouze pokud Vám bot například nenakupuje jak by měl. 
$CreateAzureLog = 'false'

########################
### SYSTEM VARIABLES ###
########################

# Pokud proměnné neexistují, nastaví se default
if($null -eq $MaxWithdrawalPercentageFee){
    $MaxWithdrawalPercentageFee = '0.001'
}

if($null -eq $MaxWithdrawalAbsoluteFee){
    $MaxWithdrawalAbsoluteFee = 15
}

if($null -eq $WithdrawalEnabled){
    $WithdrawalEnabled = 'false'
}

if($null -eq $WithdrawalAddress){
    $WithdrawalAddress = ''
}

if($null -eq $CreateAzureLog){
    $CreateAzureLog = 'false'
}

$scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
$zipFile = 'AccBot.zip'

$resourceGroupName='AccBot'

$cosmosDBAccountName='accbotcosmosdbaccount-'+$([System.Guid]::NewGuid().ToString())
$cosmosDBAccountName = $cosmosDBAccountName.Substring(0,44)

$appInsightsName ='appinsights-'+$([System.Guid]::NewGuid().ToString())
$appInsightsName = $appInsightsName.Substring(0,44)

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
    Write-Error $err
    pause
    exit
}


########################


#Přihlášení do Azure portal
$LoginResult = az login --allow-no-subscriptions --only-show-errors  | ConvertFrom-Json

if (0 -eq $LoginResult.Count){
    $err = "ERROR: Azure subscription does not exists. Please create the new subscription in the Azure portal. (https://https://portal.azure.com/)"
    Write-Error $err
    pause
    exit
}

Write-Host "Login to Azure was successful. Please wait, installation will take several minutes..." -ForegroundColor cyan

################ RESOURCE GROUP #####################
Write-Host "[Step 1 / 9] Resource group -> Starting ..." -ForegroundColor cyan

#Kontrola, zdali již náhodou Resource group "AccBot" neexistuje
$query = "[?name == '" + $resourceGroupName + "']"

try{
    $existingEntity = az group list --query $query | ConvertFrom-Json
}catch{
    Write-Error $_
    pause
    exit
}


if ( $existingEntity.Count -gt 0 )
{
    $alreadyExistPrint = "Resource group '" + $resourceGroupName + "' already exists. This step will be skipped."
    Write-Warning $alreadyExistPrint
}else{
    #Vytvoření Resource group
    $createResourceGroupResult =  az group create -l $location -n $resourceGroupName

    # Resource group check
    $query = "[?name == '" + $resourceGroupName + "']"
    $existingEntity = az group list --query $query | ConvertFrom-Json

    if ( $existingEntity.Count -eq 0 )
    {
        $err = "ERROR: Resource group check failed."
        Write-Error $err
        pause
        exit
    }
}



Write-Host "[Step 1 / 9] Resource group -> DONE" -ForegroundColor cyan

################ COSMOSDB ACCOUNT #####################

Write-Host "[Step 2 / 9] CosmosDB Account -> Starting ..." -ForegroundColor cyan

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

    Write-Host "[Step 2 / 9] CosmosDB account -> DONE" -ForegroundColor cyan
    Write-Host "[Step 3 / 9] CosmosDB DB -> Starting ..." -ForegroundColor cyan
    Write-Host "[Step 3 / 9] CosmosDB DB -> DONE" -ForegroundColor cyan
    Write-Host "[Step 4 / 9] CosmosDB DB Container -> Starting ..." -ForegroundColor cyan
    Write-Host "[Step 4 / 9] CosmosDB DB Container -> DONE" -ForegroundColor cyan
}else{
    #Vytvoření CosmosDB
    $CosmosDBAccountResult = az cosmosdb create -n $cosmosDBAccountName -g $resourceGroupName --only-show-errors --default-consistency-level Session | ConvertFrom-Json

    # CosmosDB account check
    $query = "[?contains(name, 'accbotcosmosdbaccount') && resourceGroup == '" + $resourceGroupName + "']"
    $existingEntity = az cosmosdb list --query $query | ConvertFrom-Json

    if ( $existingEntity.Count -eq 0 )
    {
        $err = "ERROR: CosmosDB account check failed."
        Write-Error $err
        pause
        exit
    }
    Write-Host "[Step 2 / 9] CosmosDB account -> DONE" -ForegroundColor cyan
    Write-Host "[Step 3 / 9] CosmosDB DB -> Starting ..." -ForegroundColor cyan

    $cosmosDbResult = az cosmosdb sql database create --account-name $cosmosDBAccountName --resource-group $resourceGroupName --only-show-errors --name $cosmosDBName | ConvertFrom-Json
    Write-Host "[Step 3 / 9] CosmosDB DB -> DONE" -ForegroundColor cyan

    Write-Host "[Step 4 / 9] CosmosDB DB Container -> Starting ..." -ForegroundColor cyan
    $containerResult = az cosmosdb sql container create -g $resourceGroupName -a $cosmosDBAccountName -d $cosmosDBName -n $cosmosContainerName --only-show-errors --partition-key-path "/CryptoName" --throughput "400" | ConvertFrom-Json
    Write-Host "[Step 4 / 9] CosmosDB DB Container -> DONE" -ForegroundColor cyan
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

Write-Host "[Step 5 / 9] Storage account -> Starting..." -ForegroundColor cyan

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
    $storageAccountResult = az storage account create -n $storageAccountName -g $resourceGroupName --only-show-errors --sku Standard_LRS

    # Azure storage account check
    $query = "[?contains(name, 'accbotsa') && resourceGroup == '" + $resourceGroupName + "']"
    $existingEntity = az storage account list --query $query | ConvertFrom-Json

    if ( $existingEntity.Count -eq 0 )
    {
        $err = "ERROR: Azure storage account check failed."
        Write-Error $err
        pause
        exit
    }
}



Write-Host "[Step 5 / 9] Storage account -> DONE" -ForegroundColor cyan

########################################################### AZURE APP INSIGHTS ############################################################
Write-Host "[Step 6 / 9] App insights -> Starting..." -ForegroundColor cyan

#Kontrola, zdali již náhodou Azure app insights v rámci resource group neexistuje
$query = "[?type == 'Microsoft.Insights/components']"
$existingEntity = az resource list --query $query | ConvertFrom-Json

 # Check, zdali App insights existuje
if ( $existingEntity.Count -gt 0 )
{
    #Načtení existujícího App insights a pokud existuje, tak ji vymaž
    $AppInsightsResult = $existingEntity[0]
    $appInsightsName = $AppInsightsResult.name
    $AppInsightInstrumentalKey = $AppInsightsResult.properties.InstrumentationKey

    #Pokud insights existuje, ale nechceme abychom měli log, tak tento resource mažeme
    if("false" -eq $CreateAzureLog){
        $DeleteAppInsightsResult = az resource delete --name $appInsightsName --resource-group $resourceGroupName --resource-type 'Microsoft.Insights/components'
    }

}else{

    # Pokud App insights neexistuje a chceme založit Log, tak vytvoříme nové Insights
    if("true" -eq $CreateAzureLog){

        $AppInsightsResult = az resource create `
                            --resource-group $resourceGroupName `
                            --resource-type "Microsoft.Insights/components" `
                            --name $appInsightsName `
                            --location $location `
                            --properties '{\"Application_Type\":\"web\"}' | ConvertFrom-Json

        $AppInsightInstrumentalKey = $AppInsightsResult.properties.InstrumentationKey
    }

}

Write-Host "[Step 6 / 9] App insights -> DONE" -ForegroundColor cyan

########################################################### AZURE FUNCTIONAPP ############################################################
Write-Host "[Step 7 / 9] Function app -> Starting..." -ForegroundColor cyan

#Kontrola, zdali již náhodou Azure functionapp v rámci resource group AccBot neexistuje
$query = "[?contains(name, 'accbotfunction') && resourceGroup == '" + $resourceGroupName + "']"
$existingEntity = az functionapp list --query $query | ConvertFrom-Json

if ( $existingEntity.Count -gt 0 )
{
    #Načtení existujícího Azure function app a pokud existuje, tak ji vymaž
    $AzFunctionAppResult = $existingEntity[0]
    $azFunctionName = $AzFunctionAppResult.name

    $alreadyExistPrint = "Azure functionapp '" + $azFunctionName + "' already exists. This step will be skipped."
    Write-Warning $alreadyExistPrint

    # Vytvoření / Smazání App insights
    if( "false" -eq $CreateAzureLog){
        $FunctionAppResult = az functionapp config appsettings delete --name $azFunctionName --resource-group $resourceGroupName --setting-names "APPINSIGHTS_INSTRUMENTATIONKEY"
    }else {
        $appsettingsResult = az functionapp config appsettings set --name $azFunctionName --resource-group $resourceGroupName --settings "APPINSIGHTS_INSTRUMENTATIONKEY=$AppInsightInstrumentalKey"
    }

    
}else{
    #Tvorba Azure function
    if( "false" -eq $CreateAzureLog){
        $FunctionAppResult = az functionapp create -g $resourceGroupName  -n $azFunctionName -s $storageAccountName --only-show-errors --disable-app-insights --functions-version 3 --consumption-plan-location $location
    }else {
        $FunctionAppResult = az functionapp create -g $resourceGroupName  -n $azFunctionName -s $storageAccountName --app-insights $appInsightsName --only-show-errors --functions-version 3 --consumption-plan-location $location
    }

    # Azure functionapp check
    $query = "[?contains(name, 'accbotfunction') && resourceGroup == '" + $resourceGroupName + "']"
    $existingEntity = az functionapp list --query $query | ConvertFrom-Json

    if ( $existingEntity.Count -eq 0 )
    {
        $err = "ERROR: Azure functionapp check failed."
        Write-Error $err
        pause
        exit
    }
}


Write-Host "[Step 7 / 9] Function app -> DONE" -ForegroundColor cyan

#Nastavení proměnných
Write-Host "[Step 8 / 9] Function app settings uploading -> Starting..." -ForegroundColor cyan

$appsettingsResult = az functionapp config appsettings set --name $azFunctionName --resource-group $resourceGroupName `
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

# Azure functionapp settings check
$query = "[?contains(name, 'CosmosDbPrimaryKey')]"
$existingEntity = az functionapp config appsettings list --query $query -g $resourceGroupName -n $azFunctionName  | ConvertFrom-Json

if ( $existingEntity.Count -eq 0 )
{
    $err = "ERROR: Azure functionapp settings check failed."
    Write-Error $err
    pause
    exit
}

Write-Host "[Step 8 / 9] Function app settings uploading -> DONE" -ForegroundColor cyan


#Deploy AccBota do Azure function
Write-Host "[Step 9 / 9] Function app deployment -> Starting..." -ForegroundColor cyan

$env:SCM_DO_BUILD_DURING_DEPLOYMENT='true'

try{
    $DeployAzureFunctionResult = az functionapp deployment source config-zip -g $resourceGroupName -n $azFunctionName --src $zipDeploymentFileName --only-show-errors
}catch{
  Write-Error "ERROR: Azure function deployment failed."
  Write-Error $_
  Write-Host "The script will try to deploy once again..." -ForegroundColor yellow
  $DeployAzureFunctionResult = az functionapp deployment source config-zip -g $resourceGroupName -n $azFunctionName --src $zipDeploymentFileName --only-show-errors
}


# Azure functionapp deployment check
if( $null -eq $DeployAzureFunctionResult ){
    $err = "ERROR: Azure function deployment failed."
    Write-Error $err
    pause
    exit
}

Write-Host "[Step 9 / 9] Function app deployment -> DONE" -ForegroundColor cyan


#Zobrazit výstup co se stalo
$output = "The following entities were successfully created in Azure: `n`t ResourceGroup: " + $resourceGroupName + "`n"`
                + "`t CosmosDBAccount: " + $cosmosDBAccountName + "`n"`
                + "`t Azure storage: " + $storageAccountName + "`n"`
                + "`t Azure function: " + $azFunctionName + "`n"`
                + " => The AccBot bot is already deployed and should now start buying " + $Currency + " at " + $randomMinutes + ". minute every " + $NextHour + "hour."

Write-Host $output -ForegroundColor green
pause