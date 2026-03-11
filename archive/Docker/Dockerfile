# AccBot Dockerfile
# Multi-stage build for .NET 10 Azure Functions Isolated Worker

# Build stage
FROM mcr.microsoft.com/dotnet/sdk:10.0-preview AS build
WORKDIR /src

# Copy project files
COPY ["CryptoBotCore/CryptoBotCore.csproj", "CryptoBotCore/"]
COPY ["CryptoBotFunction/CryptoBotFunction.csproj", "CryptoBotFunction/"]

# Restore dependencies
RUN dotnet restore "CryptoBotFunction/CryptoBotFunction.csproj"

# Copy source code
COPY . .

# Build and publish
WORKDIR /src/CryptoBotFunction
RUN dotnet publish -c Release -o /app/publish

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0-preview AS runtime
WORKDIR /app

# Install Azure Functions host
ENV AzureWebJobsScriptRoot=/app \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true \
    FUNCTIONS_WORKER_RUNTIME=dotnet-isolated

# Copy published app
COPY --from=build /app/publish .

# Create data directory for persistence
RUN mkdir -p /app/data

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:80/api/health || exit 1

# Entry point
ENTRYPOINT ["dotnet", "CryptoBotFunction.dll"]
