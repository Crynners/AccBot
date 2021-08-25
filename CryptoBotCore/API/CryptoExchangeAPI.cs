
using CryptoBotCore.Models;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using static CryptoBotCore.API.CoinmateAPI;

namespace CryptoBotCore.API
{
    public interface CryptoExchangeAPI
    {
        /// <summary>
        /// Method which returns withdrawal fee for the acumulating cryptocurrency
        /// </summary>
        /// <returns>Absolute value of fee in the crypto currency</returns>
        public Task<double> getWithdrawalFeeAsync();

        /// <summary>
        /// Method which returns taker fee
        /// </summary>
        /// <returns>Taker fee in percent</returns>
        public double getTakerFee();

        Task<List<WalletBalances>> getBalancesAsync();

        Task withdrawAsync(double amount, string destinationAddress);

        /// <summary>
        /// Method represents buying of cryptocurrency
        /// </summary>
        /// <param name="amount">Amount of FIAT currency</param>
        public Task<string> buyOrderAsync(double amount);
    }
}
