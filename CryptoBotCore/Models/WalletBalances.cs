using System;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.Models
{
    [Serializable]
    public class WalletBalances
    {

        public WalletBalances(string currency, Double available)
        {
            this.currency = currency;
            this.available = available;
  
        }

        public string currency { get; set; }

        /// <summary>
        /// How much X there is in this wallet that is available to trade
        /// </summary>
        public double available { get; set; }
    }
}
