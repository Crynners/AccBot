using System.Text.Json;
using SoloMinator.Models;

namespace SoloMinator.Services;

public interface ICKPoolService
{
    Task<(bool IsValid, MiningStatistics? Stats, string? Error)> ValidateAndGetStatsAsync(string address, string poolVariant);
    string GetPoolUrl(string poolVariant);
}

public class CKPoolService : ICKPoolService
{
    private readonly HttpClient _httpClient;
    private readonly ILogger<CKPoolService> _logger;

    private static readonly Dictionary<string, string> PoolUrls = new()
    {
        ["solo"] = "https://solo.ckpool.org",
        ["eusolo"] = "https://eusolo.ckpool.org",
        ["ausolo"] = "https://ausolo.ckpool.org"
    };

    public CKPoolService(HttpClient httpClient, ILogger<CKPoolService> logger)
    {
        _httpClient = httpClient;
        _logger = logger;
    }

    public string GetPoolUrl(string poolVariant)
    {
        return PoolUrls.TryGetValue(poolVariant.ToLower(), out var url) ? url : PoolUrls["solo"];
    }

    public async Task<(bool IsValid, MiningStatistics? Stats, string? Error)> ValidateAndGetStatsAsync(string address, string poolVariant)
    {
        try
        {
            var baseUrl = GetPoolUrl(poolVariant);
            var url = $"{baseUrl}/users/{address}";

            _logger.LogInformation("Validating address {Address} on {Pool}", address, poolVariant);

            var response = await _httpClient.GetAsync(url);

            if (!response.IsSuccessStatusCode)
            {
                if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
                {
                    return (false, null, "Address not found on this pool. Make sure you have submitted at least one share.");
                }
                return (false, null, $"Pool returned error: {response.StatusCode}");
            }

            var content = await response.Content.ReadAsStringAsync();

            // CKPool returns "null" for non-existent addresses
            if (string.IsNullOrWhiteSpace(content) || content.Trim() == "null")
            {
                return (false, null, "Address not found on this pool. Make sure you have submitted at least one share.");
            }

            var stats = JsonSerializer.Deserialize<MiningStatistics>(content);

            if (stats == null)
            {
                return (false, null, "Could not parse pool response.");
            }

            // Check if address has any activity
            if (stats.Shares == 0 && stats.BestShare == 0)
            {
                return (false, null, "Address exists but has no mining activity yet.");
            }

            return (true, stats, null);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "HTTP error validating address {Address}", address);
            return (false, null, "Could not connect to pool. Please try again later.");
        }
        catch (JsonException ex)
        {
            _logger.LogError(ex, "JSON error parsing response for {Address}", address);
            return (false, null, "Invalid response from pool.");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error validating address {Address}", address);
            return (false, null, "An unexpected error occurred.");
        }
    }
}
