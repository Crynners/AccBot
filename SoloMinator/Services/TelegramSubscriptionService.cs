using Microsoft.EntityFrameworkCore;
using SoloMinator.Data;
using SoloMinator.Entities;

namespace SoloMinator.Services;

public interface ITelegramSubscriptionService
{
    Task<List<TelegramSubscriptionEntity>> GetSubscriptionsForRegistrationAsync(int registrationId);
    Task<TelegramSubscriptionEntity?> GetSubscriptionByIdAsync(int subscriptionId);
    Task<TelegramSubscriptionEntity> CreateSubscriptionAsync(int userRegistrationId, string chatId, string? username, string? firstName);
    Task<bool> DeleteSubscriptionAsync(int subscriptionId);
    Task<bool> ToggleSubscriptionAsync(int subscriptionId, bool isActive);
    Task<bool> SubscriptionExistsAsync(int userRegistrationId, string chatId);
}

public class TelegramSubscriptionService : ITelegramSubscriptionService
{
    private readonly SoloMinatorContext _context;
    private readonly ILogger<TelegramSubscriptionService> _logger;

    public TelegramSubscriptionService(SoloMinatorContext context, ILogger<TelegramSubscriptionService> logger)
    {
        _context = context;
        _logger = logger;
    }

    public async Task<List<TelegramSubscriptionEntity>> GetSubscriptionsForRegistrationAsync(int registrationId)
    {
        return await _context.TelegramSubscriptions
            .Where(s => s.UserRegistrationId == registrationId)
            .OrderByDescending(s => s.CreatedAt)
            .ToListAsync();
    }

    public async Task<TelegramSubscriptionEntity?> GetSubscriptionByIdAsync(int subscriptionId)
    {
        return await _context.TelegramSubscriptions
            .Include(s => s.UserRegistration)
            .FirstOrDefaultAsync(s => s.Id == subscriptionId);
    }

    public async Task<TelegramSubscriptionEntity> CreateSubscriptionAsync(int userRegistrationId, string chatId, string? username, string? firstName)
    {
        // Check if subscription already exists
        var existing = await _context.TelegramSubscriptions
            .FirstOrDefaultAsync(s => s.UserRegistrationId == userRegistrationId && s.TelegramChatId == chatId);

        if (existing != null)
        {
            _logger.LogInformation("Subscription already exists for registration {RegistrationId} and chat {ChatId}", userRegistrationId, chatId);

            // Update existing subscription if it was inactive
            if (!existing.IsActive)
            {
                existing.IsActive = true;
                existing.TelegramUsername = username;
                existing.TelegramFirstName = firstName;
                await _context.SaveChangesAsync();
            }

            return existing;
        }

        var subscription = new TelegramSubscriptionEntity
        {
            UserRegistrationId = userRegistrationId,
            TelegramChatId = chatId,
            TelegramUsername = username,
            TelegramFirstName = firstName,
            IsActive = true,
            CreatedAt = DateTime.UtcNow
        };

        _context.TelegramSubscriptions.Add(subscription);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Created subscription for registration {RegistrationId} with chat {ChatId}", userRegistrationId, chatId);

        return subscription;
    }

    public async Task<bool> DeleteSubscriptionAsync(int subscriptionId)
    {
        var subscription = await _context.TelegramSubscriptions.FindAsync(subscriptionId);
        if (subscription == null)
        {
            return false;
        }

        _context.TelegramSubscriptions.Remove(subscription);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Deleted subscription {SubscriptionId}", subscriptionId);

        return true;
    }

    public async Task<bool> ToggleSubscriptionAsync(int subscriptionId, bool isActive)
    {
        var subscription = await _context.TelegramSubscriptions.FindAsync(subscriptionId);
        if (subscription == null)
        {
            return false;
        }

        subscription.IsActive = isActive;
        await _context.SaveChangesAsync();

        _logger.LogInformation("Toggled subscription {SubscriptionId} to {IsActive}", subscriptionId, isActive);

        return true;
    }

    public async Task<bool> SubscriptionExistsAsync(int userRegistrationId, string chatId)
    {
        return await _context.TelegramSubscriptions
            .AnyAsync(s => s.UserRegistrationId == userRegistrationId && s.TelegramChatId == chatId);
    }
}
