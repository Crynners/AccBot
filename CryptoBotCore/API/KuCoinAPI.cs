
using CryptoBotCore.Models;
using Kucoin.Net;
using Kucoin.Net.Objects;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.API
{
    class KuCoinAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public KucoinClient client { get; set; }

        public KuCoinAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();

            this.Log = log;

            var key = credentials[ExchangeCredentialType.KuCoin_Key];
            var secret = credentials[ExchangeCredentialType.KuCoin_Secret];
            var passPhrase = credentials[ExchangeCredentialType.KuCoin_PassPhrase];

            client = new KucoinClient(new KucoinClientOptions()
            {
                // Specify options for the client
                ApiCredentials = new KucoinApiCredentials(key, secret, passPhrase)
            });

        }

        public async Task<string> buyOrderAsync(double amount)
        {
            var callResult = await client.Spot.PlaceOrderAsync($"{pair_base}{pair_quote}", Guid.NewGuid().ToString(), KucoinOrderSide.Buy, KucoinNewOrderType.Market, funds: (decimal)amount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.OrderId;
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.Spot.GetAccountsAsync();
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
            var callResult = await client.Spot.GetSymbolTradingFeesAsync(new List<string> { $"{pair_base}{pair_quote}" });

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {

                // Call succeeded, callResult.Data will have the resulting data
                var takerFee = callResult.Data.Where(x => x.Symbol == $"{pair_base}{pair_quote}").FirstOrDefault().TakerFeeRate;
                return Convert.ToDouble(takerFee);
            }

        }

        public async Task<double> getWithdrawalFeeAsync(double? amount = null, string destinationAddress = null)
        {
            var callResult = await client.Spot.GetWithdrawalQuotasAsync(this.pair_base);

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var withdrawInfo = callResult.Data.WithdrawMinFee;
                return Convert.ToDouble(withdrawInfo);
            }

        }

        public async Task<WithdrawalStateEnum> withdrawAsync(double amount, string destinationAddress)
        {
            var callResult = await client.Spot.WithdrawAsync(this.pair_base, destinationAddress, (decimal)amount);

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
