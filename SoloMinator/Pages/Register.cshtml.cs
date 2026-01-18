using System.ComponentModel.DataAnnotations;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using SoloMinator.Services;

namespace SoloMinator.Pages;

public class RegisterModel : PageModel
{
    private readonly ICKPoolService _ckPoolService;
    private readonly IRegistrationService _registrationService;
    private readonly ILogger<RegisterModel> _logger;

    public RegisterModel(
        ICKPoolService ckPoolService,
        IRegistrationService registrationService,
        ILogger<RegisterModel> logger)
    {
        _ckPoolService = ckPoolService;
        _registrationService = registrationService;
        _logger = logger;
    }

    [BindProperty]
    [Required(ErrorMessage = "Validation_AddressRequired")]
    [RegularExpression(@"^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,62}$", ErrorMessage = "Validation_AddressInvalid")]
    public string MiningAddress { get; set; } = string.Empty;

    [BindProperty]
    [Required]
    public string PoolVariant { get; set; } = "solo";

    public string? ErrorMessage { get; set; }

    public void OnGet()
    {
    }

    public async Task<IActionResult> OnPostAsync()
    {
        if (!ModelState.IsValid)
        {
            return Page();
        }

        // Check if already registered
        var existing = await _registrationService.GetByAddressAndPoolAsync(MiningAddress, PoolVariant);
        if (existing != null)
        {
            return RedirectToPage("/Dashboard", new { address = existing.MiningAddress, pool = existing.PoolVariant });
        }

        // Validate address on pool
        var (isValid, _, error) = await _ckPoolService.ValidateAndGetStatsAsync(MiningAddress, PoolVariant);

        if (!isValid)
        {
            ErrorMessage = error ?? "Address validation failed";
            return Page();
        }

        // Create registration
        var registration = await _registrationService.CreateRegistrationAsync(MiningAddress, PoolVariant);

        _logger.LogInformation("New registration created for {Address} on {Pool}", MiningAddress, PoolVariant);

        return RedirectToPage("/Dashboard", new { address = registration.MiningAddress, pool = registration.PoolVariant });
    }
}
