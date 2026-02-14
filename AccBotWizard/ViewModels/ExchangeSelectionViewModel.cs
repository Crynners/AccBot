using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AccBotWizard.ViewModels;

public partial class ExchangeSelectionViewModel : ViewModelBase, IValidatable
{
    private readonly MainWindowViewModel _mainViewModel;

    [ObservableProperty]
    private ExchangeInfo? _selectedExchange;

    public ObservableCollection<ExchangeInfo> Exchanges { get; } = new()
    {
        new ExchangeInfo("coinmate", "Coinmate", "Czech exchange with CZK/EUR support, low fees for CZ/SK users"),
        new ExchangeInfo("binance", "Binance", "World's largest crypto exchange, wide variety of trading pairs"),
        new ExchangeInfo("kraken", "Kraken", "US-based exchange, known for security and fiat support"),
        new ExchangeInfo("huobi", "Huobi", "Asian exchange with global reach, USDT pairs"),
        new ExchangeInfo("kucoin", "KuCoin", "Singapore-based, extensive altcoin selection"),
        new ExchangeInfo("bitfinex", "Bitfinex", "Professional trading platform, advanced features"),
        new ExchangeInfo("coinbase", "Coinbase", "US-based, beginner-friendly, regulated exchange")
    };

    public ExchangeSelectionViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;
        SelectedExchange = Exchanges.FirstOrDefault(e => e.Id == _mainViewModel.Data.SelectedExchange);
    }

    partial void OnSelectedExchangeChanged(ExchangeInfo? value)
    {
        if (value != null)
        {
            _mainViewModel.Data.SelectedExchange = value.Id;
        }
    }

    public Task<bool> ValidateAsync()
    {
        return Task.FromResult(SelectedExchange != null);
    }
}

public record ExchangeInfo(string Id, string Name, string Description);
