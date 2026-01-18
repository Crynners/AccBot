-- ============================================
-- Migration: Create Telegram Subscription Tables
-- Date: 2026-01-16
-- Description: Creates tables for 1:N Telegram subscriptions
-- ============================================

-- Ensure schema exists
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'solo')
BEGIN
    EXEC('CREATE SCHEMA solo');
END
GO

-- ============================================
-- 1. Create TelegramSubscriptions table
-- ============================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'TelegramSubscriptions' AND schema_id = SCHEMA_ID('solo'))
BEGIN
    CREATE TABLE [solo].[TelegramSubscriptions]
    (
        [Id] INT IDENTITY(1,1) NOT NULL,
        [UserRegistrationId] INT NOT NULL,
        [TelegramChatId] NVARCHAR(50) NOT NULL,
        [TelegramUsername] NVARCHAR(100) NULL,
        [TelegramFirstName] NVARCHAR(100) NULL,
        [IsActive] BIT NOT NULL DEFAULT 1,
        [CreatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
        [LastNotificationAt] DATETIME2 NULL,

        CONSTRAINT [PK_TelegramSubscriptions] PRIMARY KEY CLUSTERED ([Id]),
        CONSTRAINT [FK_TelegramSubscriptions_UserRegistrations]
            FOREIGN KEY ([UserRegistrationId])
            REFERENCES [solo].[UserRegistrations]([Id])
            ON DELETE CASCADE
    );

    PRINT 'Created table [solo].[TelegramSubscriptions]';
END
ELSE
BEGIN
    PRINT 'Table [solo].[TelegramSubscriptions] already exists';
END
GO

-- Create unique index for UserRegistrationId + TelegramChatId
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_TelegramSubscriptions_UserRegistrationId_TelegramChatId')
BEGIN
    CREATE UNIQUE INDEX [IX_TelegramSubscriptions_UserRegistrationId_TelegramChatId]
    ON [solo].[TelegramSubscriptions] ([UserRegistrationId], [TelegramChatId]);

    PRINT 'Created index [IX_TelegramSubscriptions_UserRegistrationId_TelegramChatId]';
END
GO

-- ============================================
-- 2. Create TelegramLinkTokens table
-- ============================================
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'TelegramLinkTokens' AND schema_id = SCHEMA_ID('solo'))
BEGIN
    CREATE TABLE [solo].[TelegramLinkTokens]
    (
        [Id] INT IDENTITY(1,1) NOT NULL,
        [UserRegistrationId] INT NOT NULL,
        [Token] NVARCHAR(64) NOT NULL,
        [ExpiresAt] DATETIME2 NOT NULL,
        [IsUsed] BIT NOT NULL DEFAULT 0,
        [CreatedAt] DATETIME2 NOT NULL DEFAULT GETUTCDATE(),

        CONSTRAINT [PK_TelegramLinkTokens] PRIMARY KEY CLUSTERED ([Id]),
        CONSTRAINT [FK_TelegramLinkTokens_UserRegistrations]
            FOREIGN KEY ([UserRegistrationId])
            REFERENCES [solo].[UserRegistrations]([Id])
            ON DELETE CASCADE
    );

    PRINT 'Created table [solo].[TelegramLinkTokens]';
END
ELSE
BEGIN
    PRINT 'Table [solo].[TelegramLinkTokens] already exists';
END
GO

-- Create unique index for Token lookup
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_TelegramLinkTokens_Token')
BEGIN
    CREATE UNIQUE INDEX [IX_TelegramLinkTokens_Token]
    ON [solo].[TelegramLinkTokens] ([Token]);

    PRINT 'Created index [IX_TelegramLinkTokens_Token]';
END
GO

-- Create index for cleanup of expired tokens
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_TelegramLinkTokens_ExpiresAt_IsUsed')
BEGIN
    CREATE INDEX [IX_TelegramLinkTokens_ExpiresAt_IsUsed]
    ON [solo].[TelegramLinkTokens] ([ExpiresAt], [IsUsed]);

    PRINT 'Created index [IX_TelegramLinkTokens_ExpiresAt_IsUsed]';
END
GO

PRINT 'Migration 001 completed successfully';
GO
