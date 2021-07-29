using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CryptoBotCore.Models
{
    [Serializable]
    public class OpenOrder
    {
        public string id { get; set; }
        public Double timestamp { get; set; }
        public string type { get; set; }
        public double price { get; set; }
        public double amount { get; set; }
        public long orderOrphanTTL { get; set; }

        public Double packageSize { get; set; }

        public string market { get; set; }
        public string currencyPair { get; set; }

        /// <summary>
        /// Is it already in the eschange market? Default is true
        /// </summary>
        public bool isActive { get; set; } = true;

        public Double? buyFee { get; set; }

        public string feeCurrency { get; set; }

        public Double? executed_amount { get; set; }
        public Double? remaining_amount { get; set; }
        public Double? original_amount { get; set; }

        public OpenOrder buyOrder { get; set; }

        public string pair { get; set; }

        public OpenOrder() { }

        public OpenOrder(string id, string type, double price, double amount, Double timestamp, Double packageSize, string market = "", string pair = ""
                        , Double? executed_amount = null, Double? remaining_amount = null, Double? original_amount = null)
        {
            this.id = id;
            this.type = type;
            this.price = price;
            this.amount = amount;
            this.timestamp = timestamp;
            this.orderOrphanTTL = 0;
            this.market = market;
            this.pair = pair;
            this.packageSize = packageSize;
            this.executed_amount = executed_amount;
            this.remaining_amount = remaining_amount;
            this.original_amount = original_amount;
            this.isActive = true;
        }

        public string ToString(string pair)
        {
            string baseCurr = pair.Substring(3, 3).ToUpper();
            string cryptoCurr = pair.Substring(0, 3).ToUpper();

            return String.Format("{0, 15} {1, 3} @ {2,15} {3,3} = {4, 9} {5,3} in {6,19}", Math.Round(amount, 8).ToString("N8"), cryptoCurr, Math.Round(price, 5).ToString("N5"), baseCurr, Math.Round(amount * price, 2).ToString("N2"), baseCurr, Utility.getDateTimeFromUnixTimestamp(timestamp).ToString("yyyy-MM-dd HH:mm:ss"));
        }

        public string ToString(string baseCurrency, string quoteCurrency)
        {
            string baseCurr = baseCurrency.ToUpper();
            string cryptoCurr = quoteCurrency.ToUpper();

            return String.Format("{0, 15} {1, 3} @ {2,15} {3,3} = {4, 9} {5,3} in {6,19}", Math.Round(amount, 8).ToString("N8"), cryptoCurr, Math.Round(price, 5).ToString("N5"), baseCurr, Math.Round(amount * price, 2).ToString("N2"), baseCurr, Utility.getDateTimeFromUnixTimestamp(timestamp).ToString("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
