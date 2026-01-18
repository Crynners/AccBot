-- Migration: Add index on WorkerName and FK to UserRegistrations
-- Date: 2025-01-18
-- Description: Optimizes Stats table queries by adding index and foreign key relationship

-- Step 1: Add index on WorkerName (critical for performance)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Stats_WorkerName' AND object_id = OBJECT_ID('[solo].[Stats]'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Stats_WorkerName]
    ON [solo].[Stats] ([WorkerName])
    INCLUDE ([BestEver], [BestShare], [RecordedAt]);

    PRINT 'Created index IX_Stats_WorkerName';
END
ELSE
BEGIN
    PRINT 'Index IX_Stats_WorkerName already exists';
END
GO

-- Step 2: Add UserRegistrationId column
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('[solo].[Stats]') AND name = 'UserRegistrationId')
BEGIN
    ALTER TABLE [solo].[Stats]
    ADD [UserRegistrationId] INT NULL;

    PRINT 'Added UserRegistrationId column';
END
ELSE
BEGIN
    PRINT 'UserRegistrationId column already exists';
END
GO

-- Step 3: Add WorkerSuffix column (for worker name without address prefix)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('[solo].[Stats]') AND name = 'WorkerSuffix')
BEGIN
    ALTER TABLE [solo].[Stats]
    ADD [WorkerSuffix] NVARCHAR(100) NULL;

    PRINT 'Added WorkerSuffix column';
END
ELSE
BEGIN
    PRINT 'WorkerSuffix column already exists';
END
GO

-- Step 4: Populate UserRegistrationId from existing WorkerName data
-- Pattern: WorkerName = "pool:address" or "pool:address.worker"
UPDATE s
SET s.UserRegistrationId = ur.Id,
    s.WorkerSuffix = CASE
        WHEN CHARINDEX('.', s.WorkerName, CHARINDEX(':', s.WorkerName)) > 0
        THEN SUBSTRING(s.WorkerName, CHARINDEX('.', s.WorkerName, CHARINDEX(':', s.WorkerName)) + 1, LEN(s.WorkerName))
        ELSE NULL
    END
FROM [solo].[Stats] s
INNER JOIN [solo].[UserRegistrations] ur
    ON s.WorkerName LIKE ur.PoolVariant + ':' + ur.MiningAddress + '%'
WHERE s.UserRegistrationId IS NULL;

PRINT 'Populated UserRegistrationId and WorkerSuffix from existing data';
GO

-- Step 5: Add foreign key constraint (with NOCHECK for existing data)
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_Stats_UserRegistrations')
BEGIN
    ALTER TABLE [solo].[Stats]
    ADD CONSTRAINT [FK_Stats_UserRegistrations]
    FOREIGN KEY ([UserRegistrationId])
    REFERENCES [solo].[UserRegistrations]([Id])
    ON DELETE SET NULL;

    PRINT 'Created FK_Stats_UserRegistrations';
END
ELSE
BEGIN
    PRINT 'FK_Stats_UserRegistrations already exists';
END
GO

-- Step 6: Add index on UserRegistrationId for fast lookups
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Stats_UserRegistrationId' AND object_id = OBJECT_ID('[solo].[Stats]'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Stats_UserRegistrationId]
    ON [solo].[Stats] ([UserRegistrationId])
    INCLUDE ([WorkerName], [WorkerSuffix], [BestEver], [BestShare], [RecordedAt]);

    PRINT 'Created index IX_Stats_UserRegistrationId';
END
ELSE
BEGIN
    PRINT 'Index IX_Stats_UserRegistrationId already exists';
END
GO

-- Step 7: Add composite index for RecordedAt queries
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Stats_RecordedAt' AND object_id = OBJECT_ID('[solo].[Stats]'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Stats_RecordedAt]
    ON [solo].[Stats] ([RecordedAt] DESC)
    INCLUDE ([WorkerName], [UserRegistrationId], [BestEver]);

    PRINT 'Created index IX_Stats_RecordedAt';
END
ELSE
BEGIN
    PRINT 'Index IX_Stats_RecordedAt already exists';
END
GO

PRINT 'Migration 004_AddStatsIndexAndFK completed successfully';
