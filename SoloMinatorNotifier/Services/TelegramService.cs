using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Telegram.Bot;
using Telegram.Bot.Types.Enums;

namespace SoloMinatorNotifier.Services
{
    public interface ITelegramService
    {
        Task SendNotificationAsync(string chatId, string message);
        Task SendNotificationToAllAsync(IEnumerable<string> chatIds, string message);
        Task SendErrorNotificationAsync(string message);
    }

    public class TelegramService : ITelegramService
    {
        private readonly ITelegramBotClient _botClient;
        private readonly IConfiguration _configuration;
        private readonly ILogger<TelegramService> _logger;

        public TelegramService(
            ITelegramBotClient botClient,
            IConfiguration configuration,
            ILogger<TelegramService> logger)
        {
            _botClient = botClient;
            _configuration = configuration;
            _logger = logger;
        }

        public async Task SendNotificationAsync(string chatId, string message)
        {
            try
            {
                await _botClient.SendTextMessageAsync(
                    chatId: chatId,
                    text: message,
                    parseMode: ParseMode.Markdown);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to send notification to chat {ChatId}", chatId);
            }
        }

        public async Task SendNotificationToAllAsync(IEnumerable<string> chatIds, string message)
        {
            var tasks = chatIds.Select(chatId => SendNotificationAsync(chatId, message));
            await Task.WhenAll(tasks);
        }

        public async Task SendErrorNotificationAsync(string message)
        {
            var adminChatId = _configuration["AdminTelegramChatId"] ?? _configuration["TelegramChatId"];
            if (!string.IsNullOrEmpty(adminChatId))
            {
                await SendNotificationAsync(adminChatId, message);
            }
        }
    }
}
