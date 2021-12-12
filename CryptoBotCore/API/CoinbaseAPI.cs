
using Bitfinex.Net;
using Bitfinex.Net.Objects;
using Bittrex.Net;
using Bittrex.Net.Objects;
using Coinbase;
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
    class CoinbaseAPI : ICryptoExchangeAPI
    {

        public ILogger Log { get; set; }

        public string pair_quote { get; set; }
        public string pair_base { get; set; }

        public  CoinbaseClient client { get; set; }

        public CoinbaseAPI(string pair, Dictionary<ExchangeCredentialType, string> credentials, ILogger log)
        {
            this.pair_base = pair.Split('_')[0].ToUpper();
            this.pair_quote = pair.Split('_')[1].ToUpper();

            this.Log = log;

            var key = credentials[ExchangeCredentialType.Coinbase_Key];
            var secret = credentials[ExchangeCredentialType.Coinbase_Secret];

            client = new CoinbaseClient(new ApiKeyConfig { ApiKey = key, ApiSecret = secret });

        }

        public async Task<string> buyOrderAsync(decimal amount)
        {
            var callResult = await client.Accounts.ListAccountsAsync();
            var accountId = callResult.Data.Where(x => x.Currency.Code == this.pair_quote).First().Id;

            var buyResult = await client.Buys.PlaceBuyOrderAsync(accountId, new Coinbase.Models.PlaceBuy() { Total = amount.ToString(), Currency = this.pair_base }); 

            // Make sure to check if the call was successful
            if (buyResult.HasError())
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(String.Join(", ", callResult.Errors.Select(x => x.Message)));
            }
            else
            {
                // Call succeeded, callResult.Data will have the resulting data
                return buyResult.Data.Id;
            }
        }

        public async Task<List<WalletBalances>> getBalancesAsync()
        {
            var callResult = await client.Accounts.ListAccountsAsync();

            // Make sure to check if the call was successful
            if (callResult.HasError())
            {
                // Call failed, check callResult.Error for more info
                throw new Exception(String.Join(", ", callResult.Errors.Select(x => x.Message)));
            }
            else
            {
                var wallets = new List<WalletBalances>();

                foreach (var account in callResult.Data)
                {
                    wallets.Add(new WalletBalances(account.Currency.Code, account.Balance.Amount));
                }
                

                return wallets;
            }
        }

        public Task<decimal> getTakerFee()
        {
            // TODO: Add methods for taker fee
            return Task.FromResult(0.005m); // HARDCODED: value is up to date on 24/11/2021 
        }

        public Task<decimal> getWithdrawalFeeAsync(decimal? amount = null, string? destinationAddress = null)
        {
            // TODO: Add methods for withdrawal fees
            return Task.FromResult(decimal.MaxValue);
        }

        public Task<WithdrawalStateEnum> withdrawAsync(decimal amount, string destinationAddress)
        {
            // TODO: Implement withdraws
            return Task.FromResult(WithdrawalStateEnum.OK);
        }
    }
}
