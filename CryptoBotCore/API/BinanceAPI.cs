using Binance.Net;
using Binance.Net.Objects;
using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.API
{
    class BinanceAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public BinanceClient client { get; set; }

        public BinanceAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();
            

            this.Log = log;

            var key = credentials[ExchangeCredentialType.Binance_Key];
            var secret = credentials[ExchangeCredentialType.Binance_Secret];

            client = new BinanceClient(new BinanceClientOptions()
            {
                // Specify options for the client
                ApiCredentials = new ApiCredentials(key, secret)
            });

        }

        public async Task<string> buyOrderAsync(double amount)
        {
            var callResult = await client.Spot.Order.PlaceOrderAsync($"{pair_base}{pair_quote}", Binance.Net.Enums.OrderSide.Buy, Binance.Net.Enums.OrderType.Market, quoteOrderQuantity: (decimal?)amount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.ClientOrderId;
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.General.GetAccountInfoAsync();
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var balances = callResult.Data.Balances;

                var wallets = new List<WalletBalances>();

                foreach (var item in balances)
                {
                    wallets.Add(new WalletBalances(item.Asset, Convert.ToDouble(item.Free)));
                }

                return wallets;
            }
        }

        public async Task<double> getTakerFee()
        {
            var callResult = await client.Spot.Market.GetTradeFeeAsync($"{pair_base}{pair_quote}");

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var item = callResult.Data.Where(x => x.Symbol == $"{pair_base}{pair_quote}").FirstOrDefault();
                return Convert.ToDouble(item.TakerFee);
            }
        }

        public async Task<double> getWithdrawalFeeAsync(double? amount = null, string destinationAddress = null)
        {
            var callResult = await client.WithdrawDeposit.GetAssetDetailsAsync();

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var withdrawInfo = callResult.Data[this.pair_base];
                return Convert.ToDouble(withdrawInfo.WithdrawFee);
            }

        }

        public async Task<WithdrawalStateEnum> withdrawAsync(double amount, string destinationAddress)
        {
            var callResult = await client.WithdrawDeposit.WithdrawAsync(this.pair_base, destinationAddress, Convert.ToDecimal(amount));
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
