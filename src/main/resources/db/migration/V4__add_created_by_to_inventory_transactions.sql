IF OBJECT_ID(N'[dbo].[inventory_transactions]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.inventory_transactions', 'created_by') IS NULL
        ALTER TABLE [dbo].[inventory_transactions] ADD [created_by] NVARCHAR(255) NULL;
END;
