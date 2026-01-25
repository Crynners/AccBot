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
        Task<List<UserWithSubscriptions>> GetUsersWithActiveSubscriptionsAsync();
    }

    public class UserRegistrationRepository : IUserRegistrationRepository
    {
        private readonly MiningContext _dbContext;

        public UserRegistrationRepository(MiningContext dbContext)
        {
            _dbContext = dbContext;
        }

        public async Task<List<UserWithSubscriptions>> GetUsersWithActiveSubscriptionsAsync()
        {
            // Get users with active telegram subscriptions
            var usersWithSubscriptions = await _dbContext.UserRegistrations
                .GroupJoin(
                    _dbContext.TelegramSubscriptions.Where(s => s.IsActive),
                    user => user.Id,
                    sub => sub.UserRegistrationId,
                    (user, subs) => new { User = user, Subscriptions = subs.ToList() }
                )
                .Where(x => x.Subscriptions.Any())
                .ToListAsync();

            // Build result with chat IDs
            var result = new List<UserWithSubscriptions>();

            foreach (var item in usersWithSubscriptions)
            {
                var chatIds = item.Subscriptions.Select(s => s.TelegramChatId).ToList();

                if (chatIds.Count > 0)
                {
                    result.Add(new UserWithSubscriptions
                    {
                        User = item.User,
                        ChatIds = chatIds
                    });
                }
            }

            return result;
        }
    }
}
