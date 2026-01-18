using Microsoft.EntityFrameworkCore;
using SoloMinatorNotifier.Data;

namespace SoloMinatorNotifier.Repositories
{
    public interface ITelegramSubscriptionRepository
    {
        Task<List<string>> GetActiveChatIdsForUserAsync(int userRegistrationId);
        Task<List<string>> GetAllDistinctActiveChatIdsAsync();
        Task UpdateLastNotificationAsync(int userRegistrationId, List<string> chatIds);
    }

    public class TelegramSubscriptionRepository : ITelegramSubscriptionRepository
    {
        private readonly MiningContext _dbContext;

        public TelegramSubscriptionRepository(MiningContext dbContext)
        {
            _dbContext = dbContext;
        }

        public async Task<List<string>> GetActiveChatIdsForUserAsync(int userRegistrationId)
        {
            return await _dbContext.TelegramSubscriptions
                .Where(s => s.UserRegistrationId == userRegistrationId && s.IsActive)
                .Select(s => s.TelegramChatId)
                .ToListAsync();
        }

        public async Task<List<string>> GetAllDistinctActiveChatIdsAsync()
        {
            return await _dbContext.TelegramSubscriptions
                .Where(s => s.IsActive)
                .Select(s => s.TelegramChatId)
                .Distinct()
                .ToListAsync();
        }

        public async Task UpdateLastNotificationAsync(int userRegistrationId, List<string> chatIds)
        {
            var subscriptions = await _dbContext.TelegramSubscriptions
                .Where(s => s.UserRegistrationId == userRegistrationId && chatIds.Contains(s.TelegramChatId))
                .ToListAsync();

            foreach (var subscription in subscriptions)
            {
                subscription.LastNotificationAt = DateTime.UtcNow;
            }

            await _dbContext.SaveChangesAsync();
        }
    }
}
