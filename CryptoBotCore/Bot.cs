using CryptoBotCore.API;
using CryptoBotCore.Models;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace CryptoBotCore
{
    public class Bot : BackgroundService
    {
        [NonSerialized]
        private CoinmateAPI cryptoExchangeAPI;
        private List<string> sellOrderTransaction { get; set; }

        private List<string> buyOrderTransaction { get; set; }

        private List<OpenOrder> openSellOrder { get; set; }

        private List<OpenOrder> openBuyOrder { get; set; }

        private ILogger _Log { get; set; }


        private readonly IConfiguration _configuration;


        public Bot(IConfiguration configuration, ILogger log)
        {
            _configuration = configuration;
            //cryptoExchangeAPI = new CoinmateAPI()
        }

        public override Task StartAsync(CancellationToken cancellationToken)
        {
            return base.StartAsync(cancellationToken);
        }

        public override Task StopAsync(CancellationToken cancellationToken)
        {
            return base.StopAsync(cancellationToken);
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            while(true)
            {
                await Tick();
                await Task.Delay(30000);
            }
        }

        public async Task Tick()
        {
            try
            {
                if (cryptoExchangeAPI == null)
                {
                    cryptoExchangeAPI = new CoinmateAPI($"{BotConfiguration.Currency}_{BotConfiguration.Fiat}", BotConfiguration.CoinMateCredentials, _Log);
                }

                Tuple<Double, Double> newBuySell = cryptoExchangeAPI.getActualExchangeRate();
                double newMID = (newBuySell.Item1 + newBuySell.Item2) / 2;

                var wdrfee = cryptoExchangeAPI.getBTCWidthrawalFee();

                Console.WriteLine(wdrfee * newMID);
            }
            catch(Exception ex)
            {
                Console.WriteLine(ex);
            }
        }
    }
}
