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
            // Optimized: Single query using LEFT JOIN to get users with their subscriptions
            // This replaces the previous 3 separate queries + in-memory merging
            var usersWithSubscriptions = await _dbContext.UserRegistrations
                .GroupJoin(
                    _dbContext.TelegramSubscriptions.Where(s => s.IsActive),
                    user => user.Id,
                    sub => sub.UserRegistrationId,
                    (user, subs) => new { User = user, Subscriptions = subs.ToList() }
                )
                .Where(x =>
                    // Has active subscriptions
                    x.Subscriptions.Any() ||
                    // Or has legacy notifications enabled
                    (x.User.NotificationsEnabled && !string.IsNullOrEmpty(x.User.TelegramChatId))
                )
                .ToListAsync();

            // Build result with merged chat IDs
            var result = new List<UserWithSubscriptions>();

            foreach (var item in usersWithSubscriptions)
            {
                var chatIds = new HashSet<string>();

                // Add subscription chat IDs
                foreach (var sub in item.Subscriptions)
                {
                    chatIds.Add(sub.TelegramChatId);
                }

                // Add legacy chat ID if notifications are enabled
                if (item.User.NotificationsEnabled && !string.IsNullOrEmpty(item.User.TelegramChatId))
                {
                    chatIds.Add(item.User.TelegramChatId);
                }

                if (chatIds.Count > 0)
                {
                    result.Add(new UserWithSubscriptions
                    {
                        User = item.User,
                        ChatIds = chatIds.ToList()
                    });
                }
            }

            return result;
        }
    }
}
