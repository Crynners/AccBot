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
        private ICryptoExchangeAPI? cryptoExchangeAPI = null;
        readonly ILogger Log;

        private string pair = $"{BotConfiguration.Currency}_{BotConfiguration.Fiat}";
        private string account = string.IsNullOrEmpty(BotConfiguration.Account)?"main":BotConfiguration.Account;

        // for display
        private int roundDigitsFiat = 2; // HARDCODED: 2 digits as fiat money are only displayed up to 2 numbers after the decimal part // FTX displays up to 4 digits though
        private int roundDigitsCurrency = 8; // HARDCODED: 8 digits as BTC leads the crypto currency markets/exchange by having satoshis as subdivisions (0.00000001)
        private int roundDigitsFillingPrice = 0; // HARDCODED: we buy x.xxxxxxxx BTC for X EUR or USD, not x.xx
        private int roundDigitsFees = 8; // HARDCODED: set to match roundDigitsCurrency

        public AccumulationBot(ILogger log)
        {
            Log = log;
        }
        
        private void InitializeAPI()
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
                Log.LogInformation("Start Tick: " + DateTime.Now);

                if (cryptoExchangeAPI == null){
                    InitializeAPI();
                }

                /*  
                    Starting here, some values may be wrongly infered in case of concurrent or parallel actions made on the account used by this bot
                */
        #if DEBUG 
                // as we want to make sure to be able to DEBUG even though there too few FIAT on the account,
                // we prefer to fake a rich enough account
                // fixing values will also help debugging/testing/developping future stats calculus
                var initialFiatBalance = BotConfiguration.ChunkSize*2;
                var initialCurrencyBalance = 0.0004m*2;
        #else
                var initialBalances = await cryptoExchangeAPI.getBalancesAsync();
                decimal initialFiatBalance = initialBalances.Where(x => x.currency == BotConfiguration.Fiat).Sum(x => x.available);
                decimal initialCurrencyBalance = initialBalances.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available);
        #endif
                var isSuccess = await AttemptBuy(initialFiatBalance); // Doesn't actually buy if running in DEBUG
                if(! isSuccess){
                    await SendWarningMessage(initialFiatBalance);
                    return;
                }

        #if DEBUG
                // as we didn't actually buy in the AttemptBuy function due to being in DEBUG mode,
                // we have to fake some data to be able to properly test the formulas and notifications below 
                var postBuyFiatBalance =  initialFiatBalance - BotConfiguration.ChunkSize;
                var postBuyCurrencyBalance = initialCurrencyBalance + 0.0004m;
        #else
                var postBuyBalances = await cryptoExchangeAPI.getBalancesAsync();
                decimal postBuyFiatBalance = postBuyBalances.Where(x => x.currency == BotConfiguration.Fiat).Sum(x => x.available);
                decimal postBuyCurrencyBalance = postBuyBalances.Where(x => x.currency == BotConfiguration.Currency).Sum(x => x.available);
        #endif

        #region Stats calculus
                decimal totalCostOfOperation = initialFiatBalance - postBuyFiatBalance; // the fee is included in this result as it's already paid in FIAT as we used a market buy order
                decimal takerFee = await cryptoExchangeAPI.getTakerFee(); // ex : 0.00063 representing 0,063%

                // let's infer the fee paid and filling price hit by the market buy order just executed
                decimal currencyCost = totalCostOfOperation/(1+takerFee); // amount of FIAT exchanged against the currency we just bought
                decimal currencyBought = postBuyCurrencyBalance - initialCurrencyBalance;
                decimal fillingPrice = currencyCost/currencyBought; // price in FIAT for 1 full coin of the currency we bought at the time of the filling
                decimal feeCost = totalCostOfOperation * takerFee; // expressed in FIAT value as market buy order always are in FIAT 

                var currentCryptoBalanceValueInFiat = postBuyCurrencyBalance * fillingPrice;
        #endregion

                /*  
                    End of the sensitive part where some values may be wrongly infered in case of concurrent or parallel actions made on the account used by this bot
                */

                var withdrawalMessageBlock = await AttemptWithdrawal(postBuyCurrencyBalance, fillingPrice, currentCryptoBalanceValueInFiat);

                var accumulationSummary = await UpdateSummary(totalCostOfOperation, currencyBought);

                await SendSuccessMessage(withdrawalMessageBlock, accumulationSummary, currencyBought, 
                                        currencyCost, fillingPrice, feeCost, postBuyFiatBalance, 
                                        postBuyCurrencyBalance, currentCryptoBalanceValueInFiat);
            }
            catch (Exception ex)
            {
                await SendMessageAsync(ex.ToString(), MessageTypeEnum.Error);
            }
        }

        private async Task<bool> AttemptBuy(decimal initialFiatBalance){
            // The account should have strictly more money povisionned than what the bot expects to buy 
            // This is to account for any potential price slippage between the moment it calculated the size of the spot position to buy
            // And the actual market buy order executed
            // Otherwise we could fail buying despite thinking we have enough and it should have executed
            if (initialFiatBalance <= BotConfiguration.ChunkSize){
                return false;
            }
            
        #if !DEBUG
            // Placing a market buy order
            await cryptoExchangeAPI.buyOrderAsync(BotConfiguration.ChunkSize);     
        #endif
            // We don't want to buy for real if we're in a test/debug scenario
            
            Log.LogInformation($"Market buy {BotConfiguration.Currency} for {BotConfiguration.ChunkSize} {BotConfiguration.Fiat}");
            return true;
        }

        private async Task<string> AttemptWithdrawal(decimal postBuyCurrencyBalance, decimal fillingPrice, decimal currentCryptoBalanceValueInFiat)
        {
            var sbActions = new StringBuilder();

            decimal withdrawalFeeCurrencyCost = await cryptoExchangeAPI.getWithdrawalFeeAsync(postBuyCurrencyBalance); // returns 0 ?!? when no param with FTX
            decimal withdrawalPercentageFee = (withdrawalFeeCurrencyCost / postBuyCurrencyBalance); // percent of the overall crypto we'd like to move
            decimal withdrawalFeeFiatCost = withdrawalFeeCurrencyCost * fillingPrice;

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
                
                sbActions.Append($"<b>Withdrawal:</b> Denied [{String.Join(", ", reason)}]").Append("\r\n");
                sbActions.Append($"Fees would have been {(withdrawalPercentageFee * 100).ToString($"N{roundDigitsFiat}")} % ({withdrawalFeeFiatCost} {BotConfiguration.Fiat})").Append("\r\n");
                sbActions.Append($"The limits being {String.Join(" and ", limits)}").Append("\r\n");
            }

            return sbActions.ToString();
        }

        #region Data persistence
        private async Task<AccumulationSummary> UpdateSummary(decimal totalCostOfOperation, decimal currencyBought)
        {
            var _cosmosDbContext = new CosmosDbContext();

            var accumulationSummary = await _cosmosDbContext.GetAccumulationSummary(pair);

            accumulationSummary = await MigrateLegacyDatabaseRecord(_cosmosDbContext, accumulationSummary);
            accumulationSummary.Increment(totalCostOfOperation,currencyBought);

        #if !DEBUG
            await _cosmosDbContext.UpdateItemAsync(accumulationSummary);
        #endif

            return accumulationSummary;
        }

        private async Task<AccumulationSummary> MigrateLegacyDatabaseRecord(CosmosDbContext _cosmosDbContext, AccumulationSummary accumulationSummary){
            //Update a CosmosDB record to the new PartitionKey structure
            AccumulationSummary? accSumOLD = null;
            // if we didn't get a result, that might mean that there are "old format" data to migrate
            if (accumulationSummary.Buys == 0) 
            {
                // so, let's query the "old" way
                accSumOLD = await _cosmosDbContext.GetAccumulationSummary(BotConfiguration.Currency); 
            }

            if(accSumOLD != null)
            {
                // we found old data to migrate, let's pass it to the rest of the code as if it was found from the initial "recent" query format
                accumulationSummary.AccumulatedCryptoAmount = accSumOLD.AccumulatedCryptoAmount;
                accumulationSummary.Buys = accSumOLD.Buys;
                accumulationSummary.InvestedFiatAmount = accSumOLD.InvestedFiatAmount;
                
            #if !DEBUG
                // now we can delete the old record as we don't want data saved in this "old" way anymore
                await _cosmosDbContext.DeleteItemAsync(accSumOLD.Id.ToString(), accSumOLD.CryptoName);
            #endif
            }

            // this returned value has to be saved later or we will lose forever the existing data
            return accumulationSummary;
        }
        #endregion

        #region Messaging

        #region Generate display message
        private string GenerateActionsMessageBlock(decimal currencyBought, decimal currencyCost, decimal fillingPrice, decimal feeCost){
            var sbAccumulationStats = new StringBuilder();

            sbAccumulationStats.Append($"<b>Accumulation:</b>").Append("\r\n");
            sbAccumulationStats.Append($"Just bought {currencyBought.ToString($"N{roundDigitsCurrency}")} {BotConfiguration.Currency} for {currencyCost.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat} @ {fillingPrice.ToString($"N{roundDigitsFillingPrice}")} {BotConfiguration.Fiat}").Append("\r\n");
            sbAccumulationStats.Append($"Fees are {feeCost.ToString($"N{roundDigitsFees}")} {BotConfiguration.Fiat}").Append("\r\n");

            return sbAccumulationStats.ToString();
        }

        private string GenerateSummaryMessageBlock(AccumulationSummary accumulationSummary, decimal fillingPrice, decimal postBuyFiatBalance, decimal postBuyCurrencyBalance, decimal currentCryptoBalanceValueInFiat){
            var sb = new StringBuilder();

            decimal profitPercent = ((accumulationSummary.AccumulatedCryptoAmount * fillingPrice) / accumulationSummary.InvestedFiatAmount) - 1;
            decimal averageInvestedFiatAmount = accumulationSummary.InvestedFiatAmount/accumulationSummary.Buys;
            decimal averageCryptoAmountBought = accumulationSummary.AccumulatedCryptoAmount/accumulationSummary.Buys;
            decimal averageBuyingPrice = averageInvestedFiatAmount/averageCryptoAmountBought; // also equal to : accumulationSummary.InvestedFiatAmount/accumulationSummary.AccumulatedCryptoAmount

            sb.Append("<b>Total accumulation</b>: " + accumulationSummary.AccumulatedCryptoAmount.ToString($"N{roundDigitsCurrency}") + " " + BotConfiguration.Currency + " (" + accumulationSummary.InvestedFiatAmount.ToString($"N{roundDigitsFiat}") + $" {BotConfiguration.Fiat})").Append("\r\n");
            sb.Append("<b>Avg Accumulated Price</b>: " + averageBuyingPrice.ToString($"N{roundDigitsFillingPrice}") + $" {BotConfiguration.Fiat}/" + BotConfiguration.Currency).Append("\r\n");
            sb.Append("<b>Current Price</b>: " + fillingPrice.ToString($"N{roundDigitsFillingPrice}") + $" {BotConfiguration.Fiat}/" + BotConfiguration.Currency).Append("\r\n");
            sb.Append("<b>Current Profit</b>: " + (profitPercent * 100).ToString($"N{roundDigitsFiat}") + " % (" + (profitPercent * accumulationSummary.InvestedFiatAmount).ToString($"N{roundDigitsFiat}") + $" {BotConfiguration.Fiat})").Append("\r\n");

            sb.Append("<b>Fiat balance</b>: " + postBuyFiatBalance.ToString($"N{roundDigitsFiat}") + $" {BotConfiguration.Fiat}").Append("\r\n");
            sb.Append($"<b>Current balance</b>: {postBuyCurrencyBalance.ToString($"N{roundDigitsCurrency}")} {BotConfiguration.Currency} ({currentCryptoBalanceValueInFiat.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat})").Append("\r\n");

            sb.Append($"<b>Avg invested amount</b>: {averageInvestedFiatAmount.ToString($"N{roundDigitsFiat}")} {BotConfiguration.Fiat} over {accumulationSummary.Buys} transactions").Append("\r\n");

            return sb.ToString();
        }
        #endregion

        private async Task SendSuccessMessage(string withdrawalMessageBlock, AccumulationSummary accumulationSummary, decimal currencyBought, decimal currencyCost, decimal fillingPrice, decimal feeCost, decimal postBuyFiatBalance, decimal postBuyCurrencyBalance, decimal currentCryptoBalanceValueInFiat){
            var accumulationStatsMessageBlock = GenerateActionsMessageBlock(currencyBought, currencyCost, fillingPrice, feeCost);
            var summaryMessageBlock = GenerateSummaryMessageBlock(accumulationSummary, fillingPrice, postBuyFiatBalance, postBuyCurrencyBalance, currentCryptoBalanceValueInFiat);

            var sb = new StringBuilder();
            sb.Append("\r\n");
            sb.Append("<b>[ACTIONS]</b>").Append("\r\n");
            sb.Append(accumulationStatsMessageBlock);
            sb.Append(withdrawalMessageBlock);
            sb.Append("\r\n");
            sb.Append("<b>[SUMMARY]</b>").Append("\r\n");
            sb.Append(summaryMessageBlock);

            await SendMessageAsync(sb.ToString());
        }

        private async Task SendWarningMessage(decimal initialFiatBalance){
            var sbWarningMessage = new StringBuilder();
            sbWarningMessage.Append($"Not enough {BotConfiguration.Fiat} on the {account} account").Append("\r\n");
            sbWarningMessage.Append($"The account balance ({initialFiatBalance} {BotConfiguration.Fiat}) should be strictly superior than the bot configuration chunk size ({BotConfiguration.ChunkSize} {BotConfiguration.Fiat})");

            await SendMessageAsync(sbWarningMessage.ToString(), MessageTypeEnum.Warning);
        }

        private async Task SendMessageAsync(string message, MessageTypeEnum messageType = MessageTypeEnum.Information, int attempt = 0)
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
                var TelegramBot = new TelegramBotClient(BotConfiguration.TelegramBot);
                await TelegramBot.SendTextMessageAsync(BotConfiguration.TelegramChannel, message.Substring(0, Math.Min(4000, message.Length)), Telegram.Bot.Types.Enums.ParseMode.Html); // Why risking to truncate the message to 4000 chars ?
            }
            catch (Exception e)
            {
                if (attempt >= 2)
                {
                    Log.LogError(e, "SendTextMessageAsync error");
                }
                //Repeat if exception (i.e. too many requests) occured
                Thread.Sleep(300);
                await SendMessageAsync(message, MessageTypeEnum.Error , ++attempt);
            }
        }
        #endregion
    }
}
