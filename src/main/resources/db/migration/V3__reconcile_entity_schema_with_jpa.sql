-- Reconcile SQL Server schema with current JPA entity mappings
-- Safe/idempotent: only adds missing objects, adds missing columns, and widens selected column sizes.

-- 1) Auditable columns used by Category/Wine via Auditable base class
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

    IF COL_LENGTH('dbo.wines', 'deleted') IS NULL
        ALTER TABLE [dbo].[wines] ADD [deleted] BIT NOT NULL CONSTRAINT [DF_wines_deleted] DEFAULT 0;
END;

IF OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.categories', 'created_by') IS NULL
        ALTER TABLE [dbo].[categories] ADD [created_by] NVARCHAR(255) NULL;

    IF COL_LENGTH('dbo.categories', 'updated_by') IS NULL
        ALTER TABLE [dbo].[categories] ADD [updated_by] NVARCHAR(255) NULL;

    IF COL_LENGTH('dbo.categories', 'created_at') IS NULL
        ALTER TABLE [dbo].[categories] ADD [created_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.categories', 'updated_at') IS NULL
        ALTER TABLE [dbo].[categories] ADD [updated_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.categories', 'deleted') IS NULL
        ALTER TABLE [dbo].[categories] ADD [deleted] BIT NOT NULL CONSTRAINT [DF_categories_deleted] DEFAULT 0;
END;

-- 2) Warehouse entity uses table [warehouse] (singular)
IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[warehouse] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [name] NVARCHAR(255) NOT NULL,
        [location] NVARCHAR(500) NULL,
        [active] BIT NOT NULL CONSTRAINT [DF_warehouse_active] DEFAULT 1,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_warehouse_created_at] DEFAULT GETDATE()
    );
END;

-- If old table [warehouses] exists, copy rows into [warehouse] without deleting anything.
IF OBJECT_ID(N'[dbo].[warehouses]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM sys.identity_columns WHERE object_id = OBJECT_ID(N'[dbo].[warehouse]'))
    BEGIN
        SET IDENTITY_INSERT [dbo].[warehouse] ON;

        INSERT INTO [dbo].[warehouse] ([id], [name], [location], [active], [created_at])
        SELECT w.[id],
               w.[name],
               CAST(w.[location] AS NVARCHAR(500)),
               ISNULL(w.[active], 1),
               GETDATE()
        FROM [dbo].[warehouses] w
        WHERE NOT EXISTS (
            SELECT 1
            FROM [dbo].[warehouse] x
            WHERE x.[id] = w.[id]
        );

        SET IDENTITY_INSERT [dbo].[warehouse] OFF;
    END;
END;

IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.warehouse', 'location') IS NULL
        ALTER TABLE [dbo].[warehouse] ADD [location] NVARCHAR(500) NULL;

    IF COL_LENGTH('dbo.warehouse', 'active') IS NULL
        ALTER TABLE [dbo].[warehouse] ADD [active] BIT NOT NULL CONSTRAINT [DF_warehouse_active_2] DEFAULT 1;

    IF COL_LENGTH('dbo.warehouse', 'created_at') IS NULL
        ALTER TABLE [dbo].[warehouse] ADD [created_at] DATETIME2 NULL;

    IF COL_LENGTH('dbo.warehouse', 'name') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM sys.indexes
           WHERE object_id = OBJECT_ID(N'[dbo].[warehouse]')
             AND name = N'UX_warehouse_name'
       )
       AND NOT EXISTS (
           SELECT [name]
           FROM [dbo].[warehouse]
           GROUP BY [name]
           HAVING COUNT(1) > 1
       )
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX [UX_warehouse_name]
            ON [dbo].[warehouse]([name]);
    END;
END;

-- 3) Inventory table/fk reconciliation
IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[inventory] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [wine_id] BIGINT NOT NULL,
        [warehouse_id] BIGINT NOT NULL,
        [current_quantity] INT NOT NULL CONSTRAINT [DF_inventory_current_quantity] DEFAULT 0,
        [reserved_quantity] INT NOT NULL CONSTRAINT [DF_inventory_reserved_quantity] DEFAULT 0,
        [reorder_level] INT NOT NULL CONSTRAINT [DF_inventory_reorder_level] DEFAULT 10,
        [updated_at] DATETIME2 NOT NULL CONSTRAINT [DF_inventory_updated_at] DEFAULT GETDATE(),
        [version] BIGINT NULL,
        CONSTRAINT [UK_inventory_wine_warehouse] UNIQUE ([wine_id], [warehouse_id])
    );
END;

IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.inventory', 'version') IS NULL
        ALTER TABLE [dbo].[inventory] ADD [version] BIGINT NULL;

    IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_inventory_wine_entity'
       )
    BEGIN
        ALTER TABLE [dbo].[inventory]
            ADD CONSTRAINT [FK_inventory_wine_entity]
                FOREIGN KEY ([wine_id]) REFERENCES [dbo].[wines]([id]);
    END;

    IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_inventory_warehouse_entity'
       )
    BEGIN
        ALTER TABLE [dbo].[inventory]
            ADD CONSTRAINT [FK_inventory_warehouse_entity]
                FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouse]([id]);
    END;
END;

-- 4) Reviews table required by Review entity
IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[reviews] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [wine_id] BIGINT NOT NULL,
        [user_id] BIGINT NOT NULL,
        [rating] INT NOT NULL,
        [comment] NVARCHAR(1000) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_reviews_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_reviews_wine')
    BEGIN
        ALTER TABLE [dbo].[reviews]
            ADD CONSTRAINT [FK_reviews_wine]
                FOREIGN KEY ([wine_id]) REFERENCES [dbo].[wines]([id]);
    END;

    IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_reviews_user')
    BEGIN
        ALTER TABLE [dbo].[reviews]
            ADD CONSTRAINT [FK_reviews_user]
                FOREIGN KEY ([user_id]) REFERENCES [dbo].[users]([id]);
    END;
END;

-- 5) Inventory transaction table required by InventoryTransaction entity
IF OBJECT_ID(N'[dbo].[inventory_transactions]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[inventory_transactions] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [inventory_id] BIGINT NOT NULL,
        [product_id] BIGINT NOT NULL,
        [warehouse_id] BIGINT NOT NULL,
        [quantity] INT NOT NULL,
        [operation_type] NVARCHAR(255) NOT NULL,
        [reference_type] NVARCHAR(100) NULL,
        [reference_id] BIGINT NULL,
        [user_id] BIGINT NULL,
        [note] NVARCHAR(500) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_inventory_transactions_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[inventory_transactions]', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_inventory_transactions_inventory')
    BEGIN
        ALTER TABLE [dbo].[inventory_transactions]
            ADD CONSTRAINT [FK_inventory_transactions_inventory]
                FOREIGN KEY ([inventory_id]) REFERENCES [dbo].[inventory]([id]);
    END;

    IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_inventory_transactions_wine')
    BEGIN
        ALTER TABLE [dbo].[inventory_transactions]
            ADD CONSTRAINT [FK_inventory_transactions_wine]
                FOREIGN KEY ([product_id]) REFERENCES [dbo].[wines]([id]);
    END;

    IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_inventory_transactions_warehouse')
    BEGIN
        ALTER TABLE [dbo].[inventory_transactions]
            ADD CONSTRAINT [FK_inventory_transactions_warehouse]
                FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouse]([id]);
    END;
END;

-- 6) Stock log table required by StockLog entity
IF OBJECT_ID(N'[dbo].[stock_logs]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[stock_logs] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [inventory_id] BIGINT NOT NULL,
        [available_quantity] INT NOT NULL,
        [message] NVARCHAR(500) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_stock_logs_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[stock_logs]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_stock_logs_inventory')
BEGIN
    ALTER TABLE [dbo].[stock_logs]
        ADD CONSTRAINT [FK_stock_logs_inventory]
            FOREIGN KEY ([inventory_id]) REFERENCES [dbo].[inventory]([id]);
END;

-- 7) Payment transaction table required by PaymentTransaction entity
IF OBJECT_ID(N'[dbo].[payment_transactions]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[payment_transactions] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [payment_id] BIGINT NOT NULL,
        [transaction_type] NVARCHAR(100) NOT NULL,
        [status] NVARCHAR(50) NOT NULL,
        [payload] NVARCHAR(2000) NULL,
        [created_at] DATETIME2 NOT NULL CONSTRAINT [DF_payment_transactions_created_at] DEFAULT GETDATE()
    );
END;

IF OBJECT_ID(N'[dbo].[payment_transactions]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_payment_transactions_payment')
BEGIN
    ALTER TABLE [dbo].[payment_transactions]
        ADD CONSTRAINT [FK_payment_transactions_payment]
            FOREIGN KEY ([payment_id]) REFERENCES [dbo].[payments]([id]);
END;

-- 8) Widen selected payment columns to match entity max lengths (safe, non-destructive)
IF OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'[dbo].[payments]')
          AND c.name = N'gateway_session_id'
          AND c.max_length > 0
          AND c.max_length < 300
    )
    BEGIN
        ALTER TABLE [dbo].[payments] ALTER COLUMN [gateway_session_id] NVARCHAR(150) NULL;
    END;

    IF EXISTS (
        SELECT 1
        FROM sys.columns c
        WHERE c.object_id = OBJECT_ID(N'[dbo].[payments]')
          AND c.name = N'gateway_response'
          AND c.max_length > 0
          AND c.max_length < 4000
    )
    BEGIN
        ALTER TABLE [dbo].[payments] ALTER COLUMN [gateway_response] NVARCHAR(2000) NULL;
    END;
END;
