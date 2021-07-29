using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace CryptoBotCore.Models
{
    [Serializable]
    public class Offer
    {
        public int id { get; set; }
        public string currency { get; set; }
        public string rate { get; set; }
        public int period { get; set; }
        public string direction { get; set; }
        public string timestamp { get; set; }
        public bool is_live { get; set; }
        public bool is_cancelled { get; set; }
        public string original_amount { get; set; }
        public string remaining_amount { get; set; }
        public string executed_amount { get; set; }
    }

    public class CancelOfferRequest : GenericRequest
    {
        public long offer_id;
        public CancelOfferRequest(string nonce, long offer_id)
        {
            this.nonce = nonce;
            this.offer_id = offer_id;
            this.request = "/v1/offer/cancel";
        }
    }

    public class OfferRequest : GenericRequest
    {
        public string amount;
        public string currency;
        public string rate;
        public int period;
        public string direction;

        public OfferRequest(string nonce, double amount, string currency, double rate, int period)
        {
            this.amount = amount.ToString();
            this.currency = currency;
            this.rate = rate.ToString();
            this.period = period;
            this.direction = "lend";
            this.nonce = nonce;
            this.request = "/v1/offer/new";
        }
    }

    public class OfferResponce
    {
        public Offer offer;

        public static OfferResponce FromJSON(string response)
        {
            Offer offer = JsonSerializer.Deserialize<Offer>(response);
            return new OfferResponce(offer);
        }

        private OfferResponce(Offer offer)
        {
            this.offer = offer;
        }
    }
}
