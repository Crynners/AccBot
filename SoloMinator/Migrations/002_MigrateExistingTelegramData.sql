-- ============================================
-- Migration: Migrate Existing Telegram Data
-- Date: 2026-01-16
-- Description: Migrates existing TelegramChatId from UserRegistrations to TelegramSubscriptions
-- ============================================

-- Show current state before migration
PRINT 'Current state before migration:';
PRINT '================================';

SELECT
    'UserRegistrations with TelegramChatId' AS [Description],
    COUNT(*) AS [Count]
FROM [solo].[UserRegistrations]
WHERE TelegramChatId IS NOT NULL AND TelegramChatId != '';

SELECT
    'Existing TelegramSubscriptions' AS [Description],
    COUNT(*) AS [Count]
FROM [solo].[TelegramSubscriptions];

GO

-- ============================================
-- Migrate data
-- ============================================
PRINT '';
PRINT 'Starting data migration...';
PRINT '==========================';

-- Insert only if not already migrated (to make script idempotent)
INSERT INTO [solo].[TelegramSubscriptions]
    ([UserRegistrationId], [TelegramChatId], [IsActive], [CreatedAt])
SELECT
    ur.[Id],
    ur.[TelegramChatId],
    1, -- IsActive = true
    GETUTCDATE()
FROM [solo].[UserRegistrations] ur
WHERE ur.[TelegramChatId] IS NOT NULL
  AND ur.[TelegramChatId] != ''
  AND NOT EXISTS (
      SELECT 1
      FROM [solo].[TelegramSubscriptions] ts
      WHERE ts.[UserRegistrationId] = ur.[Id]
        AND ts.[TelegramChatId] = ur.[TelegramChatId]
  );

DECLARE @RowsMigrated INT = @@ROWCOUNT;
PRINT CONCAT('Migrated ', @RowsMigrated, ' subscription(s)');

GO

-- ============================================
-- Show state after migration
-- ============================================
PRINT '';
PRINT 'State after migration:';
PRINT '======================';

SELECT
    'Total TelegramSubscriptions' AS [Description],
    COUNT(*) AS [Count]
FROM [solo].[TelegramSubscriptions];

-- Show detail of migrated subscriptions
SELECT
    ts.[Id] AS [SubscriptionId],
    ur.[MiningAddress],
    ur.[PoolVariant],
    ts.[TelegramChatId],
    ts.[IsActive],
    ts.[CreatedAt]
FROM [solo].[TelegramSubscriptions] ts
INNER JOIN [solo].[UserRegistrations] ur ON ts.[UserRegistrationId] = ur.[Id]
ORDER BY ts.[CreatedAt] DESC;

GO

PRINT '';
PRINT 'Migration 002 completed successfully';
GO
