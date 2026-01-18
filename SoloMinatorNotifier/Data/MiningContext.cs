using Microsoft.EntityFrameworkCore;
using SoloMinatorNotifier.Entities;


namespace SoloMinatorNotifier.Data
{
    public class MiningContext : DbContext
    {
        public MiningContext(DbContextOptions<MiningContext> options) : base(options)
        {
        }

        public DbSet<MiningStatisticsEntity> MiningStatistics { get; set; } = null!;
        public DbSet<MiningDifficultyAdjustmentEntity> MiningDifficultyAdjustments { get; set; } = null!;
        public DbSet<UserRegistrationEntity> UserRegistrations { get; set; } = null!;
        public DbSet<TelegramSubscriptionEntity> TelegramSubscriptions { get; set; } = null!;

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<UserRegistrationEntity>()
                .HasIndex(e => new { e.MiningAddress, e.PoolVariant })
                .IsUnique();

            modelBuilder.Entity<TelegramSubscriptionEntity>()
                .HasIndex(e => new { e.UserRegistrationId, e.TelegramChatId })
                .IsUnique();
        }
    }
}
