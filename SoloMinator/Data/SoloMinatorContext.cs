using Microsoft.EntityFrameworkCore;
using SoloMinator.Entities;

namespace SoloMinator.Data;

public class SoloMinatorContext : DbContext
{
    public SoloMinatorContext(DbContextOptions<SoloMinatorContext> options) : base(options)
    {
    }

    public DbSet<UserRegistrationEntity> UserRegistrations { get; set; }
    public DbSet<MiningDifficultyAdjustmentEntity> MiningDifficultyAdjustments { get; set; }
    public DbSet<TelegramSubscriptionEntity> TelegramSubscriptions { get; set; }
    public DbSet<TelegramLinkTokenEntity> TelegramLinkTokens { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<UserRegistrationEntity>(entity =>
        {
            // Composite unique index - allows same address on different pools
            entity.HasIndex(e => new { e.MiningAddress, e.PoolVariant }).IsUnique();
        });

        modelBuilder.Entity<TelegramSubscriptionEntity>(entity =>
        {
            // Unique index - one chat per user registration
            entity.HasIndex(e => new { e.UserRegistrationId, e.TelegramChatId }).IsUnique();
        });

        modelBuilder.Entity<TelegramLinkTokenEntity>(entity =>
        {
            // Unique index for token lookup
            entity.HasIndex(e => e.Token).IsUnique();
        });
    }
}
