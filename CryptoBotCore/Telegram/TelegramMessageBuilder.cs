using System.Text;
using CryptoBotCore.CosmosDB.Model;
using CryptoBotCore.Models;

namespace CryptoBotCore.Telegram;

/// <summary>
/// Builds formatted Telegram messages with improved UX
/// </summary>
public class TelegramMessageBuilder
{
    private readonly StringBuilder _sb = new();
    private readonly string _currency;
    private readonly string _fiat;

    // Display precision
    private const int FiatDigits = 2;
    private const int CryptoDigits = 8;
    private const int PriceDigits = 0;
    private const int FeeDigits = 8;

    public TelegramMessageBuilder(string currency, string fiat)
    {
        _currency = currency;
        _fiat = fiat;
    }

    public TelegramMessageBuilder AddHeader(MessageTypeEnum messageType, string username, string? account = null)
    {
        var emoji = messageType switch
        {
            MessageTypeEnum.Information => "✅",
            MessageTypeEnum.Warning => "⚠️",
            MessageTypeEnum.Error => "❌",
            _ => "📊"
        };

        var accountTag = string.IsNullOrEmpty(account) ? "" : $" #{account}";
        _sb.AppendLine($"{emoji} <b>AccBot</b> | #{_currency}{accountTag}");
        _sb.AppendLine($"👤 #{username.Replace(' ', '_')}");
        _sb.AppendLine();
        return this;
    }

    public TelegramMessageBuilder AddBuySection(decimal amount, decimal cost, decimal price, decimal fee)
    {
        _sb.AppendLine("📈 <b>Purchase</b>");
        _sb.AppendLine($"├ {FormatCrypto(amount)} {_currency}");
        _sb.AppendLine($"├ Cost: {FormatFiat(cost)} {_fiat}");
        _sb.AppendLine($"├ Price: {FormatPrice(price)} {_fiat}");
        _sb.AppendLine($"└ Fee: {FormatFee(fee)} {_fiat}");
        _sb.AppendLine();
        return this;
    }

    public TelegramMessageBuilder AddWithdrawalSuccess(decimal amount, string address, decimal feePercent, decimal feeFiat)
    {
        _sb.AppendLine("💸 <b>Withdrawal</b> ✓");
        _sb.AppendLine($"├ {FormatCrypto(amount)} {_currency}");
        _sb.AppendLine($"├ To: <code>{TruncateAddress(address)}</code>");
        _sb.AppendLine($"└ Fee: {FormatPercent(feePercent)} ({FormatFiat(feeFiat)} {_fiat})");
        _sb.AppendLine();
        return this;
    }

    public TelegramMessageBuilder AddWithdrawalDenied(List<string> reasons, decimal feePercent, decimal feeFiat, decimal maxPercent, decimal? maxAbsolute = null)
    {
        _sb.AppendLine("💸 <b>Withdrawal</b> ✗");
        _sb.AppendLine($"├ Reason: {string.Join(", ", reasons)}");
        _sb.AppendLine($"├ Fee: {FormatPercent(feePercent)} ({FormatFiat(feeFiat)} {_fiat})");

        var limits = new List<string> { FormatPercent(maxPercent) };
        if (maxAbsolute.HasValue && maxAbsolute.Value != -1)
            limits.Add($"{FormatFiat(maxAbsolute.Value)} {_fiat}");

        _sb.AppendLine($"└ Limit: {string.Join(" / ", limits)}");
        _sb.AppendLine();
        return this;
    }

    public TelegramMessageBuilder AddWithdrawalError(string error)
    {
        _sb.AppendLine("💸 <b>Withdrawal</b> ❌");
        _sb.AppendLine($"└ Error: {error}");
        _sb.AppendLine();
        return this;
    }

    public TelegramMessageBuilder AddSummarySection(AccumulationSummary summary, decimal currentPrice, decimal fiatBalance, decimal cryptoBalance)
    {
        var totalValue = summary.AccumulatedCryptoAmount * currentPrice;
        var profitPercent = (totalValue / summary.InvestedFiatAmount) - 1;
        var profitFiat = profitPercent * summary.InvestedFiatAmount;
        var avgPrice = summary.InvestedFiatAmount / summary.AccumulatedCryptoAmount;
        var avgInvestment = summary.InvestedFiatAmount / summary.Buys;

        var profitEmoji = profitPercent >= 0 ? "📈" : "📉";
        var profitSign = profitPercent >= 0 ? "+" : "";

        _sb.AppendLine("📊 <b>Summary</b>");
        _sb.AppendLine($"├ Total: {FormatCrypto(summary.AccumulatedCryptoAmount)} {_currency}");
        _sb.AppendLine($"├ Invested: {FormatFiat(summary.InvestedFiatAmount)} {_fiat}");
        _sb.AppendLine($"├ Avg price: {FormatPrice(avgPrice)} {_fiat}");
        _sb.AppendLine($"├ Current: {FormatPrice(currentPrice)} {_fiat}");
        _sb.AppendLine($"├ {profitEmoji} P/L: {profitSign}{FormatPercent(profitPercent)} ({profitSign}{FormatFiat(profitFiat)} {_fiat})");
        _sb.AppendLine($"├ Buys: {summary.Buys} (avg {FormatFiat(avgInvestment)} {_fiat})");
        _sb.AppendLine();

        _sb.AppendLine("💰 <b>Balance</b>");
        _sb.AppendLine($"├ {_fiat}: {FormatFiat(fiatBalance)}");
        _sb.AppendLine($"└ {_currency}: {FormatCrypto(cryptoBalance)} ({FormatFiat(cryptoBalance * currentPrice)} {_fiat})");

        return this;
    }

    public TelegramMessageBuilder AddWarning(string message)
    {
        _sb.AppendLine($"⚠️ {message}");
        return this;
    }

    public TelegramMessageBuilder AddError(string message)
    {
        _sb.AppendLine($"❌ {message}");
        return this;
    }

    public TelegramMessageBuilder AddInsufficientFunds(decimal balance, decimal required)
    {
        _sb.AppendLine("⚠️ <b>Insufficient Funds</b>");
        _sb.AppendLine($"├ Balance: {FormatFiat(balance)} {_fiat}");
        _sb.AppendLine($"└ Required: &gt;{FormatFiat(required)} {_fiat}");
        return this;
    }

    public string Build() => _sb.ToString().TrimEnd();

    // Formatting helpers
    private string FormatFiat(decimal value) => value.ToString($"N{FiatDigits}");
    private string FormatCrypto(decimal value) => value.ToString($"N{CryptoDigits}");
    private string FormatPrice(decimal value) => value.ToString($"N{PriceDigits}");
    private string FormatFee(decimal value) => value.ToString($"N{FeeDigits}");
    private string FormatPercent(decimal value) => $"{(value * 100):N2}%";

    private static string TruncateAddress(string address)
    {
        if (string.IsNullOrEmpty(address) || address.Length <= 16)
            return address;
        return $"{address[..8]}...{address[^8..]}";
    }
}

/// <summary>
/// Static helper for generating deep links and QR codes
/// </summary>
public static class TelegramDeepLinks
{
    /// <summary>
    /// Generates a deep link to start a conversation with the bot
    /// </summary>
    public static string GetBotStartLink(string botUsername, string? startParameter = null)
    {
        if (string.IsNullOrEmpty(startParameter))
            return $"https://t.me/{botUsername}";
        return $"https://t.me/{botUsername}?start={startParameter}";
    }

    /// <summary>
    /// Generates a link to add the bot to a group
    /// </summary>
    public static string GetAddToGroupLink(string botUsername)
    {
        return $"https://t.me/{botUsername}?startgroup=true";
    }

    /// <summary>
    /// Generates a QR code URL using a public API
    /// </summary>
    public static string GetQrCodeUrl(string data, int size = 200)
    {
        var encodedData = Uri.EscapeDataString(data);
        return $"https://api.qrserver.com/v1/create-qr-code/?size={size}x{size}&data={encodedData}";
    }
}
