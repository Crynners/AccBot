using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace CryptoBotCore.API
{
    [Serializable]
    class CurrencyExchangeAPI
    {
        public static string USD = "USD";

        public Double getCZK(string currencyBase)
        {
            WebClient client = new WebClient();
            string response = client.DownloadString("http://api.fixer.io/latest?base=" + currencyBase);
            Rates result = JsonSerializer.Deserialize<GetRates>(response).rates;

            return result.CZK;
        }

        public class Rates
        {
            public double AUD { get; set; }
            public double BGN { get; set; }
            public double BRL { get; set; }
            public double CAD { get; set; }
            public double CHF { get; set; }
            public double CNY { get; set; }
            public double CZK { get; set; }
            public double DKK { get; set; }
            public double GBP { get; set; }
            public double HKD { get; set; }
            public double HRK { get; set; }
            public double HUF { get; set; }
            public double IDR { get; set; }
            public double ILS { get; set; }
            public double INR { get; set; }
            public double JPY { get; set; }
            public double KRW { get; set; }
            public double MXN { get; set; }
            public double MYR { get; set; }
            public double NOK { get; set; }
            public double NZD { get; set; }
            public double PHP { get; set; }
            public double PLN { get; set; }
            public double RON { get; set; }
            public double RUB { get; set; }
            public double SEK { get; set; }
            public double SGD { get; set; }
            public double THB { get; set; }
            public double TRY { get; set; }
            public double ZAR { get; set; }
            public double EUR { get; set; }
        }

        public class GetRates
        {
            public string @base { get; set; }
            public string date { get; set; }
            public Rates rates { get; set; }
        }
    }
}
