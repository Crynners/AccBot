using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using SoloMinator.Entities;
using SoloMinator.Services;

namespace SoloMinator.Pages;

public class SettingsModel : PageModel
{
    private readonly IRegistrationService _registrationService;
    private readonly ITelegramLinkService _telegramLinkService;
    private readonly ITelegramSubscriptionService _subscriptionService;
    private readonly ILogger<SettingsModel> _logger;

    public SettingsModel(
        IRegistrationService registrationService,
        ITelegramLinkService telegramLinkService,
        ITelegramSubscriptionService subscriptionService,
        ILogger<SettingsModel> logger)
    {
        _registrationService = registrationService;
        _telegramLinkService = telegramLinkService;
        _subscriptionService = subscriptionService;
        _logger = logger;
    }

    [BindProperty(SupportsGet = true)]
    public string Address { get; set; } = string.Empty;

    [BindProperty(SupportsGet = true)]
    public string Pool { get; set; } = "solo";

    public UserRegistrationEntity? Registration { get; set; }

    public List<TelegramSubscriptionEntity> Subscriptions { get; set; } = new();

    public string? TelegramLinkUrl { get; set; }

    public string? SuccessMessage { get; set; }
    public string? ErrorMessage { get; set; }

    public async Task<IActionResult> OnGetAsync()
    {
        if (string.IsNullOrWhiteSpace(Address))
        {
            return RedirectToPage("/Index");
        }

        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);

        if (Registration != null)
        {
            Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
        }

        return Page();
    }

    public async Task<IActionResult> OnPostUpdateTelegramAsync(string address, string pool, string? telegramChatId, bool notificationsEnabled)
    {
        Address = address;
        Pool = pool;
        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);

        if (Registration == null)
        {
            return Page();
        }

        try
        {
            await _registrationService.UpdateTelegramSettingsAsync(Address, Pool, telegramChatId?.Trim(), notificationsEnabled);
            Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);
            Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration!.Id);
            SuccessMessage = "Settings_Saved";
            _logger.LogInformation("Updated Telegram settings for {Address} on {Pool}", Registration?.MiningAddress, Pool);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error updating Telegram settings");
            ErrorMessage = "Settings_SaveError";
        }

        return Page();
    }

    public async Task<IActionResult> OnPostGenerateLinkAsync(string address, string pool)
    {
        Address = address;
        Pool = pool;
        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);

        if (Registration == null)
        {
            return Page();
        }

        try
        {
            var token = await _telegramLinkService.GenerateLinkTokenAsync(Registration.Id);
            TelegramLinkUrl = _telegramLinkService.GetDeepLink(token);
            Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
            SuccessMessage = "Settings_LinkGenerated";
            _logger.LogInformation("Generated Telegram link for {Address} on {Pool}", Registration.MiningAddress, Pool);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error generating Telegram link");
            ErrorMessage = "Settings_SaveError";
        }

        return Page();
    }

    public async Task<IActionResult> OnPostDeleteSubscriptionAsync(string address, string pool, int subscriptionId)
    {
        Address = address;
        Pool = pool;
        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);

        if (Registration == null)
        {
            return Page();
        }

        try
        {
            var subscription = await _subscriptionService.GetSubscriptionByIdAsync(subscriptionId);
            if (subscription != null && subscription.UserRegistrationId == Registration.Id)
            {
                await _subscriptionService.DeleteSubscriptionAsync(subscriptionId);
                SuccessMessage = "Settings_SubscriptionDeleted";
                _logger.LogInformation("Deleted subscription {SubscriptionId} for {Address}", subscriptionId, Registration.MiningAddress);
            }
            else
            {
                ErrorMessage = "Settings_SaveError";
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error deleting subscription");
            ErrorMessage = "Settings_SaveError";
        }

        Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
        return Page();
    }

    public async Task<IActionResult> OnPostToggleSubscriptionAsync(string address, string pool, int subscriptionId, bool isActive)
    {
        Address = address;
        Pool = pool;
        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);

        if (Registration == null)
        {
            return Page();
        }

        try
        {
            var subscription = await _subscriptionService.GetSubscriptionByIdAsync(subscriptionId);
            if (subscription != null && subscription.UserRegistrationId == Registration.Id)
            {
                await _subscriptionService.ToggleSubscriptionAsync(subscriptionId, isActive);
                SuccessMessage = "Settings_Saved";
                _logger.LogInformation("Toggled subscription {SubscriptionId} to {IsActive}", subscriptionId, isActive);
            }
            else
            {
                ErrorMessage = "Settings_SaveError";
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error toggling subscription");
            ErrorMessage = "Settings_SaveError";
        }

        Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
        return Page();
    }

    public async Task<IActionResult> OnPostDeleteAsync(string address, string pool)
    {
        var deleted = await _registrationService.DeleteRegistrationAsync(address, pool);

        if (deleted)
        {
            _logger.LogInformation("Registration deleted for {Address} on {Pool}", address, pool);
            return RedirectToPage("/Index");
        }

        Address = address;
        Pool = pool;
        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);
        if (Registration != null)
        {
            Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
        }
        ErrorMessage = "Settings_SaveError";
        return Page();
    }
}
