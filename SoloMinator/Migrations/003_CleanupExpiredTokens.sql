-- ============================================
-- Maintenance: Cleanup Expired Link Tokens
-- Description: Deletes expired or used tokens older than 7 days
-- Run periodically (e.g., weekly) to keep table clean
-- ============================================

PRINT 'Cleaning up expired Telegram link tokens...';
PRINT '============================================';

-- Show tokens to be deleted
SELECT
    'Tokens to delete' AS [Description],
    COUNT(*) AS [Count]
FROM [solo].[TelegramLinkTokens]
WHERE [IsUsed] = 1
   OR [ExpiresAt] < DATEADD(DAY, -7, GETUTCDATE());

-- Delete old tokens
DELETE FROM [solo].[TelegramLinkTokens]
WHERE [IsUsed] = 1
   OR [ExpiresAt] < DATEADD(DAY, -7, GETUTCDATE());

DECLARE @DeletedCount INT = @@ROWCOUNT;
PRINT CONCAT('Deleted ', @DeletedCount, ' expired/used token(s)');

-- Show remaining tokens
SELECT
    'Remaining active tokens' AS [Description],
    COUNT(*) AS [Count]
FROM [solo].[TelegramLinkTokens]
WHERE [IsUsed] = 0 AND [ExpiresAt] > GETUTCDATE();

GO

PRINT 'Cleanup completed';
GO
