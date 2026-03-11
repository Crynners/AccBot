using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using Kucoin.Net.Clients;
using Microsoft.Extensions.Logging;

namespace CryptoBotCore.API
{
    class KuCoinAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public KucoinRestClient client { get; set; }

        public KuCoinAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();

            this.Log = log;

            var key = credentials[ExchangeCredentialType.KuCoin_Key];
            var secret = credentials[ExchangeCredentialType.KuCoin_Secret];
            var passPhrase = credentials[ExchangeCredentialType.KuCoin_PassPhrase];

            client = new KucoinRestClient(options =>
            {
                options.ApiCredentials = new ApiCredentials(key, secret, passPhrase);
            });

        }

        public async Task<string> buyOrderAsync(decimal amount)
        {
            var callResult = await client.SpotApi.Trading.PlaceOrderAsync($"{pair_base}-{pair_quote}", Kucoin.Net.Enums.OrderSide.Buy,
                                                                            Kucoin.Net.Enums.NewOrderType.Market,

                                                                            quoteQuantity: (decimal)amount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.Id;
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.SpotApi.Account.GetAccountsAsync();
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                var wallets = new List<WalletBalances>();

                foreach (var account in callResult.Data)
                {
                    wallets.Add(new WalletBalances(account.Asset, account.Available));
                }

                return wallets;
            }
        }

        public async Task<decimal> getTakerFee()
        {
            var callResult = await client.SpotApi.Account.GetSymbolTradingFeesAsync($"{pair_base}-{pair_quote}");

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var takerFee = callResult.Data.First().TakerFeeRate;
                return takerFee;
            }
        }

        public async Task<decimal> getWithdrawalFeeAsync(decimal? amount = null, string? destinationAddress = null)
        {
            var callResult = await client.SpotApi.Account.GetWithdrawalQuotasAsync(this.pair_base);

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                var withdrawInfo = callResult.Data.WithdrawMinFee;
                return withdrawInfo;
            }
        }

        public async Task<WithdrawalStateEnum> withdrawAsync(decimal amount, string destinationAddress)
        {
            var callResult = await client.SpotApi.Account.WithdrawAsync(
                Kucoin.Net.Enums.WithdrawType.Address,
                this.pair_base,
                destinationAddress,
                amount);

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
