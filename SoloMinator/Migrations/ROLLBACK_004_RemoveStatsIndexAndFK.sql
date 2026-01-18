-- Rollback Migration: Remove index on WorkerName and FK to UserRegistrations
-- Date: 2025-01-18

-- Drop indexes
IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Stats_RecordedAt' AND object_id = OBJECT_ID('[solo].[Stats]'))
BEGIN
    DROP INDEX [IX_Stats_RecordedAt] ON [solo].[Stats];
    PRINT 'Dropped index IX_Stats_RecordedAt';
END
GO

IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Stats_UserRegistrationId' AND object_id = OBJECT_ID('[solo].[Stats]'))
BEGIN
    DROP INDEX [IX_Stats_UserRegistrationId] ON [solo].[Stats];
    PRINT 'Dropped index IX_Stats_UserRegistrationId';
END
GO

-- Drop foreign key
IF EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_Stats_UserRegistrations')
BEGIN
    ALTER TABLE [solo].[Stats]
    DROP CONSTRAINT [FK_Stats_UserRegistrations];
    PRINT 'Dropped FK_Stats_UserRegistrations';
END
GO

-- Drop columns
IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('[solo].[Stats]') AND name = 'WorkerSuffix')
BEGIN
    ALTER TABLE [solo].[Stats]
    DROP COLUMN [WorkerSuffix];
    PRINT 'Dropped WorkerSuffix column';
END
GO

IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('[solo].[Stats]') AND name = 'UserRegistrationId')
BEGIN
    ALTER TABLE [solo].[Stats]
    DROP COLUMN [UserRegistrationId];
    PRINT 'Dropped UserRegistrationId column';
END
GO

IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Stats_WorkerName' AND object_id = OBJECT_ID('[solo].[Stats]'))
BEGIN
    DROP INDEX [IX_Stats_WorkerName] ON [solo].[Stats];
    PRINT 'Dropped index IX_Stats_WorkerName';
END
GO

PRINT 'Rollback 004 completed successfully';
