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
    private readonly ITelegramNotificationService _telegramNotificationService;
    private readonly ICKPoolService _ckPoolService;
    private readonly ILogger<SettingsModel> _logger;

    public SettingsModel(
        IRegistrationService registrationService,
        ITelegramLinkService telegramLinkService,
        ITelegramSubscriptionService subscriptionService,
        ITelegramNotificationService telegramNotificationService,
        ICKPoolService ckPoolService,
        ILogger<SettingsModel> logger)
    {
        _registrationService = registrationService;
        _telegramLinkService = telegramLinkService;
        _subscriptionService = subscriptionService;
        _telegramNotificationService = telegramNotificationService;
        _ckPoolService = ckPoolService;
        _logger = logger;
    }

    [BindProperty(SupportsGet = true)]
    public string Address { get; set; } = string.Empty;

    [BindProperty(SupportsGet = true)]
    public string Pool { get; set; } = "solo";

    public UserRegistrationEntity? Registration { get; set; }

    public List<UserRegistrationEntity> AllRegistrations { get; set; } = new();

    public List<TelegramSubscriptionEntity> Subscriptions { get; set; } = new();

    public string? TelegramLinkUrl { get; set; }

    public string? SuccessMessage { get; set; }
    public string? ErrorMessage { get; set; }

    /// <summary>
    /// Initializes common page state for POST handlers.
    /// Returns true if Registration was found, false otherwise.
    /// </summary>
    private async Task<bool> InitializePageContextAsync(string address, string pool)
    {
        if (string.IsNullOrWhiteSpace(address))
            return false;

        Address = address;
        Pool = pool;
        AllRegistrations = await _registrationService.GetAllPoolsForAddressAsync(Address);
        Registration = await _registrationService.GetByAddressAndPoolAsync(Address, Pool);

        if (Registration != null)
        {
            Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
        }

        return Registration != null;
    }

    public async Task<IActionResult> OnGetAsync()
    {
        if (string.IsNullOrWhiteSpace(Address))
        {
            return RedirectToPage("/Index");
        }

        // Load all registrations for pool tabs
        AllRegistrations = await _registrationService.GetAllPoolsForAddressAsync(Address);

        // Select current registration based on pool parameter or first available
        if (!string.IsNullOrEmpty(Pool))
        {
            Registration = AllRegistrations.FirstOrDefault(r => r.PoolVariant == Pool);
        }
        Registration ??= AllRegistrations.FirstOrDefault();

        if (Registration != null)
        {
            Pool = Registration.PoolVariant;
            Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
        }

        return Page();
    }

    public async Task<IActionResult> OnPostUpdateTelegramAsync(string address, string pool, string? telegramChatId, bool notificationsEnabled)
    {
        if (!await InitializePageContextAsync(address, pool))
            return Page();

        try
        {
            await _registrationService.UpdateTelegramSettingsAsync(Address, Pool, telegramChatId?.Trim(), notificationsEnabled);
            // Reload registration to get updated values
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
        if (!await InitializePageContextAsync(address, pool))
            return Page();

        try
        {
            var token = await _telegramLinkService.GenerateLinkTokenAsync(Registration!.Id);
            TelegramLinkUrl = _telegramLinkService.GetDeepLink(token);
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
        if (!await InitializePageContextAsync(address, pool))
            return Page();

        try
        {
            var subscription = await _subscriptionService.GetSubscriptionByIdAsync(subscriptionId);
            if (subscription != null && subscription.UserRegistrationId == Registration!.Id)
            {
                var chatId = subscription.TelegramChatId;
                await _subscriptionService.DeleteSubscriptionAsync(subscriptionId);

                // Send notification to the deleted chat
                await _telegramNotificationService.SendSubscriptionDeletedNotificationAsync(chatId, Registration.MiningAddress);

                SuccessMessage = "Settings_SubscriptionDeleted";
                _logger.LogInformation("Deleted subscription {SubscriptionId} for {Address}", subscriptionId, Registration.MiningAddress);

                // Reload subscriptions after deletion
                Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
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

        return Page();
    }

    public async Task<IActionResult> OnPostToggleSubscriptionAsync(string address, string pool, int subscriptionId, bool isActive)
    {
        if (!await InitializePageContextAsync(address, pool))
            return Page();

        try
        {
            var subscription = await _subscriptionService.GetSubscriptionByIdAsync(subscriptionId);
            if (subscription != null && subscription.UserRegistrationId == Registration!.Id)
            {
                await _subscriptionService.ToggleSubscriptionAsync(subscriptionId, isActive);

                // Send notification about pause/resume
                if (isActive)
                {
                    await _telegramNotificationService.SendSubscriptionResumedNotificationAsync(
                        subscription.TelegramChatId, Registration.MiningAddress);
                }
                else
                {
                    await _telegramNotificationService.SendSubscriptionPausedNotificationAsync(
                        subscription.TelegramChatId, Registration.MiningAddress);
                }

                SuccessMessage = "Settings_Saved";
                _logger.LogInformation("Toggled subscription {SubscriptionId} to {IsActive}", subscriptionId, isActive);

                // Reload subscriptions to reflect changes
                Subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(Registration.Id);
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

        return Page();
    }

    public async Task<IActionResult> OnPostDiscoverPoolsAsync(string address, string pool)
    {
        if (string.IsNullOrWhiteSpace(address))
        {
            return RedirectToPage("/Index");
        }

        Address = address;
        Pool = pool;

        // Get existing registrations
        AllRegistrations = await _registrationService.GetAllPoolsForAddressAsync(Address);
        var existingPools = AllRegistrations.Select(r => r.PoolVariant).ToHashSet();

        // Check all pools
        var poolResults = await _ckPoolService.CheckAllPoolsAsync(Address);

        foreach (var (poolName, result) in poolResults)
        {
            if (result.IsValid && !existingPools.Contains(poolName))
            {
                await _registrationService.CreateRegistrationAsync(Address, poolName);
                _logger.LogInformation("Discovered and registered {Address} on {Pool}", Address, poolName);
            }
        }

        return RedirectToPage("/Settings", new { address = Address, pool = Pool });
    }

    public async Task<IActionResult> OnPostDeleteAsync(string address, string pool)
    {
        // Get subscriptions before deleting (so we can notify them)
        var registration = await _registrationService.GetByAddressAndPoolAsync(address, pool);
        List<string> chatIds = new();

        if (registration != null)
        {
            var subscriptions = await _subscriptionService.GetSubscriptionsForRegistrationAsync(registration.Id);
            chatIds = subscriptions.Select(s => s.TelegramChatId).ToList();
        }

        var deleted = await _registrationService.DeleteRegistrationAsync(address, pool);

        if (deleted)
        {
            _logger.LogInformation("Registration deleted for {Address} on {Pool}", address, pool);

            // Notify all subscribed chats about the deletion
            if (chatIds.Count > 0)
            {
                await _telegramNotificationService.SendAddressDeletedNotificationAsync(chatIds, address);
            }

            // Check if there are other registrations for this address
            var remainingRegistrations = await _registrationService.GetAllPoolsForAddressAsync(address);
            if (remainingRegistrations.Count > 0)
            {
                // Redirect to the first remaining pool's settings
                return RedirectToPage("/Settings", new { address = address, pool = remainingRegistrations.First().PoolVariant });
            }

            return RedirectToPage("/Index");
        }

        // Delete failed - initialize page context and show error
        await InitializePageContextAsync(address, pool);
        ErrorMessage = "Settings_SaveError";
        return Page();
    }
}
