using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Newtonsoft.Json;
using SoloMinatorNotifier.Data;
using SoloMinatorNotifier.Entities;
using SoloMinatorNotifier.Models;

namespace SoloMinatorNotifier.Services
{
    public interface IMiningStatsService
    {
        Task<MiningStatistics?> GetCurrentStatsAsync();
        Task<MiningStatistics?> GetStatsForUserAsync(UserRegistrationEntity user);
        Task SaveStatsAsync(MiningStatistics stats);
        Task<MiningStatisticsEntity?> GetLastRecordAsync(string workerName);
    }

    public class MiningStatsService : IMiningStatsService
    {
        private readonly HttpClient _httpClient;
        private readonly MiningContext _context;
        private readonly IConfiguration _configuration;
        private readonly ILogger<MiningStatsService> _logger;

        public MiningStatsService(
            IHttpClientFactory httpClientFactory,
            MiningContext context,
            IConfiguration configuration,
            ILogger<MiningStatsService> logger)
        {
            _httpClient = httpClientFactory.CreateClient();
            _context = context;
            _configuration = configuration;
            _logger = logger;
        }

        public async Task<MiningStatistics?> GetCurrentStatsAsync()
        {
            var url = _configuration["MiningApiUrl"];
            return await FetchStatsFromUrlAsync(url!);
        }

        public async Task<MiningStatistics?> GetStatsForUserAsync(UserRegistrationEntity user)
        {
            return await FetchStatsFromUrlAsync(user.StatsApiUrl);
        }

        private async Task<MiningStatistics?> FetchStatsFromUrlAsync(string url)
        {
            try
            {
                var response = await _httpClient.GetAsync(url);
                if (!response.IsSuccessStatusCode)
                {
                    _logger.LogWarning("Failed to fetch stats from {Url}: {StatusCode}", url, response.StatusCode);
                    return null;
                }

                var content = await response.Content.ReadAsStringAsync();
                return JsonConvert.DeserializeObject<MiningStatistics>(content);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching stats from {Url}", url);
                return null;
            }
        }

        public async Task SaveStatsAsync(MiningStatistics stats)
        {
            // Global stats update
            var globalStats = await _context.MiningStatistics
                .FirstOrDefaultAsync(x => x.WorkerName == _configuration["Wallet"]);

            if (globalStats == null)
            {
                globalStats = new MiningStatisticsEntity { WorkerName = _configuration["Wallet"] };
                _context.MiningStatistics.Add(globalStats);
            }

            if (stats.BestShare != globalStats.BestShare || stats.BestEver != globalStats.BestEver)
            {
                globalStats.Hashrate1m = stats.Hashrate1m;
                globalStats.Hashrate5m = stats.Hashrate5m;
                globalStats.Hashrate1hr = stats.Hashrate1hr;
                globalStats.Hashrate1d = stats.Hashrate1d;
                globalStats.Hashrate7d = stats.Hashrate7d;
                globalStats.LastShare = stats.LastShare;
                globalStats.Shares = stats.Shares;
                globalStats.BestShare = stats.BestShare;
                globalStats.BestEver = stats.BestEver;
                globalStats.Authorised = stats.Authorised;
                globalStats.Workers = stats.Workers;
                globalStats.RecordedAt = DateTime.UtcNow;
            }

            // Worker stats update
            foreach (var worker in stats.Worker)
            {
                var workerStats = await _context.MiningStatistics
                    .FirstOrDefaultAsync(x => x.WorkerName == worker.WorkerName);

                if (workerStats == null)
                {
                    workerStats = new MiningStatisticsEntity { WorkerName = worker.WorkerName };
                    _context.MiningStatistics.Add(workerStats);
                }

                if (worker.BestShare != workerStats.BestShare || worker.BestEver != workerStats.BestEver)
                {
                    workerStats.Hashrate1m = worker.Hashrate1m;
                    workerStats.Hashrate5m = worker.Hashrate5m;
                    workerStats.Hashrate1hr = worker.Hashrate1hr;
                    workerStats.Hashrate1d = worker.Hashrate1d;
                    workerStats.Hashrate7d = worker.Hashrate7d;
                    workerStats.LastShare = worker.LastShare;
                    workerStats.Shares = worker.Shares;
                    workerStats.BestShare = worker.BestShare;
                    workerStats.BestEver = worker.BestEver;
                    workerStats.RecordedAt = DateTime.UtcNow;
                }
            }

            if (_context.ChangeTracker.HasChanges())
            {
                await _context.SaveChangesAsync();
            }
        }

        public async Task<MiningStatisticsEntity?> GetLastRecordAsync(string workerName)
        {
            return await _context.MiningStatistics
                .Where(x => x.WorkerName == workerName)
                .OrderByDescending(x => x.RecordedAt)
                .FirstOrDefaultAsync();
        }


    }
}
