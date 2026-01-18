using Microsoft.EntityFrameworkCore;
using SoloMinator.Data;
using SoloMinator.Entities;

namespace SoloMinator.Services;

public interface IRegistrationService
{
    Task<UserRegistrationEntity?> GetByAddressAsync(string address);
    Task<UserRegistrationEntity?> GetByAddressAndPoolAsync(string address, string poolVariant);
    Task<List<UserRegistrationEntity>> GetAllPoolsForAddressAsync(string address);
    Task<UserRegistrationEntity> CreateRegistrationAsync(string address, string poolVariant);
    Task<UserRegistrationEntity> UpdateTelegramSettingsAsync(string address, string poolVariant, string? chatId, bool notificationsEnabled);
    Task<bool> DeleteRegistrationAsync(string address, string poolVariant);
}

public class RegistrationService : IRegistrationService
{
    private readonly SoloMinatorContext _context;
    private readonly ILogger<RegistrationService> _logger;

    public RegistrationService(SoloMinatorContext context, ILogger<RegistrationService> logger)
    {
        _context = context;
        _logger = logger;
    }

    public async Task<UserRegistrationEntity?> GetByAddressAsync(string address)
    {
        return await _context.UserRegistrations
            .FirstOrDefaultAsync(r => r.MiningAddress == address);
    }

    public async Task<UserRegistrationEntity?> GetByAddressAndPoolAsync(string address, string poolVariant)
    {
        return await _context.UserRegistrations
            .FirstOrDefaultAsync(r => r.MiningAddress == address && r.PoolVariant == poolVariant);
    }

    public async Task<List<UserRegistrationEntity>> GetAllPoolsForAddressAsync(string address)
    {
        return await _context.UserRegistrations
            .Where(r => r.MiningAddress == address)
            .OrderBy(r => r.PoolVariant)
            .ToListAsync();
    }

    public async Task<UserRegistrationEntity> CreateRegistrationAsync(string address, string poolVariant)
    {
        var existing = await GetByAddressAndPoolAsync(address, poolVariant);
        if (existing != null)
        {
            _logger.LogInformation("Registration already exists for {Address} on {Pool}", address, poolVariant);
            return existing;
        }

        var registration = new UserRegistrationEntity
        {
            MiningAddress = address,
            PoolVariant = poolVariant.ToLower(),
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _context.UserRegistrations.Add(registration);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Created registration for {Address} on {Pool}", address, poolVariant);

        return registration;
    }

    public async Task<UserRegistrationEntity> UpdateTelegramSettingsAsync(string address, string poolVariant, string? chatId, bool notificationsEnabled)
    {
        var registration = await GetByAddressAndPoolAsync(address, poolVariant);
        if (registration == null)
        {
            throw new InvalidOperationException("Registration not found");
        }

        registration.TelegramChatId = chatId;
        registration.NotificationsEnabled = notificationsEnabled && !string.IsNullOrWhiteSpace(chatId);
        registration.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync();

        _logger.LogInformation("Updated Telegram settings for {Address} on {Pool}", registration.MiningAddress, poolVariant);

        return registration;
    }

    public async Task<bool> DeleteRegistrationAsync(string address, string poolVariant)
    {
        var registration = await GetByAddressAndPoolAsync(address, poolVariant);
        if (registration == null)
        {
            return false;
        }

        _context.UserRegistrations.Remove(registration);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Deleted registration for {Address} on {Pool}", registration.MiningAddress, poolVariant);

        return true;
    }
}
