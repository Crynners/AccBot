using System;
using System.Collections.Generic;
using System.Text;
using Microsoft.Azure.Cosmos;
using System.Linq;
using System.Net;
using System.Threading.Tasks;
using CryptoBotCore.CosmosDB.Model;
using CryptoBotCore.Models;

namespace CryptoBotCore.CosmosDB
{
    public class CosmosDbContext
    {
        // The Cosmos client instance
        private CosmosClient cosmosClient;

        private Database database;

        private Container container;

        private string databaseId = "AccBotDatabase";
        private string containerId = "AccBotContainer";

        public CosmosDbContext()
        {
            this.cosmosClient = new CosmosClient(BotConfiguration.CosmosDbEndpointUri, BotConfiguration.CosmosDbPrimaryKey);
            CreateDatabaseAsync().GetAwaiter().GetResult();
            CreateContainerAsync().GetAwaiter().GetResult();
        }


        /// <summary>
        /// Create the database if it does not exist
        /// </summary>
        private async Task CreateDatabaseAsync()
        {
            // Create a new database
            this.database = await this.cosmosClient.CreateDatabaseIfNotExistsAsync(databaseId);
            Console.WriteLine("Created Database: {0}\n", this.database.Id);
        }

        /// <summary>
        /// Create the container if it does not exist. 
        /// Specifiy "/LastName" as the partition key since we're storing family information, to ensure good distribution of requests and storage.
        /// </summary>
        /// <returns></returns>
        private async Task CreateContainerAsync()
        {
            // Create a new container
            this.container = await this.database.CreateContainerIfNotExistsAsync(containerId, "/CryptoName");
            Console.WriteLine("Created Container: {0}\n", this.container.Id);
        }

        public async Task AddItemAsync(AccumulationSummary item)
        {
            await this.container.CreateItemAsync<AccumulationSummary>(item, new PartitionKey(item.CryptoName.ToString()));
        }

        public async Task DeleteItemAsync(string id, string cryptoName)
        {
            await this.container.DeleteItemAsync<AccumulationSummary>(id, new PartitionKey(cryptoName));
        }

        public async Task<AccumulationSummary> GetItemAsync(string id)
        {
            try
            {
                ItemResponse<AccumulationSummary> response = await this.container.ReadItemAsync<AccumulationSummary>(id, new PartitionKey(id));
                return response.Resource;
            }
            catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                return null;
            }

        }

        public async Task<IEnumerable<AccumulationSummary>> GetItemsAsync(string queryString)
        {
            var query = this.container.GetItemQueryIterator<AccumulationSummary>(new QueryDefinition(queryString));
            List<AccumulationSummary> results = new List<AccumulationSummary>();
            while (query.HasMoreResults)
            {
                var response = await query.ReadNextAsync();

                results.AddRange(response.ToList());
            }

            return results;
        }

        private async Task<AccumulationSummary> GetAccumulationSummaryQuery(string CryptoName)
        {
            var query = this.container.GetItemQueryIterator<AccumulationSummary>(new QueryDefinition($"select top 1 * from c where c.CryptoName = '{CryptoName}'"));
            List<AccumulationSummary> results = new List<AccumulationSummary>();
            while (query.HasMoreResults)
            {
                var response = await query.ReadNextAsync();

                results.AddRange(response.ToList());
            }

            return results.FirstOrDefault();
        }

        public async Task<AccumulationSummary> GetAccumulationSummary(string CryptoName)
        {
            var summary = await GetAccumulationSummaryQuery(CryptoName);

            if (summary == null)
            {
                AccumulationSummary accumulationSummary = new AccumulationSummary()
                {
                    AccumulatedCryptoAmount = 0,
                    CryptoName = CryptoName,
                    Buys = 0,
                    Id = Guid.NewGuid(),
                    InvestedFiatAmount = 0
                };
                ItemResponse<AccumulationSummary> accumulationSummaryResponse = await this.container.CreateItemAsync<AccumulationSummary>(accumulationSummary, new PartitionKey(accumulationSummary.CryptoName));
                Console.WriteLine("Created item in database with id: {0} Operation consumed {1} RUs.\n", accumulationSummaryResponse.Resource.Id, accumulationSummaryResponse.RequestCharge);
                summary = await GetAccumulationSummaryQuery(CryptoName);
            }

            return summary;
        }

        public async Task UpdateItemAsync(AccumulationSummary item)
        {
            await this.container.UpsertItemAsync<AccumulationSummary>(item, new PartitionKey(item.CryptoName.ToString()));
        }
    }
}
