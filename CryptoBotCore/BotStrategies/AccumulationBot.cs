using CryptoBotCore.API;
using CryptoBotCore.CosmosDB;
using CryptoBotCore.Models;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Telegram.Bot;

namespace CryptoBotCore.BotStrategies
{
    public class AccumulationBot
    {
        Double packageSize { get; set; }

        private static readonly string CONST_ASTERIX = "*";

        private static readonly int ROUND_NUMBER = 7;

        [NonSerialized]
        private Dictionary<string, CoinmateAPI> coinmateAPIs;

        TelegramBotClient bot = new TelegramBotClient(BotConfiguration.TelegramBot);

        private CosmosDbContext _cosmosDbContext;

        ILogger Log;

        public AccumulationBot(ILogger log)
        {
            Log = log;
        }


        public async Task Tick()
        {

            try
            {

                StringBuilder sbInformationMessage = new StringBuilder();
                Log.LogInformation("Start Tick: " + DateTime.Now);

                var pair = $"{BotConfiguration.Currency}_CZK";

                if (coinmateAPIs == null)
                {
                    inicializeAPI(BotConfiguration.Currency);
                }

                var initBalance = coinmateAPIs[BotConfiguration.Currency].getBalances();

                double CZK = initBalance.Where(x => x.currency == "CZK").Sum(x => x.available);



                Dictionary<string, StringBuilder> sb_actions = new Dictionary<string, StringBuilder>();


                if (CZK > BotConfiguration.ChunkSize)
                {
                    var response = coinmateAPIs[BotConfiguration.Currency].buy(BotConfiguration.ChunkSize, 0, true, OrderType.ExchangeMarket);

                    Log.LogInformation($"Market buy {BotConfiguration.Currency} for {BotConfiguration.ChunkSize} CZK");

                    //Serializer.SendEmail("Accumulation Bot - Buy", "You just spent " + schedule.FiatChunk + " CZK on " + schedule.Currency + ".", configuration.userId, forceEmail);
                }
                else
                {
                    //sb_actions[schedule.Currency].Append("<b>Not enough money to spend " + schedule.FiatChunk + " CZK on " + schedule.Currency + ".").Append("\r\n");
                    //Serializer.SendEmail("Accumulation Bot - No Money", "Not enough money to spend " + schedule.FiatChunk + " CZK on " + schedule.Currency + ".", configuration.userId, forceEmail);
                    //MessageBox.Show("No Money!");
                    await SendMessageAsync($"Not enough money ({CZK} CZK)", MessageTypeEnum.Warning);
                    return;
                }
      

                var afterBalance = coinmateAPIs[BotConfiguration.Currency].getBalances();
                double CZKafterBuy = afterBalance.Where(x => x.currency == "CZK").Sum(x => x.available);

                Dictionary<string, double> fees = new Dictionary<string, double>();

                if(BotConfiguration.Currency == "BTC")
                {
                    fees["BTC"] = coinmateAPIs["BTC"].getBTCWidthrawalFee();
                }

                fees["LTC"] = 0.0004;
                fees["ETH"] = 0.01;
                fees["DSH"] = 0.00001;

                
                var price = coinmateAPIs[BotConfiguration.Currency].getActualExchangeRate().Item1;


                double available = afterBalance.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available);
                double init = initBalance.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available);

                double fee_cost = (fees[BotConfiguration.Currency] / available);

                sbInformationMessage.Append("<b>Accumulation:</b> " + (available - init).ToString("N8") + " " + BotConfiguration.Currency + " for " + BotConfiguration.ChunkSize.ToString("N2") + " CZK @ " + (BotConfiguration.ChunkSize / (available - init)).ToString("N2") + " CZK").Append("\r\n");

                //Send them home
                if (fee_cost <= BotConfiguration.MaxWithdrawalPercentageFee && BotConfiguration.WithdrawalEnabled && !String.IsNullOrEmpty(BotConfiguration.WithdrawalAddress))
                {
                    coinmateAPIs[BotConfiguration.Currency].withdraw(available, BotConfiguration.WithdrawalAddress);

                    sbInformationMessage.Append("<b>Withdrawal:</b> " + available.ToString("N8") + " " + BotConfiguration.Currency + " to " + BotConfiguration.WithdrawalAddress + " with " + (fee_cost * 100).ToString("N2") + " % fee").Append("\r\n");
                    //Serializer.SendEmail("Accumulation Bot - Widthraw", "Widthraw of " + + " " + increase_string + " to " + schedule.WidthrawalAddress + ".", configuration.userId, forceEmail);
                }
                else
                {
                    List<string> reason = new List<string>();
                    if (fee_cost > BotConfiguration.MaxWithdrawalPercentageFee)
                        reason.Add("Limit exceeded");
                    if (String.IsNullOrEmpty(BotConfiguration.WithdrawalAddress))
                        reason.Add("No address");
                    if (!BotConfiguration.WithdrawalEnabled)
                        reason.Add("Turned off");

                    sbInformationMessage.Append("<b>Withdrawal:</b> Denied - [" + String.Join(", ", reason) + "] - fee cost " + (fee_cost * 100).ToString("N2") + " %, limit " + (BotConfiguration.MaxWithdrawalPercentageFee * 100).ToString("N2") + " %").Append("\r\n");
                    //Serializer.SendEmail("Accumulation Bot - Balance", "Current balance of " + schedule.Currency + " is " + available.ToString("N8") + " " + increase_string + ", fee cost " + (fee_cost * 100).ToString("N2") + "% above limit " + (schedule.WidthrawalFeeLimit * 100).ToString("N2") + "%).", configuration.userId, forceEmail);
                }

                _cosmosDbContext = new CosmosDbContext();

                var accumulationSummary = await _cosmosDbContext.GetAccumulationSummary(BotConfiguration.Currency);

                accumulationSummary.Buys += 1;
                accumulationSummary.InvestedFiatAmount += (CZK - CZKafterBuy);
                accumulationSummary.AccumulatedCryptoAmount += (available - init);

                await _cosmosDbContext.UpdateItemAsync(accumulationSummary);

                var profit = ((accumulationSummary.AccumulatedCryptoAmount * price) / accumulationSummary.InvestedFiatAmount) - 1;

                StringBuilder sb = new StringBuilder();
                sb.Append("🛒 <b>[ACTIONS]</b>").Append("\r\n");
                sb.Append(sbInformationMessage.ToString());
                sb.Append("").Append("\r\n");
                sb.Append("ℹ️ <b>[SUMMARY]</b>").Append("\r\n");
                sb.Append("<b>Total accumulation</b>: " + accumulationSummary.AccumulatedCryptoAmount.ToString("N8") + " " + BotConfiguration.Currency + " (" + accumulationSummary.InvestedFiatAmount.ToString("N2") + " CZK)").Append("\r\n");
                sb.Append("<b>Avg Accumulated Price</b>: " + (accumulationSummary.InvestedFiatAmount/accumulationSummary.AccumulatedCryptoAmount).ToString("N2") + " CZK/" + BotConfiguration.Currency).Append("\r\n");
                sb.Append("<b>Current Price</b>: " + price.ToString("N2") + " CZK/" + BotConfiguration.Currency).Append("\r\n");
                sb.Append("<b>Current Profit</b>: " + (profit * 100).ToString("N2") + " % (" + (profit * accumulationSummary.InvestedFiatAmount).ToString("N2") + " CZK)").Append("\r\n");
                //sb.Append("<b>Zero-out the profit</b>: " + ((profit >= 0) ? ("Sell " + (profit * totals[schedule.Currency].Item1).ToString("N8") + " " + schedule.Currency + " (" + (profit * totals[schedule.Currency].Item2).ToString("N2") + " CZK)") : "You are at loss, don't sell")).Append("\r\n");

                //sb.Append("<b>Next accumulation</b>: " + schedule.NextExecutedAccumulation.ToString("dd-MM-yyy HH:mm:ss")).Append("\r\n");
                sb.Append("<b>Fiat balance</b>: " + CZKafterBuy.ToString("N2") + " CZK").Append("\r\n");
                sb.Append("<b>Current balance</b>: " + afterBalance.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available).ToString("N8") + " " + BotConfiguration.Currency).Append("\r\n");
                //sb.Append("<b>Fiat depletion</b>: " + Simulate(CZK, schedule).ToString("dd-MM-yyy HH:00:00")).Append("\r\n");
                await SendMessageAsync(sb.ToString());
            }
            catch (Exception ex)
            {
                await SendMessageAsync(ex.ToString(), MessageTypeEnum.Error);
                return;
            }

        }

        private void inicializeAPI(string crypto)
        {
            this.coinmateAPIs = new Dictionary<string, CoinmateAPI>();
            this.coinmateAPIs[crypto] = new CoinmateAPI($"{crypto}_CZK", BotConfiguration.CoinMateCredentials, Log);
        }

        public async Task<string> SendMessageAsync(string message, MessageTypeEnum messageType = MessageTypeEnum.Information, int attempt = 0)
        {
            switch (messageType)
            {
                case MessageTypeEnum.Information:
                    Log.LogInformation(message);
                    message = $"#AccuBot - #{BotConfiguration.Currency} - #{BotConfiguration.UserName}{Environment.NewLine}{message}";
                    break;
                case MessageTypeEnum.Warning:
                    Log.LogWarning(message);
                    message = $"⚠️ #AccuBot - #{BotConfiguration.Currency} - #{BotConfiguration.UserName}{Environment.NewLine}{message}";
                    break;
                case MessageTypeEnum.Error:
                    Log.LogError(message);
                    message = $"❌ #AccuBot - #{BotConfiguration.Currency} - #{BotConfiguration.UserName}{Environment.NewLine}{message}";
                    break;
            }

            try
            {
                await bot.SendTextMessageAsync(BotConfiguration.TelegramChannel, message, Telegram.Bot.Types.Enums.ParseMode.Html);
            }
            catch (Exception e)
            {
                if (attempt >= 2)
                {
                    Log.LogError(e, "SendTextMessageAsync error");
                    return e.ToString();
                }
                //Repeat if exception (i.e. too many requests) occured
                Thread.Sleep(300);
                return await SendMessageAsync(message, MessageTypeEnum.Error , ++attempt);
            }

            return null;
        }
    }
}
