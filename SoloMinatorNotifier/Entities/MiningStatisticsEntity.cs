using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace SoloMinatorNotifier.Entities
{
    [Table("Stats", Schema = "solo")]
    public class MiningStatisticsEntity
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public int Id { get; set; }

        /// <summary>
        /// Foreign key to UserRegistrations table. Null for orphaned records.
        /// </summary>
        public int? UserRegistrationId { get; set; }

        /// <summary>
        /// Worker suffix without address prefix (e.g., "worker1" instead of "pool:address.worker1").
        /// Null for global address stats.
        /// </summary>
        [StringLength(100)]
        public string? WorkerSuffix { get; set; }

        /// <summary>
        /// Legacy field: Full worker name in format "pool:address" or "pool:address.worker".
        /// Kept for backward compatibility during migration.
        /// </summary>
        [Required]
        [StringLength(100)]
        public string WorkerName { get; set; } = default!;

        public string Hashrate1m { get; set; } = string.Empty;
        public string Hashrate5m { get; set; } = string.Empty;
        public string Hashrate1hr { get; set; } = string.Empty;
        public string Hashrate1d { get; set; } = string.Empty;
        public string Hashrate7d { get; set; } = string.Empty;
        public long LastShare { get; set; }
        public long Shares { get; set; }
        public DateOnly? LastSufficientDiffDate { get; set; }
        public double BestShare { get; set; }
        public double BestEver { get; set; }
        public long? Authorised { get; set; }
        public int? Workers { get; set; }

        [Required]
        public DateTime RecordedAt { get; set; }

        // Navigation property
        [ForeignKey(nameof(UserRegistrationId))]
        public virtual UserRegistrationEntity? UserRegistration { get; set; }
    }
}