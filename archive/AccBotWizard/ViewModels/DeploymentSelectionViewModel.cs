using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AccBotWizard.ViewModels;

public partial class DeploymentSelectionViewModel : ViewModelBase, IValidatable
{
    private readonly MainWindowViewModel _mainViewModel;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowAzureOptions))]
    private DeploymentOption? _selectedDeployment;

    [ObservableProperty]
    private string _azureLocation = "germanywestcentral";

    public bool ShowAzureOptions => SelectedDeployment?.Id == "Azure";

    public ObservableCollection<DeploymentOption> DeploymentOptions { get; } = new()
    {
        new DeploymentOption("Azure", "Azure Functions", "Deploy to Microsoft Azure with automatic scaling. ~0.04 EUR/month.", true),
        new DeploymentOption("Docker", "Docker Container", "Run as a Docker container on your own server. Free hosting.", true),
        new DeploymentOption("AWS", "AWS Lambda", "Deploy to Amazon Web Services. Coming soon.", false),
        new DeploymentOption("GCP", "Google Cloud Functions", "Deploy to Google Cloud Platform. Coming soon.", false)
    };

    public ObservableCollection<string> AzureLocations { get; } = new()
    {
        "westeurope",
        "northeurope",
        "germanywestcentral",
        "eastus",
        "westus",
        "centralus",
        "uksouth",
        "australiaeast",
        "southeastasia"
    };

    public DeploymentSelectionViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;
        SelectedDeployment = DeploymentOptions.FirstOrDefault(d => d.Id == mainViewModel.Data.DeploymentTarget);
        AzureLocation = mainViewModel.Data.AzureLocation;
    }

    partial void OnSelectedDeploymentChanged(DeploymentOption? value)
    {
        if (value != null)
        {
            _mainViewModel.Data.DeploymentTarget = value.Id;
        }
    }

    partial void OnAzureLocationChanged(string value)
    {
        _mainViewModel.Data.AzureLocation = value;
    }

    public Task<bool> ValidateAsync()
    {
        if (SelectedDeployment == null)
        {
            return Task.FromResult(false);
        }

        if (!SelectedDeployment.IsAvailable)
        {
            return Task.FromResult(false);
        }

        return Task.FromResult(true);
    }
}

public record DeploymentOption(string Id, string Name, string Description, bool IsAvailable);
