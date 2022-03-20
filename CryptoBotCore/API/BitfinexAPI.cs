
using Bitfinex.Net;
using Bitfinex.Net.Clients;
using Bitfinex.Net.Objects;
using Bittrex.Net;
using Bittrex.Net.Objects;
using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using Microsoft.Extensions.Logging;


namespace CryptoBotCore.API
{
    class BitfinexAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public  BitfinexClient client { get; set; }

        public BitfinexAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();


            this.Log = log;

            var key = credentials[ExchangeCredentialType.Bitfinex_Key];
            var secret = credentials[ExchangeCredentialType.Bitfinex_Key];

            client = new BitfinexClient(new BitfinexClientOptions()
            {
                // Specify options for the client
                ApiCredentials = new ApiCredentials(key, secret)
            });
        }

        private async Task<decimal> getCurrentPrice()
        {
            var callResult = await client.SpotApi.ExchangeData.GetTickerAsync($"{pair_base}{pair_quote}");
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult?.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.BestAskPrice;
            }
        }

        public async Task<string> buyOrderAsync(decimal amount)
        {
            var currentPrice = await getCurrentPrice();
            var baseAmount = (decimal)amount / currentPrice;
            var callResult = await client.SpotApi.Trading.PlaceOrderAsync($"{pair_base}{pair_quote}", 
                                                            Bitfinex.Net.Enums.OrderSide.Buy, 
                                                            Bitfinex.Net.Enums.OrderType.Market,
                                                            currentPrice,
                                                            baseAmount);
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
            var callResult = await client.SpotApi.Account.GetBalancesAsync();
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
                    wallets.Add(new WalletBalances(account.Asset, account.Available??0m));
                }

                return wallets;
            }
        }

        public async Task<decimal> getTakerFee()
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
                var takerFee = callResult.Data.TakerFee;
                return takerFee;
            }
        }

        public async Task<decimal> getWithdrawalFeeAsync(decimal? amount = null, string? destinationAddress = null)
        {
            var callResult = await client.SpotApi.Account.GetWithdrawalFeesAsync();

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {

                // Call succeeded, callResult.Data will have the resulting data
                var withdrawFee = callResult.Data.Withdraw[this.pair_base];
                return withdrawFee;
            }
        }

        public async Task<WithdrawalStateEnum> withdrawAsync(decimal amount, string destinationAddress)
        {
            //maping into withdrawal_type, see https://docs.bitfinex.com/v1/reference#rest-auth-withdrawal
            string withdrawal_type;

            if(this.pair_base == "BTC")
            {
                withdrawal_type = "bitcoin";
            }else if(this.pair_base == "LTC")
            {
                withdrawal_type = "litecoin";
            }else if(this.pair_base == "ETH")
            {
                withdrawal_type = "ethereum";
            }
            else
            {
                withdrawal_type = this.pair_base.ToLower();
            }

            var callResult = await client.SpotApi.Account.WithdrawAsync(withdrawal_type, Bitfinex.Net.Enums.WithdrawWallet.Trading, (decimal)amount, destinationAddress);

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
