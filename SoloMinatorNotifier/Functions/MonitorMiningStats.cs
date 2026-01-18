using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using SoloMinatorNotifier.Services;
using SoloMinatorNotifier.Repositories;
using SoloMinatorNotifier.Entities;

namespace SoloMinatorNotifier.Functions
{
    public class MonitorMiningStats
    {
        private readonly IMiningStatsService _miningStatsService;
        private readonly ILogger<MonitorMiningStats> _logger;
        private readonly IConfiguration _configuration;
        private readonly IDifficultyService _difficultyService;
        private readonly ITelegramService _telegramService;
        private readonly IMiningStatsRepository _miningStatsRepository;
        private readonly IDifficultyRepository _difficultyRepository;
        private readonly IUserRegistrationRepository _userRegistrationRepository;

        public MonitorMiningStats(
            IMiningStatsService miningStatsService,
            ILogger<MonitorMiningStats> logger,
            IConfiguration configuration,
            IDifficultyService difficultyService,
            ITelegramService telegramService,
            IMiningStatsRepository miningStatsRepository,
            IDifficultyRepository difficultyRepository,
            IUserRegistrationRepository userRegistrationRepository)
        {
            _miningStatsService = miningStatsService;
            _logger = logger;
            _configuration = configuration;
            _difficultyService = difficultyService;
            _telegramService = telegramService;
            _miningStatsRepository = miningStatsRepository;
            _difficultyRepository = difficultyRepository;
            _userRegistrationRepository = userRegistrationRepository;
        }

        [Function("MonitorMiningStats")]
        public async Task Run([TimerTrigger("0 0 * * * *")] TimerInfo? myTimer)
        {
            _logger.LogInformation("MonitorMiningStats triggered at {Time}", DateTime.UtcNow);

            try
            {
                // Update difficulty data
                _logger.LogInformation("Updating difficulty data...");
                await _difficultyService.UpdateDifficultyDataAsync();
                _logger.LogInformation("Difficulty data updated");

                // Get current difficulty
                var latestDifficulty = await _difficultyRepository.GetLatestDifficultyAsync();
                if (latestDifficulty == null)
                {
                    throw new InvalidOperationException("Cannot get current difficulty");
                }

                // Get all users with active subscriptions (both legacy and new system)
                var usersWithSubscriptions = await _userRegistrationRepository.GetUsersWithActiveSubscriptionsAsync();
                _logger.LogInformation("Processing {UserCount} users with active subscriptions", usersWithSubscriptions.Count);

                foreach (var userWithSubs in usersWithSubscriptions)
                {
                    await ProcessUserAsync(userWithSubs.User, userWithSubs.ChatIds, latestDifficulty.Difficulty);
                }
            }
            catch (Exception ex)
            {
                var errorMessage = ex.InnerException != null
                    ? $"*Error Monitoring Mining Stats*\n{ex.Message}\nInnerException: {ex.InnerException.Message}"
                    : $"*Error Monitoring Mining Stats*\n{ex.Message}";

                _logger.LogError(ex, "Error monitoring mining stats");
                await _telegramService.SendErrorNotificationAsync(errorMessage);
            }
        }

        private async Task ProcessUserAsync(UserRegistrationEntity user, List<string> chatIds, double currentDifficulty)
        {
            try
            {
                _logger.LogInformation("Processing user {Address} on pool {Pool} with {ChatCount} chats",
                    user.MiningAddress, user.PoolVariant, chatIds.Count);

                // Fetch stats for this user from their pool
                var stats = await _miningStatsService.GetStatsForUserAsync(user);
                if (stats == null)
                {
                    _logger.LogWarning("Could not fetch stats for user {Address} on pool {Pool}",
                        user.MiningAddress, user.PoolVariant);
                    return;
                }

                // Create unique wallet ID for this user (pool:address) - kept for backward compatibility
                var walletId = $"{user.PoolVariant}:{user.MiningAddress}";
                _logger.LogInformation("Saving stats for walletId: {WalletId}, UserRegistrationId: {UserId}, BestEver: {BestEver}",
                    walletId, user.Id, stats.BestEver);

                // Save stats and get notifications (pass UserRegistrationId for optimized FK-based queries)
                var notifications = await _miningStatsRepository.SaveStatsAndGetNotificationsAsync(
                    stats,
                    walletId,
                    currentDifficulty,
                    user.Id);  // Pass FK for optimized indexed lookups

                _logger.LogInformation("Stats saved, {NotificationCount} notifications generated", notifications.Count);

                // Send notifications to all subscribed chats
                foreach (var notification in notifications)
                {
                    foreach (var chatId in chatIds)
                    {
                        await _telegramService.SendNotificationAsync(chatId, notification.FormattedMessage);
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing user {Address} on pool {Pool}",
                    user.MiningAddress, user.PoolVariant);
            }
        }
    }
}