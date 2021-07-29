using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Text.Json;
using CryptoBotCore.Models;
using Newtonsoft.Json;
using Microsoft.Extensions.Logging;

namespace CryptoBotCore.API
{
    [Serializable]
    public class CoinmateAPI : CryptoExchangeAPI
    {
        private int clientId { get; set; }
        private string publicKey { get; set; }
        private string privateKey { get; set; }

        //private static long nonce { get; set; }
        static object nonceLock = new object();
        public ILogger Log { get; set; }
        private string pair { get; set; }
        public string pair_base { get; set; }
        public string pair_quote { get; set; }

        static Tuple<DateTime, Dictionary<string, double>> LimitAmountTuple = new Tuple<DateTime, Dictionary<string, double>>(new DateTime(1900, 1, 1), null);

        public CoinmateAPI(string pair, CoinMateCredentials credentials, ILogger log)
        {
            this.pair = pair;
            this.pair_base = pair.Split('_')[1];
            this.pair_quote = pair.Split('_')[0];

            this.Log = log;

            clientId = credentials.ClientId;
            publicKey = credentials.PublicKey;
            privateKey = credentials.PrivateKey;

            //nonce = (long)Utility.getUnixTimestamps(DateTime.UtcNow);

        }

        private string getSignature(Double time)
        {
            Encoding ascii = Encoding.ASCII;
            string message = time.ToString() + clientId.ToString() + publicKey;
            HMACSHA256 hmac = new HMACSHA256(ascii.GetBytes(privateKey));
            byte[] data = hmac.ComputeHash(ascii.GetBytes(message));
            return BitConverter.ToString(data).Replace("-", string.Empty);
        }

        [Serializable]
        class Nonce
        {
            public long Number { get; set; }

            public Nonce()
            {
                Number = (long)Utility.getUnixTimestamps(DateTime.UtcNow);
            }
        }

        public static long getNonce()
        {
            lock (nonceLock)
            {
                Nonce nonce = new Nonce();
                return nonce.Number;
            }
        }

        public string getSecuredHeaderPart()
        {
            Double nonce = getNonce();
            return "clientId=" + clientId + "&publicKey=" + publicKey + "&nonce=" + nonce + "&signature=" + getSignature(nonce);
        }

        // Dle CoinMate Supportu toto není validní a funguje to jen v případě, kdy máme jeden klíč
        //public string getSecuredHeaderPartWithoutPublicKey()
        //{
        //    long nonce = getNonce();
        //    return "clientId=" + clientId + "&nonce=" + nonce + "&signature=" + getSignature(nonce);
        //}


        public string buy(double amount, double exchangeRate, bool isReal, OrderType orderType = OrderType.ExchangeLimit)
        {
            exchangeRate = Math.Round(exchangeRate, 1);

            int wait = 0;
            do
            {
                try
                {
                    if (isReal)
                    {
                        if (orderType == OrderType.ExchangeMarket)
                        {
                            WebClient client = new WebClient();
                            client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");

                            //amount = Math.Floor(amount);

                            string body = "total=" + amount + "&currencyPair=" + pair + "&" + getSecuredHeaderPart();
                            string response = client.UploadString("https://coinmate.io/api/buyInstant", body);
                            Response<string> result = JsonConvert.DeserializeObject<Response<string>>(response);

                            if (result.error)
                            {
                                Log.LogError(result.errorMessage.ToString());
                                throw new Exception(result.errorMessage.ToString());
                            }

                            return result.data;
                        }
                        else
                        {
                            WebClient client = new WebClient();
                            client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");

                            string body = "amount=" + amount + "&currencyPair=" + pair + "&price=" + exchangeRate + "&" + getSecuredHeaderPart();
                            string response = client.UploadString("https://coinmate.io/api/buyLimit", body);
                            Response<string> result = JsonConvert.DeserializeObject<Response<string>>(response);

                            if (result.error)
                            {
                                Log.LogError(result.errorMessage.ToString());
                                throw new Exception(result.errorMessage.ToString());
                            }

                            return result.data;
                        }
                    }
                    else
                    {
                        return Utility.getUnixTimestamps(DateTime.UtcNow).ToString();
                    }
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));
                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public Tuple<double, double> getActualExchangeRate()
        {
            return getActualExchangeRate(this.pair);
        }

        public Tuple<double, double> getActualExchangeRate(string pair)
        {
            int wait = 0;
            do
            {
                try
                {
                    WebClient client = new WebClient();
                    string response = client.DownloadString("https://coinmate.io/api/ticker?currencyPair=" + pair);
                    Response<ActualExchangeRates> result = JsonConvert.DeserializeObject<Response<ActualExchangeRates>>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return new Tuple<Double, Double>(result.data.ask, result.data.bid);
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public double getBTCWidthrawalFee()
        {
            int wait = 0;
            do
            {
                try
                {
                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                    string body = getSecuredHeaderPart();
                    string response = client.UploadString("https://coinmate.io/api/bitcoinWithdrawalFees", body);
                    BTCWidthrawalFee_RootObject result = JsonConvert.DeserializeObject<BTCWidthrawalFee_RootObject>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return result.data.low;
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public class BTCWidthrawalFee_Data
        {
            public double low { get; set; }
            public double high { get; set; }
            public long timestamp { get; set; }
        }

        public class BTCWidthrawalFee_RootObject
        {
            public bool error { get; set; }
            public object errorMessage { get; set; }
            public BTCWidthrawalFee_Data data { get; set; }
        }

        public string sell(double amount, double exchangeRate, bool isReal, OrderType orderType = OrderType.ExchangeLimit)
        {
            exchangeRate = Math.Round(exchangeRate, 1);

            int wait = 0;
            do
            {
                try
                {
                    if (isReal)
                    {
                        WebClient client = new WebClient();
                        client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                        string body = "amount=" + amount + "&currencyPair=" + this.pair + "&price=" + exchangeRate + "&" + getSecuredHeaderPart();
                        string response = client.UploadString("https://coinmate.io/api/sellLimit", body);
                        Response<string> result = JsonConvert.DeserializeObject<Response<string>>(response);

                        if (result.error)
                        {
                            throw new Exception(result.errorMessage.ToString());
                        }

                        return result.data;
                    }
                    else
                    {
                        return Utility.getUnixTimestamps(DateTime.UtcNow).ToString();
                    }
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }


        internal void withdrawBTC(double BTC, string destinationAddress)
        {
            int wait = 0;
            do
            {
                try
                {
                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");

                    string body = "amount=" + BTC + "&address=" + destinationAddress + "&" + getSecuredHeaderPart();
                    string response = client.UploadString("https://coinmate.io/api/bitcoinWithdrawal", body);
                    Response<string> result = JsonConvert.DeserializeObject<Response<string>>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return;
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        internal void withdraw(double amount, string destinationAddress)
        {
            int wait = 0;
            do
            {
                try
                {
                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");

                    string keypair = pair == "BTC_CZK" ? "bitcoinWithdrawal" :
                                     pair == "LTC_CZK" ? "litecoinWithdrawal" :
                                     pair == "ETH_CZK" ? "ethereumWithdrawal" :
                                     pair == "DASH_CZK" ? "dashWithdrawal" :
                                     null;

                    string fee_priority = pair == "BTC_CZK" ? "&feePriority=LOW" : "";

                    string body = "amount=" + amount + "&address=" + destinationAddress + fee_priority + "&" + getSecuredHeaderPart();
                    string response = client.UploadString("https://coinmate.io/api/" + keypair, body);
                    Response<string> result = JsonConvert.DeserializeObject<Response<string>>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return;
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public List<OpenOrder> getAllOpenOrders(bool isReal)
        {
            int wait = 0;
            do
            {
                try
                {
                    if (isReal)
                    {
                        WebClient client = new WebClient();
                        client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                        string body = "currencyPair=" + this.pair + "&" + getSecuredHeaderPart();
                        string response = client.UploadString("https://coinmate.io/api/openOrders", body);
                        Response<List<OpenOrder>> result = JsonConvert.DeserializeObject<Response<List<OpenOrder>>>(response);

                        if (result.error)
                        {
                            throw new Exception(result.errorMessage.ToString());
                        }

                        return result.data;
                    }
                    else
                    {
                        //TODO - tady musim projit buying packages a podivat, jestli uz nejsou po expiraci nejake nabidky 
                        return null;
                    }
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public void getLimits()
        {
            if ((DateTime.Now - LimitAmountTuple.Item1).TotalMinutes > 15)
            {
                DateTime now = DateTime.Now;
                LimitAmountTuple = new Tuple<DateTime, Dictionary<string, double>>(now, getActualLimits());
            }
        }

        public class CoinMatePairConfiguration
        {
            public string name { get; set; }
            public string firstCurrency { get; set; }
            public string secondCurrency { get; set; }
            public int priceDecimals { get; set; }
            public int lotDecimals { get; set; }
            public double minAmount { get; set; }
            public string tradesWebSocketChannelId { get; set; }
            public string orderBookWebSocketChannelId { get; set; }
            public string tradeStatisticsWebSocketChannelId { get; set; }
        }

        private Dictionary<string, double> getActualLimits()
        {
            int wait = 0;
            do
            {
                try
                {
                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                    string response = client.DownloadString("https://coinmate.io/api/tradingPairs");
                    Response<List<CoinMatePairConfiguration>> result = JsonConvert.DeserializeObject<Response<List<CoinMatePairConfiguration>>>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return result.data.ToDictionary(x => x.name, x => x.minAmount);
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        /// <summary>
        /// Cancel order. Method returns remainingAmount of the order
        /// </summary>
        public Boolean cancelOrder(string orderId)
        {
            int wait = 0;
            do
            {
                try
                {

                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                    string body = "orderId=" + orderId + "&" + getSecuredHeaderPart();
                    string response = client.UploadString("https://coinmate.io/api/cancelOrder", body);
                    Response<Boolean> result = JsonConvert.DeserializeObject<Response<Boolean>>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return result.data;
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public List<Transaction> getMyTrades()
        {
            int wait = 0;
            do
            {
                try
                {

                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                    string body = "offset=0&limit=1000&sort=DESC&" + getSecuredHeaderPart();
                    string response = client.UploadString("https://coinmate.io/api/transactionHistory", body);
                    Response<List<Transaction>> result = JsonConvert.DeserializeObject<Response<List<Transaction>>>(response);

                    if (result.error)
                    {
                        throw new Exception(result.errorMessage.ToString());
                    }

                    return result.data;
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public List<WalletBalances> getBalances()
        {
            int wait = 0;
            do
            {
                try
                {
                    WebClient client = new WebClient();
                    client.Headers.Add(HttpRequestHeader.ContentType, "application/x-www-form-urlencoded");
                    string body = getSecuredHeaderPart();
                    string response = client.UploadString("https://coinmate.io/api/balances", body);
                    Response<BalanceData> result = JsonConvert.DeserializeObject<Response<BalanceData>>(response);

                    if (result.error)
                    {
                        Log.LogError(result.errorMessage.ToString());
                        throw new Exception(result.errorMessage.ToString());
                    }

                    List<WalletBalances> wallets = new List<WalletBalances>();

                    wallets.Add(new WalletBalances("CZK", result.data.CZK.balance, result.data.CZK.available, "exchange"));
                    wallets.Add(new WalletBalances("EUR", result.data.EUR.balance, result.data.EUR.available, "exchange"));
                    wallets.Add(new WalletBalances("BTC", result.data.BTC.balance, result.data.BTC.available, "exchange"));
                    wallets.Add(new WalletBalances("LTC", result.data.LTC.balance, result.data.LTC.available, "exchange"));
                    wallets.Add(new WalletBalances("ETH", result.data.ETH.balance, result.data.ETH.available, "exchange"));
                    wallets.Add(new WalletBalances("DSH", result.data.DASH.balance, result.data.DASH.available, "exchange"));

                    return wallets;
                }
                catch (Exception ex)
                {
                    Log.LogError(JsonConvert.SerializeObject(ex));

                    wait = (wait == 0) ? 200 : wait * 2;
                    Thread.Sleep(wait);
                }
            } while (true);
        }

        public double getLimitAmount()
        {
            getLimits();

            if (!LimitAmountTuple.Item2.ContainsKey(this.pair))
            {
                throw new NotImplementedException("Neexistujici par definovany pro limit");
            }

            return LimitAmountTuple.Item2[this.pair];
        }

        public void refresh()
        {
            getLimits();
        }

        public bool transfer(double amount, string currency, string walletfrom, string walletto)
        {
            throw new NotImplementedException();
        }

        //not in down
        public double getATH()
        {
            throw new NotImplementedException();
        }

        //not in down
        public List<Offer> getOffers()
        {
            throw new NotImplementedException();
        }

        //not in down
        public string newOffer(double amount, string currency, double rate, int period)
        {
            throw new NotImplementedException();
        }

        //not in down
        public void cancelOffer(Offer offer)
        {
            throw new NotImplementedException();
        }

        //not in down
        public List<Credit> getCredits()
        {
            throw new NotImplementedException();
        }


        public int getNumberOfAllOpenOrdersOnPair()
        {
            return getAllOpenOrders(true).Count();
        }


        public class Response<T>
        {
            public bool error { get; set; }
            public object errorMessage { get; set; }
            public T data { get; set; }
        }

        public class Orders
        {
            /// <summary>
            /// Buys
            /// </summary>
            public List<Order> asks { get; set; }
            /// <summary>
            /// Sells
            /// </summary>
            public List<Order> bids { get; set; }
            public int timestamp { get; set; }
        }

        public class Order
        {
            public double price { get; set; }
            public double amount { get; set; }
        }

        public class ActualExchangeRates
        {
            public double last { get; set; }
            public double high { get; set; }
            public double low { get; set; }
            public double amount { get; set; }
            public double bid { get; set; }
            public double ask { get; set; }
            public int timestamp { get; set; }
        }

        public class BalanceCurrency
        {
            public string currency { get; set; }
            public double balance { get; set; }
            public double reserved { get; set; }
            public double available { get; set; }
        }

        public class BalanceData
        {
            public BalanceCurrency EUR { get; set; }
            public BalanceCurrency CZK { get; set; }
            public BalanceCurrency BTC { get; set; }
            public BalanceCurrency LTC { get; set; }
            public BalanceCurrency ETH { get; set; }
            public BalanceCurrency DASH { get; set; }
        }

        [Serializable]
        public class Transaction
        {
            public object timestamp { get; set; }
            public string transactionId { get; set; }
            public string transactionType { get; set; }
            public double? price { get; set; }
            public string priceCurrency { get; set; }
            public double amount { get; set; }
            public string amountCurrency { get; set; }
            public double? fee { get; set; }
            public string feeCurrency { get; set; }
            public object description { get; set; }
            public string status { get; set; }
            public string orderId { get; set; }

            public Transaction(Double timestamp, string type, string orderId, string transactionId, Double amount, Double price, double fee, string feeCurrency)
            {
                this.transactionType = type.ToUpper();
                this.orderId = orderId;
                this.transactionId = transactionId;
                this.amount = amount;
                this.price = price;
                this.fee = fee;
                this.feeCurrency = feeCurrency;
                this.timestamp = timestamp;
            }

            public Transaction() { }

            public override string ToString()
            {
                return "timestamp: " + timestamp + "," + Environment.NewLine +
                        "transactionId: " + transactionId + "," + Environment.NewLine +
                        "transactionType: " + transactionType + "," + Environment.NewLine +
                        "price: " + price + "," + Environment.NewLine +
                        "priceCurrency: " + priceCurrency + "," + Environment.NewLine +
                        "amount: " + amount + "," + Environment.NewLine +
                        "amountCurrency: " + amountCurrency + "," + Environment.NewLine +
                        "fee: " + fee + "," + Environment.NewLine +
                        "feeCurrency: " + feeCurrency + "," + Environment.NewLine +
                        "description: " + description + "," + Environment.NewLine +
                        "status: " + status + "," + Environment.NewLine +
                        "orderId: " + orderId + "," + Environment.NewLine +
                        "---------------------------------------------------" + Environment.NewLine;
            }
        }

        public class TransactionString
        {
            public object timestamp { get; set; }
            public string transactionId { get; set; }
            public string transactionType { get; set; }
            public double? price { get; set; }
            public string priceCurrency { get; set; }
            public double? amount { get; set; }
            public string amountCurrency { get; set; }
            public double? fee { get; set; }
            public string feeCurrency { get; set; }
            public object description { get; set; }
            public string status { get; set; }
            public string orderId { get; set; }

        }

        public class OrderBook_Ask
        {
            public double price { get; set; }
            public double amount { get; set; }
        }

        public class OrderBook_Bid
        {
            public double price { get; set; }
            public double amount { get; set; }
        }

        public class OrderBook
        {
            public List<OrderBook_Ask> asks { get; set; }
            public List<OrderBook_Bid> bids { get; set; }
            public int timestamp { get; set; }
        }

        [Serializable]
        public class WalletBalances
        {

            public WalletBalances(string currency, Double amount, Double available, string type)
            {
                this.currency = currency;
                this.amount = amount;
                this.available = available;
                this.type = type;
            }

            public string type { get; set; }
            public string currency { get; set; }
            /// <summary>
            /// How much balance of this currency in this wallet
            /// </summary>
            public Double amount { get; set; }
            /// <summary>
            /// How much X there is in this wallet that is available to trade
            /// </summary>
            public Double available { get; set; }
        }

    }
}
