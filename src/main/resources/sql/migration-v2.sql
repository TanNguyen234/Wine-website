-- StrongWine migration v2
-- Adds inventory, payment, warehouse and order lifecycle fields.

IF COL_LENGTH('wines', 'image_url') IS NULL
    ALTER TABLE wines ADD image_url NVARCHAR(500);
GO

IF COL_LENGTH('wines', 'country') IS NULL
    ALTER TABLE wines ADD country NVARCHAR(100);
GO

IF COL_LENGTH('orders', 'status') IS NULL ALTER TABLE orders ADD status NVARCHAR(50) NOT NULL DEFAULT 'PENDING';
IF COL_LENGTH('orders', 'payment_status') IS NULL ALTER TABLE orders ADD payment_status NVARCHAR(50) NOT NULL DEFAULT 'PENDING';
IF COL_LENGTH('orders', 'payment_method') IS NULL ALTER TABLE orders ADD payment_method NVARCHAR(50);
IF COL_LENGTH('orders', 'shipping_full_name') IS NULL ALTER TABLE orders ADD shipping_full_name NVARCHAR(255);
IF COL_LENGTH('orders', 'shipping_phone') IS NULL ALTER TABLE orders ADD shipping_phone NVARCHAR(50);
IF COL_LENGTH('orders', 'shipping_address') IS NULL ALTER TABLE orders ADD shipping_address NVARCHAR(1000);
IF COL_LENGTH('orders', 'order_note') IS NULL ALTER TABLE orders ADD order_note NVARCHAR(1000);
IF COL_LENGTH('orders', 'payment_reference') IS NULL ALTER TABLE orders ADD payment_reference NVARCHAR(100);
IF COL_LENGTH('orders', 'paid_at') IS NULL ALTER TABLE orders ADD paid_at DATETIME2;
IF COL_LENGTH('orders', 'updated_at') IS NULL ALTER TABLE orders ADD updated_at DATETIME2 NOT NULL DEFAULT GETDATE();
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[warehouse]') AND type in (N'U'))
BEGIN
    CREATE TABLE warehouse (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        name NVARCHAR(255) NOT NULL UNIQUE,
        location NVARCHAR(500),
        active BIT NOT NULL DEFAULT 1,
        created_at DATETIME2 NOT NULL DEFAULT GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[inventory]') AND type in (N'U'))
BEGIN
    CREATE TABLE inventory (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        wine_id BIGINT NOT NULL,
        warehouse_id BIGINT NOT NULL,
        current_quantity INT NOT NULL DEFAULT 0,
        reserved_quantity INT NOT NULL DEFAULT 0,
        reorder_level INT NOT NULL DEFAULT 10,
        updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT uk_inventory_wine_warehouse UNIQUE (wine_id, warehouse_id),
        FOREIGN KEY (wine_id) REFERENCES wines(id) ON DELETE CASCADE,
        FOREIGN KEY (warehouse_id) REFERENCES warehouse(id) ON DELETE CASCADE
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[inventory_transactions]') AND type in (N'U'))
BEGIN
    CREATE TABLE inventory_transactions (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        inventory_id BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        warehouse_id BIGINT NOT NULL,
        quantity INT NOT NULL,
        operation_type NVARCHAR(50) NOT NULL,
        reference_type NVARCHAR(100),
        reference_id BIGINT,
        user_id BIGINT,
        note NVARCHAR(500),
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE,
        FOREIGN KEY (product_id) REFERENCES wines(id) ON DELETE CASCADE,
        FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[stock_logs]') AND type in (N'U'))
BEGIN
    CREATE TABLE stock_logs (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        inventory_id BIGINT NOT NULL,
        available_quantity INT NOT NULL,
        message NVARCHAR(500),
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[payments]') AND type in (N'U'))
BEGIN
    CREATE TABLE payments (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        order_id BIGINT NOT NULL,
        method NVARCHAR(50) NOT NULL,
        status NVARCHAR(50) NOT NULL,
        amount DECIMAL(10,2) NOT NULL,
        currency NVARCHAR(10) NOT NULL,
        payment_reference NVARCHAR(100) NOT NULL UNIQUE,
        gateway_session_id NVARCHAR(150),
        gateway_response NVARCHAR(2000),
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[payment_transactions]') AND type in (N'U'))
BEGIN
    CREATE TABLE payment_transactions (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        payment_id BIGINT NOT NULL,
        transaction_type NVARCHAR(100) NOT NULL,
        status NVARCHAR(50) NOT NULL,
        payload NVARCHAR(2000),
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_orders_status' AND object_id = OBJECT_ID('orders'))
    CREATE INDEX idx_orders_status ON orders(status, payment_status);
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_inventory_product' AND object_id = OBJECT_ID('inventory'))
    CREATE INDEX idx_inventory_product ON inventory(wine_id);
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_inventory_transactions_product' AND object_id = OBJECT_ID('inventory_transactions'))
    CREATE INDEX idx_inventory_transactions_product ON inventory_transactions(product_id, created_at DESC);
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_payments_order_status' AND object_id = OBJECT_ID('payments'))
    CREATE INDEX idx_payments_order_status ON payments(order_id, status);
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_payments_reference' AND object_id = OBJECT_ID('payments'))
    CREATE UNIQUE INDEX idx_payments_reference ON payments(payment_reference);
GO
