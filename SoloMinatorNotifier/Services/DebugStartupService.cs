using Microsoft.Extensions.Hosting;
using SoloMinatorNotifier.Functions;

namespace SoloMinatorNotifier.Services
{
    public class DebugStartupService : IHostedService
    {
        private readonly MonitorMiningStats _monitorMiningStats;

        public DebugStartupService(MonitorMiningStats monitorMiningStats)
        {
            _monitorMiningStats = monitorMiningStats;
        }

        public async Task StartAsync(CancellationToken cancellationToken)
        {
            await _monitorMiningStats.Run(null);
        }

        public Task StopAsync(CancellationToken cancellationToken)
        {
            return Task.CompletedTask;
        }
    }
}
