using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using SoloMinator.Data;
using SoloMinator.Models;

namespace SoloMinator.Services;

public record HistoricalDifficultyMatch(DateTime Date, double Difficulty);

public interface IBitcoinService
{
    Task<DifficultyInfo?> GetCurrentDifficultyAsync();
    Task<double?> GetCurrentDifficultyValueAsync();
    Task<string?> GetNetworkHashrateAsync();
    Task<double?> GetNetworkHashrateValueAsync();
    Task<HistoricalDifficultyMatch?> GetLastDateWhenDifficultyWasBelow(double targetDifficulty);
}

public class BitcoinService : IBitcoinService
{
    private readonly HttpClient _httpClient;
    private readonly SoloMinatorContext _dbContext;
    private readonly ILogger<BitcoinService> _logger;

    public BitcoinService(HttpClient httpClient, SoloMinatorContext dbContext, ILogger<BitcoinService> logger)
    {
        _httpClient = httpClient;
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task<DifficultyInfo?> GetCurrentDifficultyAsync()
    {
        try
        {
            var response = await _httpClient.GetAsync("https://mempool.space/api/v1/difficulty-adjustment");
            response.EnsureSuccessStatusCode();

            var content = await response.Content.ReadAsStringAsync();
            return JsonSerializer.Deserialize<DifficultyInfo>(content);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error fetching difficulty info");
            return null;
        }
    }

    public async Task<double?> GetCurrentDifficultyValueAsync()
    {
        try
        {
            var response = await _httpClient.GetAsync("https://mempool.space/api/v1/mining/difficulty-adjustments/1");
            response.EnsureSuccessStatusCode();

            var content = await response.Content.ReadAsStringAsync();
            // API returns array of arrays: [[timestamp, height, difficulty, adjustment], ...]
            var adjustments = JsonSerializer.Deserialize<List<List<double>>>(content);

            // Index 2 is difficulty
            return adjustments?.FirstOrDefault()?[2];
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error fetching difficulty value");
            return null;
        }
    }

    public async Task<string?> GetNetworkHashrateAsync()
    {
        try
        {
            var response = await _httpClient.GetAsync("https://mempool.space/api/v1/mining/hashrate/1d");
            response.EnsureSuccessStatusCode();

            var content = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(content);

            // Get the current hashrate from the response
            if (doc.RootElement.TryGetProperty("currentHashrate", out var hashrateElement))
            {
                var hashrate = hashrateElement.GetDouble();
                return FormatHashrate(hashrate);
            }

            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error fetching network hashrate");
            return null;
        }
    }

    private static string FormatHashrate(double hashrate)
    {
        if (hashrate >= 1e21) return $"{hashrate / 1e21:F2} ZH/s";
        if (hashrate >= 1e18) return $"{hashrate / 1e18:F2} EH/s";
        if (hashrate >= 1e15) return $"{hashrate / 1e15:F2} PH/s";
        if (hashrate >= 1e12) return $"{hashrate / 1e12:F2} TH/s";
        return $"{hashrate:F0} H/s";
    }

    public async Task<double?> GetNetworkHashrateValueAsync()
    {
        try
        {
            var response = await _httpClient.GetAsync("https://mempool.space/api/v1/mining/hashrate/1d");
            response.EnsureSuccessStatusCode();

            var content = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(content);

            if (doc.RootElement.TryGetProperty("currentHashrate", out var hashrateElement))
            {
                return hashrateElement.GetDouble();
            }

            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error fetching network hashrate value");
            return null;
        }
    }

    public async Task<HistoricalDifficultyMatch?> GetLastDateWhenDifficultyWasBelow(double targetDifficulty)
    {
        try
        {
            // Query database for the most recent difficulty adjustment where difficulty was <= target
            var adjustment = await _dbContext.MiningDifficultyAdjustments
                .Where(d => d.Difficulty <= targetDifficulty)
                .OrderByDescending(d => d.BlockHeight)
                .FirstOrDefaultAsync();

            if (adjustment == null)
                return null;

            return new HistoricalDifficultyMatch(adjustment.BlockTime, adjustment.Difficulty);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error fetching historical difficulty data from database");
            return null;
        }
    }
}
