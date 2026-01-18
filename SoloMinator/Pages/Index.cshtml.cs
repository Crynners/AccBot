using Microsoft.AspNetCore.Mvc.RazorPages;
using SoloMinator.Models;
using SoloMinator.Services;

namespace SoloMinator.Pages;

public class IndexModel : PageModel
{
    private readonly IBitcoinService _bitcoinService;
    private readonly ILogger<IndexModel> _logger;

    public IndexModel(
        IBitcoinService bitcoinService,
        ILogger<IndexModel> logger)
    {
        _bitcoinService = bitcoinService;
        _logger = logger;
    }

    // Bitcoin network info
    public DifficultyInfo? DifficultyInfo { get; set; }
    public double? CurrentDifficulty { get; set; }
    public string? NetworkHashrate { get; set; }

    public async Task OnGetAsync()
    {
        // Load Bitcoin network info
        try
        {
            var difficultyTask = _bitcoinService.GetCurrentDifficultyAsync();
            var difficultyValueTask = _bitcoinService.GetCurrentDifficultyValueAsync();
            var hashrateTask = _bitcoinService.GetNetworkHashrateAsync();

            await Task.WhenAll(difficultyTask, difficultyValueTask, hashrateTask);

            DifficultyInfo = await difficultyTask;
            CurrentDifficulty = await difficultyValueTask;
            NetworkHashrate = await hashrateTask;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to load Bitcoin network info");
        }
    }
}
