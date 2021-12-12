using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.CosmosDB.Model
{
    public class AccumulationSummary
    {
        [JsonProperty(PropertyName = "id")]
        public Guid Id { get; set; }
        public string? CryptoName { get; set; }
        public decimal AccumulatedCryptoAmount { get; set; }
        public decimal InvestedFiatAmount { get; set; }
        public int Buys { get; set; }

        public void Increment(decimal totalCostOfOperation, decimal currencyBought){
            this.Buys += 1;
            this.InvestedFiatAmount += totalCostOfOperation; // includes the fees
            this.AccumulatedCryptoAmount += currencyBought;
        }
    }
}
