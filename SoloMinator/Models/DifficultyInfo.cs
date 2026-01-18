using System.Text.Json.Serialization;

namespace SoloMinator.Models;

public class DifficultyInfo
{
    [JsonPropertyName("progressPercent")]
    public double ProgressPercent { get; set; }

    [JsonPropertyName("difficultyChange")]
    public double DifficultyChange { get; set; }

    [JsonPropertyName("estimatedRetargetDate")]
    public long EstimatedRetargetDate { get; set; }

    [JsonPropertyName("remainingBlocks")]
    public int RemainingBlocks { get; set; }

    [JsonPropertyName("remainingTime")]
    public long RemainingTime { get; set; }

    [JsonPropertyName("previousRetarget")]
    public double PreviousRetarget { get; set; }

    [JsonPropertyName("previousTime")]
    public long PreviousTime { get; set; }

    [JsonPropertyName("nextRetargetHeight")]
    public int NextRetargetHeight { get; set; }

    [JsonPropertyName("timeAvg")]
    public long TimeAvg { get; set; }

    [JsonPropertyName("timeOffset")]
    public int TimeOffset { get; set; }

    [JsonPropertyName("expectedBlocks")]
    public double ExpectedBlocks { get; set; }
}

public class DifficultyAdjustment
{
    [JsonPropertyName("time")]
    public long Time { get; set; }

    [JsonPropertyName("height")]
    public int Height { get; set; }

    [JsonPropertyName("difficulty")]
    public double Difficulty { get; set; }

    [JsonPropertyName("adjustment")]
    public double Adjustment { get; set; }
}
