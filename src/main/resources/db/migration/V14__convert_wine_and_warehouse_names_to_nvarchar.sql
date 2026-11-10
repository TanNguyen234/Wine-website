-- Migration V14: Ensure wines and warehouse names support full Unicode Vietnamese characters
IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    ALTER TABLE [dbo].[wines] ALTER COLUMN [name] NVARCHAR(255) NOT NULL;
END;

IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM sys.objects WHERE name = 'UKdbmkeyi4co3vmnwnjxoocd4nh')
    BEGIN
        ALTER TABLE [dbo].[warehouse] DROP CONSTRAINT [UKdbmkeyi4co3vmnwnjxoocd4nh];
    END;
    ALTER TABLE [dbo].[warehouse] ALTER COLUMN [name] NVARCHAR(255) NOT NULL;
    ALTER TABLE [dbo].[warehouse] ALTER COLUMN [location] NVARCHAR(255) NULL;
    IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE name = 'UKdbmkeyi4co3vmnwnjxoocd4nh')
    BEGIN
        ALTER TABLE [dbo].[warehouse] ADD CONSTRAINT [UKdbmkeyi4co3vmnwnjxoocd4nh] UNIQUE ([name]);
    END;
END;
