using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace SoloMinator.Entities;

[Table("MiningDifficultyAdjustment", Schema = "solo")]
public class MiningDifficultyAdjustmentEntity
{
    [Key]
    public int BlockHeight { get; set; }

    public long BlockTimestamp { get; set; }

    public double Difficulty { get; set; }

    public double DifficultyChange { get; set; }

    public DateTime BlockTime { get; set; }
}
