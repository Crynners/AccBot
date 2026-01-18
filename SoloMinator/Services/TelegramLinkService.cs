using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using SoloMinator.Data;
using SoloMinator.Entities;

namespace SoloMinator.Services;

public interface ITelegramLinkService
{
    Task<string> GenerateLinkTokenAsync(int userRegistrationId);
    Task<TelegramLinkTokenEntity?> ValidateTokenAsync(string token);
    Task MarkTokenAsUsedAsync(int tokenId);
    string GetDeepLink(string token);
}

public class TelegramSettings
{
    public string BotToken { get; set; } = string.Empty;
    public string BotUsername { get; set; } = string.Empty;
    public string WebhookSecret { get; set; } = string.Empty;
    public int LinkTokenExpirationMinutes { get; set; } = 30;
}

public class TelegramLinkService : ITelegramLinkService
{
    private readonly SoloMinatorContext _context;
    private readonly TelegramSettings _settings;
    private readonly ILogger<TelegramLinkService> _logger;

    public TelegramLinkService(
        SoloMinatorContext context,
        IOptions<TelegramSettings> settings,
        ILogger<TelegramLinkService> logger)
    {
        _context = context;
        _settings = settings.Value;
        _logger = logger;
    }

    public async Task<string> GenerateLinkTokenAsync(int userRegistrationId)
    {
        // Invalidate any existing unused tokens for this user
        var existingTokens = await _context.TelegramLinkTokens
            .Where(t => t.UserRegistrationId == userRegistrationId && !t.IsUsed)
            .ToListAsync();

        foreach (var existingToken in existingTokens)
        {
            existingToken.IsUsed = true;
        }

        // Generate a secure random token
        var tokenBytes = new byte[32];
        using (var rng = RandomNumberGenerator.Create())
        {
            rng.GetBytes(tokenBytes);
        }
        var token = Convert.ToBase64String(tokenBytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');

        var linkToken = new TelegramLinkTokenEntity
        {
            UserRegistrationId = userRegistrationId,
            Token = token,
            ExpiresAt = DateTime.UtcNow.AddMinutes(_settings.LinkTokenExpirationMinutes),
            IsUsed = false,
            CreatedAt = DateTime.UtcNow
        };

        _context.TelegramLinkTokens.Add(linkToken);
        await _context.SaveChangesAsync();

        _logger.LogInformation("Generated link token for registration {RegistrationId}", userRegistrationId);

        return token;
    }

    public async Task<TelegramLinkTokenEntity?> ValidateTokenAsync(string token)
    {
        // Use transaction to atomically validate and mark token as used to prevent race conditions
        await using var transaction = await _context.Database.BeginTransactionAsync();
        try
        {
            var linkToken = await _context.TelegramLinkTokens
                .Include(t => t.UserRegistration)
                .FirstOrDefaultAsync(t => t.Token == token && !t.IsUsed && t.ExpiresAt > DateTime.UtcNow);

            if (linkToken == null)
            {
                _logger.LogWarning("Invalid or expired token: {Token}", token.Substring(0, Math.Min(10, token.Length)) + "...");
                await transaction.RollbackAsync();
                return null;
            }

            // Mark as used immediately within the same transaction
            linkToken.IsUsed = true;
            await _context.SaveChangesAsync();
            await transaction.CommitAsync();

            return linkToken;
        }
        catch (Exception ex)
        {
            await transaction.RollbackAsync();
            _logger.LogError(ex, "Error validating token");
            throw;
        }
    }

    public async Task MarkTokenAsUsedAsync(int tokenId)
    {
        // This method is now a no-op since ValidateTokenAsync marks the token as used atomically
        // Kept for backward compatibility
        _logger.LogDebug("MarkTokenAsUsedAsync called for token {TokenId} - token already marked as used during validation", tokenId);
    }

    public string GetDeepLink(string token)
    {
        return $"https://t.me/{_settings.BotUsername}?start={token}";
    }
}
