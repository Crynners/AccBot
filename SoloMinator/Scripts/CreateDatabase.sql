-- Create schema if not exists
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'solo')
BEGIN
    EXEC('CREATE SCHEMA solo')
END
GO

-- Create UserRegistrations table
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[solo].[UserRegistrations]') AND type in (N'U'))
BEGIN
    CREATE TABLE [solo].[UserRegistrations] (
        [Id] INT IDENTITY(1,1) NOT NULL,
        [MiningAddress] NVARCHAR(100) NOT NULL,
        [PoolVariant] NVARCHAR(50) NOT NULL,
        [TelegramChatId] NVARCHAR(50) NULL,
        [NotificationsEnabled] BIT NOT NULL DEFAULT 0,
        [CreatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
        [UpdatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
        CONSTRAINT [PK_UserRegistrations] PRIMARY KEY CLUSTERED ([Id] ASC)
    );

    -- Unique index on MiningAddress + PoolVariant combination
    CREATE UNIQUE NONCLUSTERED INDEX [IX_UserRegistrations_MiningAddress_PoolVariant]
        ON [solo].[UserRegistrations] ([MiningAddress], [PoolVariant]);

    -- Index for address lookups
    CREATE NONCLUSTERED INDEX [IX_UserRegistrations_MiningAddress]
        ON [solo].[UserRegistrations] ([MiningAddress]);

    PRINT 'Table [solo].[UserRegistrations] created successfully.'
END
ELSE
BEGIN
    PRINT 'Table [solo].[UserRegistrations] already exists.'
END
GO
