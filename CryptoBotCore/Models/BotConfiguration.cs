using System;
using System.Collections.Generic;
using System.Text;

namespace CryptoBotCore.Models
{
    public static class BotConfiguration
    {
        public static string TelegramChannel { get; set; }
        public static string TelegramBot { get; set; }
        public static string Currency { get; set; }
        public static CoinMateCredentials CoinMateCredentials { get; set; }
        public static int ChunkSize { get; set; }
        public static double MaxWithdrawalPercentageFee { get; set; } = 0.001;
        public static string WithdrawalAddress { get; set; }
        public static bool WithdrawalEnabled { get; set; }
        public static string UserName { get; set; }
        public static string CosmosDbEndpointUri { get; set; }
        public static string CosmosDbPrimaryKey { get; set; }
}
}
