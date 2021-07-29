using System;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.Models
{
        using System;
        using System.Collections.Generic;

        public class MarketPriceSnapshot
        {
            public System.DateTime MARPS_DateTime { get; set; }
            public System.Guid MARPS_ApplicationId { get; set; }
            public string MARPS_PLATF_Platform_Key { get; set; }
            public string MARPS_CURRE_CurrencyFrom_Key { get; set; }
            public string MARPS_CURRE_CurrencyTo_Key { get; set; }
            public Nullable<decimal> MARPS_CurrencyFrom_Buy { get; set; }
            public Nullable<decimal> MARPS_CurrencyFrom_Sell { get; set; }
            public Nullable<decimal> MARPS_CurrencyCZK_Buy { get; set; }
            public Nullable<decimal> MARPS_CurrencyCZK_Sell { get; set; }
            public Nullable<decimal> MARPS_CurrencyCZK_ExchangeRate { get; set; }
            public int MARPS_Id { get; set; }

            public virtual Currency Currency { get; set; }
            public virtual Currency Currency1 { get; set; }
            public virtual Plaftorm Plaftorm { get; set; }
        }
    }