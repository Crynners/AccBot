using CryptoBotCore.API;
using CryptoBotCore.CosmosDB;
using CryptoBotCore.CosmosDB.Model;
using CryptoBotCore.Models;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Globalization;
using Telegram.Bot;

namespace CryptoBotCore.BotStrategies
{
    public class AccumulationBot
    {
        [NonSerialized]
        private ICryptoExchangeAPI cryptoExchangeAPI;

        TelegramBotClient TelegramBot { get; set; }

        private CosmosDbContext _cosmosDbContext;

        readonly ILogger Log;

        public AccumulationBot(ILogger log)
        {
            Log = log;
        }
        
        private void initializeAPI()
        {
            switch (BotConfiguration.CryptoExchangeAPIEnum)
            {
                case CryptoExchangeAPIEnum.Coinmate:
                    this.cryptoExchangeAPI = new CoinmateAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.Huobi:
                    this.cryptoExchangeAPI = new HuobiAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.Binance:
                    this.cryptoExchangeAPI = new BinanceAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.Kraken:
                    this.cryptoExchangeAPI = new KrakenAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", 
                                                             BotConfiguration.WithdrawalKeyName, BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.Bitfinex:
                    this.cryptoExchangeAPI = new BitfinexAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.Bittrex:
                    this.cryptoExchangeAPI = new BittrexAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.FTX:
                    this.cryptoExchangeAPI = new FtxAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}",
                                                          BotConfiguration.Account, BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.KuCoin:
                    this.cryptoExchangeAPI = new KuCoinAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;
                case CryptoExchangeAPIEnum.Coinbase:
                    this.cryptoExchangeAPI = new CoinbaseAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.ExchangeCredentials, Log);
                    break;

                default:
                    throw new NotImplementedException();
            }
        }

        public async Task Tick()
        {
            try
            {
                StringBuilder sbActions = new StringBuilder();
                Log.LogInformation("Start Tick: " + DateTime.Now);

                if (cryptoExchangeAPI == null)
                {
                    initializeAPI();
                }

                var pair = $"{BotConfiguration.Currency}_{BotConfiguration.Fiat}";
                var account = string.IsNullOrEmpty(BotConfiguration.Account)?"main":BotConfiguration.Account;
                // for display
                int roundDigitsFiat = 2; // HARDCODED: 2 digits as fiat money are only displayed up to 2 numbers after the decimal part // FTX displays up to 4 digits though
                int roundDigitsCurrency = 8; // HARDCODED: 8 digits as BTC leads the crypto currency markets/exchange by having satoshis as subdivisions (0.00000001)
                int roundDigitsFillingPrice = 0; // HARDCODED: we buy x.xxxxxxxx BTC for X EUR or USD, not x.xx
                int roundDigitsFees = roundDigitsCurrency;

                /*  
                    Starting here, some values may be wrongly infered in case of concurrent or parallel actions made on the account used by this bot
                */
                var initialBalances = await cryptoExchangeAPI.getBalancesAsync();
                decimal initialFiatBalance = initialBalances.Where(x => x.currency == BotConfiguration.Fiat).Sum(x => x.available);
                decimal initialCurrencyBalance = initialBalances.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available);

                // The account should have strictly more money povisionned than what the bot expects to buy 
                // This is to account for any potential price slippage between the moment it calculated the size of the spot position to buy
                // And the actual market buy order executed
                // Otherwise we could fail buying despite thinking we have enough and it should have executed
                if (initialFiatBalance > 2) // BotConfiguration.ChunkSize
                {
                    // Placing a market buy order
                    await cryptoExchangeAPI.buyOrderAsync(BotConfiguration.ChunkSize);

                    Log.LogInformation($"Market buy {BotConfiguration.Currency} for {BotConfiguration.ChunkSize} {BotConfiguration.Fiat}");
                }
                else
                {
                    var sbWarningMessage = new StringBuilder();
                    sbWarningMessage.Append($"Not enough {BotConfiguration.Fiat} on the {account} account").Append("\r\n");
                    sbWarningMessage.Append($"The account balance ({initialFiatBalance} {BotConfiguration.Fiat}) should be strictly superior than the bot configuration chunk size ({BotConfiguration.ChunkSize} {BotConfiguration.Fiat})");
                    await SendMessageAsync(sbWarningMessage.ToString(), MessageTypeEnum.Warning);
                    return;
                }

                var postBuyBalances = await cryptoExchangeAPI.getBalancesAsync();
                decimal postBuyFiatBalance = postBuyBalances.Where(x => x.currency == BotConfiguration.Fiat).Sum(x => x.available);
                decimal postBuyCurrencyBalance = postBuyBalances.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available);

#if DEBUG
                // faking an arbitrary setup to test the formulas below and notifications
                initialFiatBalance = (initialFiatBalance!=0)?initialFiatBalance:(BotConfiguration.ChunkSize*2);
                initialCurrencyBalance = (initialCurrencyBalance!=0)?initialCurrencyBalance:(0.0004m*2);
                postBuyFiatBalance =  initialFiatBalance - BotConfiguration.ChunkSize;
                postBuyCurrencyBalance = initialCurrencyBalance + 0.0004m;
#endif
                decimal totalCostOfOperation = initialFiatBalance - postBuyFiatBalance; // the fee is included in this result as it's already paid in FIAT as we used a market buy order
                decimal takerFee = await cryptoExchangeAPI.getTakerFee(); // ex : 0.00063 representing 0,063%

                // let's infer the fee paid and filling price hit by the market buy order just executed
                decimal currencyCost = totalCostOfOperation/(1+takerFee); // amount of FIAT exchanged against the currency we just bought
                decimal currencyBought = postBuyCurrencyBalance - initialCurrencyBalance;
                decimal fillingPrice = currencyCost/currencyBought; // price in FIAT for 1 full coin of the currency we bought at the time of the filling
                decimal feeCost = totalCostOfOperation * takerFee; // expressed in FIAT value as market buy order always are in FIAT 

                decimal withdrawalFeeCurrencyCost = await cryptoExchangeAPI.getWithdrawalFeeAsync(postBuyCurrencyBalance); // returns 0 ?!? when no param with FTX
                decimal withdrawalPercentageFee = (withdrawalFeeCurrencyCost / postBuyCurrencyBalance); // percent of the overall crypto we'd like to move
                decimal withdrawalFeeFiatCost = withdrawalFeeCurrencyCost * fillingPrice;

                var currentCryptoBalanceValueInFiat = postBuyCurrencyBalance * fillingPrice;

                /*  
                    End of the sensitive part where some values may be wrongly infered in case of concurrent or parallel actions made on the account used by this bot
                */

                sbActions.Append($"<b>Accumulation:</b>").Append("\r\n");
                sbActions.Append($"Just bought {currencyBought.ToString($"N{roundDigitsCurrency}")} {BotConfiguration.Currency} for {currencyCost.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat} @ {fillingPrice.ToString($"N{roundDigitsFillingPrice}")} {BotConfiguration.Fiat}").Append("\r\n");
                sbActions.Append($"Fees are {feeCost.ToString($"N{roundDigitsFees}")} {BotConfiguration.Fiat}").Append("\r\n");

                // Withdraw all the currency accumulated if enabled and the cost is in the configuration bounds
                if (BotConfiguration.WithdrawalEnabled && 
                    !String.IsNullOrEmpty(BotConfiguration.WithdrawalAddress) &&
                    withdrawalPercentageFee <= BotConfiguration.MaxWithdrawalPercentageFee &&
                    (BotConfiguration.MaxWithdrawalAbsoluteFee == -1 || withdrawalFeeFiatCost <= BotConfiguration.MaxWithdrawalAbsoluteFee)
                    )
                {
                    var withdrawResult = await cryptoExchangeAPI.withdrawAsync(postBuyCurrencyBalance, BotConfiguration.WithdrawalAddress);

                    // construct the notification message
                    if(withdrawResult == WithdrawalStateEnum.OK)
                    {
                        sbActions.Append($"<b>Withdrawal:</b>✔️ Success").Append("\r\n");
                        sbActions.Append($"Initiated transfer of {postBuyCurrencyBalance.ToString($"N{roundDigitsCurrency}")} {BotConfiguration.Currency} to the address {BotConfiguration.WithdrawalAddress}").Append("\r\n");
                        sbActions.Append($"Fees are {(withdrawalPercentageFee * 100).ToString($"N{roundDigitsFiat}")} % ({withdrawalFeeFiatCost} {BotConfiguration.Fiat})").Append("\r\n");
                    }else if(withdrawResult == WithdrawalStateEnum.InsufficientKeyPrivileges){
                        sbActions.Append($"<b>Withdrawal:</b>❌ Error [Insufficient key privileges]").Append("\r\n");
                    }
                }
                else
                {
                    // construct the notification message
                    List<string> reason = new List<string>();
                    if (withdrawalPercentageFee > BotConfiguration.MaxWithdrawalPercentageFee)
                        reason.Add("Fee percentage limit exceeded");
                    if (withdrawalFeeFiatCost > BotConfiguration.MaxWithdrawalAbsoluteFee)
                        reason.Add("Fee absolute limit exceeded");
                    if (String.IsNullOrEmpty(BotConfiguration.WithdrawalAddress))
                        reason.Add("No address");
                    if (!BotConfiguration.WithdrawalEnabled)
                        reason.Add("Turned off");

                    var maxWithdrawalFeeInFiat = (BotConfiguration.MaxWithdrawalPercentageFee * currentCryptoBalanceValueInFiat).ToString($"N{roundDigitsFiat}");
                    List<string> limits = new List<string>
                    {
                        $"{(BotConfiguration.MaxWithdrawalPercentageFee * 100).ToString($"N{roundDigitsFiat}")} % ({maxWithdrawalFeeInFiat} {BotConfiguration.Fiat})"
                    };
                    if (BotConfiguration.MaxWithdrawalAbsoluteFee != -1)
                    {
                        limits.Add($"{BotConfiguration.MaxWithdrawalAbsoluteFee.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat}");
                    }
                    
                    sbActions.Append($"<b>Withdrawal:</b>Denied [{String.Join(", ", reason)}]").Append("\r\n");
                    sbActions.Append($"Fees would have been {(withdrawalPercentageFee * 100).ToString($"N{roundDigitsFiat}")} % ({withdrawalFeeFiatCost} {BotConfiguration.Fiat})").Append("\r\n");
                    sbActions.Append($"The limits being {String.Join(" and ", limits)}").Append("\r\n");
                }

                _cosmosDbContext = new CosmosDbContext();
                var accumulationSummary = await _cosmosDbContext.GetAccumulationSummary(pair);

                AccumulationSummary accSumOLD = null;

                //Update a CosmosDB record to the new PartitionKey structure
                if (accumulationSummary.Buys == 0)
                {
                    accSumOLD = await _cosmosDbContext.GetAccumulationSummary(BotConfiguration.Currency);
                    accumulationSummary.AccumulatedCryptoAmount = accSumOLD.AccumulatedCryptoAmount;
                    accumulationSummary.Buys = accSumOLD.Buys;
                    accumulationSummary.InvestedFiatAmount = accSumOLD.InvestedFiatAmount;
                }

                accumulationSummary.Buys += 1;
                accumulationSummary.InvestedFiatAmount += totalCostOfOperation; // includes the fees
                accumulationSummary.AccumulatedCryptoAmount += currencyBought;

                await _cosmosDbContext.UpdateItemAsync(accumulationSummary);

                if(accSumOLD != null)
                {
                    //Deleting an old record from a previous version of the bot
                    await _cosmosDbContext.DeleteItemAsync(accSumOLD.Id.ToString(), accSumOLD.CryptoName);
                }

#if DEBUG
                // Removing any trace of our test/debug
                await _cosmosDbContext.DeleteItemAsync(accumulationSummary.Id.ToString(), accumulationSummary.CryptoName);
#endif

                decimal profitPercent = ((accumulationSummary.AccumulatedCryptoAmount * fillingPrice) / accumulationSummary.InvestedFiatAmount) - 1;
                decimal averageInvestedFiatAmount = accumulationSummary.InvestedFiatAmount/accumulationSummary.Buys;
                decimal averageCryptoAmountBought = accumulationSummary.AccumulatedCryptoAmount/accumulationSummary.Buys;
                decimal averageBuyingPrice = averageInvestedFiatAmount/averageCryptoAmountBought; // also equal to : accumulationSummary.InvestedFiatAmount/accumulationSummary.AccumulatedCryptoAmount

                StringBuilder sb = new StringBuilder();
                sb.Append("\r\n");
                sb.Append("<b>[ACTIONS]</b>").Append("\r\n");
                sb.Append(sbActions.ToString());
                sb.Append("\r\n");
                sb.Append("<b>[SUMMARY]</b>").Append("\r\n");
                sb.Append("<b>Total accumulation</b>: " + accumulationSummary.AccumulatedCryptoAmount.ToString($"N{roundDigitsCurrency}") + " " + BotConfiguration.Currency + " (" + accumulationSummary.InvestedFiatAmount.ToString($"N{roundDigitsFiat}") + $" {BotConfiguration.Fiat})").Append("\r\n");
                sb.Append("<b>Avg Accumulated Price</b>: " + averageBuyingPrice.ToString($"N{roundDigitsFillingPrice}") + $" {BotConfiguration.Fiat}/" + BotConfiguration.Currency).Append("\r\n");
                sb.Append("<b>Current Price</b>: " + fillingPrice.ToString($"N{roundDigitsFillingPrice}") + $" {BotConfiguration.Fiat}/" + BotConfiguration.Currency).Append("\r\n");
                sb.Append("<b>Current Profit</b>: " + (profitPercent * 100).ToString($"N{roundDigitsFiat}") + " % (" + (profitPercent * accumulationSummary.InvestedFiatAmount).ToString($"N{roundDigitsFiat}") + $" {BotConfiguration.Fiat})").Append("\r\n");

                sb.Append("<b>Fiat balance</b>: " + postBuyFiatBalance.ToString($"N{roundDigitsFiat}") + $" {BotConfiguration.Fiat}").Append("\r\n");
                sb.Append($"<b>Current balance</b>: {postBuyCurrencyBalance.ToString($"N{roundDigitsCurrency}")} {BotConfiguration.Currency} ({currentCryptoBalanceValueInFiat.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat})").Append("\r\n");

                sb.Append($"<b>Avg invested amount</b>: {averageInvestedFiatAmount.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat} over {accumulationSummary.Buys} transactions").Append("\r\n");

                await SendMessageAsync(sb.ToString());
            }
            catch (Exception ex)
            {
                await SendMessageAsync(ex.ToString(), MessageTypeEnum.Error);
                return;
            }
        }

        public async Task<string> SendMessageAsync(string message, MessageTypeEnum messageType = MessageTypeEnum.Information, int attempt = 0)
        {
            var username = BotConfiguration.UserName.Replace(' ','_'); // so that spaces don't break the hashtag created in telegram
            var account = string.IsNullOrEmpty(BotConfiguration.Account)?"Main":BotConfiguration.Account + "_Account";

            switch (messageType)
            {
                case MessageTypeEnum.Information:
                    Log.LogInformation(message);
                    message = $"✔️ #AccuBot - #{BotConfiguration.Currency} - #{username} - #{account}{Environment.NewLine}{message}";
                    break;
                case MessageTypeEnum.Warning:
                    Log.LogWarning(message);
                    message = $"⚠️ #AccuBot - #{BotConfiguration.Currency} - #{username} - #{account}{Environment.NewLine}{message}";
                    break;
                case MessageTypeEnum.Error:
                    Log.LogError(message);
                    message = $"❌ #AccuBot - #{BotConfiguration.Currency} - #{username} - #{account}{Environment.NewLine}{message}";
                    break;
            }

            try
            {
                TelegramBot = new TelegramBotClient(BotConfiguration.TelegramBot);
                await TelegramBot.SendTextMessageAsync(BotConfiguration.TelegramChannel, message.Substring(0, Math.Min(4000, message.Length)), Telegram.Bot.Types.Enums.ParseMode.Html); // Why risking to truncate the message to 4000 chars ?
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
