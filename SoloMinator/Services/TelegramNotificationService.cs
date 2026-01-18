using Microsoft.Extensions.Options;

namespace SoloMinator.Services;

public interface ITelegramNotificationService
{
    Task SendSubscriptionPausedNotificationAsync(string chatId, string address);
    Task SendSubscriptionResumedNotificationAsync(string chatId, string address);
    Task SendSubscriptionDeletedNotificationAsync(string chatId, string address);
    Task SendAddressDeletedNotificationAsync(List<string> chatIds, string address);
}

public class TelegramNotificationService : ITelegramNotificationService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly TelegramSettings _settings;
    private readonly ILogger<TelegramNotificationService> _logger;

    public TelegramNotificationService(
        IHttpClientFactory httpClientFactory,
        IOptions<TelegramSettings> settings,
        ILogger<TelegramNotificationService> logger)
    {
        _httpClientFactory = httpClientFactory;
        _settings = settings.Value;
        _logger = logger;
    }

    public async Task SendSubscriptionPausedNotificationAsync(string chatId, string address)
    {
        var message = $"Notifications for mining address `{EscapeMarkdown(address)}` have been *paused*.\n\n" +
                     "You will no longer receive updates for this address until notifications are resumed.";
        await SendMessageAsync(chatId, message);
    }

    public async Task SendSubscriptionResumedNotificationAsync(string chatId, string address)
    {
        var message = $"Notifications for mining address `{EscapeMarkdown(address)}` have been *resumed*.\n\n" +
                     "You will now receive updates for this address.";
        await SendMessageAsync(chatId, message);
    }

    public async Task SendSubscriptionDeletedNotificationAsync(string chatId, string address)
    {
        var message = $"Subscription for mining address `{EscapeMarkdown(address)}` has been *deleted*.\n\n" +
                     "You will no longer receive notifications for this address.";
        await SendMessageAsync(chatId, message);
    }

    public async Task SendAddressDeletedNotificationAsync(List<string> chatIds, string address)
    {
        var message = $"Mining address `{EscapeMarkdown(address)}` has been *unregistered* from SoloMinator.\n\n" +
                     "All subscriptions for this address have been removed.";

        foreach (var chatId in chatIds)
        {
            await SendMessageAsync(chatId, message);
        }
    }

    private async Task SendMessageAsync(string chatId, string text)
    {
        if (string.IsNullOrEmpty(_settings.BotToken))
        {
            _logger.LogWarning("Cannot send Telegram message - BotToken is not configured");
            return;
        }

        try
        {
            var client = _httpClientFactory.CreateClient();
            var url = $"https://api.telegram.org/bot{_settings.BotToken}/sendMessage";

            var content = new FormUrlEncodedContent(new[]
            {
                new KeyValuePair<string, string>("chat_id", chatId),
                new KeyValuePair<string, string>("text", text),
                new KeyValuePair<string, string>("parse_mode", "Markdown")
            });

            var response = await client.PostAsync(url, content);
            response.EnsureSuccessStatusCode();

            _logger.LogInformation("Sent notification to chat {ChatId}", chatId);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending Telegram notification to chat {ChatId}", chatId);
        }
    }

    private static string EscapeMarkdown(string? text)
    {
        if (string.IsNullOrEmpty(text))
            return string.Empty;

        return text
            .Replace("\\", "\\\\")
            .Replace("*", "\\*")
            .Replace("_", "\\_")
            .Replace("`", "\\`")
            .Replace("[", "\\[")
            .Replace("]", "\\]");
    }
}
