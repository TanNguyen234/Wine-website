IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.wines', 'created_by') IS NULL
        ALTER TABLE [dbo].[wines] ADD [created_by] NVARCHAR(255) NULL;

    IF COL_LENGTH('dbo.wines', 'updated_by') IS NULL
        ALTER TABLE [dbo].[wines] ADD [updated_by] NVARCHAR(255) NULL;

    IF COL_LENGTH('dbo.wines', 'created_at') IS NULL
        ALTER TABLE [dbo].[wines] ADD [created_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.wines', 'updated_at') IS NULL
        ALTER TABLE [dbo].[wines] ADD [updated_at] DATETIME2 NULL;
END;
