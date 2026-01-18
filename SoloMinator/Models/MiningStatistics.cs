using System.Text.Json.Serialization;

namespace SoloMinator.Models;

public class MiningStatistics
{
    [JsonPropertyName("hashrate1m")]
    public string? Hashrate1m { get; set; }

    [JsonPropertyName("hashrate5m")]
    public string? Hashrate5m { get; set; }

    [JsonPropertyName("hashrate1hr")]
    public string? Hashrate1hr { get; set; }

    [JsonPropertyName("hashrate1d")]
    public string? Hashrate1d { get; set; }

    [JsonPropertyName("hashrate7d")]
    public string? Hashrate7d { get; set; }

    [JsonPropertyName("lastshare")]
    public long LastShare { get; set; }

    [JsonPropertyName("shares")]
    public long Shares { get; set; }

    [JsonPropertyName("bestshare")]
    public double BestShare { get; set; }

    [JsonPropertyName("bestever")]
    public double BestEver { get; set; }

    [JsonPropertyName("authorised")]
    public long? Authorised { get; set; }

    [JsonPropertyName("workers")]
    public int? Workers { get; set; }

    [JsonPropertyName("worker")]
    public List<WorkerStatistics>? Worker { get; set; }
}

public class WorkerStatistics
{
    [JsonPropertyName("workername")]
    public string? WorkerName { get; set; }

    [JsonPropertyName("hashrate1m")]
    public string? Hashrate1m { get; set; }

    [JsonPropertyName("hashrate5m")]
    public string? Hashrate5m { get; set; }

    [JsonPropertyName("hashrate1hr")]
    public string? Hashrate1hr { get; set; }

    [JsonPropertyName("hashrate1d")]
    public string? Hashrate1d { get; set; }

    [JsonPropertyName("hashrate7d")]
    public string? Hashrate7d { get; set; }

    [JsonPropertyName("lastshare")]
    public long LastShare { get; set; }

    [JsonPropertyName("shares")]
    public long Shares { get; set; }

    [JsonPropertyName("bestshare")]
    public double BestShare { get; set; }

    [JsonPropertyName("bestever")]
    public double BestEver { get; set; }
}
