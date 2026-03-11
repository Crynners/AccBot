using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AccBotWizard.ViewModels;

public partial class AccumulationConfigViewModel : ViewModelBase, IValidatable
{
    private readonly MainWindowViewModel _mainViewModel;

    [ObservableProperty]
    private string _currency;

    [ObservableProperty]
    private string _fiat;

    [ObservableProperty]
    private int _chunkSize;

    [ObservableProperty]
    private int _hourDivider;

    [ObservableProperty]
    private string _botName;

    [ObservableProperty]
    private bool _withdrawalEnabled;

    [ObservableProperty]
    private string _withdrawalAddress = "";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasError))]
    private string _errorMessage = "";

    public bool HasError => !string.IsNullOrEmpty(ErrorMessage);

    public ObservableCollection<string> AvailableCurrencies { get; } = new()
    {
        "BTC", "ETH", "LTC", "XRP", "DASH", "XMR"
    };

    public ObservableCollection<string> AvailableFiats { get; } = new();

    public ObservableCollection<int> AvailableHourDividers { get; } = new()
    {
        1, 2, 3, 4, 6, 8, 12, 24
    };

    public AccumulationConfigViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;

        Currency = mainViewModel.Data.Currency;
        Fiat = mainViewModel.Data.Fiat;
        ChunkSize = mainViewModel.Data.ChunkSize;
        HourDivider = mainViewModel.Data.HourDivider;
        BotName = mainViewModel.Data.BotName;
        WithdrawalEnabled = mainViewModel.Data.WithdrawalEnabled;
        WithdrawalAddress = mainViewModel.Data.WithdrawalAddress;

        UpdateAvailableFiats();
    }

    private void UpdateAvailableFiats()
    {
        AvailableFiats.Clear();
        switch (_mainViewModel.Data.SelectedExchange)
        {
            case "coinmate":
                AvailableFiats.Add("CZK");
                AvailableFiats.Add("EUR");
                break;
            case "binance":
                AvailableFiats.Add("USDT");
                AvailableFiats.Add("BUSD");
                AvailableFiats.Add("USDC");
                AvailableFiats.Add("EUR");
                break;
            default:
                AvailableFiats.Add("USDT");
                AvailableFiats.Add("USD");
                AvailableFiats.Add("EUR");
                break;
        }

        if (!AvailableFiats.Contains(Fiat))
        {
            Fiat = AvailableFiats.First();
        }
    }

    public Task<bool> ValidateAsync()
    {
        if (ChunkSize <= 0)
        {
            ErrorMessage = "Chunk size must be greater than 0";
            return Task.FromResult(false);
        }

        if (string.IsNullOrWhiteSpace(BotName))
        {
            ErrorMessage = "Bot name is required";
            return Task.FromResult(false);
        }

        if (WithdrawalEnabled && string.IsNullOrWhiteSpace(WithdrawalAddress))
        {
            ErrorMessage = "Withdrawal address is required when withdrawal is enabled";
            return Task.FromResult(false);
        }

        // Save to wizard data
        _mainViewModel.Data.Currency = Currency;
        _mainViewModel.Data.Fiat = Fiat;
        _mainViewModel.Data.ChunkSize = ChunkSize;
        _mainViewModel.Data.HourDivider = HourDivider;
        _mainViewModel.Data.BotName = BotName;
        _mainViewModel.Data.WithdrawalEnabled = WithdrawalEnabled;
        _mainViewModel.Data.WithdrawalAddress = WithdrawalAddress;

        ErrorMessage = "";
        return Task.FromResult(true);
    }
}
