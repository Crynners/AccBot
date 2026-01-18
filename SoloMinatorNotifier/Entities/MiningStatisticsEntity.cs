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

        [Required]
        [StringLength(100)]
        public string WorkerName { get; set; } = default!;

        public string Hashrate1m { get; set; }
        public string Hashrate5m { get; set; }
        public string Hashrate1hr { get; set; }
        public string Hashrate1d { get; set; }
        public string Hashrate7d { get; set; }
        public long LastShare { get; set; }
        public long Shares { get; set; }
        public DateOnly? LastSufficientDiffDate { get; set; }
        public double BestShare { get; set; }
        public double BestEver { get; set; }
        public long? Authorised { get; set; }
        public int? Workers { get; set; }

        [Required]
        public DateTime RecordedAt { get; set; }
    }
}