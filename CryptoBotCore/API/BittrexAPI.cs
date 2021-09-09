
using Bittrex.Net;
using Bittrex.Net.Objects;
using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using Kucoin.Net.Objects;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.API
{
    class BittrexAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public BittrexClient client { get; set; }

        public BittrexAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();

            this.Log = log;

            var key = credentials[ExchangeCredentialType.Bittrex_Key];
            var secret = credentials[ExchangeCredentialType.Bittrex_Secret];

            client = new BittrexClient(new BittrexClientOptions()
            {
                // Specify options for the client
                ApiCredentials = new ApiCredentials(key, secret)
            });

        }

        private async Task<decimal> getCurrentPrice()
        {
            var callResult = await client.GetOrderBookAsync($"{pair_base}{pair_quote}", 0);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.Ask.FirstOrDefault().Price;
            }
        }

        public async Task<string> buyOrderAsync(double amount)
        {
            var baseAmount = (decimal)amount / (await getCurrentPrice());
            var callResult = await client.PlaceOrderAsync($"{pair_base}{pair_quote}", 
                                                            Bittrex.Net.Objects.OrderSide.Buy, 
                                                            Bittrex.Net.Objects.OrderType.Market,
                                                            TimeInForce.FillOrKill,
                                                            baseAmount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.Id;
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.GetBalancesAsync();
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                var wallets = new List<WalletBalances>();

                foreach (var account in callResult.Data)
                {
                    wallets.Add(new WalletBalances(account.Currency, Convert.ToDouble(account.Available)));
                }
                

                return wallets;
            }
        }

        public async Task<double> getTakerFee()
        {
            var callResult = await client.GetTradingFeesAsync();

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {

                // Call succeeded, callResult.Data will have the resulting data
                var takerFee = callResult.Data.Where(x => x.Symbol == $"{pair_base}{pair_quote}").FirstOrDefault().TakerRate;
                return Convert.ToDouble(takerFee);
            }

        }

        public Task<double> getWithdrawalFeeAsync(double? amount = null, string destinationAddress = null)
        {
            // the method to get the withdrawal fee probably doesn't exist, so you'd better return the maximum double value so that the withdrawal is never performed
            return Task.FromResult(double.MaxValue);
        }

        public async Task<WithdrawalStateEnum> withdrawAsync(double amount, string destinationAddress)
        {
            var callResult = await client.WithdrawAsync(this.pair_base, (decimal)amount, destinationAddress);

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return WithdrawalStateEnum.OK;
            }
        }
    }
}
