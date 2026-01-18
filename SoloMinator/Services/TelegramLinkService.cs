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
        var linkToken = await _context.TelegramLinkTokens
            .Include(t => t.UserRegistration)
            .FirstOrDefaultAsync(t => t.Token == token && !t.IsUsed && t.ExpiresAt > DateTime.UtcNow);

        if (linkToken == null)
        {
            _logger.LogWarning("Invalid or expired token: {Token}", token.Substring(0, Math.Min(10, token.Length)) + "...");
            return null;
        }

        return linkToken;
    }

    public async Task MarkTokenAsUsedAsync(int tokenId)
    {
        var token = await _context.TelegramLinkTokens.FindAsync(tokenId);
        if (token != null)
        {
            token.IsUsed = true;
            await _context.SaveChangesAsync();
            _logger.LogInformation("Token {TokenId} marked as used", tokenId);
        }
    }

    public string GetDeepLink(string token)
    {
        return $"https://t.me/{_settings.BotUsername}?start={token}";
    }
}
