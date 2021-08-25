using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using CryptoBotCore.BotStrategies;
using CryptoBotCore.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.WebJobs;
using Microsoft.Azure.WebJobs.Extensions.Http;
using Microsoft.Azure.WebJobs.Host;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace CryptoBotFunction
{
    public static class AccumulationBotFunction
    {
        [FunctionName("AccumulationBotFunction")]
        public static void Run([TimerTrigger("%DayDividerSchedule%")] TimerInfo myTimer, ILogger log)
        {
            LoadAppSettings();

            var accumulationBot = new AccumulationBot(log);
            accumulationBot.Tick().GetAwaiter().GetResult();
        }

        private static void LoadAppSettings()
        {
            var env = Environment.GetEnvironmentVariable("ASPNETCORE_ENVIRONMENT");
            var builder = new ConfigurationBuilder()
              .AddJsonFile($"appsettings.json", true, true)
              .AddJsonFile($"appsettings.{env}.json", true, true)
              .AddEnvironmentVariables();

            var config = builder.Build();

            BotConfiguration.Currency = config["Currency"];
            BotConfiguration.Fiat = config["Fiat"];
            BotConfiguration.UserName = config["Name"];
            BotConfiguration.ChunkSize = Int32.Parse(config["ChunkSize"]);

            var WithdrawalEnabledValid = bool.TryParse(config["WithdrawalEnabled"], out bool WithdrawalEnabled);
            BotConfiguration.WithdrawalEnabled = WithdrawalEnabledValid ? WithdrawalEnabled : false;

            BotConfiguration.WithdrawalAddress = config["WithdrawalAddress"];

            var isPercentageFeeValid = Double.TryParse(config["MaxWithdrawalPercentageFee"], out double MaxWithdrawalPercentageFee);
            BotConfiguration.MaxWithdrawalPercentageFee = isPercentageFeeValid ? MaxWithdrawalPercentageFee : 0.001;

            var isMaxWithdrawalAbsoluteFeeValid = Int32.TryParse(config["MaxWithdrawalAbsoluteFee"], out int MaxWithdrawalAbsoluteFee);
            BotConfiguration.MaxWithdrawalAbsoluteFee = isMaxWithdrawalAbsoluteFeeValid ? MaxWithdrawalAbsoluteFee : -1;

            BotConfiguration.TelegramChannel = config["TelegramChannel"];
            BotConfiguration.TelegramBot = config["TelegramBot"];
            BotConfiguration.CosmosDbEndpointUri = config["CosmosDbEndpointUri"];
            BotConfiguration.CosmosDbPrimaryKey = config["CosmosDbPrimaryKey"];

            BotConfiguration.CryptoExchangeAPIEnum = SetCryptoExchangeAPIEnum(config["ExchangeName"]?.ToLower());

            BotConfiguration.ExchangeCredentials = new Dictionary<ExchangeCredentialType, string>();

            switch (BotConfiguration.CryptoExchangeAPIEnum)
            {
                case CryptoExchangeAPIEnum.Coinmate:
                    
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_ClientId] = config["CoinMateCredentials_ClientId"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_PublicKey] = config["CoinMateCredentials_PublicKey"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_PrivateKey] = config["CoinMateCredentials_PrivateKey"];
                    break;

                case CryptoExchangeAPIEnum.Binance:
                    throw new NotImplementedException();

                case CryptoExchangeAPIEnum.Coinbase:
                    throw new NotImplementedException();

                case CryptoExchangeAPIEnum.Huobi:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Huobi_Key] = config["HuobiCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Huobi_Secret] = config["HuobiCredentials_Secret"];
                    break;

                default:
                    throw new NotImplementedException();
            }

        }

        private static CryptoExchangeAPIEnum SetCryptoExchangeAPIEnum(string exchangeName)
        {
            if (String.IsNullOrEmpty(exchangeName))
            {
                return CryptoExchangeAPIEnum.Coinmate;
            }

            if (exchangeName == "coinmate")
            {
                return CryptoExchangeAPIEnum.Coinmate;
            }
            else if (exchangeName == "huobi")
            {
                return CryptoExchangeAPIEnum.Huobi;
            }else if (exchangeName == "coinbase")
            {
                return CryptoExchangeAPIEnum.Coinbase;
            }else if(exchangeName == "binance")
            {
                return CryptoExchangeAPIEnum.Binance;
            }
            else
            {
                throw new NotImplementedException();
            }
        }
    }
}