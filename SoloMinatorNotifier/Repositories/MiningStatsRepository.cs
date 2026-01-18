using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using SoloMinatorNotifier.Data;
using SoloMinatorNotifier.Entities;
using SoloMinatorNotifier.Models;

namespace SoloMinatorNotifier.Repositories
{
    public class WorkerRanking
    {
        public string WorkerName { get; set; }
        public double BestShare { get; set; }
        public DateOnly? LastSufficientDiffDate { get; set; }
        public DateTime RecordedAt { get; set; }
    }

    public class EnhancedShareNotification
    {
        private readonly double _currentDifficulty;
        private readonly List<WorkerRanking> _rankings;
        private readonly string _shortWorkerName;
        private readonly string _walletId;
        private readonly double _previousBest;
        private readonly double _newBest;
        private readonly bool _isGlobalBest;

        public EnhancedShareNotification(
            string workerName,
            string walletId,
            double previousBest,
            double newBest,
            double currentDifficulty,
            List<WorkerRanking> rankings,
            bool isGlobalBest)
        {
            _shortWorkerName = GetWorkerName(workerName, walletId);
            _previousBest = previousBest;
            _newBest = newBest;
            _currentDifficulty = currentDifficulty;
            _rankings = rankings;
            _isGlobalBest = isGlobalBest;
            _walletId = walletId;
        }

        public string FormattedMessage
        {
            get
            {
                var improvement = _newBest - _previousBest;
                var improvementPercentage = _previousBest > 0
                    ? (_newBest - _previousBest) / _previousBest * 100
                    : 0;

                var previousDifficultyPercentage = _currentDifficulty > 0
                    ? (_previousBest / _currentDifficulty) * 100
                    : 0;
                var newDifficultyPercentage = _currentDifficulty > 0
                    ? (_newBest / _currentDifficulty) * 100
                    : 0;

                var message = $"🎯 *{_shortWorkerName} has reached a new Best Share!* 🎯\n\n" +
                            $"📊 *Previous record:*\n" +
                            $"• {FormatShareNumber(_previousBest)} ({previousDifficultyPercentage:F5}% of current difficulty)\n\n" +
                            $"📈 *New record:*\n" +
                            $"• *{FormatShareNumber(_newBest)}* ({newDifficultyPercentage:F5}% of current difficulty)\n" +
                            $"• Improvement: +{FormatShareNumber(improvement)} (+{improvementPercentage:F2}%)\n\n" +
                            $"🏆 *Current Miners Status:*\n";

                var workerRangings = _rankings.Where(x => !x.WorkerName.Equals(_walletId)).ToList();
                // Add rankings
                for (int i = 0; i < workerRangings.Count; i++)
                {
                    var ranking = workerRangings[i];
                    var timeAgo = GetTimeAgo(ranking.RecordedAt);
                    var isCurrentMiner = ranking.WorkerName == _shortWorkerName;

                    var suffDate = ranking.LastSufficientDiffDate?.ToString("dd. MM. yyyy") ?? "N/A";
                    message += $"{i + 1}. {GetWorkerName(ranking.WorkerName, _walletId)}: " +
                             $"{(isCurrentMiner ? "*" : "")}{FormatShareNumber(ranking.BestShare)}{(isCurrentMiner ? "*" : "")} " +
                             $"({timeAgo}) (Suff {suffDate})\n";
                }

                if (_isGlobalBest)
                {
                    message += $"\n✨ *{_shortWorkerName} is now the global best!* ✨";
                }

                return message;
            }
        }

        private string GetTimeAgo(DateTime recordTime)
        {
            var difference = DateTime.UtcNow - recordTime;

            if (difference.TotalMinutes < 1) return "just now";
            if (difference.TotalMinutes < 60) return $"{(int)difference.TotalMinutes}m ago";
            if (difference.TotalHours < 24) return $"{(int)difference.TotalHours}h ago";
            if (difference.TotalDays < 7) return $"{(int)difference.TotalDays}d ago";
            if (difference.TotalDays < 30) return $"{(int)(difference.TotalDays / 7)}w ago";
            return $"{(int)(difference.TotalDays / 30)}mo ago";
        }

        private string FormatShareNumber(double number)
        {
            string[] units = { "", "K", "M", "G", "T", "P", "E" };
            int unitIndex = 0;

            while (Math.Abs(number) >= 1000 && unitIndex < units.Length - 1)
            {
                number /= 1000;
                unitIndex++;
            }

            return $"{Math.Round(number, 2)}{units[unitIndex]}";
        }

        private string GetWorkerName(string fullString, string walletId)
        {
            if (fullString.StartsWith(walletId + "."))
            {
                return fullString.Substring(walletId.Length + 1);
            }
            return fullString;
        }
    }

    public interface IMiningStatsRepository
    {
        Task<MiningStatisticsEntity?> GetLastRecordAsync(string workerName);
        Task<List<EnhancedShareNotification>> SaveStatsAndGetNotificationsAsync(
            MiningStatistics stats,
            string walletId,
            double currentDifficulty,
            int? userRegistrationId = null);
        Task SaveStatsAsync(MiningStatistics stats, string walletId, int? userRegistrationId = null);
        Task<List<WorkerRanking>> GetWorkersRankingAsync(string? walletId = null, int? userRegistrationId = null);
    }

    public class MiningStatsRepository : IMiningStatsRepository
    {
        private readonly MiningContext _dbContext;

        public MiningStatsRepository(MiningContext dbContext)
        {
            _dbContext = dbContext;
        }

        public async Task<MiningStatisticsEntity?> GetLastRecordAsync(string workerName)
        {
            return await _dbContext.MiningStatistics
                .Where(x => x.WorkerName == workerName)
                .OrderByDescending(x => x.RecordedAt)
                .FirstOrDefaultAsync();
        }

        public async Task<List<WorkerRanking>> GetWorkersRankingAsync(string? walletId = null, int? userRegistrationId = null)
        {
            IQueryable<MiningStatisticsEntity> query;

            // Prefer FK-based query (indexed, fast) over string-based query
            if (userRegistrationId.HasValue)
            {
                // Use FK index - much faster
                query = _dbContext.MiningStatistics
                    .Where(x => x.UserRegistrationId == userRegistrationId.Value && x.WorkerSuffix != null);
            }
            else
            {
                // Fallback to string-based query (for backward compatibility)
                query = _dbContext.MiningStatistics
                    .Where(x => x.WorkerName.Contains("."));

                if (!string.IsNullOrEmpty(walletId))
                {
                    var walletPrefix = walletId + ".";
                    query = query.Where(x => x.WorkerName.StartsWith(walletPrefix));
                }
            }

            var rankings = await query
                .GroupBy(x => x.WorkerName)
                .Select(g => new WorkerRanking
                {
                    WorkerName = g.Key,
                    BestShare = g.Max(x => x.BestEver),
                    LastSufficientDiffDate = g.Max(x => x.LastSufficientDiffDate),
                    RecordedAt = g.OrderByDescending(x => x.BestEver)
                                 .First().RecordedAt
                })
                .OrderByDescending(x => x.BestShare)
                .ToListAsync();

            return rankings;
        }

        public async Task SaveStatsAsync(MiningStatistics stats, string walletId, int? userRegistrationId = null)
        {
            await SaveGlobalStatsAsync(stats, walletId, userRegistrationId);
            await SaveWorkersStatsAsync(stats.Worker, walletId, userRegistrationId);

            if (_dbContext.ChangeTracker.HasChanges())
            {
                await _dbContext.SaveChangesAsync();
            }
        }

        public async Task<List<EnhancedShareNotification>> SaveStatsAndGetNotificationsAsync(
            MiningStatistics stats,
            string walletId,
            double currentDifficulty,
            int? userRegistrationId = null)
        {
            var notifications = new List<EnhancedShareNotification>();

            // Global stats check and update
            var globalStats = await SaveGlobalStatsAsync(stats, walletId, userRegistrationId);
            var workerResults = await SaveWorkersStatsAsync(stats.Worker, walletId, userRegistrationId);

            if (_dbContext.ChangeTracker.HasChanges())
            {
                await _dbContext.SaveChangesAsync();
            }

            // Use FK-based query if userRegistrationId is available (faster)
            var rankings = await GetWorkersRankingAsync(walletId, userRegistrationId);

            // Extract pool prefix for creating full worker names
            var poolPrefix = walletId.Contains(':') ? walletId.Substring(0, walletId.IndexOf(':') + 1) : "";

            // Worker stats check and update
            foreach (var (worker, previousBestEver, wasUpdated) in workerResults)
            {
                if (wasUpdated)  // If there was an update
                {
                    var isGlobalBest = stats.BestEver == worker.BestEver;
                    var fullWorkerName = poolPrefix + worker.WorkerName;
                    var notification = new EnhancedShareNotification(
                        fullWorkerName,
                        walletId,
                        previousBestEver,
                        worker.BestEver,
                        currentDifficulty,
                        rankings,
                        isGlobalBest);
                    notifications.Add(notification);
                }

            }

            return notifications;
        }

        private async Task<(double previousBestEver, bool wasUpdated)> SaveGlobalStatsAsync(
            MiningStatistics stats,
            string walletId,
            int? userRegistrationId = null)
        {
            MiningStatisticsEntity? globalStats;

            // Prefer FK-based lookup if available (indexed)
            if (userRegistrationId.HasValue)
            {
                globalStats = await _dbContext.MiningStatistics
                    .FirstOrDefaultAsync(x => x.UserRegistrationId == userRegistrationId.Value && x.WorkerSuffix == null);
            }
            else
            {
                globalStats = await _dbContext.MiningStatistics
                    .FirstOrDefaultAsync(x => x.WorkerName == walletId);
            }

            double previousBestEver = globalStats?.BestEver ?? 0;
            bool wasUpdated = false;

            if (globalStats == null)
            {
                globalStats = new MiningStatisticsEntity
                {
                    WorkerName = walletId,
                    UserRegistrationId = userRegistrationId,
                    WorkerSuffix = null  // Global stats have no worker suffix
                };
                _dbContext.MiningStatistics.Add(globalStats);
                wasUpdated = true;
            }
            else if (globalStats.UserRegistrationId == null && userRegistrationId.HasValue)
            {
                // Migrate existing record to use FK
                globalStats.UserRegistrationId = userRegistrationId;
            }

            if (stats.BestEver != globalStats.BestEver)
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

                // Find the last date when this share would have been sufficient to mine a block
                globalStats.LastSufficientDiffDate = await GetLastSufficientDiffDateAsync(stats.BestEver);

                wasUpdated = true;
            }

            // Only notify if there was an actual improvement over a previous record (not first initialization)
            return (previousBestEver, wasUpdated && stats.BestEver > previousBestEver && previousBestEver > 0);
        }

        /// <summary>
        /// Finds the most recent date when the given share would have been sufficient to mine a block.
        /// </summary>
        private async Task<DateOnly?> GetLastSufficientDiffDateAsync(double bestEver)
        {
            var lastSufficientAdjustment = await _dbContext.MiningDifficultyAdjustments
                .Where(x => bestEver >= x.Difficulty)
                .OrderByDescending(x => x.BlockTime)
                .FirstOrDefaultAsync();

            return lastSufficientAdjustment != null
                ? DateOnly.FromDateTime(lastSufficientAdjustment.BlockTime)
                : null;
        }

        private async Task<List<(WorkerStatistics worker, double previousBestEver, bool wasUpdated)>> SaveWorkersStatsAsync(
            IEnumerable<WorkerStatistics> workers,
            string walletId,
            int? userRegistrationId = null)
        {
            var workerList = workers.ToList();
            if (workerList.Count == 0)
                return new List<(WorkerStatistics, double, bool)>();

            // Extract pool prefix from walletId (e.g., "eusolo:address" -> "eusolo:")
            var poolPrefix = walletId.Contains(':') ? walletId.Substring(0, walletId.IndexOf(':') + 1) : "";

            // Batch load all existing worker stats in ONE query
            Dictionary<string, MiningStatisticsEntity> existingStats;
            if (userRegistrationId.HasValue)
            {
                existingStats = await _dbContext.MiningStatistics
                    .Where(x => x.UserRegistrationId == userRegistrationId.Value && x.WorkerSuffix != null)
                    .ToDictionaryAsync(x => x.WorkerSuffix!, x => x);
            }
            else
            {
                var workerNames = workerList.Select(w => poolPrefix + w.WorkerName).ToList();
                existingStats = await _dbContext.MiningStatistics
                    .Where(x => workerNames.Contains(x.WorkerName))
                    .ToDictionaryAsync(x => x.WorkerName.Contains('.')
                        ? x.WorkerName.Substring(x.WorkerName.LastIndexOf('.') + 1)
                        : x.WorkerName, x => x);
            }

            var results = new List<(WorkerStatistics worker, double previousBestEver, bool wasUpdated)>();

            foreach (var worker in workerList)
            {
                // Extract worker suffix
                var workerSuffix = worker.WorkerName.Contains('.')
                    ? worker.WorkerName.Substring(worker.WorkerName.LastIndexOf('.') + 1)
                    : worker.WorkerName;

                var fullWorkerName = poolPrefix + worker.WorkerName;
                existingStats.TryGetValue(workerSuffix, out var workerStats);

                double previousBestEver = workerStats?.BestEver ?? 0;
                bool wasUpdated = false;

                if (workerStats == null)
                {
                    workerStats = new MiningStatisticsEntity
                    {
                        WorkerName = fullWorkerName,
                        UserRegistrationId = userRegistrationId,
                        WorkerSuffix = workerSuffix
                    };
                    _dbContext.MiningStatistics.Add(workerStats);
                    wasUpdated = true;
                }
                else if (workerStats.UserRegistrationId == null && userRegistrationId.HasValue)
                {
                    // Migrate existing record to use FK
                    workerStats.UserRegistrationId = userRegistrationId;
                    workerStats.WorkerSuffix = workerSuffix;
                }

                if (worker.BestEver != workerStats.BestEver)
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

                    // Find the last date when this share would have been sufficient to mine a block
                    workerStats.LastSufficientDiffDate = await GetLastSufficientDiffDateAsync(worker.BestEver);

                    wasUpdated = true;
                }

                // Only notify if there was an actual improvement over a previous record (not first initialization)
                var shouldNotify = wasUpdated && worker.BestEver > previousBestEver && previousBestEver > 0;
                results.Add((worker, previousBestEver, shouldNotify));
            }

            return results;
        }
    }
}