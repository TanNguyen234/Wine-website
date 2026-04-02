IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.shipments', 'estimated_delivery_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [estimated_delivery_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'promised_window_start') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [promised_window_start] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'promised_window_end') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [promised_window_end] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'delivery_attempt_count') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [delivery_attempt_count] INT NOT NULL CONSTRAINT [DF_shipments_delivery_attempt_count] DEFAULT 0;

    IF COL_LENGTH('dbo.shipments', 'last_delivery_attempt_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [last_delivery_attempt_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'next_attempt_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [next_attempt_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'assigned_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [assigned_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'failed_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [failed_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'returned_at') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [returned_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shipments', 'failure_code') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [failure_code] NVARCHAR(50) NULL;

    IF COL_LENGTH('dbo.shipments', 'status_reason') IS NULL
        ALTER TABLE [dbo].[shipments] ADD [status_reason] NVARCHAR(500) NULL;
END;

IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.shippers', 'current_latitude') IS NULL
        ALTER TABLE [dbo].[shippers] ADD [current_latitude] FLOAT NULL;

    IF COL_LENGTH('dbo.shippers', 'current_longitude') IS NULL
        ALTER TABLE [dbo].[shippers] ADD [current_longitude] FLOAT NULL;

    IF COL_LENGTH('dbo.shippers', 'location_updated_at') IS NULL
        ALTER TABLE [dbo].[shippers] ADD [location_updated_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.shippers', 'max_concurrent_shipments') IS NULL
        ALTER TABLE [dbo].[shippers] ADD [max_concurrent_shipments] INT NOT NULL CONSTRAINT [DF_shippers_max_concurrent_shipments] DEFAULT 1;

    IF COL_LENGTH('dbo.shippers', 'active_shipment_count') IS NULL
        ALTER TABLE [dbo].[shippers] ADD [active_shipment_count] INT NOT NULL CONSTRAINT [DF_shippers_active_shipment_count] DEFAULT 0;

    IF COL_LENGTH('dbo.shippers', 'last_assignment_at') IS NULL
        ALTER TABLE [dbo].[shippers] ADD [last_assignment_at] DATETIME2 NULL;
END;

IF OBJECT_ID(N'[dbo].[shipment_status_history]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[shipment_status_history] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [shipment_id] BIGINT NOT NULL,
        [from_status] NVARCHAR(50) NULL,
        [to_status] NVARCHAR(50) NOT NULL,
        [reason] NVARCHAR(500) NULL,
        [metadata] NVARCHAR(2000) NULL,
        [actor_user_id] BIGINT NULL,
        [actor_username] NVARCHAR(255) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_shipment_status_history_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[shipment_status_history]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys
        WHERE [name] = N'FK_shipment_status_history_shipment'
   )
BEGIN
    ALTER TABLE [dbo].[shipment_status_history]
        ADD CONSTRAINT [FK_shipment_status_history_shipment]
            FOREIGN KEY ([shipment_id]) REFERENCES [dbo].[shipments]([id]);
END;

IF OBJECT_ID(N'[dbo].[shipment_status_history]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'[dbo].[shipment_status_history]')
          AND [name] = N'IX_shipment_status_history_shipment_created_at'
    )
    BEGIN
        CREATE NONCLUSTERED INDEX [IX_shipment_status_history_shipment_created_at]
            ON [dbo].[shipment_status_history]([shipment_id], [created_at] DESC);
    END;
END;

IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    EXEC sp_executesql N'
        UPDATE sh
        SET sh.[active_shipment_count] = q.[active_count],
            sh.[is_available] = CASE
                WHEN sh.[status] = ''ACTIVE'' AND q.[active_count] < CASE WHEN sh.[max_concurrent_shipments] IS NULL OR sh.[max_concurrent_shipments] < 1 THEN 1 ELSE sh.[max_concurrent_shipments] END THEN 1
                ELSE 0
            END
        FROM [dbo].[shippers] sh
        OUTER APPLY (
            SELECT COUNT(1) AS [active_count]
            FROM [dbo].[shipments] s
            WHERE s.[shipper_id] = sh.[id]
              AND s.[status] IN (''ASSIGNED'', ''PICKED_UP'', ''DELIVERING'')
        ) q;
    ';
END;
