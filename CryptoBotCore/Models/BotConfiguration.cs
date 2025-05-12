using System;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.Models
{
    public static class BotConfiguration
    {
        public static CryptoExchangeAPIEnum CryptoExchangeAPIEnum { get; set; }
        public static string TelegramChannel { get; set; }
        public static string TelegramBot { get; set; }
        public static string Currency { get; set; }
        public static string Fiat { get; set; }
        public static string Account { get; set; }
        public static Dictionary<ExchangeCredentialType, string> ExchangeCredentials { get; set; }
        public static int ChunkSize { get; set; }
        public static string WithdrawalAddress { get; set; }
        
        /// <summary>
        /// Only for Kraken Exchange
        /// </summary>
        public static string WithdrawalKeyName { get; set; }

        public static bool WithdrawalEnabled { get; set; }
        public static int MaxWithdrawalAbsoluteFee { get; set; }
        public static decimal MaxWithdrawalPercentageFee { get; set; }
        public static string UserName { get; set; }
        public static string CosmosDbEndpointUri { get; set; }
        public static string CosmosDbPrimaryKey { get; set; }

}
}
