using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace CryptoBotCore.Models
{
    public class OrderStatusResponse
    {
        public string id;
        public string symbol;
        public string exchange;
        public string price;
        public string avg_execution_price;
        public string type;
        public string timestamp;
        public string is_live;
        public string is_cancelled;
        public string was_forced;
        public string executed_amount;
        public string remaining_amount;
        public string original_amount;
        public string side;

        public static OrderStatusResponse FromJSON(string response)
        {
            return JsonSerializer.Deserialize<OrderStatusResponse>(response);
        }
    }

    public class CancelOrderRequest : GenericRequest
    {
        public long order_id;
        public CancelOrderRequest(string nonce, long order_id)
        {
            this.nonce = nonce;
            this.order_id = order_id;
            this.request = "/v1/order/cancel";
        }
    }

    public class ActiveOrdersRequest : GenericRequest
    {
        public ActiveOrdersRequest(string nonce)
        {
            this.nonce = nonce;
            this.request = "/v1/orders";
        }
    }

    public class CancelOrderResponse : OrderStatusResponse
    {
        public static CancelOrderResponse FromJSON(string response)
        {
            return JsonSerializer.Deserialize<CancelOrderResponse>(response);
        }
    }

    public class ActiveOrdersResponse
    {
        public List<OrderStatusResponse> orders;

        public static ActiveOrdersResponse FromJSON(string response)
        {
            List<OrderStatusResponse> orders = JsonSerializer.Deserialize<List<OrderStatusResponse>>(response);
            return new ActiveOrdersResponse(orders);
        }
        private ActiveOrdersResponse(List<OrderStatusResponse> orders)
        {
            this.orders = orders;
        }
    }
}
