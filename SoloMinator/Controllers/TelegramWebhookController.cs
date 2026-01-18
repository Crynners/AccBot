using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using SoloMinator.Data;
using SoloMinator.Services;
using Telegram.Bot.Types;
using Telegram.Bot.Types.Enums;

namespace SoloMinator.Controllers;

[ApiController]
[Route("api/telegram")]
public class TelegramWebhookController : ControllerBase
{
    private readonly ITelegramLinkService _linkService;
    private readonly ITelegramSubscriptionService _subscriptionService;
    private readonly ICKPoolService _poolService;
    private readonly SoloMinatorContext _dbContext;
    private readonly TelegramSettings _settings;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<TelegramWebhookController> _logger;

    public TelegramWebhookController(
        ITelegramLinkService linkService,
        ITelegramSubscriptionService subscriptionService,
        ICKPoolService poolService,
        SoloMinatorContext dbContext,
        IOptions<TelegramSettings> settings,
        IHttpClientFactory httpClientFactory,
        ILogger<TelegramWebhookController> logger)
    {
        _linkService = linkService;
        _subscriptionService = subscriptionService;
        _poolService = poolService;
        _dbContext = dbContext;
        _settings = settings.Value;
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    [HttpPost("webhook/{secret}")]
    public async Task<IActionResult> HandleWebhook(string secret, [FromBody] Update update)
    {
        // Validate webhook secret
        if (secret != _settings.WebhookSecret)
        {
            _logger.LogWarning("Invalid webhook secret received");
            return Unauthorized();
        }

        if (update.Type != UpdateType.Message || update.Message?.Text == null)
        {
            return Ok();
        }

        var message = update.Message;
        var text = message.Text;
        var chatId = message.Chat.Id.ToString();

        _logger.LogInformation("Received message from chat {ChatId}: {Text}", chatId, text);

        // Handle /start command with token
        if (text.StartsWith("/start "))
        {
            var token = text.Substring(7).Trim();
            await HandleStartCommandAsync(chatId, token, message);
        }
        else if (text == "/start")
        {
            await SendMessageAsync(chatId, "Welcome to SoloMinator Bot! 🎉\n\nTo link your mining account, please generate a link from the SoloMinator website Settings page and click on it.");
        }

        return Ok();
    }

    private async Task HandleStartCommandAsync(string chatId, string token, Message message)
    {
        var linkToken = await _linkService.ValidateTokenAsync(token);

        if (linkToken == null)
        {
            await SendMessageAsync(chatId, "⚠️ This link is invalid or has expired.\n\nPlease generate a new link from the SoloMinator website Settings page.");
            return;
        }

        // Create subscription
        var username = message.From?.Username;
        var firstName = message.From?.FirstName;

        try
        {
            var subscription = await _subscriptionService.CreateSubscriptionAsync(
                linkToken.UserRegistrationId,
                chatId,
                username,
                firstName);

            // Mark token as used
            await _linkService.MarkTokenAsUsedAsync(linkToken.Id);

            var address = linkToken.UserRegistration?.MiningAddress ?? "Unknown";
            var poolVariant = linkToken.UserRegistration?.PoolVariant ?? "solo";
            var displayName = !string.IsNullOrEmpty(firstName) ? firstName : "there";

            await SendMessageAsync(chatId,
                $"✅ Success, {displayName}!\n\n" +
                $"This chat is now linked to your mining address:\n" +
                $"`{address}`\n\n" +
                $"You will receive notifications about:\n" +
                $"• New best shares\n" +
                $"• Bitcoin difficulty adjustments\n\n" +
                $"You can manage your subscriptions on the SoloMinator website.");

            _logger.LogInformation("Successfully created subscription for registration {RegistrationId} with chat {ChatId}",
                linkToken.UserRegistrationId, chatId);

            // Send welcome notification with current stats
            await SendWelcomeStatsNotificationAsync(chatId, address, poolVariant);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error creating subscription for token");
            await SendMessageAsync(chatId, "❌ An error occurred while linking your account. Please try again later.");
        }
    }

    private async Task SendWelcomeStatsNotificationAsync(string chatId, string address, string poolVariant)
    {
        try
        {
            // Fetch current stats from pool
            var (isValid, stats, error) = await _poolService.ValidateAndGetStatsAsync(address, poolVariant);

            if (!isValid || stats == null)
            {
                _logger.LogWarning("Could not fetch stats for welcome notification: {Error}", error);
                return;
            }

            // Get current difficulty
            var latestDifficulty = await _dbContext.MiningDifficultyAdjustments
                .OrderByDescending(x => x.BlockHeight)
                .FirstOrDefaultAsync();

            if (latestDifficulty == null)
            {
                _logger.LogWarning("Could not get latest difficulty for welcome notification");
                return;
            }

            // Find last sufficient difficulty date
            var lastSufficientDiff = await _dbContext.MiningDifficultyAdjustments
                .Where(x => stats.BestEver >= x.Difficulty)
                .OrderByDescending(x => x.BlockTime)
                .FirstOrDefaultAsync();

            var suffDate = lastSufficientDiff != null
                ? lastSufficientDiff.BlockTime.ToString("dd. MM. yyyy")
                : "N/A";

            var difficultyPercentage = (stats.BestEver / latestDifficulty.Difficulty) * 100;

            // Format welcome notification
            var workerInfo = "";
            if (stats.Worker != null && stats.Worker.Count > 0)
            {
                workerInfo = "\n🏆 *Your Workers:*\n";
                for (int i = 0; i < stats.Worker.Count && i < 5; i++)
                {
                    var worker = stats.Worker[i];
                    var workerName = worker.WorkerName.Contains('.')
                        ? worker.WorkerName.Substring(worker.WorkerName.LastIndexOf('.') + 1)
                        : worker.WorkerName;
                    workerInfo += $"{i + 1}. {workerName}: {FormatShareNumber(worker.BestEver)}\n";
                }
            }

            var message = $"📊 *Your Current Mining Stats* 📊\n\n" +
                         $"💎 *Best Share Ever:*\n" +
                         $"• {FormatShareNumber(stats.BestEver)} ({difficultyPercentage:F5}% of current difficulty)\n\n" +
                         $"⛏️ *Pool:* {poolVariant}\n" +
                         $"📅 *Last Sufficient Diff:* {suffDate}\n" +
                         $"🔢 *Total Shares:* {stats.Shares:N0}\n" +
                         workerInfo +
                         $"\n_You will be notified when you find a new best share!_";

            await SendMessageAsync(chatId, message);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending welcome stats notification");
        }
    }

    private static string FormatShareNumber(double number)
    {
        string[] units = { "", "K", "M", "G", "T", "P", "E" };
        int unitIndex = 0;

        while (Math.Abs(number) >= 1000 && unitIndex < units.Length - 1)
        {
            number /= 1000;
            unitIndex++;
        }

        return $"{Math.Round(number, 2)}{units[unitIndex]}";
    }

    private async Task SendMessageAsync(string chatId, string text)
    {
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
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending Telegram message to chat {ChatId}", chatId);
        }
    }
}
