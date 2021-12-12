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
            BotConfiguration.Account = config["Account"];
            BotConfiguration.ChunkSize = Int32.Parse(config["ChunkSize"]);

            var WithdrawalEnabledValid = bool.TryParse(config["WithdrawalEnabled"], out bool WithdrawalEnabled);
            BotConfiguration.WithdrawalEnabled = WithdrawalEnabledValid ? WithdrawalEnabled : false;

            BotConfiguration.WithdrawalAddress = config["WithdrawalAddress"];

            var isPercentageFeeValid = decimal.TryParse(config["MaxWithdrawalPercentageFee"], out decimal MaxWithdrawalPercentageFee);
            BotConfiguration.MaxWithdrawalPercentageFee = isPercentageFeeValid ? MaxWithdrawalPercentageFee : 0.001m; // HARDCODED

            var isMaxWithdrawalAbsoluteFeeValid = Int32.TryParse(config["MaxWithdrawalAbsoluteFee"], out int MaxWithdrawalAbsoluteFee);
            BotConfiguration.MaxWithdrawalAbsoluteFee = isMaxWithdrawalAbsoluteFeeValid ? MaxWithdrawalAbsoluteFee : -1;

            BotConfiguration.TelegramChannel = config["TelegramChannel"];
            BotConfiguration.TelegramBot = config["TelegramBot"];
            BotConfiguration.CosmosDbEndpointUri = config["CosmosDbEndpointUri"];
            BotConfiguration.CosmosDbPrimaryKey = config["CosmosDbPrimaryKey"];

            BotConfiguration.CryptoExchangeAPIEnum = FillCryptoExchangeParameters(config);

            BotConfiguration.WithdrawalKeyName = config["WithdrawalKeyName"];
        }

        private static CryptoExchangeAPIEnum FillCryptoExchangeParameters(IConfigurationRoot config)
        {
            var exchangeName = config["ExchangeName"]?.ToLower();
            BotConfiguration.ExchangeCredentials = new Dictionary<ExchangeCredentialType, string>();

            if (String.IsNullOrEmpty(exchangeName) || exchangeName == "coinmate")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_ClientId] = config["CoinMateCredentials_ClientId"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_PublicKey] = config["CoinMateCredentials_PublicKey"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinmate_PrivateKey] = config["CoinMateCredentials_PrivateKey"];
                return CryptoExchangeAPIEnum.Coinmate;
            }
            else if (exchangeName == "huobi")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Huobi_Key] = config["HuobiCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Huobi_Secret] = config["HuobiCredentials_Secret"];
                return CryptoExchangeAPIEnum.Huobi;
            }else if (exchangeName == "coinbase")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinbase_Key] = config["CoinbaseCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Coinbase_Secret] = config["CoinbaseCredentials_Secret"];
                return CryptoExchangeAPIEnum.Coinbase;
            }else if(exchangeName == "binance")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Binance_Key] = config["BinanceCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Binance_Secret] = config["BinanceCredentials_Secret"];
                return CryptoExchangeAPIEnum.Binance;
            }
            else if (exchangeName == "bittrex")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bittrex_Key] = config["BittrexCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bittrex_Secret] = config["BittrexCredentials_Secret"];
                return CryptoExchangeAPIEnum.Bittrex;
            }
            else if (exchangeName == "kraken")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Kraken_Key] = config["KrakenCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Kraken_Secret] = config["KrakenCredentials_Secret"];
                return CryptoExchangeAPIEnum.Kraken;
            }
            else if (exchangeName == "kucoin")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.KuCoin_Key] = config["KuCoinCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.KuCoin_Secret] = config["KuCoinCredentials_Secret"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.KuCoin_PassPhrase] = config["KuCoinCredentials_PassPhrase"];
                return CryptoExchangeAPIEnum.KuCoin;
            }
            else if (exchangeName == "bitfinex")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bitfinex_Key] = config["BitfinexCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.Bitfinex_Secret] = config["BitfinexCredentials_Secret"];
                return CryptoExchangeAPIEnum.Bitfinex;
            }
            else if (exchangeName == "ftx")
            {
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.FTX_Key] = config["FTXCredentials_Key"];
                BotConfiguration.ExchangeCredentials[ExchangeCredentialType.FTX_Secret] = config["FTXCredentials_Secret"];
                return CryptoExchangeAPIEnum.FTX;
            }
            else
            {
                throw new NotImplementedException($"Exchange '{exchangeName}' is not implemented yet!");
            }
        }
    }
}