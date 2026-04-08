IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.wines', 'version') IS NULL
        ALTER TABLE [dbo].[wines] ADD [version] BIGINT NOT NULL CONSTRAINT [DF_wines_version] DEFAULT 0;
END;