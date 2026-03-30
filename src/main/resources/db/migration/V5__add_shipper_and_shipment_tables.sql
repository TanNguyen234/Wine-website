IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[shippers] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [user_id] BIGINT NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        [phone] NVARCHAR(50) NOT NULL,
        [vehicle_type] NVARCHAR(100) NULL,
        [status] NVARCHAR(50) NOT NULL CONSTRAINT [DF_shippers_status] DEFAULT 'ACTIVE',
        [is_available] BIT NOT NULL CONSTRAINT [DF_shippers_is_available] DEFAULT 0,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_shippers_created_at] DEFAULT GETDATE(),
        [updated_at] DATETIME2 NOT NULL CONSTRAINT [DF_shippers_updated_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE [name] = N'FK_shippers_user')
    BEGIN
        ALTER TABLE [dbo].[shippers]
            ADD CONSTRAINT [FK_shippers_user]
                FOREIGN KEY ([user_id]) REFERENCES [dbo].[users]([id]);
    END;

    IF NOT EXISTS (
           SELECT 1
           FROM sys.key_constraints
           WHERE [type] = 'UQ'
             AND [name] = N'UK_shippers_user'
             AND [parent_object_id] = OBJECT_ID(N'[dbo].[shippers]')
       )
    BEGIN
        ALTER TABLE [dbo].[shippers]
            ADD CONSTRAINT [UK_shippers_user] UNIQUE ([user_id]);
    END;
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[shipments] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [order_id] BIGINT NOT NULL,
        [shipper_id] BIGINT NULL,
        [status] NVARCHAR(50) NOT NULL CONSTRAINT [DF_shipments_status] DEFAULT 'PENDING_ASSIGNMENT',
        [shipping_name] NVARCHAR(255) NULL,
        [shipping_phone] NVARCHAR(50) NULL,
        [shipping_address] NVARCHAR(1000) NULL,
        [otp_code] NVARCHAR(6) NULL,
        [otp_verified] BIT NOT NULL CONSTRAINT [DF_shipments_otp_verified] DEFAULT 0,
        [failure_note] NVARCHAR(500) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_shipments_created_at] DEFAULT GETDATE(),
        [updated_at] DATETIME2 NOT NULL CONSTRAINT [DF_shipments_updated_at] DEFAULT GETDATE(),
        [picked_up_at] DATETIME2 NULL,
        [delivering_at] DATETIME2 NULL,
        [completed_at] DATETIME2 NULL
    );
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE [name] = N'FK_shipments_order')
    BEGIN
        ALTER TABLE [dbo].[shipments]
            ADD CONSTRAINT [FK_shipments_order]
                FOREIGN KEY ([order_id]) REFERENCES [dbo].[orders]([id]);
    END;

    IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE [name] = N'FK_shipments_shipper')
    BEGIN
        ALTER TABLE [dbo].[shipments]
            ADD CONSTRAINT [FK_shipments_shipper]
                FOREIGN KEY ([shipper_id]) REFERENCES [dbo].[shippers]([id]);
    END;

    IF NOT EXISTS (
           SELECT 1
           FROM sys.key_constraints
           WHERE [type] = 'UQ'
             AND [name] = N'UK_shipments_order'
             AND [parent_object_id] = OBJECT_ID(N'[dbo].[shipments]')
       )
    BEGIN
        ALTER TABLE [dbo].[shipments]
            ADD CONSTRAINT [UK_shipments_order] UNIQUE ([order_id]);
    END;
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (
           SELECT 1
           FROM sys.indexes
           WHERE [name] = N'IX_shipments_shipper_status'
             AND [object_id] = OBJECT_ID(N'[dbo].[shipments]')
       )
    BEGIN
        CREATE NONCLUSTERED INDEX [IX_shipments_shipper_status]
            ON [dbo].[shipments]([shipper_id], [status], [created_at]);
    END;
END;
