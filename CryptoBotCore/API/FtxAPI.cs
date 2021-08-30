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

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public FTXClient client { get; set; }

        public FtxAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();
            

            this.Log = log;

            var key = credentials[ExchangeCredentialType.Binance_Key];
            var secret = credentials[ExchangeCredentialType.Binance_Secret];

            client = new FTXClient(new FTXClientOptions()
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
                return callResult.Data.Asks.FirstOrDefault().Price;
            }
        }

        public async Task<string> buyOrderAsync(double amount)
        {
            var baseAmount = (decimal)amount / (await getCurrentPrice());

            var callResult = await client.PlaceOrderAsync($"{pair_base}{pair_quote}", FTX.Net.Enums.OrderSide.Buy, FTX.Net.Enums.OrderType.Market, quantity: baseAmount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
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
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data

                var wallets = new List<WalletBalances>();

                foreach (KeyValuePair<string, IEnumerable<FTXBalance>> entry in callResult.Data)
                {
                    wallets.Add(new WalletBalances(entry.Key, Convert.ToDouble(entry.Value.FirstOrDefault().Free)));
                }

                return wallets;
            }
        }

        public async Task<double> getTakerFee()
        {
            var callResult = await client.GetAccountInfoAsync();

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return Convert.ToDouble(callResult.Data.TakerFee);
            }
        }

        public async Task<double> getWithdrawalFeeAsync(double? amount = null, string destinationAddress = null)
        {
            var callResult = await client.GetWithdrawalFeesAsync(this.pair_base, (decimal)amount, destinationAddress);

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return Convert.ToDouble(callResult.Data.Fee);
            }

        }

        public async Task<WithdrawalStateEnum> withdrawAsync(double amount, string destinationAddress)
        {
            var callResult = await client.WithdrawAsync(this.pair_base, Convert.ToDecimal(amount), destinationAddress);
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
