using Binance.Net;
using Binance.Net.Objects;
using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using FTX.Net;
using FTX.Net.Objects;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.API
{
    class FtxAPI : ICryptoExchangeAPI
    {
        private string account { get; set; }

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }
        
        public FTXClient client { get; set; }

        public FtxAPI(string pair, string account, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();

            this.Log = log;

            var key = credentials[ExchangeCredentialType.FTX_Key];
            var secret = credentials[ExchangeCredentialType.FTX_Secret];
            // the FTX api's result displays the "Main Account" account (as seen in the web UI) as "main"
            this.account = string.IsNullOrEmpty(account)?"main":account; 

            client = new FTXClient(new FTXClientOptions()
            {
                // Specify options for the client
                ApiCredentials = new ApiCredentials(key, secret)
            });
        }

        private async Task<decimal> getCurrentPrice()
        {
            var callResult = await client.GetOrderBookAsync($"{pair_base}/{pair_quote}", 20); 
            // depth of the orderbook arbitrarly set to 20 (default value according to the official FTX api doc)
            // depth can't be set to 0 or we get an error 400 with the answer "Depth cannot be negative"
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var entryBook = callResult.Data.Asks.FirstOrDefault();
                if(entryBook == null)
                    // Fall back to a less precise value
                    entryBook = callResult.Data.Bids.FirstOrDefault();
                if(entryBook == null)
                    throw new Exception($"Cannot define the current price for {pair_base}/{pair_quote} on FTX.");
                return entryBook.Price;
            }
        }

        public async Task<string> buyOrderAsync(decimal amount)
        {
            var baseAmount = amount / (await getCurrentPrice());
            var callResult = await client.PlaceOrderAsync($"{pair_base}/{pair_quote}", FTX.Net.Enums.OrderSide.Buy, FTX.Net.Enums.OrderType.Market, quantity: baseAmount, subaccountName: account);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.Id.ToString();
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.GetAllAccountBalancesAsync();
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data

                var wallets = new List<WalletBalances>();

                foreach (KeyValuePair<string, IEnumerable<FTXBalance>> ftxAccounts in callResult.Data)
                {
                    var ftxAccountName = ftxAccounts.Key;
                    if(ftxAccountName == account)
                    {
                        var ftxAccountBalances = ftxAccounts.Value;
                        foreach (var entry in ftxAccountBalances)
                        {
                            wallets.Add(new WalletBalances(entry.Asset, entry.Free));
                        }
                    }
                }
                return wallets;
            }
        }

        public async Task<decimal> getTakerFee()
        {
            var callResult = await client.GetAccountInfoAsync();

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.TakerFee;
            }
        }

        public async Task<decimal> getWithdrawalFeeAsync(decimal? amount = null, string? destinationAddress = null)
        {
            var callResult = await client.GetWithdrawalFeesAsync(this.pair_base, (amount??0m), destinationAddress??""); // protecting from null values as the underlying lib don't support them

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.Fee;
            }
        }

        public async Task<WithdrawalStateEnum> withdrawAsync(decimal amount, string destinationAddress)
        {
            var callResult = await client.WithdrawAsync(this.pair_base, Convert.ToDecimal(amount), destinationAddress);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return WithdrawalStateEnum.OK;
            }
        }
    }
}
