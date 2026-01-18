using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.DependencyInjection;
using Telegram.Bot;
using SoloMinatorNotifier.Services;
using Microsoft.EntityFrameworkCore;
using SoloMinatorNotifier.Data;
using SoloMinatorNotifier.Functions;
using SoloMinatorNotifier.Repositories;

var builder = new HostBuilder()
    .ConfigureFunctionsWebApplication()
    .ConfigureServices((context, services) =>
    {
        services.AddHttpClient();

        // Add Database Context
        var connectionString = context.Configuration["ConnectionStrings:DefaultConnection"]
            ?? context.Configuration["DefaultConnection"];
        services.AddDbContext<MiningContext>(options =>
            options.UseSqlServer(connectionString));

        // Add Telegram Bot
        services.AddSingleton<ITelegramBotClient>(x =>
            new TelegramBotClient(context.Configuration["TelegramBotToken"]));

        // Add Services
        services.AddScoped<ITelegramService, TelegramService>();
        services.AddScoped<IDifficultyRepository, DifficultyRepository>();
        services.AddScoped<IMiningStatsRepository, MiningStatsRepository>();
        services.AddScoped<IUserRegistrationRepository, UserRegistrationRepository>();
        services.AddScoped<ITelegramSubscriptionRepository, TelegramSubscriptionRepository>();
        services.AddScoped<IMiningStatsService, MiningStatsService>();
        services.AddScoped<MonitorMiningStats>();
        services.AddHttpClient<IDifficultyService, DifficultyService>();
        services.AddScoped<IDifficultyService, DifficultyService>();

#if DEBUG
        services.AddHostedService<DebugStartupService>();
#endif
    });

var app = builder.Build();

app.Run();