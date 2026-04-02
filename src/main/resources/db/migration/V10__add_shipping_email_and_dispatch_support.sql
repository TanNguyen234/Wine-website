IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.orders', 'shipping_email') IS NULL
        ALTER TABLE [dbo].[orders] ADD [shipping_email] NVARCHAR(255) NULL;
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.shipments', 'shipping_email') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [shipping_email] NVARCHAR(255) NULL;
END;

IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
     AND COL_LENGTH('dbo.orders', 'shipping_email') IS NOT NULL
BEGIN
        EXEC sp_executesql N'
                UPDATE o
                SET o.[shipping_email] = LOWER(LTRIM(RTRIM(u.[email])))
                FROM [dbo].[orders] o
                INNER JOIN [dbo].[users] u ON u.[id] = o.[user_id]
                WHERE (o.[shipping_email] IS NULL OR LTRIM(RTRIM(o.[shipping_email])) = '''')
                    AND u.[email] IS NOT NULL
                    AND LTRIM(RTRIM(u.[email])) <> '''';
        ';
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
     AND COL_LENGTH('dbo.shipments', 'shipping_email') IS NOT NULL
     AND COL_LENGTH('dbo.orders', 'shipping_email') IS NOT NULL
BEGIN
        EXEC sp_executesql N'
                UPDATE s
                SET s.[shipping_email] = LOWER(LTRIM(RTRIM(o.[shipping_email])))
                FROM [dbo].[shipments] s
                INNER JOIN [dbo].[orders] o ON o.[id] = s.[order_id]
                WHERE (s.[shipping_email] IS NULL OR LTRIM(RTRIM(s.[shipping_email])) = '''')
                    AND o.[shipping_email] IS NOT NULL
                    AND LTRIM(RTRIM(o.[shipping_email])) <> '''';
        ';
END;
