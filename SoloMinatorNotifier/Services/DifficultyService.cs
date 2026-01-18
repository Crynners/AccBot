using System;
using System.Net.Http;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using SoloMinatorNotifier.Models;
using SoloMinatorNotifier.Entities;
using SoloMinatorNotifier.Repositories;

namespace SoloMinatorNotifier.Services
{
    public interface IDifficultyService
    {
        Task<MiningDifficultyAdjustmentEntity> UpdateDifficultyDataAsync();
    }

    public class DifficultyService : IDifficultyService
    {
        private readonly HttpClient _httpClient;
        private readonly ILogger<DifficultyService> _logger;
        private readonly IDifficultyRepository _difficultyRepository;
        private readonly ITelegramService _telegramService;
        private readonly ITelegramSubscriptionRepository _subscriptionRepository;
        private const string API_URL = "https://mempool.space/api/v1/mining/difficulty-adjustments/1m";

        public DifficultyService(
            HttpClient httpClient,
            ILogger<DifficultyService> logger,
            IDifficultyRepository difficultyRepository,
            ITelegramService telegramService,
            ITelegramSubscriptionRepository subscriptionRepository)
        {
            _httpClient = httpClient;
            _logger = logger;
            _difficultyRepository = difficultyRepository;
            _telegramService = telegramService;
            _subscriptionRepository = subscriptionRepository;
        }

        public async Task<MiningDifficultyAdjustmentEntity> UpdateDifficultyDataAsync()
        {
            try
            {
                // Kontrola, zda je potřeba aktualizovat data
                var latestDifficulty = await _difficultyRepository.GetLatestDifficultyAsync();

                if (latestDifficulty != null)
                {
                    var daysSinceLastUpdate = (DateTime.UtcNow - latestDifficulty.BlockTime).TotalDays;
                    if (daysSinceLastUpdate < 10)
                    {
                        return latestDifficulty;
                    }
                }

                // Stažení dat z API
                var response = await _httpClient.GetStringAsync(API_URL);
                var difficultyAdjustments = DifficultyAdjustment.FromJson(response);

                MiningDifficultyAdjustmentEntity lastAddedEntity = null;

                // Konverze a uložení do databáze
                foreach (var adjustment in difficultyAdjustments)
                {
                    var entity = new MiningDifficultyAdjustmentEntity
                    {
                        BlockHeight = adjustment.BlockHeight,
                        BlockTimestamp = adjustment.BlockTimestamp,
                        Difficulty = adjustment.Difficulty,
                        DifficultyChange = adjustment.DifficultyChange,
                        BlockTime = DateTimeOffset.FromUnixTimeSeconds(adjustment.BlockTimestamp).UtcDateTime
                    };

                    // Kontrola, zda záznam již neexistuje
                    var exists = await _difficultyRepository.ExistsAsync(entity.BlockHeight);

                    if (!exists)
                    {
                        lastAddedEntity = await _difficultyRepository.AddAsync(entity);

                        // Získání a odeslání statistik všem uživatelům (distinct chat IDs)
                        var stats = await _difficultyRepository.GetDifficultyStatsAsync();
                        if (stats != null)
                        {
                            var distinctChatIds = await _subscriptionRepository.GetAllDistinctActiveChatIdsAsync();
                            if (distinctChatIds.Count > 0)
                            {
                                await _telegramService.SendNotificationToAllAsync(distinctChatIds, stats.FormattedMessage);
                                _logger.LogInformation("Sent difficulty notification to {Count} distinct chat(s)", distinctChatIds.Count);
                            }
                            else
                            {
                                // Fallback to admin channel if no subscriptions
                                await _telegramService.SendErrorNotificationAsync(stats.FormattedMessage);
                            }
                        }
                    }
                }

                return lastAddedEntity ?? await _difficultyRepository.GetLatestDifficultyAsync();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Chyba při aktualizaci difficulty dat");
                var errorMessage = ex.InnerException != null
                    ? $"*Error Monitoring Mining Stats*\n{ex.Message}\nInnerException: {ex.InnerException.Message}"
                    : $"*Error Monitoring Mining Stats*\n{ex.Message}";
                await _telegramService.SendErrorNotificationAsync(errorMessage);
                throw;
            }
        }
    }
}