namespace AccBotWizard.ViewModels;

public partial class WelcomeViewModel : ViewModelBase
{
    private readonly MainWindowViewModel _mainViewModel;

    public string WelcomeTitle => "Welcome to AccBot Setup Wizard";

    public string WelcomeDescription => @"AccBot is an open-source DCA (Dollar Cost Averaging) bot that automatically buys cryptocurrency at regular intervals.

Key Features:
- Self-custody: Your API keys stay on your own infrastructure
- Automatic purchases at configurable intervals
- Telegram notifications for every transaction
- Optional automatic withdrawal to your wallet
- Multiple exchange support

This wizard will guide you through:
1. Selecting your exchange
2. Entering API credentials
3. Configuring your DCA strategy
4. Setting up Telegram notifications
5. Deploying to your chosen platform

Your data never leaves your infrastructure.";

    public WelcomeViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;
    }
}
