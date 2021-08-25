using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace CryptoBotCore.Models
{
    public enum CryptoExchangeAPIEnum
    {
        Coinmate = 1, Binance = 2, Huobi = 3, Coinbase = 4
    }

    public enum ExchangeCredentialType
    {
        Coinmate_ClientId = 1, Coinmate_PublicKey = 2, Coinmate_PrivateKey = 3,
        Huobi_Key = 4, Huobi_Secret = 5
    }


    public enum OrderType
    {
        MarginMarket,
        MarginLimit,
        MarginStop,
        MarginTrailingStop,
        ExchangeMarket,
        ExchangeLimit,
        ExchangeStop,
        ExchangeTrailingStop
    }
    public enum OrderSide
    {
        Buy,
        Sell
    }
    public enum OrderExchange
    {
        Bitfinex,
        Bitstamp,
        All
    }

    public enum MessageTypeEnum
    {
        Information,
        Warning,
        Error
    }

}
