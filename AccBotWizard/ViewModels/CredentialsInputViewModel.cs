using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AccBotWizard.ViewModels;

public partial class CredentialsInputViewModel : ViewModelBase, IValidatable
{
    private readonly MainWindowViewModel _mainViewModel;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasError))]
    private string _errorMessage = "";

    public bool HasError => !string.IsNullOrEmpty(ErrorMessage);

    public ObservableCollection<CredentialField> CredentialFields { get; } = new();

    public CredentialsInputViewModel(MainWindowViewModel mainViewModel)
    {
        _mainViewModel = mainViewModel;
        UpdateCredentialFields();
    }

    public void UpdateCredentialFields()
    {
        CredentialFields.Clear();

        switch (_mainViewModel.Data.SelectedExchange)
        {
            case "coinmate":
                CredentialFields.Add(new CredentialField("ClientId", "Client ID", false));
                CredentialFields.Add(new CredentialField("PublicKey", "Public Key", false));
                CredentialFields.Add(new CredentialField("PrivateKey", "Private Key", true));
                break;
            case "binance":
            case "huobi":
            case "kraken":
            case "bitfinex":
            case "coinbase":
                CredentialFields.Add(new CredentialField("Key", "API Key", false));
                CredentialFields.Add(new CredentialField("Secret", "API Secret", true));
                break;
            case "kucoin":
                CredentialFields.Add(new CredentialField("Key", "API Key", false));
                CredentialFields.Add(new CredentialField("Secret", "API Secret", true));
                CredentialFields.Add(new CredentialField("PassPhrase", "Passphrase", true));
                break;
        }

        // Load existing values
        foreach (var field in CredentialFields)
        {
            if (_mainViewModel.Data.Credentials.TryGetValue(field.Id, out var value))
            {
                field.Value = value;
            }
        }
    }

    public Task<bool> ValidateAsync()
    {
        // Save values to wizard data
        _mainViewModel.Data.Credentials.Clear();
        foreach (var field in CredentialFields)
        {
            _mainViewModel.Data.Credentials[field.Id] = field.Value;
        }

        // Validate all fields are filled
        foreach (var field in CredentialFields)
        {
            if (string.IsNullOrWhiteSpace(field.Value))
            {
                ErrorMessage = $"{field.Label} is required";
                return Task.FromResult(false);
            }
        }

        ErrorMessage = "";
        return Task.FromResult(true);
    }
}

public partial class CredentialField : ObservableObject
{
    public string Id { get; }
    public string Label { get; }
    public bool IsPassword { get; }

    [ObservableProperty]
    private string _value = "";

    public CredentialField(string id, string label, bool isPassword)
    {
        Id = id;
        Label = label;
        IsPassword = isPassword;
    }
}
