# AccBot Docker Deployment

This directory contains Docker configuration for running AccBot as a containerized application.

## Quick Start

1. Copy the environment template:
   ```bash
   cp .env.template .env
   ```

2. Edit `.env` with your configuration:
   - Exchange credentials
   - Telegram bot token and channel
   - DCA settings (currency, amount, frequency)

3. Start the containers:
   ```bash
   docker-compose up -d
   ```

## Configuration

### Using CosmosDB Emulator (Default)

The default `docker-compose.yml` includes the Azure CosmosDB Emulator for data persistence.
This is recommended for production-like environments.

```bash
docker-compose up -d
```

### Using MongoDB (Lighter Alternative)

For systems with limited resources, you can use MongoDB instead:

```bash
docker-compose --profile mongodb up -d
```

Note: You'll need to modify the bot to use MongoDB instead of CosmosDB.

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `ExchangeName` | Exchange to use | `coinmate`, `binance`, `kraken` |
| `Currency` | Crypto to accumulate | `BTC`, `ETH`, `LTC` |
| `Fiat` | Fiat currency | `EUR`, `USD`, `CZK` |
| `ChunkSize` | Amount per purchase | `10` |
| `DayDividerSchedule` | NCRONTAB schedule | `0 0 * * * *` |
| `TelegramChannel` | Telegram channel | `@MyChannel` |
| `TelegramBot` | Bot token | `123456:ABC...` |

## Viewing Logs

```bash
# View all logs
docker-compose logs -f

# View only AccBot logs
docker-compose logs -f accbot
```

## Stopping

```bash
docker-compose down
```

To also remove volumes (data):
```bash
docker-compose down -v
```

## Updating

```bash
docker-compose pull
docker-compose up -d --build
```

## Troubleshooting

### Bot not buying

1. Check logs: `docker-compose logs -f accbot`
2. Verify exchange credentials in `.env`
3. Ensure sufficient balance on exchange

### CosmosDB connection issues

The emulator uses a self-signed certificate. The bot should be configured to trust it.
If issues persist, check the emulator status:

```bash
docker-compose logs cosmosdb-emulator
```

### High memory usage

The CosmosDB Emulator can use significant memory. Consider using the MongoDB profile instead.
