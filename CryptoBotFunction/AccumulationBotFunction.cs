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

            BotConfiguration.WithdrawalKeyName = config["WithdrawalKeyName"];

            switch (BotConfiguration.CryptoExchangeAPIEnum)
            {
                case CryptoExchangeAPIEnum.Coinmate:
                    
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_ClientId] = config["CoinMateCredentials_ClientId"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_PublicKey] = config["CoinMateCredentials_PublicKey"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_PrivateKey] = config["CoinMateCredentials_PrivateKey"];
                    break;

                case CryptoExchangeAPIEnum.Binance:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Binance_Key] = config["BinanceCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Binance_Secret] = config["BinanceCredentials_Secret"];
                    break;

                case CryptoExchangeAPIEnum.Coinbase:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinbase_Key] = config["CoinbaseCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinbase_Secret] = config["CoinbaseCredentials_Secret"];
                    break;

                case CryptoExchangeAPIEnum.Huobi:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Huobi_Key] = config["HuobiCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Huobi_Secret] = config["HuobiCredentials_Secret"];
                    break;


                case CryptoExchangeAPIEnum.Kraken:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Kraken_Key] = config["KrakenCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Kraken_Secret] = config["KrakenCredentials_Secret"];
                    break;

                case CryptoExchangeAPIEnum.FTX:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.FTX_Key] = config["FTXCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.FTX_Secret] = config["FTXCredentials_Secret"];
                    break;

                case CryptoExchangeAPIEnum.Bittrex:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bittrex_Key] = config["BittrexCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bittrex_Secret] = config["BittrexCredentials_Secret"];
                    break;

                case CryptoExchangeAPIEnum.Bitfinex:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bitfinex_Key] = config["BitfinexCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bitfinex_Secret] = config["BitfinexCredentials_Secret"];
                    break;

                case CryptoExchangeAPIEnum.KuCoin:
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.KuCoin_Key] = config["KuCoinCredentials_Key"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.KuCoin_Secret] = config["KuCoinCredentials_Secret"];
                    BotConfiguration.ExchangeCredentials[ExchangeCredentialType.KuCoin_PassPhrase] = config["KuCoinCredentials_PassPhrase"];
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