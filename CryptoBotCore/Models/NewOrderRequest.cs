using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace CryptoBotCore.Models
{
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

    public class NewOrderRequest : GenericRequest
    {
        public string symbol;
        public string amount;
        public string price;
        public string exchange;
        public string side;
        public string type;
        //public bool is_hidden=false;
        public NewOrderRequest(string nonce, string symbol, Double amount, Double price, OrderExchange exchange, OrderSide side, OrderType type)
        {
            this.symbol = symbol;
            this.amount = amount.ToString(CultureInfo.InvariantCulture);
            this.price = price.ToString(CultureInfo.InvariantCulture);
            this.exchange = EnumHelper.EnumToStr(exchange);
            this.side = EnumHelper.EnumToStr(side);
            this.type = EnumHelper.EnumToStr(type);
            this.nonce = nonce;
            this.request = "/v1/order/new";
        }
    }

    public class NewOrderResponse : OrderStatusResponse
    {
        public string order_id;

        public static NewOrderResponse FromJSON(string response)
        {
            NewOrderResponse resp = JsonSerializer.Deserialize<NewOrderResponse>(response);
            return resp;
        }
    }
    public class EnumHelper
    {
        private static Dictionary<object, string> enumStr = null;
        private static Dictionary<object, string> Get()
        {
            if (enumStr == null)
            {
                enumStr = new Dictionary<object, string>();
                enumStr.Add(OrderExchange.All, "all");
                enumStr.Add(OrderExchange.Bitfinex, "bitfinex");
                enumStr.Add(OrderExchange.Bitstamp, "bitstamp");

                enumStr.Add(OrderSide.Buy, "buy");
                enumStr.Add(OrderSide.Sell, "sell");

                enumStr.Add(OrderType.MarginLimit, "limit");
                enumStr.Add(OrderType.MarginMarket, "market");
                enumStr.Add(OrderType.MarginStop, "stop");
                enumStr.Add(OrderType.MarginTrailingStop, "trailing-stop");
                enumStr.Add(OrderType.ExchangeLimit, "exchange limit");
                enumStr.Add(OrderType.ExchangeMarket, "exchange market");
                enumStr.Add(OrderType.ExchangeStop, "exchange stop");
                enumStr.Add(OrderType.ExchangeTrailingStop, "exchange trailing-stop");
            }
            return enumStr;
        }

        public static string EnumToStr(object enumItem)
        {
            return Get()[enumItem];
        }

    }
}
