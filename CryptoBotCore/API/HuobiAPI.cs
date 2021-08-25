using CryptoBotCore.Models;
using CryptoExchange.Net.Authentication;
using Huobi.Net;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.API
{
    public class HuobiAPI : CryptoExchangeAPI
    {
        private HuobiClient client { get; set; }
        public string pair_base { get; set; }
        public string pair_quote { get; set; }

        public ILogger Log { get; set; }

        public HuobiAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[1].ToUpper();
            this.pair_quote = pair.Split('_')[0].ToUpper();

            this.Log = log;

            client = new HuobiClient(new HuobiClientOptions()
            {
                // Specify options for the client
                ApiCredentials = new ApiCredentials(credentials[ExchangeCredentialType.Huobi_Key], credentials[ExchangeCredentialType.Huobi_Secret])
            });
        }

        public async Task<string> buyOrderAsync(double amount)
        {
            
            var convertedAmount = Convert.ToDecimal(amount);

            var accountResult = await client.GetAccountsAsync();
            if (!accountResult.Success)
            {
                // Call failed, check accountResult .Error for more info
                return accountResult.Error?.Message;
            }
            var callResult = await client.PlaceOrderAsync(accountResult.Data.First().Id, $"{this.pair_quote}{this.pair_base}", Huobi.Net.Objects.HuobiOrderType.MarketBuy, convertedAmount);
            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                return callResult.Error?.Message;
            }
            else
            {
                return callResult.Data.ToString();
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var accountResult = await client.GetAccountsAsync();
            if (!accountResult.Success)
            {
                // Call failed, check accountResult .Error for more info
                throw new Exception(accountResult.Error?.Message);
            }


            var callResult = await client.GetBalancesAsync(accountResult.Data.First().Id);

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
            else
            {
                var balances = callResult.Data.Where(x => x.Type == Huobi.Net.Objects.HuobiBalanceType.Trade);

                var wallets = new List<WalletBalances>();

                foreach(var item in balances)
                {
                    wallets.Add(new WalletBalances(item.Currency, Convert.ToDouble(item.Balance)));
                }

                return wallets;
            }
        }

        public double getTakerFee()
        {
            return 1.002;
        }

        public async Task<double> getWithdrawalFeeAsync()
        {
            switch (this.pair_base)
            {
                case "BTC":
                    return 0.0004;
                case "LTC":
                    return 0.001;
                case "ETH":
                    return 0.004;
                default:
                    return -1;
            }
        }

        public async Task withdrawAsync(double amount, string destinationAddress)
        {
            var accountResult = await client.GetAccountsAsync();
            if (!accountResult.Success)
            {
                // Call failed, check accountResult .Error for more info
                throw new Exception(accountResult.Error?.Message);
            }

            var fee = await getWithdrawalFeeAsync();
            var callResult = await client.WithdrawAsync(destinationAddress, this.pair_base.ToLower(), Convert.ToDecimal(amount), Convert.ToDecimal(fee));

            // Make sure to check if the call was successful
            if (!callResult.Success)
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(callResult.Error?.Message);
            }
        }
    }
}
