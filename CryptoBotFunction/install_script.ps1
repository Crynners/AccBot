Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
########################
### LOADING USER-DEFINED VARIABLES ###
########################
$scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
$VariableNameFile = join-path -path $scriptPath -childpath "init_variables.ps1"

. $VariableNameFile


if("coinmate" -eq $ExchangeName){
    $scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
    $VariableNameFile = join-path -path $scriptPath -childpath "coinmate_variables.ps1"
    . $VariableNameFile

}elseif("huobi" -eq $ExchangeName){
    $scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
    $VariableNameFile = join-path -path $scriptPath -childpath "huobi_variables.ps1"
    . $VariableNameFile
}elseif("kraken" -eq $ExchangeName){
    $scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
    $VariableNameFile = join-path -path $scriptPath -childpath "kraken_variables.ps1"
    . $VariableNameFile
}elseif("ftx" -eq $ExchangeName){
    $scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
    $VariableNameFile = join-path -path $scriptPath -childpath "ftx_variables.ps1"
    . $VariableNameFile
}elseif("binance" -eq $ExchangeName){
    $scriptPath = Split-Path $MyInvocation.MyCommand.Path -Parent
    $VariableNameFile = join-path -path $scriptPath -childpath "binance_variables.ps1"
    . $VariableNameFile
}else{
    $err = "ERROR: The exchange name '$ExchangeName' is not supported."
    Write-Error $err
    pause
    exit
}


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


if ( ($NCronTabExpression -eq $null) -or ($NCronTabExpression -eq '')) {

	Write-Verbose "Scheduler is set by HourDivider variable"

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

} else {
		
	Write-Verbose "So you think you are PRO right? OK then, using your `$NCronTabExpression to set `$DayDividerSchedule"

	$DayDividerSchedule = $NCronTabExpression
}



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
                    "ExchangeName=$ExchangeName" `
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
                    "CoinMateCredentials_PrivateKey=$CoinMateCredentials_PrivateKey" `
                    "KrakenCredentials_Key=$KrakenCredentials_Key" `
                    "KrakenCredentials_Secret=$KrakenCredentials_Secret" `
                    "FTXCredentials_Key=$FTXCredentials_Key" `
                    "FTXCredentials_Secret=$FTXCredentials_Secret" `
                    "BinanceCredentials_Key=$BinanceCredentials_Key" `
                    "BinanceCredentials_Secret=$BinanceCredentials_Secret" `
                    "HuobiCredentials_Key=$HuobiCredentials_Key" `
                    "HuobiCredentials_Secret=$HuobiCredentials_Secret"

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

if ( ($NCronTabExpression -eq $null) -or ($NCronTabExpression -eq '')) {
    $DeploymentResult = " => The AccBot bot is already deployed and should now start buying " + $Currency + " at " + $randomMinutes + ". minute every " + $NextHour + "hour." 
}else{
    $DeploymentResult = " => The AccBot bot is already deployed and should now start buying " + $Currency + " by NCRONTAB '" + $NCronTabExpression + "' expression. Check the details https://ncrontab.swimburger.net/"
}

#Zobrazit výstup co se stalo
$output = "The following entities were successfully created in Azure: `n`t ResourceGroup: " + $resourceGroupName + "`n"`
                + "`t CosmosDBAccount: " + $cosmosDBAccountName + "`n"`
                + "`t Azure storage: " + $storageAccountName + "`n"`
                + "`t Azure function: " + $azFunctionName + "`n"`
                + $DeploymentResult

Write-Host $output -ForegroundColor green
pause

