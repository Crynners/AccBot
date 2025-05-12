using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using CryptoBotCore.BotStrategies;
using CryptoBotCore.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Microsoft.Azure.Functions.Worker;

namespace CryptoBotFunction
{
    public class AccumulationBotFunction
    {
        private readonly ILogger<AccumulationBotFunction> _logger;
        private readonly IConfiguration _configuration;

        public AccumulationBotFunction(ILoggerFactory loggerFactory, IConfiguration configuration)
        {
            _logger = loggerFactory.CreateLogger<AccumulationBotFunction>();
            _configuration = configuration; // Inject configuration
        }

        [Function("AccumulationBotFunction")]
        public void Run([TimerTrigger("%DayDividerSchedule%"
            #if DEBUG || TEST
            , RunOnStartup=true
            #endif
            )] TimerInfo myTimer)
        {
            LoadAppSettings(); // Consider moving this logic to startup/DI
            _logger.LogInformation($"C# Timer trigger function executed at: {DateTime.Now}");

            var accumulationBot = new AccumulationBot(_logger); // Pass the correct logger
            accumulationBot.Tick().GetAwaiter().GetResult();

            if (myTimer.ScheduleStatus is not null)
            {
                _logger.LogInformation($"Next timer schedule at: {myTimer.ScheduleStatus.Next}");
            }
        }

        private void LoadAppSettings()
        {
            BotConfiguration.Currency = _configuration["Currency"];
            BotConfiguration.Fiat = _configuration["Fiat"];
            BotConfiguration.UserName = _configuration["Name"];
            BotConfiguration.Account = _configuration["Account"];
            BotConfiguration.ChunkSize = Int32.Parse(_configuration["ChunkSize"] ?? "0"); // Added null check

            var WithdrawalEnabledValid = bool.TryParse(_configuration["WithdrawalEnabled"], out bool WithdrawalEnabled);
            BotConfiguration.WithdrawalEnabled = WithdrawalEnabledValid && WithdrawalEnabled;

            BotConfiguration.WithdrawalAddress = _configuration["WithdrawalAddress"];

            var isPercentageFeeValid = decimal.TryParse(_configuration["MaxWithdrawalPercentageFee"], out decimal MaxWithdrawalPercentageFee);
            BotConfiguration.MaxWithdrawalPercentageFee = isPercentageFeeValid ? MaxWithdrawalPercentageFee : 0.001m; // HARDCODED

            var isMaxWithdrawalAbsoluteFeeValid = Int32.TryParse(_configuration["MaxWithdrawalAbsoluteFee"], out int MaxWithdrawalAbsoluteFee);
            BotConfiguration.MaxWithdrawalAbsoluteFee = isMaxWithdrawalAbsoluteFeeValid ? MaxWithdrawalAbsoluteFee : -1;

            BotConfiguration.TelegramChannel = _configuration["TelegramChannel"];
            BotConfiguration.TelegramBot = _configuration["TelegramBot"];
            BotConfiguration.CosmosDbEndpointUri = _configuration["CosmosDbEndpointUri"];
            BotConfiguration.CosmosDbPrimaryKey = _configuration["CosmosDbPrimaryKey"];

            BotConfiguration.CryptoExchangeAPIEnum = FillCryptoExchangeParameters(_configuration); // Pass IConfiguration

            BotConfiguration.WithdrawalKeyName = _configuration["WithdrawalKeyName"];
            _logger.LogInformation("Application settings loaded.");
        }

        private CryptoExchangeAPIEnum FillCryptoExchangeParameters(IConfiguration config)
        {
            var exchangeName = config["ExchangeName"]?.ToLower();
            BotConfiguration.ExchangeCredentials = new Dictionary<ExchangeCredentialType, string>();
            _logger.LogInformation($"Configuring exchange: {exchangeName}");

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
            else
            {
                _logger.LogError($"Exchange '{exchangeName}' is not implemented yet!");
                throw new NotImplementedException($"Exchange '{exchangeName}' is not implemented yet!");
            }
        }
    }
}