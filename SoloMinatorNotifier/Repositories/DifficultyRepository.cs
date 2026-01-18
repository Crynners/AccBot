using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using SoloMinatorNotifier.Data;
using SoloMinatorNotifier.Entities;
using SoloMinatorNotifier.Models;

namespace SoloMinatorNotifier.Repositories
{
    public class DifficultyAdjustmentStats
    {
        private const int BLOCKS_BETWEEN_ADJUSTMENTS = 2016;

        public double NewDifficulty { get; set; }
        public double PreviousDifficulty { get; set; }
        public double PercentageChange { get; set; }
        public int DaysDifference { get; set; }
        public int HoursDifference { get; set; }
        public double AverageBlockTimeMinutes
        {
            get
            {
                var totalMinutes = (DaysDifference * 24 * 60) + (HoursDifference * 60);
                return Math.Round((double)totalMinutes / BLOCKS_BETWEEN_ADJUSTMENTS, 2);
            }
        }

        public string FormattedMessage
        {
            get
            {
                return $"📢 *New Bitcoin Difficulty Adjustment!* 📢\r\n\r\n" +
                       $"📈 *New Difficulty:*\r\n" +
                       $"{FormatShareNumber(NewDifficulty)} " +
                       $"({(PercentageChange < 0 ? "" : "+")}{PercentageChange:F2} %)\r\n\r\n" +
                       $"⚡️ *Previous Period:*\r\n" +
                       $"Difficulty: {FormatShareNumber(PreviousDifficulty)}\r\n" +
                       $"Time: {DaysDifference} days {HoursDifference} hours\r\n" +
                       $"Average Block Time: {AverageBlockTimeMinutes} minutes";
            }
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

            return $"{Math.Round(number, 2)} {units[unitIndex]}";
        }
    }

    public interface IDifficultyRepository
    {
        Task<MiningDifficultyAdjustmentEntity> GetLatestDifficultyAsync();
        Task<MiningDifficultyAdjustmentEntity> GetByBlockHeightAsync(long blockHeight);
        Task<List<MiningDifficultyAdjustmentEntity>> GetLastTwoAdjustmentsAsync();
        Task<DifficultyAdjustmentStats> GetDifficultyStatsAsync();
        Task<MiningDifficultyAdjustmentEntity> AddAsync(MiningDifficultyAdjustmentEntity entity);
        Task<bool> ExistsAsync(long blockHeight);
        Task<DateOnly?> GetLastSufficientDiffDateAsync(double bestEver);
    }

    public class DifficultyRepository : IDifficultyRepository
    {
        private readonly MiningContext _dbContext;

        public DifficultyRepository(MiningContext dbContext)
        {
            _dbContext = dbContext;
        }

        public async Task<MiningDifficultyAdjustmentEntity> GetLatestDifficultyAsync()
        {
            return await _dbContext.MiningDifficultyAdjustments
                .OrderByDescending(x => x.BlockHeight)
                .FirstOrDefaultAsync();
        }

        public async Task<MiningDifficultyAdjustmentEntity> GetByBlockHeightAsync(long blockHeight)
        {
            return await _dbContext.MiningDifficultyAdjustments
                .FirstOrDefaultAsync(x => x.BlockHeight == blockHeight);
        }

        public async Task<List<MiningDifficultyAdjustmentEntity>> GetLastTwoAdjustmentsAsync()
        {
            return await _dbContext.MiningDifficultyAdjustments
                .OrderByDescending(x => x.BlockHeight)
                .Take(2)
                .ToListAsync();
        }

        public async Task<DifficultyAdjustmentStats> GetDifficultyStatsAsync()
        {
            var lastTwoAdjustments = await GetLastTwoAdjustmentsAsync();

            if (lastTwoAdjustments.Count != 2)
                return null;

            var current = lastTwoAdjustments[0];
            var previous = lastTwoAdjustments[1];

            var percentageIncrease = ((current.Difficulty - previous.Difficulty) / previous.Difficulty) * 100;
            var timeDifference = current.BlockTime - previous.BlockTime;

            var stats = new DifficultyAdjustmentStats
            {
                NewDifficulty = current.Difficulty,
                PreviousDifficulty = previous.Difficulty,
                PercentageChange = percentageIncrease,
                DaysDifference = timeDifference.Days,
                HoursDifference = timeDifference.Hours
            };

            return stats;
        }

        public async Task<MiningDifficultyAdjustmentEntity> AddAsync(MiningDifficultyAdjustmentEntity entity)
        {
            await _dbContext.MiningDifficultyAdjustments.AddAsync(entity);
            await _dbContext.SaveChangesAsync();
            return entity;
        }

        public async Task<bool> ExistsAsync(long blockHeight)
        {
            return await _dbContext.MiningDifficultyAdjustments
                .AnyAsync(x => x.BlockHeight == blockHeight);
        }

        /// <summary>
        /// Finds the most recent date when the given share would have been sufficient to mine a block.
        /// Returns the BlockTime of the most recent difficulty adjustment where BestEver >= Difficulty.
        /// </summary>
        public async Task<DateOnly?> GetLastSufficientDiffDateAsync(double bestEver)
        {
            var lastSufficientAdjustment = await _dbContext.MiningDifficultyAdjustments
                .Where(x => bestEver >= x.Difficulty)
                .OrderByDescending(x => x.BlockTime)
                .FirstOrDefaultAsync();

            return lastSufficientAdjustment != null
                ? DateOnly.FromDateTime(lastSufficientAdjustment.BlockTime)
                : null;
        }
    }
}