using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using SoloMinator.Entities;
using SoloMinator.Models;
using SoloMinator.Services;

namespace SoloMinator.Pages;

public class DashboardModel : PageModel
{
    private readonly IRegistrationService _registrationService;
    private readonly ICKPoolService _ckPoolService;
    private readonly IBitcoinService _bitcoinService;
    private readonly ILogger<DashboardModel> _logger;

    public DashboardModel(
        IRegistrationService registrationService,
        ICKPoolService ckPoolService,
        IBitcoinService bitcoinService,
        ILogger<DashboardModel> logger)
    {
        _registrationService = registrationService;
        _ckPoolService = ckPoolService;
        _bitcoinService = bitcoinService;
        _logger = logger;
    }

    [BindProperty(SupportsGet = true)]
    public string Address { get; set; } = string.Empty;

    [BindProperty(SupportsGet = true)]
    public string? Pool { get; set; }

    public UserRegistrationEntity? Registration { get; set; }
    public List<UserRegistrationEntity> AllRegistrations { get; set; } = new();
    public MiningStatistics? Stats { get; set; }
    public DifficultyInfo? DifficultyInfo { get; set; }
    public double? CurrentDifficulty { get; set; }

    // Lottery statistics
    public double? NetworkHashrateValue { get; set; }
    public double? UserHashrateValue { get; set; }
    public double? ChancePerWeek { get; set; }
    public double? ChancePerYear { get; set; }
    public double? ExpectedDaysToBlock { get; set; }
    public HistoricalDifficultyMatch? LastBlockFoundMatch { get; set; }

    public async Task<IActionResult> OnGetAsync()
    {
        if (string.IsNullOrWhiteSpace(Address))
        {
            return RedirectToPage("/Index");
        }

        // Get all registrations for this address
        AllRegistrations = await _registrationService.GetAllPoolsForAddressAsync(Address);

        if (AllRegistrations.Count == 0)
        {
            return Page();
        }

        // Select current registration based on pool parameter or first available
        if (!string.IsNullOrEmpty(Pool))
        {
            Registration = AllRegistrations.FirstOrDefault(r => r.PoolVariant == Pool);
        }
        Registration ??= AllRegistrations.First();
        Pool = Registration.PoolVariant;

        // Fetch stats in parallel
        var statsTask = _ckPoolService.ValidateAndGetStatsAsync(Registration.MiningAddress, Registration.PoolVariant);
        var difficultyTask = _bitcoinService.GetCurrentDifficultyAsync();
        var difficultyValueTask = _bitcoinService.GetCurrentDifficultyValueAsync();
        var networkHashrateTask = _bitcoinService.GetNetworkHashrateValueAsync();

        await Task.WhenAll(statsTask, difficultyTask, difficultyValueTask, networkHashrateTask);

        var (_, stats, _) = await statsTask;
        Stats = stats;
        DifficultyInfo = await difficultyTask;
        CurrentDifficulty = await difficultyValueTask;
        NetworkHashrateValue = await networkHashrateTask;

        // Calculate lottery statistics
        if (Stats != null && CurrentDifficulty.HasValue && CurrentDifficulty.Value > 0)
        {
            // Parse user hashrate (use 1d hashrate as most stable)
            UserHashrateValue = ParseHashrate(Stats.Hashrate1d ?? Stats.Hashrate1hr ?? "0");

            if (UserHashrateValue > 0)
            {
                // Expected hashes needed to find a block = difficulty * 2^32
                var expectedHashes = CurrentDifficulty.Value * Math.Pow(2, 32);

                // Hashes per time period
                var secondsPerWeek = 7.0 * 24 * 60 * 60;
                var secondsPerYear = 365.0 * 24 * 60 * 60;

                var hashesPerWeek = UserHashrateValue.Value * secondsPerWeek;
                var hashesPerYear = UserHashrateValue.Value * secondsPerYear;

                // Probability of finding a block in time period
                ChancePerWeek = (hashesPerWeek / expectedHashes) * 100;
                ChancePerYear = (hashesPerYear / expectedHashes) * 100;

                // Expected time to find a block (in days)
                var expectedSeconds = expectedHashes / UserHashrateValue.Value;
                ExpectedDaysToBlock = expectedSeconds / 86400.0;
            }

            // Find when BestEver would have found a block
            if (Stats.BestEver > 0)
            {
                LastBlockFoundMatch = await _bitcoinService.GetLastDateWhenDifficultyWasBelow(Stats.BestEver);
            }
        }

        return Page();
    }

    private static double ParseHashrate(string hashrate)
    {
        if (string.IsNullOrWhiteSpace(hashrate))
            return 0;

        hashrate = hashrate.Trim().ToUpper();

        // Extract number and unit
        var numberPart = "";
        var unitPart = "";

        foreach (var c in hashrate)
        {
            if (char.IsDigit(c) || c == '.' || c == ',')
                numberPart += c;
            else if (char.IsLetter(c))
                unitPart += c;
        }

        if (!double.TryParse(numberPart.Replace(',', '.'), System.Globalization.NumberStyles.Any,
            System.Globalization.CultureInfo.InvariantCulture, out var value))
            return 0;

        // Convert to H/s based on unit
        // CKPool returns formats like "2.3T" (single letter) or "2.3TH" or "2.3TH/S"
        return unitPart switch
        {
            "Z" or "ZH" or "ZH/S" or "ZHS" => value * 1e21,
            "E" or "EH" or "EH/S" or "EHS" => value * 1e18,
            "P" or "PH" or "PH/S" or "PHS" => value * 1e15,
            "T" or "TH" or "TH/S" or "THS" => value * 1e12,
            "G" or "GH" or "GH/S" or "GHS" => value * 1e9,
            "M" or "MH" or "MH/S" or "MHS" => value * 1e6,
            "K" or "KH" or "KH/S" or "KHS" => value * 1e3,
            _ => value
        };
    }
}
