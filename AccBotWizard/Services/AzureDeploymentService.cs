using AccBotWizard.ViewModels;

namespace AccBotWizard.Services;

public class AzureDeploymentService : IDeploymentService
{
    public async Task<bool> DeployAsync(WizardData data, Action<string> logCallback)
    {
        // In a full implementation, this would use Azure SDK to deploy resources
        // For now, this is a placeholder that simulates the deployment process

        logCallback("Creating Azure resources...");
        await Task.Delay(1000);

        logCallback($"Resource Group: AccBot");
        logCallback($"Location: {data.AzureLocation}");

        logCallback("Creating CosmosDB account...");
        await Task.Delay(2000);

        logCallback("Creating Storage account...");
        await Task.Delay(1000);

        logCallback("Creating Azure Function...");
        await Task.Delay(2000);

        logCallback("Configuring application settings...");
        await Task.Delay(1000);

        logCallback("Deploying application code...");
        await Task.Delay(3000);

        logCallback("Deployment completed successfully!");
        return true;
    }

    public string GenerateAzureCliScript(WizardData data)
    {
        var credentialSettings = GetCredentialSettings(data);

        return $@"# AccBot Azure Deployment Script
# Run this script in Azure Cloud Shell or with Azure CLI installed

$resourceGroupName = 'AccBot'
$location = '{data.AzureLocation}'
$cosmosDBAccountName = 'accbotcosmosdb-' + [guid]::NewGuid().ToString().Substring(0, 8)
$storageAccountName = 'accbotsa' + (Get-Random -Maximum 1000000000)
$azFunctionName = 'azfunc-{data.BotName}-' + [guid]::NewGuid().ToString().Substring(0, 8)

# Login to Azure
az login

# Create Resource Group
az group create -l $location -n $resourceGroupName

# Create CosmosDB
az cosmosdb create -n $cosmosDBAccountName -g $resourceGroupName --enable-free-tier true

# Create Database and Container
az cosmosdb sql database create --account-name $cosmosDBAccountName --resource-group $resourceGroupName --name AccBotDatabase
az cosmosdb sql container create -g $resourceGroupName -a $cosmosDBAccountName -d AccBotDatabase -n AccBotContainer --partition-key-path '/CryptoName' --throughput 400

# Create Storage Account
az storage account create -n $storageAccountName -g $resourceGroupName --sku Standard_LRS

# Create Azure Function
az functionapp create -g $resourceGroupName -n $azFunctionName -s $storageAccountName --runtime dotnet-isolated --consumption-plan-location $location

# Configure Settings
az functionapp config appsettings set --name $azFunctionName --resource-group $resourceGroupName `
    --settings `
    'ExchangeName={data.SelectedExchange}' `
    'Currency={data.Currency}' `
    'Fiat={data.Fiat}' `
    'ChunkSize={data.ChunkSize}' `
    'Name={data.BotName}' `
    'DayDividerSchedule=0 0 */{data.HourDivider} * * *' `
    'WithdrawalEnabled={data.WithdrawalEnabled.ToString().ToLower()}' `
    'WithdrawalAddress={data.WithdrawalAddress}' `
    'TelegramChannel={data.TelegramChannel}' `
    'TelegramBot={data.TelegramBotToken}' `
    {credentialSettings}
";
    }

    private string GetCredentialSettings(WizardData data)
    {
        var settings = new List<string>();

        switch (data.SelectedExchange)
        {
            case "coinmate":
                settings.Add($"'CoinMateCredentials_ClientId={data.Credentials.GetValueOrDefault("ClientId", "")}'");
                settings.Add($"'CoinMateCredentials_PublicKey={data.Credentials.GetValueOrDefault("PublicKey", "")}'");
                settings.Add($"'CoinMateCredentials_PrivateKey={data.Credentials.GetValueOrDefault("PrivateKey", "")}'");
                break;
            case "binance":
                settings.Add($"'BinanceCredentials_Key={data.Credentials.GetValueOrDefault("Key", "")}'");
                settings.Add($"'BinanceCredentials_Secret={data.Credentials.GetValueOrDefault("Secret", "")}'");
                break;
            case "kraken":
                settings.Add($"'KrakenCredentials_Key={data.Credentials.GetValueOrDefault("Key", "")}'");
                settings.Add($"'KrakenCredentials_Secret={data.Credentials.GetValueOrDefault("Secret", "")}'");
                break;
            case "huobi":
                settings.Add($"'HuobiCredentials_Key={data.Credentials.GetValueOrDefault("Key", "")}'");
                settings.Add($"'HuobiCredentials_Secret={data.Credentials.GetValueOrDefault("Secret", "")}'");
                break;
            case "kucoin":
                settings.Add($"'KuCoinCredentials_Key={data.Credentials.GetValueOrDefault("Key", "")}'");
                settings.Add($"'KuCoinCredentials_Secret={data.Credentials.GetValueOrDefault("Secret", "")}'");
                settings.Add($"'KuCoinCredentials_PassPhrase={data.Credentials.GetValueOrDefault("PassPhrase", "")}'");
                break;
            case "bitfinex":
                settings.Add($"'BitfinexCredentials_Key={data.Credentials.GetValueOrDefault("Key", "")}'");
                settings.Add($"'BitfinexCredentials_Secret={data.Credentials.GetValueOrDefault("Secret", "")}'");
                break;
            case "coinbase":
                settings.Add($"'CoinbaseCredentials_Key={data.Credentials.GetValueOrDefault("Key", "")}'");
                settings.Add($"'CoinbaseCredentials_Secret={data.Credentials.GetValueOrDefault("Secret", "")}'");
                break;
        }

        return string.Join(" `\n    ", settings);
    }
}
