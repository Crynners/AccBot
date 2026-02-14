using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using HTX.Net.Clients;
using HTX.Net.Objects.Options;
using Microsoft.Extensions.Logging;


namespace CryptoBotCore.API
{
    public class HuobiAPI : ICryptoExchangeAPI
    {
        private HTXRestClient client { get; set; }
        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public ILogger Log { get; set; }

        public HuobiAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();


            this.Log = log;

            client = new HTXRestClient(options =>
            {
                options.ApiCredentials = new ApiCredentials(credentials[ExchangeCredentialType.Huobi_Key], credentials[ExchangeCredentialType.Huobi_Secret]);
            });
        }

        private async Task<decimal> getCurrentPrice()
        {
            var callResult = await client.SpotApi.ExchangeData.GetOrderBookAsync($"{pair_base}{pair_quote}".ToLower(), 0);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return callResult.Data.Asks.First().Price;
            }
        }

        public async Task<string> buyOrderAsync(decimal amount)
        {
            var baseAmount = amount / (await getCurrentPrice());

            var accountResult = await client.SpotApi.Account.GetAccountsAsync();
            if (!accountResult.Success)
            {
                // Call failed, check accountResult .Error for more info
                throw new Exception(accountResult.Error?.Message);
            }

            var callResult = await client.SpotApi.Trading.PlaceOrderAsync(accountResult.Data.First().Id,
                                                                          $"{this.pair_base}{this.pair_quote}".ToLower(),
                                                                          HTX.Net.Enums.OrderSide.Buy,
                                                                          HTX.Net.Enums.OrderType.Market,
                                                                          baseAmount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                return callResult.Data.ToString();
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var accountResult = await client.SpotApi.Account.GetAccountsAsync();
            if (!accountResult.Success)
            {
                // Call failed, check accountResult .Error for more info
                throw new Exception(accountResult.Error?.Message);
            }


            var callResult = await client.SpotApi.Account.GetBalancesAsync(accountResult.Data.First().Id);

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                var balances = callResult.Data.Where(x => x.Type == HTX.Net.Enums.BalanceType.Trade);

                var wallets = new List<WalletBalances>();

                foreach(var item in balances)
                {
                    wallets.Add(new WalletBalances(item.Asset, item.Balance));
                }

                return wallets;
            }
        }

        public Task<decimal> getTakerFee()
        {
            // TODO: find an API call to get this value dynamically as one could have rebates due to referrals or holding/staking HT (HTX Token)
            return Task.FromResult(0.002m); // HARDCODED: value seems up to date on 24/11/2021 as a base default value
        }

        public Task<decimal> getWithdrawalFeeAsync(decimal? amount = null, string? destinationAddress = null)
        {
            switch (this.pair_base)
            {
                case "BTC":
                    return Task.FromResult(0.0004m); // HARDCODED
                case "LTC":
                    return Task.FromResult(0.001m); // HARDCODED
                case "ETH":
                    return Task.FromResult(0.004m); // HARDCODED
                default:
                    return Task.FromResult(Decimal.MaxValue);
            }
        }

        public async Task<WithdrawalStateEnum> withdrawAsync(decimal amount, string destinationAddress)
        {
            var accountResult = await client.SpotApi.Account.GetAccountsAsync();
            if (!accountResult.Success)
            {
                // Call failed, check accountResult .Error for more info
                throw new Exception(accountResult.Error?.Message);
            }

            var fee = await getWithdrawalFeeAsync();
            var callResult = await client.SpotApi.Account.WithdrawAsync(destinationAddress, this.pair_base.ToLower(), Convert.ToDecimal(amount), Convert.ToDecimal(fee));
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {

                if(callResult.Error?.Code == 1003)
                {
                    return WithdrawalStateEnum.InsufficientKeyPrivileges;
                }

                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }

            return WithdrawalStateEnum.OK;
        }
    }
}
