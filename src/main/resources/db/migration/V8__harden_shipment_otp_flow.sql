IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.users', 'email') IS NULL
        ALTER TABLE [dbo].[users] ADD [email] NVARCHAR(255) NULL;

    IF COL_LENGTH('dbo.users', 'email') IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
            FROM sys.indexes
            WHERE object_id = OBJECT_ID(N'[dbo].[users]')
              AND name = N'UX_users_email_not_null'
       )
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX [UX_users_email_not_null]
            ON [dbo].[users]([email])
            WHERE [email] IS NOT NULL;
    END;
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.shipments', 'admin_override') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [admin_override] BIT NOT NULL CONSTRAINT [DF_shipments_admin_override] DEFAULT 0;

    IF COL_LENGTH('dbo.shipments', 'admin_override_reason') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [admin_override_reason] NVARCHAR(500) NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_delivery_status') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_delivery_status] NVARCHAR(20) NOT NULL CONSTRAINT [DF_shipments_otp_delivery_status] DEFAULT 'PENDING';

    IF COL_LENGTH('dbo.shipments', 'otp_sent_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_sent_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_user_id') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [otp_user_id] BIGINT NULL;

    IF COL_LENGTH('dbo.shipments', 'otp_sent_at') IS NOT NULL
       AND COL_LENGTH('dbo.shipments', 'otp_last_sent_at') IS NOT NULL
    BEGIN
        EXEC sp_executesql N'
            UPDATE [dbo].[shipments]
            SET [otp_sent_at] = [otp_last_sent_at]
            WHERE [otp_sent_at] IS NULL
              AND [otp_last_sent_at] IS NOT NULL;
        ';
    END;

    IF COL_LENGTH('dbo.shipments', 'otp_delivery_status') IS NOT NULL
       AND COL_LENGTH('dbo.shipments', 'otp_last_sent_at') IS NOT NULL
    BEGIN
        EXEC sp_executesql N'
            UPDATE [dbo].[shipments]
            SET [otp_delivery_status] = CASE
                WHEN [otp_last_sent_at] IS NOT NULL THEN ''SENT''
                ELSE ''PENDING''
            END
            WHERE [otp_delivery_status] IS NULL
               OR LTRIM(RTRIM([otp_delivery_status])) = '''';
        ';
    END;
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.shipments', 'otp_user_id') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys
        WHERE [name] = N'FK_shipments_otp_user'
   )
BEGIN
    ALTER TABLE [dbo].[shipments]
        ADD CONSTRAINT [FK_shipments_otp_user]
            FOREIGN KEY ([otp_user_id]) REFERENCES [dbo].[users]([id]);
END;

IF OBJECT_ID(N'[dbo].[shipment_otp_audit_logs]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[shipment_otp_audit_logs] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [shipment_id] BIGINT NOT NULL,
        [order_id] BIGINT NULL,
        [otp_user_id] BIGINT NULL,
        [actor_user_id] BIGINT NULL,
        [actor_username] NVARCHAR(255) NULL,
        [action] NVARCHAR(100) NOT NULL,
        [status] NVARCHAR(50) NOT NULL,
        [reason] NVARCHAR(1000) NULL,
        [metadata] NVARCHAR(2000) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_shipment_otp_audit_logs_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[shipment_otp_audit_logs]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys
        WHERE [name] = N'FK_shipment_otp_audit_logs_shipment'
   )
BEGIN
    ALTER TABLE [dbo].[shipment_otp_audit_logs]
        ADD CONSTRAINT [FK_shipment_otp_audit_logs_shipment]
            FOREIGN KEY ([shipment_id]) REFERENCES [dbo].[shipments]([id]);
END;

IF OBJECT_ID(N'[dbo].[shipment_otp_audit_logs]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'[dbo].[shipment_otp_audit_logs]')
          AND [name] = N'IX_shipment_otp_audit_logs_shipment_created_at'
    )
    BEGIN
        CREATE NONCLUSTERED INDEX [IX_shipment_otp_audit_logs_shipment_created_at]
            ON [dbo].[shipment_otp_audit_logs]([shipment_id], [created_at] DESC);
    END;
END;
