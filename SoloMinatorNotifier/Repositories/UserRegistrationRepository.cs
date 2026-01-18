using Microsoft.EntityFrameworkCore;
using SoloMinatorNotifier.Data;
using SoloMinatorNotifier.Entities;

namespace SoloMinatorNotifier.Repositories
{
    public class UserWithSubscriptions
    {
        public UserRegistrationEntity User { get; set; } = null!;
        public List<string> ChatIds { get; set; } = new();
    }

    public interface IUserRegistrationRepository
    {
        Task<List<UserRegistrationEntity>> GetUsersWithNotificationsEnabledAsync();
        Task<List<UserWithSubscriptions>> GetUsersWithActiveSubscriptionsAsync();
    }

    public class UserRegistrationRepository : IUserRegistrationRepository
    {
        private readonly MiningContext _dbContext;

        public UserRegistrationRepository(MiningContext dbContext)
        {
            _dbContext = dbContext;
        }

        public async Task<List<UserRegistrationEntity>> GetUsersWithNotificationsEnabledAsync()
        {
            return await _dbContext.UserRegistrations
                .Where(u => u.NotificationsEnabled && !string.IsNullOrEmpty(u.TelegramChatId))
                .ToListAsync();
        }

        public async Task<List<UserWithSubscriptions>> GetUsersWithActiveSubscriptionsAsync()
        {
            // Get users with legacy notifications (TelegramChatId on UserRegistration)
            var legacyUsers = await _dbContext.UserRegistrations
                .Where(u => u.NotificationsEnabled && !string.IsNullOrEmpty(u.TelegramChatId))
                .ToListAsync();

            // Get users with new subscription system
            var subscriptionUsers = await _dbContext.TelegramSubscriptions
                .Where(s => s.IsActive)
                .GroupBy(s => s.UserRegistrationId)
                .Select(g => new { UserRegistrationId = g.Key, ChatIds = g.Select(s => s.TelegramChatId).ToList() })
                .ToListAsync();

            // Get the user registrations for subscription users
            var subscriptionUserIds = subscriptionUsers.Select(s => s.UserRegistrationId).ToList();
            var subscriptionUserEntities = await _dbContext.UserRegistrations
                .Where(u => subscriptionUserIds.Contains(u.Id))
                .ToListAsync();

            var result = new List<UserWithSubscriptions>();

            // Add subscription-based users
            foreach (var subUser in subscriptionUsers)
            {
                var user = subscriptionUserEntities.FirstOrDefault(u => u.Id == subUser.UserRegistrationId);
                if (user != null)
                {
                    var existing = result.FirstOrDefault(r => r.User.Id == user.Id);
                    if (existing != null)
                    {
                        // Merge chat IDs
                        foreach (var chatId in subUser.ChatIds.Where(c => !existing.ChatIds.Contains(c)))
                        {
                            existing.ChatIds.Add(chatId);
                        }
                    }
                    else
                    {
                        result.Add(new UserWithSubscriptions
                        {
                            User = user,
                            ChatIds = subUser.ChatIds
                        });
                    }
                }
            }

            // Add legacy users (if not already in the result)
            foreach (var legacyUser in legacyUsers)
            {
                var existing = result.FirstOrDefault(r => r.User.Id == legacyUser.Id);
                if (existing != null)
                {
                    // Add legacy chat ID if not already present
                    if (!existing.ChatIds.Contains(legacyUser.TelegramChatId!))
                    {
                        existing.ChatIds.Add(legacyUser.TelegramChatId!);
                    }
                }
                else
                {
                    result.Add(new UserWithSubscriptions
                    {
                        User = legacyUser,
                        ChatIds = new List<string> { legacyUser.TelegramChatId! }
                    });
                }
            }

            return result;
        }
    }
}
