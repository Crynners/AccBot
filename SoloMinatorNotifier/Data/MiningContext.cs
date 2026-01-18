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

            // MiningStatistics indexes and FK configuration
            modelBuilder.Entity<MiningStatisticsEntity>()
                .HasIndex(e => e.WorkerName)
                .HasDatabaseName("IX_Stats_WorkerName");

            modelBuilder.Entity<MiningStatisticsEntity>()
                .HasIndex(e => e.UserRegistrationId)
                .HasDatabaseName("IX_Stats_UserRegistrationId");

            modelBuilder.Entity<MiningStatisticsEntity>()
                .HasIndex(e => e.RecordedAt)
                .HasDatabaseName("IX_Stats_RecordedAt")
                .IsDescending();

            modelBuilder.Entity<MiningStatisticsEntity>()
                .HasOne(s => s.UserRegistration)
                .WithMany()
                .HasForeignKey(s => s.UserRegistrationId)
                .OnDelete(DeleteBehavior.SetNull);
        }
    }
}
