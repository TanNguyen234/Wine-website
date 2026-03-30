-- schema.sql

-- 1. roles
IF OBJECT_ID('dbo.roles', 'U') IS NULL
    CREATE TABLE [dbo].[roles] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [name] NVARCHAR(50) NOT NULL UNIQUE
    );

-- 2. users
IF OBJECT_ID('dbo.users', 'U') IS NULL
    CREATE TABLE [dbo].[users] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [username] NVARCHAR(255) NOT NULL UNIQUE,
        [password] NVARCHAR(255) NOT NULL,
        [role] NVARCHAR(50), 
        [email] NVARCHAR(255),
        [full_name] NVARCHAR(255),
        [phone] NVARCHAR(50),
        [created_at] DATETIME2 DEFAULT GETDATE(),
        [updated_at] DATETIME2 DEFAULT GETDATE(),
        [is_deleted] BIT NOT NULL DEFAULT 0
    );

-- Ensure audit fields and soft delete on users if table already existed
IF COL_LENGTH('users', 'is_deleted') IS NULL ALTER TABLE [dbo].[users] ADD [is_deleted] BIT NOT NULL DEFAULT 0;
IF COL_LENGTH('users', 'created_at') IS NULL ALTER TABLE [dbo].[users] ADD [created_at] DATETIME2 DEFAULT GETDATE();
IF COL_LENGTH('users', 'updated_at') IS NULL ALTER TABLE [dbo].[users] ADD [updated_at] DATETIME2 DEFAULT GETDATE();

-- 3. user_roles
IF OBJECT_ID('dbo.user_roles', 'U') IS NULL
    CREATE TABLE [dbo].[user_roles] (
        [user_id] BIGINT NOT NULL,
        [role_id] BIGINT NOT NULL,
        PRIMARY KEY ([user_id], [role_id]),
        CONSTRAINT [fk_ur_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[users] ([id]),
        CONSTRAINT [fk_ur_role] FOREIGN KEY ([role_id]) REFERENCES [dbo].[roles] ([id])
    );

-- 4. categories
IF OBJECT_ID('dbo.categories', 'U') IS NULL
    CREATE TABLE [dbo].[categories] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [name] NVARCHAR(255) NOT NULL UNIQUE,
        [description] NVARCHAR(500),
        [created_at] DATETIME2 DEFAULT GETDATE(),
        [updated_at] DATETIME2 DEFAULT GETDATE(),
        [created_by] NVARCHAR(255),
        [updated_by] NVARCHAR(255),
        [deleted] BIT NOT NULL DEFAULT 0
    );

-- 5. products (wines)
IF OBJECT_ID('dbo.wines', 'U') IS NULL
    CREATE TABLE [dbo].[wines] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [name] NVARCHAR(255) NOT NULL,
        [type] NVARCHAR(255) NOT NULL,
        [year] INT NOT NULL,
        [price] DECIMAL(10,2) NOT NULL,
        [description] NVARCHAR(1000),
        [country] NVARCHAR(100),
        [image_url] NVARCHAR(500),
        [category_id] BIGINT,
        [created_at] DATETIME2 DEFAULT GETDATE(),
        [updated_at] DATETIME2 DEFAULT GETDATE(),
        [created_by] NVARCHAR(255),
        [updated_by] NVARCHAR(255),
        [deleted] BIT NOT NULL DEFAULT 0,
        CONSTRAINT [fk_wines_category] FOREIGN KEY ([category_id]) REFERENCES [dbo].[categories] ([id])
    );

IF COL_LENGTH('wines', 'category_id') IS NULL 
    ALTER TABLE [dbo].[wines] ADD [category_id] BIGINT;

IF COL_LENGTH('wines', 'category_id') IS NOT NULL 
AND NOT EXISTS (
    SELECT * FROM sys.foreign_keys 
    WHERE name = 'fk_wines_category'
)
    ALTER TABLE [dbo].[wines] 
    ADD CONSTRAINT [fk_wines_category] 
    FOREIGN KEY ([category_id]) REFERENCES [dbo].[categories] ([id]);
IF COL_LENGTH('wines', 'deleted') IS NULL ALTER TABLE [dbo].[wines] ADD [deleted] BIT NOT NULL DEFAULT 0;
IF COL_LENGTH('wines', 'created_at') IS NULL ALTER TABLE [dbo].[wines] ADD [created_at] DATETIME2 DEFAULT GETDATE();
IF COL_LENGTH('wines', 'updated_at') IS NULL ALTER TABLE [dbo].[wines] ADD [updated_at] DATETIME2 DEFAULT GETDATE();

-- 6. warehouses (required for inventory)
IF OBJECT_ID('dbo.warehouses', 'U') IS NULL
    CREATE TABLE [dbo].[warehouses] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [name] NVARCHAR(255) NOT NULL,
        [location] NVARCHAR(255),
        [active] BIT NOT NULL DEFAULT 1
    );

-- 7. inventory
IF OBJECT_ID('dbo.inventory', 'U') IS NULL
    CREATE TABLE [dbo].[inventory] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [wine_id] BIGINT NOT NULL,
        [warehouse_id] BIGINT NOT NULL,
        [current_quantity] INT NOT NULL DEFAULT 0,
        [reserved_quantity] INT NOT NULL DEFAULT 0,
        [reorder_level] INT NOT NULL DEFAULT 10,
        [updated_at] DATETIME2 DEFAULT GETDATE(),
        [version] BIGINT DEFAULT 0,
        CONSTRAINT [uk_inventory_wine_wh] UNIQUE ([wine_id], [warehouse_id]),
        CONSTRAINT [fk_inv_wine] FOREIGN KEY ([wine_id]) REFERENCES [dbo].[wines] ([id]),
        CONSTRAINT [fk_inv_warehouse] FOREIGN KEY ([warehouse_id]) REFERENCES [dbo].[warehouses] ([id])
    );

IF COL_LENGTH('inventory', 'version') IS NULL ALTER TABLE [dbo].[inventory] ADD [version] BIGINT DEFAULT 0;

-- 8. carts
IF OBJECT_ID('dbo.carts', 'U') IS NULL
    CREATE TABLE [dbo].[carts] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [user_id] BIGINT UNIQUE,
        CONSTRAINT [fk_cart_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[users] ([id])
    );

-- 9. cart_items
IF OBJECT_ID('dbo.cart_items', 'U') IS NULL
    CREATE TABLE [dbo].[cart_items] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [cart_id] BIGINT NOT NULL,
        [wine_id] BIGINT NOT NULL,
        [quantity] INT NOT NULL,
        CONSTRAINT [fk_cartitem_cart] FOREIGN KEY ([cart_id]) REFERENCES [dbo].[carts] ([id]),
        CONSTRAINT [fk_cartitem_wine] FOREIGN KEY ([wine_id]) REFERENCES [dbo].[wines] ([id])
    );

-- 10. orders
IF OBJECT_ID('dbo.orders', 'U') IS NULL
    CREATE TABLE [dbo].[orders] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [user_id] BIGINT NOT NULL,
        [total_price] DECIMAL(10,2) NOT NULL,
        [status] NVARCHAR(50) NOT NULL,
        [payment_status] NVARCHAR(50) NOT NULL,
        [payment_method] NVARCHAR(50),
        [shipping_full_name] NVARCHAR(255),
        [shipping_phone] NVARCHAR(50),
        [shipping_address] NVARCHAR(1000),
        [order_note] NVARCHAR(1000),
        [payment_reference] NVARCHAR(100),
        [paid_at] DATETIME2,
        [order_date] DATETIME2 DEFAULT GETDATE(),
        [updated_at] DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT [fk_order_user] FOREIGN KEY ([user_id]) REFERENCES [dbo].[users] ([id])
    );

-- 11. order_items
IF OBJECT_ID('dbo.order_items', 'U') IS NULL
    CREATE TABLE [dbo].[order_items] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [order_id] BIGINT NOT NULL,
        [wine_id] BIGINT NOT NULL,
        [quantity] INT NOT NULL,
        [price] DECIMAL(10,2) NOT NULL,
        CONSTRAINT [fk_orderitem_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[orders] ([id]),
        CONSTRAINT [fk_orderitem_wine] FOREIGN KEY ([wine_id]) REFERENCES [dbo].[wines] ([id])
    );

-- 12. payments
IF OBJECT_ID('dbo.payments', 'U') IS NULL
    CREATE TABLE [dbo].[payments] (
        [id] BIGINT IDENTITY(1,1) PRIMARY KEY,
        [order_id] BIGINT NOT NULL,
        [amount] DECIMAL(10,2) NOT NULL,
        [currency] NVARCHAR(10) NOT NULL,
        [method] NVARCHAR(50) NOT NULL,
        [status] NVARCHAR(50) NOT NULL,
        [payment_reference] NVARCHAR(100) UNIQUE,
        [gateway_session_id] NVARCHAR(255),
        [gateway_response] NVARCHAR(1000),
        [created_at] DATETIME2 DEFAULT GETDATE(),
        [updated_at] DATETIME2 DEFAULT GETDATE(),
        CONSTRAINT [fk_payment_order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[orders] ([id])
    );

-- Indexes for performance
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_wine_deleted' AND object_id = OBJECT_ID('wines'))
    CREATE NONCLUSTERED INDEX idx_wine_deleted ON [dbo].[wines]([deleted]);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_order_user' AND object_id = OBJECT_ID('orders'))
    CREATE NONCLUSTERED INDEX idx_order_user ON [dbo].[orders]([user_id]);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_cart_user' AND object_id = OBJECT_ID('carts'))
    CREATE UNIQUE NONCLUSTERED INDEX idx_cart_user ON [dbo].[carts]([user_id]) WHERE [user_id] IS NOT NULL;
