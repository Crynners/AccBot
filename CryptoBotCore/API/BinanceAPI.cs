using Binance.Net.Clients;
using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using Microsoft.Extensions.Logging;

namespace CryptoBotCore.API
{
    class BinanceAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public BinanceRestClient client { get; set; }

        public BinanceAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();


            this.Log = log;

            var key = credentials[ExchangeCredentialType.Binance_Key];
            var secret = credentials[ExchangeCredentialType.Binance_Secret];

            client = new BinanceRestClient(options =>
            {
                options.ApiCredentials = new ApiCredentials(key, secret);
            });
        }

        public async Task<string> buyOrderAsync(decimal amount)
        {
            var callResult = await client.SpotApi.Trading.PlaceOrderAsync($"{pair_base}{pair_quote}",
                                                                        Binance.Net.Enums.OrderSide.Buy,
                                                                        Binance.Net.Enums.SpotOrderType.Market,
                                                                        quoteQuantity: (decimal?)amount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.ClientOrderId;
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.SpotApi.Account.GetAccountInfoAsync();
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var balances = callResult.Data.Balances;

                var wallets = new List<WalletBalances>();

                foreach (var item in balances)
                {
                    wallets.Add(new WalletBalances(item.Asset, item.Available));
                }

                return wallets;
            }
        }

        public async Task<decimal> getTakerFee()
        {
            var callResult = await client.SpotApi.Account.GetTradeFeeAsync($"{pair_base}{pair_quote}");

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var item = callResult.Data.Where(x => x.Symbol == $"{pair_base}{pair_quote}").First();
                return item.TakerFee;
            }
        }

        public async Task<decimal> getWithdrawalFeeAsync(decimal? amount = null, string? destinationAddress = null)
        {
            var callResult = await client.SpotApi.ExchangeData.GetAssetDetailsAsync();

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var withdrawInfo = callResult.Data[this.pair_base];
                return withdrawInfo.WithdrawFee;
            }
        }

        public async Task<WithdrawalStateEnum> withdrawAsync(decimal amount, string destinationAddress)
        {
            var callResult = await client.SpotApi.Account.WithdrawAsync(this.pair_base, destinationAddress, Convert.ToDecimal(amount));
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
