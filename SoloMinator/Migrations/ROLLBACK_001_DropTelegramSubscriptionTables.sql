-- ============================================
-- ROLLBACK: Drop Telegram Subscription Tables
-- WARNING: This will delete ALL subscription data!
-- Only run if you need to completely rollback the migration
-- ============================================

PRINT '!!! WARNING: This script will DROP tables and DELETE all data !!!';
PRINT 'You have 10 seconds to cancel (Ctrl+C)...';
WAITFOR DELAY '00:00:10';

PRINT '';
PRINT 'Starting rollback...';
PRINT '====================';

-- Drop TelegramLinkTokens table
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'TelegramLinkTokens' AND schema_id = SCHEMA_ID('solo'))
BEGIN
    DROP TABLE [solo].[TelegramLinkTokens];
    PRINT 'Dropped table [solo].[TelegramLinkTokens]';
END
GO

-- Drop TelegramSubscriptions table
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'TelegramSubscriptions' AND schema_id = SCHEMA_ID('solo'))
BEGIN
    DROP TABLE [solo].[TelegramSubscriptions];
    PRINT 'Dropped table [solo].[TelegramSubscriptions]';
END
GO

PRINT '';
PRINT 'Rollback completed';
PRINT 'Note: Original TelegramChatId data in UserRegistrations table is preserved';
GO
