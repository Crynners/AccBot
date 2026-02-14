using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System.Collections.ObjectModel;

namespace AccBotWizard.ViewModels;

public partial class MainWindowViewModel : ViewModelBase
{
    [ObservableProperty]
    private int _currentStep;

    [ObservableProperty]
    private ViewModelBase? _currentStepViewModel;

    [ObservableProperty]
    private bool _canGoBack;

    [ObservableProperty]
    private bool _canGoNext = true;

    [ObservableProperty]
    private string _nextButtonText = "Next";

    public ObservableCollection<string> Steps { get; } = new()
    {
        "Welcome",
        "Select Exchange",
        "API Credentials",
        "DCA Configuration",
        "Telegram Setup",
        "Deployment",
        "Review & Deploy"
    };

    // Wizard data
    public WizardData Data { get; } = new();

    private readonly List<ViewModelBase> _stepViewModels;

    public MainWindowViewModel()
    {
        _stepViewModels = new List<ViewModelBase>
        {
            new WelcomeViewModel(this),
            new ExchangeSelectionViewModel(this),
            new CredentialsInputViewModel(this),
            new AccumulationConfigViewModel(this),
            new TelegramSetupViewModel(this),
            new DeploymentSelectionViewModel(this),
            new ReviewDeployViewModel(this)
        };

        CurrentStepViewModel = _stepViewModels[0];
        UpdateNavigationState();
    }

    [RelayCommand]
    private void GoBack()
    {
        if (CurrentStep > 0)
        {
            CurrentStep--;
            CurrentStepViewModel = _stepViewModels[CurrentStep];
            UpdateNavigationState();
        }
    }

    [RelayCommand]
    private async Task GoNext()
    {
        if (CurrentStep < Steps.Count - 1)
        {
            // Validate current step before proceeding
            if (CurrentStepViewModel is IValidatable validatable && !await validatable.ValidateAsync())
            {
                return;
            }

            CurrentStep++;
            CurrentStepViewModel = _stepViewModels[CurrentStep];
            UpdateNavigationState();
        }
        else
        {
            // Final step - deploy
            if (CurrentStepViewModel is ReviewDeployViewModel reviewVm)
            {
                await reviewVm.DeployAsync();
            }
        }
    }

    private void UpdateNavigationState()
    {
        CanGoBack = CurrentStep > 0;
        CanGoNext = true;
        NextButtonText = CurrentStep == Steps.Count - 1 ? "Deploy" : "Next";
    }
}

public interface IValidatable
{
    Task<bool> ValidateAsync();
}

public class WizardData
{
    // Exchange
    public string SelectedExchange { get; set; } = "coinmate";

    // Credentials
    public Dictionary<string, string> Credentials { get; set; } = new();

    // DCA Configuration
    public string Currency { get; set; } = "BTC";
    public string Fiat { get; set; } = "EUR";
    public int ChunkSize { get; set; } = 10;
    public int HourDivider { get; set; } = 1;
    public string BotName { get; set; } = "BTC-AccBot";

    // Telegram
    public string TelegramBotToken { get; set; } = "";
    public string TelegramChannel { get; set; } = "";

    // Withdrawal
    public bool WithdrawalEnabled { get; set; } = false;
    public string WithdrawalAddress { get; set; } = "";
    public decimal MaxWithdrawalPercentageFee { get; set; } = 0.001m;

    // Deployment
    public string DeploymentTarget { get; set; } = "Azure";
    public string AzureLocation { get; set; } = "germanywestcentral";
}
