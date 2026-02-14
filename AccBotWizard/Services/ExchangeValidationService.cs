using AccBotWizard.ViewModels;

namespace AccBotWizard.Services;

public class ExchangeValidationService
{
    public async Task<ValidationResult> ValidateCredentialsAsync(WizardData data)
    {
        try
        {
            // In a full implementation, this would actually test the API credentials
            // For now, we just validate the format

            switch (data.SelectedExchange)
            {
                case "coinmate":
                    if (!data.Credentials.ContainsKey("ClientId") ||
                        !data.Credentials.ContainsKey("PublicKey") ||
                        !data.Credentials.ContainsKey("PrivateKey"))
                    {
                        return new ValidationResult(false, "All Coinmate credentials are required");
                    }
                    break;

                case "kucoin":
                    if (!data.Credentials.ContainsKey("Key") ||
                        !data.Credentials.ContainsKey("Secret") ||
                        !data.Credentials.ContainsKey("PassPhrase"))
                    {
                        return new ValidationResult(false, "All KuCoin credentials including passphrase are required");
                    }
                    break;

                default:
                    if (!data.Credentials.ContainsKey("Key") ||
                        !data.Credentials.ContainsKey("Secret"))
                    {
                        return new ValidationResult(false, "API Key and Secret are required");
                    }
                    break;
            }

            // Simulate API check delay
            await Task.Delay(500);

            return new ValidationResult(true, "Credentials validated successfully");
        }
        catch (Exception ex)
        {
            return new ValidationResult(false, $"Validation error: {ex.Message}");
        }
    }

    public async Task<ValidationResult> TestConnectionAsync(WizardData data)
    {
        try
        {
            // In a full implementation, this would make an actual API call to test the connection
            await Task.Delay(1000);

            return new ValidationResult(true, "Connection test successful");
        }
        catch (Exception ex)
        {
            return new ValidationResult(false, $"Connection test failed: {ex.Message}");
        }
    }
}

public record ValidationResult(bool IsValid, string Message);
