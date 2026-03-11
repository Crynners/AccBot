using System.Text;
using CryptoBotCore.CosmosDB;
using CryptoBotCore.Models;
using Microsoft.Extensions.Logging;
using global::Telegram.Bot;
using global::Telegram.Bot.Types;
using global::Telegram.Bot.Types.Enums;
using global::Telegram.Bot.Types.ReplyMarkups;

namespace CryptoBotCore.Telegram;

/// <summary>
/// Handles interactive Telegram commands for AccBot
/// </summary>
public class TelegramCommandHandler
{
    private readonly TelegramBotClient _bot;
    private readonly ILogger _logger;
    private readonly string _currency;
    private readonly string _fiat;

    public TelegramCommandHandler(string botToken, ILogger logger, string currency, string fiat)
    {
        _bot = new TelegramBotClient(botToken);
        _logger = logger;
        _currency = currency;
        _fiat = fiat;
    }

    /// <summary>
    /// Process incoming command from Telegram
    /// </summary>
    public async Task HandleCommandAsync(string command, long chatId)
    {
        var cmd = command.ToLowerInvariant().Split(' ')[0].TrimStart('/');

        try
        {
            var response = cmd switch
            {
                "start" => await HandleStartCommand(),
                "help" => HandleHelpCommand(),
                "status" => await HandleStatusCommand(),
                "stats" => await HandleStatsCommand(),
                "config" => HandleConfigCommand(),
                _ => $"Unknown command: /{cmd}\nUse /help to see available commands."
            };

            await SendMessageAsync(chatId, response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling command {Command}", command);
            await SendMessageAsync(chatId, $"❌ Error processing command: {ex.Message}");
        }
    }

    private Task<string> HandleStartCommand()
    {
        var sb = new StringBuilder();
        sb.AppendLine("👋 <b>Welcome to AccBot!</b>");
        sb.AppendLine();
        sb.AppendLine("I'm your automated DCA (Dollar Cost Averaging) bot for cryptocurrency accumulation.");
        sb.AppendLine();
        sb.AppendLine("📋 <b>Available Commands:</b>");
        sb.AppendLine("/status - Current bot status");
        sb.AppendLine("/stats - Accumulation statistics");
        sb.AppendLine("/config - Current configuration");
        sb.AppendLine("/help - Show this help");
        sb.AppendLine();
        sb.AppendLine("🔗 <b>Setup Guide:</b>");
        sb.AppendLine("https://github.com/crynners/AccBot");
        sb.AppendLine();
        sb.AppendLine($"Currently configured for <b>{_currency}/{_fiat}</b>");

        return Task.FromResult(sb.ToString());
    }

    private string HandleHelpCommand()
    {
        var sb = new StringBuilder();
        sb.AppendLine("📚 <b>AccBot Commands</b>");
        sb.AppendLine();
        sb.AppendLine("/start - Welcome message");
        sb.AppendLine("/status - Check if bot is running");
        sb.AppendLine("/stats - View accumulation stats");
        sb.AppendLine("/config - View current config");
        sb.AppendLine("/help - This help message");
        sb.AppendLine();
        sb.AppendLine("💡 <b>Tips:</b>");
        sb.AppendLine("• Bot runs on a schedule (hourly)");
        sb.AppendLine("• Notifications are sent after each buy");
        sb.AppendLine("• Withdrawals happen automatically when fee is low");

        return sb.ToString();
    }

    private async Task<string> HandleStatusCommand()
    {
        var sb = new StringBuilder();
        sb.AppendLine("🤖 <b>Bot Status</b>");
        sb.AppendLine();
        sb.AppendLine($"✅ Bot is <b>active</b>");
        sb.AppendLine($"📍 Exchange: {BotConfiguration.CryptoExchangeAPIEnum}");
        sb.AppendLine($"💱 Pair: {_currency}/{_fiat}");
        sb.AppendLine($"💰 Chunk: {BotConfiguration.ChunkSize} {_fiat}");
        sb.AppendLine($"🔄 Withdrawal: {(BotConfiguration.WithdrawalEnabled ? "Enabled" : "Disabled")}");

        // Try to get last activity from database
        try
        {
            var cosmosDb = new CosmosDbContext();
            var summary = await cosmosDb.GetAccumulationSummary($"{_currency}_{_fiat}");
            if (summary.Buys > 0)
            {
                sb.AppendLine($"📊 Total buys: {summary.Buys}");
            }
        }
        catch
        {
            // Database not available, skip
        }

        return sb.ToString();
    }

    private async Task<string> HandleStatsCommand()
    {
        var sb = new StringBuilder();

        try
        {
            var cosmosDb = new CosmosDbContext();
            var summary = await cosmosDb.GetAccumulationSummary($"{_currency}_{_fiat}");

            if (summary.Buys == 0)
            {
                sb.AppendLine("📊 <b>No Statistics Yet</b>");
                sb.AppendLine();
                sb.AppendLine("The bot hasn't made any purchases yet.");
                sb.AppendLine("Statistics will appear after the first buy.");
                return sb.ToString();
            }

            var avgPrice = summary.InvestedFiatAmount / summary.AccumulatedCryptoAmount;
            var avgInvestment = summary.InvestedFiatAmount / summary.Buys;

            sb.AppendLine("📊 <b>Accumulation Statistics</b>");
            sb.AppendLine();
            sb.AppendLine($"🪙 <b>Total {_currency}:</b> {summary.AccumulatedCryptoAmount:N8}");
            sb.AppendLine($"💵 <b>Total invested:</b> {summary.InvestedFiatAmount:N2} {_fiat}");
            sb.AppendLine($"📈 <b>Avg buy price:</b> {avgPrice:N0} {_fiat}");
            sb.AppendLine($"🔢 <b>Total buys:</b> {summary.Buys}");
            sb.AppendLine($"💰 <b>Avg per buy:</b> {avgInvestment:N2} {_fiat}");
        }
        catch (Exception ex)
        {
            sb.AppendLine("❌ <b>Error Loading Stats</b>");
            sb.AppendLine();
            sb.AppendLine($"Could not connect to database: {ex.Message}");
        }

        return sb.ToString();
    }

    private string HandleConfigCommand()
    {
        var sb = new StringBuilder();
        sb.AppendLine("⚙️ <b>Current Configuration</b>");
        sb.AppendLine();
        sb.AppendLine($"📍 Exchange: <code>{BotConfiguration.CryptoExchangeAPIEnum}</code>");
        sb.AppendLine($"💱 Currency: <code>{_currency}</code>");
        sb.AppendLine($"💵 Fiat: <code>{_fiat}</code>");
        sb.AppendLine($"💰 Chunk size: <code>{BotConfiguration.ChunkSize} {_fiat}</code>");
        sb.AppendLine();
        sb.AppendLine("<b>Withdrawal Settings:</b>");
        sb.AppendLine($"├ Enabled: {(BotConfiguration.WithdrawalEnabled ? "✅" : "❌")}");

        if (BotConfiguration.WithdrawalEnabled)
        {
            var addr = BotConfiguration.WithdrawalAddress;
            var truncatedAddr = addr.Length > 16 ? $"{addr[..8]}...{addr[^8..]}" : addr;
            sb.AppendLine($"├ Address: <code>{truncatedAddr}</code>");
            sb.AppendLine($"├ Max fee: {BotConfiguration.MaxWithdrawalPercentageFee * 100:N2}%");
            if (BotConfiguration.MaxWithdrawalAbsoluteFee != -1)
                sb.AppendLine($"└ Max absolute: {BotConfiguration.MaxWithdrawalAbsoluteFee} {_fiat}");
            else
                sb.AppendLine($"└ Max absolute: No limit");
        }

        return sb.ToString();
    }

    private async Task SendMessageAsync(long chatId, string message)
    {
        try
        {
            await _bot.SendMessage(
                chatId,
                message,
                parseMode: ParseMode.Html,
                linkPreviewOptions: new LinkPreviewOptions { IsDisabled = true }
            );
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to send Telegram message to chat {ChatId}", chatId);
        }
    }

    /// <summary>
    /// Send a message with inline keyboard buttons
    /// </summary>
    public async Task SendMessageWithButtonsAsync(long chatId, string message, InlineKeyboardMarkup keyboard)
    {
        try
        {
            await _bot.SendMessage(
                chatId,
                message,
                parseMode: ParseMode.Html,
                replyMarkup: keyboard
            );
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to send Telegram message with buttons");
        }
    }

    /// <summary>
    /// Create a simple inline keyboard with action buttons
    /// </summary>
    public static InlineKeyboardMarkup CreateStatusButtons()
    {
        return new InlineKeyboardMarkup(new[]
        {
            new[]
            {
                InlineKeyboardButton.WithCallbackData("📊 Stats", "/stats"),
                InlineKeyboardButton.WithCallbackData("⚙️ Config", "/config")
            },
            new[]
            {
                InlineKeyboardButton.WithUrl("📚 Docs", "https://github.com/crynners/AccBot")
            }
        });
    }
}
