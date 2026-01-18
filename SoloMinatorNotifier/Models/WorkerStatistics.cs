namespace SoloMinatorNotifier.Models
{
    public class WorkerStatistics
    {
        public string WorkerName { get; set; }
        public string Hashrate1m { get; set; }
        public string Hashrate5m { get; set; }
        public string Hashrate1hr { get; set; }
        public string Hashrate1d { get; set; }
        public string Hashrate7d { get; set; }
        public long LastShare { get; set; }
        public long Shares { get; set; }
        public double BestShare { get; set; }
        public double BestEver { get; set; }
    }
}
