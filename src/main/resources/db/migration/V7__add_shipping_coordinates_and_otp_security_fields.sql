IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.orders', 'shipping_latitude') IS NULL
        ALTER TABLE [dbo].[orders] ADD [shipping_latitude] FLOAT NULL;

    IF COL_LENGTH('dbo.orders', 'shipping_longitude') IS NULL
        ALTER TABLE [dbo].[orders] ADD [shipping_longitude] FLOAT NULL;
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.shipments', 'shipping_latitude') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [shipping_latitude] FLOAT NULL;

    IF COL_LENGTH('dbo.shipments', 'shipping_longitude') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [shipping_longitude] FLOAT NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_created_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_created_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_expires_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_expires_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_attempt_count') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_attempt_count] INT NOT NULL CONSTRAINT [DF_shipments_otp_attempt_count] DEFAULT 0;

    IF COL_LENGTH('dbo.shipments', 'otp_locked_until') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_locked_until] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_last_sent_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_last_sent_at] DATETIME2 NULL;
END;

IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.orders', 'shipping_latitude') IS NOT NULL
       AND COL_LENGTH('dbo.orders', 'shipping_longitude') IS NOT NULL
       AND COL_LENGTH('dbo.shipments', 'shipping_latitude') IS NOT NULL
       AND COL_LENGTH('dbo.shipments', 'shipping_longitude') IS NOT NULL
    BEGIN
        EXEC sp_executesql N'
            UPDATE s
            SET s.shipping_latitude = o.shipping_latitude,
                s.shipping_longitude = o.shipping_longitude
            FROM [dbo].[shipments] s
            INNER JOIN [dbo].[orders] o ON o.id = s.order_id
            WHERE s.shipping_latitude IS NULL
              AND s.shipping_longitude IS NULL
              AND o.shipping_latitude IS NOT NULL
              AND o.shipping_longitude IS NOT NULL;
        ';
    END;
END;
