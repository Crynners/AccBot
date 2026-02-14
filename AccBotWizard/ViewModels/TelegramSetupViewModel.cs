using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace AccBotWizard.ViewModels;

public partial class TelegramSetupViewModel : ViewModelBase, IValidatable
{
    private readonly MainWindowViewModel _mainViewModel;

    [ObservableProperty]
    private string _botToken = "";

    [ObservableProperty]
    private string _channelName = "";

    [ObservableProperty]
    private string _errorMessage = "";

    public string SetupInstructions => @"Follow these steps to set up Telegram notifications:

1. Open Telegram and search for @BotFather
2. Send /newbot and follow the instructions to create a new bot
3. Copy the API token and paste it below
4. Create a new channel in Telegram
5. Add your bot as an administrator to the channel
6. Enter the channel name below (e.g., @MyAccBotChannel)

For private channels:
- Open the channel in Telegram Web
- Copy the channel ID from the URL (e.g., -100123456789)
- Use the full ID including -100 prefix";

    public TelegramSetupViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;
        BotToken = mainViewModel.Data.TelegramBotToken;
        ChannelName = mainViewModel.Data.TelegramChannel;
    }

    [RelayCommand]
    private void OpenBotFather()
    {
        try
        {
            var psi = new System.Diagnostics.ProcessStartInfo
            {
                FileName = "https://t.me/BotFather",
                UseShellExecute = true
            };
            System.Diagnostics.Process.Start(psi);
        }
        catch
        {
            // Ignore if browser cannot be opened
        }
    }

    public Task<bool> ValidateAsync()
    {
        if (string.IsNullOrWhiteSpace(BotToken))
        {
            ErrorMessage = "Telegram bot token is required";
            return Task.FromResult(false);
        }

        if (string.IsNullOrWhiteSpace(ChannelName))
        {
            ErrorMessage = "Telegram channel name is required";
            return Task.FromResult(false);
        }

        // Save to wizard data
        _mainViewModel.Data.TelegramBotToken = BotToken;
        _mainViewModel.Data.TelegramChannel = ChannelName;

        ErrorMessage = "";
        return Task.FromResult(true);
    }
}
