using CryptoBotCore.Models;
using System;
using System.Collections.Generic;
using static CryptoBotCore.API.CoinmateAPI;

namespace CryptoBotCore.API
{
    public interface CryptoExchangeAPI
    {
        /// <summary>
        /// Method which gets best exchange rates for buying and selling BTC
        /// </summary>
        /// <returns></returns>
        Tuple<Double, Double> getActualExchangeRate();

        /// <summary>
        /// Returns pair ATH
        /// </summary>
        /// <returns></returns>
        double getATH();

        /// <summary>
        /// Method represents buying of BTC
        /// </summary>
        /// <param name="amount">Amount of FIAT currency</param>
        /// <param name="exchangeRate">Exchange rate for 1 BTC (in FIAT currency)</param>
        string buy(double amount, double exchangeRate, bool isReal, OrderType orderType);

        /// <summary>
        /// Method represents selling of BTC
        /// </summary>
        /// <param name="amount">Amount of BTC</param>
        /// <param name="sell">Exchange rate for 1 BTC (in FIAT currency)</param>
        string sell(double amount, double exchangeRate, bool isReal, OrderType orderType);

        List<Transaction> getMyTrades();

        List<OpenOrder> getAllOpenOrders(bool isReal);

        bool cancelOrder(string order_id);

        Double getLimitAmount();

        void refresh();

        bool transfer(double amount, string currency, string walletfrom, string walletto);

        List<Offer> getOffers();

        string newOffer(double amount, string currency, double rate, int period);

        void cancelOffer(Offer offer);

        List<Credit> getCredits();

        int getNumberOfAllOpenOrdersOnPair();
    }
}
