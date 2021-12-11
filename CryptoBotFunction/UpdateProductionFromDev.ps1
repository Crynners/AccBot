#Requires -RunAsAdministrator
#Requires -Version 5.0

$ErrorActionPreference = "Stop"

$CurrentDirectory = "$PSScriptRoot"
$BinaryFolderOutput = "$CurrentDirectory\CryptoBotFunction\bin\Release\net6.0"

$zipFile = "AccBot.zip"
$resourceGroupName= "AccBot"

Write-Output "This script will" 
Write-Output "- build the local code"
Write-Output "- update your production environment without changing any configuration"
Write-Output "It is meant to be use by humans as part of the development/test process"
while($yesno -ne "y" -and $yesno -ne "n"){
    $yesno = Read-Host "Continue? [y/n]"
    if($yesno -eq "n"){
        exit 0
    }
}

Write-Output "Checking the presence of the choco command to automatically install any missing dependencies for this script to succeed"
Get-Command choco | Out-Null
if(! $?){
    Write-Output "If you want this script to install the required dependencies,"
    Write-Output "please install chocolatey first : https://chocolatey.org/install"
    exit 1
}

Write-Output "Checking the installed dotnet framework"
Get-Command dotnet | Out-Null
if(! $?){
    Write-Output "The dotnet command is not available"
    choco install dotnet-sdk
}
$version = [Version](dotnet --version)
if($Null -eq $version){
    Write-Output "Error trying to get the dotnet version"
    exit 1
}
if($version -lt [Version]"6.0"){
    Write-Output "The installed dotnet version is too low, it should be at least 3.1"
    exit 1
}

if(! (get-command dotnet).Source -like "*\Program Files\*"){
    Write-Output "We advise to install the x64 binary of dotnet instead of the x86 version"
    Write-Output "Apparently, Visual Studio code debugging function requires x64 binaries"
    Write-Output "Source : http://disq.us/p/2ghvcri"
}

Write-Output "Checking the installed azure functions tools"
Get-Command func | Out-Null
if(! $?){
    choco upgrade -y azure-functions-core-tools --params "'/x64'" # is equal to : choco install azure-functions-core-tools-3 --params="'/x64:true'"
}

# the following lines are an adaptation of the content of the file AccBot/.github/workflows/dotnet.yml
Write-Output "Building the project"
dotnet restore
if(! $?){
  pause
  exit 1
}
dotnet build --no-restore -c release
if(! $?){
  pause
  exit 1
}
Write-Output "Packaging the binaries"
Remove-Item "$BinaryFolderOutput\local.settings.json"
Remove-Item "$BinaryFolderOutput\*.ps1"
Remove-Item "$BinaryFolderOutput\*.bat"
Compress-Archive -Force -Path "$BinaryFolderOutput\*" -DestinationPath "$CurrentDirectory\$zipFile" # overwrite if present

Write-Output "We will now execute a command like ""az functionapp deployment source config-zip -g <resource_group> -n <app_name> --src "$CurrentDirectory\$zipFile""""
Write-Output "This will update your production environment without changing any configuration"
Write-Output "But it will first require some actions from you"
$AccBotName = "BTC-AccBot" # Default value
while($yesno -ne "y" -and $yesno -ne "n"){
    $yesno = Read-Host "Do you want to specify a different AccBotName than ""$AccBotName"" as the Bot to update? [y/n]"
    if($yesno -eq "y"){
        $AccBotName = Read-Host "Please carefully enter the AccBotName (only one attempt):"
    }
}

Write-Output "We will now log you to Azure to update the Bot"
# Login to Azure
$LoginResult = az login --allow-no-subscriptions --only-show-errors  | ConvertFrom-Json
if (0 -eq $LoginResult.Count){
    Write-Error "ERROR: Azure subscription does not exists. Please create the new subscription in the Azure portal. (https://https://portal.azure.com/)"
    pause
    exit 1
}
Write-Output "Login to Azure was successful"
Write-Output "Checking the resource group presence"
# Check if the Resource group "AccBot" exists as expected
try{
    $resourceGroup = az group list --query "[?name == '$resourceGroupName']" | ConvertFrom-Json
}catch{
    Write-Error $_
    pause
    exit 1
}
if ($resourceGroup.Count -eq 0){
    Write-Warning "Resource group $resourceGroupName doesn't exist. Aborting."
    pause
    exit 1
}

Write-Output "Getting the function app name for the AccBot name you provided"
# Find the name of the Function App "azfunc-[xxx]-[guid]" present in the resource group "AccBot"
$query = "[?contains(name, 'azfunc-" + $AccBotName + "') && resourceGroup == '" + $resourceGroupName + "']"
$existingFunctionAppEntity = az functionapp list --query $query | ConvertFrom-Json
if ($existingFunctionAppEntity.Count -gt 0){
    $azFunctionName = $existingFunctionAppEntity[0].name

    Write-Output "Deploying the zip ""$CurrentDirectory\zipFile"" to the function app ""$azFunctionName"" in the resource group ""$resourceGroupName"""
    az functionapp deployment source config-zip -g "$resourceGroupName" -n "$azFunctionName" --src "$CurrentDirectory\$zipFile"""
}
else{
    Write-Output "Couldn't find any Azure Function App starting with ""azfunc-"" in the resource group ""$resourceGroupName"". Aborting."
    pause
    exit 1
}
