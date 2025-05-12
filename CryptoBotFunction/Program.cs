using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using System.Threading.Tasks;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Core;

namespace CryptoBotFunction
{
    public class Program
    {
        public static async Task Main()
        {
            var host = new HostBuilder()
                .ConfigureAppConfiguration(configurationBuilder =>
                {
                    // Add configuration sources here if needed, e.g., appsettings.json
                    // Environment variables are typically added by default
                    configurationBuilder.AddJsonFile("local.settings.json", optional: true, reloadOnChange: true);
                    configurationBuilder.AddEnvironmentVariables();
                })
                .ConfigureFunctionsWorkerDefaults()
                .ConfigureFunctionsWebApplication()
                .ConfigureServices(services =>
                {
                    // Ensure Http.AspNetCore extensions are configured
                    services.Configure<WorkerOptions>(options =>
                    {
                        // options.Serializer = new JsonObjectSerializer(); // Removed for simplicity
                    });
                    services.AddHttpClient(); // Example: Add HttpClient if needed

                    // Add services to the container.
                    // services.AddApplicationInsightsTelemetryWorkerService(); // Uncomment if using App Insights
                    // services.ConfigureFunctionsApplicationInsights(); // Uncomment if using App Insights

                    // Example: Register your function class
                    services.AddSingleton<AccumulationBotFunction>();

                    // Register other services (like your AccumulationBot, configuration objects, etc.)
                    // services.AddSingleton<IConfiguration>(provider => provider.GetRequiredService<IHostEnvironment>().Configuration); // Already available via DI
                    // services.AddSingleton<AccumulationBot>(); // Needs to be adapted if it has dependencies

                })
                .ConfigureLogging(logging =>
                {
                    // Optional: Configure logging providers
                    logging.AddConsole();
                })
                .Build();

            await host.RunAsync();
        }
    }
} 