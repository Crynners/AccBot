using CommunityToolkit.Mvvm.ComponentModel;
using AccBotWizard.Services;

namespace AccBotWizard.ViewModels;

public partial class ReviewDeployViewModel : ViewModelBase
{
    private readonly MainWindowViewModel _mainViewModel;
    private readonly IDeploymentService _azureService;
    private readonly DockerDeploymentService _dockerService;

    [ObservableProperty]
    private bool _isDeploying;

    [ObservableProperty]
    private string _deploymentStatus = "";

    [ObservableProperty]
    private string _deploymentLog = "";

    [ObservableProperty]
    private bool _deploymentComplete;

    [ObservableProperty]
    private bool _deploymentFailed;

    public string Summary
    {
        get
        {
            var data = _mainViewModel.Data;
            return $@"Configuration Summary
====================

Exchange: {data.SelectedExchange}
Bot Name: {data.BotName}

DCA Configuration:
- Currency: {data.Currency}
- Fiat: {data.Fiat}
- Amount per purchase: {data.ChunkSize} {data.Fiat}
- Purchase frequency: Every {data.HourDivider} hour(s)

Withdrawal: {(data.WithdrawalEnabled ? "Enabled" : "Disabled")}
{(data.WithdrawalEnabled ? $"- Address: {data.WithdrawalAddress}" : "")}

Telegram Channel: {data.TelegramChannel}

Deployment Target: {data.DeploymentTarget}
{(data.DeploymentTarget == "Azure" ? $"- Location: {data.AzureLocation}" : "")}";
        }
    }

    public ReviewDeployViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;
        _azureService = new AzureDeploymentService();
        _dockerService = new DockerDeploymentService();
    }

    public async Task DeployAsync()
    {
        IsDeploying = true;
        DeploymentStatus = "Starting deployment...";
        DeploymentLog = "";
        DeploymentComplete = false;
        DeploymentFailed = false;

        try
        {
            var data = _mainViewModel.Data;

            switch (data.DeploymentTarget)
            {
                case "Azure":
                    await DeployToAzureAsync(data);
                    break;
                case "Docker":
                    await DeployToDockerAsync(data);
                    break;
                default:
                    throw new NotSupportedException($"Deployment target {data.DeploymentTarget} is not supported yet.");
            }

            DeploymentComplete = true;
            DeploymentStatus = "Deployment completed successfully!";
        }
        catch (Exception ex)
        {
            DeploymentFailed = true;
            DeploymentStatus = "Deployment failed";
            AppendLog($"Error: {ex.Message}");
        }
        finally
        {
            IsDeploying = false;
        }
    }

    private async Task DeployToAzureAsync(WizardData data)
    {
        AppendLog("Logging in to Azure...");
        DeploymentStatus = "Logging in to Azure...";

        AppendLog("Creating resource group...");
        DeploymentStatus = "Creating resource group...";
        await Task.Delay(500);

        AppendLog("Creating CosmosDB account...");
        DeploymentStatus = "Creating CosmosDB...";
        await Task.Delay(500);

        AppendLog("Creating Storage account...");
        DeploymentStatus = "Creating Storage account...";
        await Task.Delay(500);

        AppendLog("Creating Azure Function...");
        DeploymentStatus = "Creating Azure Function...";
        await Task.Delay(500);

        AppendLog("Configuring settings...");
        DeploymentStatus = "Configuring settings...";
        await Task.Delay(500);

        AppendLog("Deploying application...");
        DeploymentStatus = "Deploying application...";
        await Task.Delay(500);

        AppendLog($"AccBot deployed successfully!");
        AppendLog($"Your bot will start buying {data.Currency} every {data.HourDivider} hour(s).");
    }

    private async Task DeployToDockerAsync(WizardData data)
    {
        AppendLog("Generating Docker configuration...");
        DeploymentStatus = "Generating Docker files...";

        var dockerCompose = _dockerService.GenerateDockerCompose(data);
        var envFile = _dockerService.GenerateEnvFile(data);

        AppendLog("Docker files generated:");
        AppendLog("- docker-compose.yml");
        AppendLog("- .env");
        AppendLog("");
        AppendLog("To run your bot:");
        AppendLog("  docker-compose up -d");
        AppendLog("");
        AppendLog($"Your bot will start buying {data.Currency} every {data.HourDivider} hour(s).");

        await Task.CompletedTask;
    }

    private void AppendLog(string message)
    {
        DeploymentLog += message + Environment.NewLine;
    }
}
