-- V17: StrongWine V3 Domain Foundation (purely additive, idempotent T-SQL without GO)

-- 1. Widen Hibernate-generated enum CHECK constraints to include V3 statuses
DECLARE @dropSql NVARCHAR(MAX) = N'';

SELECT @dropSql = @dropSql
    + N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(cc.parent_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(cc.parent_object_id))
    + N' DROP CONSTRAINT ' + QUOTENAME(cc.name) + N'; '
FROM sys.check_constraints cc
JOIN sys.columns col ON cc.parent_object_id = col.object_id AND cc.parent_column_id = col.column_id
WHERE (
    (OBJECT_NAME(cc.parent_object_id) = N'orders' AND col.name = N'status' AND cc.definition NOT LIKE N'%RETURN_REQUESTED%')
    OR (OBJECT_NAME(cc.parent_object_id) = N'orders' AND col.name = N'payment_status' AND cc.definition NOT LIKE N'%REFUND_REQUIRED%')
    OR (OBJECT_NAME(cc.parent_object_id) = N'payments' AND col.name = N'status' AND cc.definition NOT LIKE N'%REFUND_REQUIRED%')
);

IF LEN(@dropSql) > 0
BEGIN
    EXEC sp_executesql @dropSql;
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_orders_status_v3' AND parent_object_id = OBJECT_ID(N'dbo.orders'))
BEGIN
    EXEC sp_executesql N'ALTER TABLE dbo.orders ADD CONSTRAINT CK_orders_status_v3 CHECK (status IN (''PENDING'', ''PAID'', ''CANCELLED'', ''RETURN_REQUESTED'', ''RETURNED''));';
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_orders_payment_status_v3' AND parent_object_id = OBJECT_ID(N'dbo.orders'))
BEGIN
    EXEC sp_executesql N'ALTER TABLE dbo.orders ADD CONSTRAINT CK_orders_payment_status_v3 CHECK (payment_status IN (''PENDING'', ''SUCCESS'', ''FAILED'', ''CANCELLED'', ''REFUND_REQUIRED'', ''REFUNDED''));';
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_payments_status_v3' AND parent_object_id = OBJECT_ID(N'dbo.payments'))
BEGIN
    EXEC sp_executesql N'ALTER TABLE dbo.payments ADD CONSTRAINT CK_payments_status_v3 CHECK (status IN (''PENDING'', ''SUCCESS'', ''FAILED'', ''CANCELLED'', ''REFUND_REQUIRED'', ''REFUNDED''));';
END;

-- 2. Alter warehouse: add geolocation coordinates & default flag, backfill seeded warehouses
IF COL_LENGTH(N'dbo.warehouse', N'latitude') IS NULL
BEGIN
    ALTER TABLE dbo.warehouse ADD latitude FLOAT NULL;
END;

IF COL_LENGTH(N'dbo.warehouse', N'longitude') IS NULL
BEGIN
    ALTER TABLE dbo.warehouse ADD longitude FLOAT NULL;
END;

IF COL_LENGTH(N'dbo.warehouse', N'is_default') IS NULL
BEGIN
    ALTER TABLE dbo.warehouse ADD is_default BIT NOT NULL CONSTRAINT [DF_warehouse_is_default] DEFAULT 0;
END;

EXEC sp_executesql N'
    UPDATE dbo.warehouse
    SET latitude = 10.7769, longitude = 106.7009
    WHERE (latitude IS NULL OR longitude IS NULL)
      AND (
          LOWER(name) LIKE N''%main%''
          OR LOWER(name) LIKE N''%hcm%''
          OR LOWER(name) LIKE N''%ho chi minh%''
          OR LOWER(ISNULL(location, N'''')) LIKE N''%ho chi minh%''
      );

    UPDATE dbo.warehouse
    SET latitude = 21.0285, longitude = 105.8542
    WHERE (latitude IS NULL OR longitude IS NULL)
      AND (
          LOWER(name) LIKE N''%ha noi%''
          OR LOWER(name) LIKE N''%hanoi%''
          OR LOWER(name) LIKE N''%hà nội%''
          OR LOWER(ISNULL(location, N'''')) LIKE N''%ha noi%''
          OR LOWER(ISNULL(location, N'''')) LIKE N''%hà nội%''
      );

    UPDATE dbo.warehouse
    SET latitude = 16.0544, longitude = 108.2022
    WHERE (latitude IS NULL OR longitude IS NULL)
      AND (
          LOWER(name) LIKE N''%da nang%''
          OR LOWER(name) LIKE N''%danang%''
          OR LOWER(name) LIKE N''%đà nẵng%''
          OR LOWER(ISNULL(location, N'''')) LIKE N''%da nang%''
          OR LOWER(ISNULL(location, N'''')) LIKE N''%đà nẵng%''
      );

    IF NOT EXISTS (SELECT 1 FROM dbo.warehouse WHERE is_default = 1)
    BEGIN
        UPDATE dbo.warehouse
        SET is_default = 1
        WHERE id = (SELECT TOP 1 id FROM dbo.warehouse WHERE LOWER(name) LIKE N''%main%'' ORDER BY id ASC);

        IF @@ROWCOUNT = 0
        BEGIN
            UPDATE dbo.warehouse
            SET is_default = 1
            WHERE id = (SELECT TOP 1 id FROM dbo.warehouse WHERE active = 1 ORDER BY id ASC);
        END;
    END;
';

-- 3. Alter orders: pricing breakdown, voucher code, allocated warehouse
IF COL_LENGTH(N'dbo.orders', N'subtotal_amount') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD subtotal_amount DECIMAL(18,2) NULL;
END;

IF COL_LENGTH(N'dbo.orders', N'discount_amount') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD discount_amount DECIMAL(18,2) NOT NULL CONSTRAINT [DF_orders_discount_amount] DEFAULT 0;
END;

IF COL_LENGTH(N'dbo.orders', N'shipping_fee') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD shipping_fee DECIMAL(18,2) NOT NULL CONSTRAINT [DF_orders_shipping_fee] DEFAULT 0;
END;

IF COL_LENGTH(N'dbo.orders', N'voucher_code') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD voucher_code NVARCHAR(64) NULL;
END;

IF COL_LENGTH(N'dbo.orders', N'allocated_warehouse_id') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD allocated_warehouse_id BIGINT NULL;
END;

EXEC sp_executesql N'
    UPDATE dbo.orders
    SET subtotal_amount = total_price
    WHERE subtotal_amount IS NULL;
';

IF OBJECT_ID(N'dbo.FK_orders_allocated_warehouse', N'F') IS NULL
   AND COL_LENGTH(N'dbo.orders', N'allocated_warehouse_id') IS NOT NULL
BEGIN
    EXEC sp_executesql N'
        ALTER TABLE dbo.orders
        ADD CONSTRAINT FK_orders_allocated_warehouse
        FOREIGN KEY (allocated_warehouse_id) REFERENCES dbo.warehouse(id);
    ';
END;

-- 4. Alter shipments: encrypted OTP, POD proof URL, delivery time, COD amount & batch link
IF COL_LENGTH(N'dbo.shipments', N'otp_encrypted') IS NULL
BEGIN
    ALTER TABLE dbo.shipments ADD otp_encrypted NVARCHAR(512) NULL;
END;

IF COL_LENGTH(N'dbo.shipments', N'proof_of_delivery_url') IS NULL
BEGIN
    ALTER TABLE dbo.shipments ADD proof_of_delivery_url NVARCHAR(500) NULL;
END;

IF COL_LENGTH(N'dbo.shipments', N'delivered_at') IS NULL
BEGIN
    ALTER TABLE dbo.shipments ADD delivered_at DATETIME2 NULL;
END;

IF COL_LENGTH(N'dbo.shipments', N'cod_collected_amount') IS NULL
BEGIN
    ALTER TABLE dbo.shipments ADD cod_collected_amount DECIMAL(18,2) NULL;
END;

IF COL_LENGTH(N'dbo.shipments', N'cod_batch_id') IS NULL
BEGIN
    ALTER TABLE dbo.shipments ADD cod_batch_id BIGINT NULL;
END;

-- 5. Alter users: safe additive profile fields
IF COL_LENGTH(N'dbo.users', N'full_name') IS NULL
BEGIN
    ALTER TABLE dbo.users ADD full_name NVARCHAR(255) NULL;
END;

IF COL_LENGTH(N'dbo.users', N'phone') IS NULL
BEGIN
    ALTER TABLE dbo.users ADD phone NVARCHAR(50) NULL;
END;

IF COL_LENGTH(N'dbo.users', N'default_address') IS NULL
BEGIN
    ALTER TABLE dbo.users ADD default_address NVARCHAR(500) NULL;
END;

-- 6. New table: user_addresses
IF OBJECT_ID(N'dbo.user_addresses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_addresses (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_user_addresses PRIMARY KEY,
        user_id BIGINT NOT NULL,
        label NVARCHAR(80) NULL,
        recipient_name NVARCHAR(150) NOT NULL,
        phone NVARCHAR(32) NOT NULL,
        address_line NVARCHAR(400) NOT NULL,
        ward NVARCHAR(120) NULL,
        district NVARCHAR(120) NULL,
        province NVARCHAR(120) NOT NULL,
        latitude FLOAT NULL,
        longitude FLOAT NULL,
        is_default BIT NOT NULL CONSTRAINT DF_user_addresses_is_default DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_user_addresses_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_user_addresses_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_user_addresses_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_user_addresses_user' AND object_id = OBJECT_ID(N'dbo.user_addresses'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_user_addresses_user ON dbo.user_addresses (user_id, is_default);';
END;

-- 7. New table: wishlist_items
IF OBJECT_ID(N'dbo.wishlist_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.wishlist_items (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_wishlist_items PRIMARY KEY,
        user_id BIGINT NOT NULL,
        wine_id BIGINT NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_wishlist_items_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_wishlist_items_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_wishlist_items_wine FOREIGN KEY (wine_id) REFERENCES dbo.wines(id),
        CONSTRAINT UQ_wishlist_user_wine UNIQUE (user_id, wine_id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_wishlist_items_user' AND object_id = OBJECT_ID(N'dbo.wishlist_items'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_wishlist_items_user ON dbo.wishlist_items (user_id, created_at DESC);';
END;

-- 8. New table: vouchers
IF OBJECT_ID(N'dbo.vouchers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.vouchers (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_vouchers PRIMARY KEY,
        code NVARCHAR(64) NOT NULL,
        type NVARCHAR(24) NOT NULL,
        value DECIMAL(18,2) NOT NULL,
        max_discount DECIMAL(18,2) NULL,
        min_order_value DECIMAL(18,2) NOT NULL CONSTRAINT DF_vouchers_min_order_value DEFAULT 0,
        usage_limit INT NULL,
        used_count INT NOT NULL CONSTRAINT DF_vouchers_used_count DEFAULT 0,
        starts_at DATETIME2 NULL,
        expires_at DATETIME2 NULL,
        active BIT NOT NULL CONSTRAINT DF_vouchers_active DEFAULT 1,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_vouchers_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_vouchers_code UNIQUE (code)
    );
END;

-- 9. New table: voucher_usages
IF OBJECT_ID(N'dbo.voucher_usages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.voucher_usages (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_voucher_usages PRIMARY KEY,
        voucher_id BIGINT NOT NULL,
        user_id BIGINT NOT NULL,
        order_id BIGINT NOT NULL,
        discount_applied DECIMAL(18,2) NOT NULL,
        used_at DATETIME2 NOT NULL CONSTRAINT DF_voucher_usages_used_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_voucher_usages_voucher FOREIGN KEY (voucher_id) REFERENCES dbo.vouchers(id),
        CONSTRAINT FK_voucher_usages_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_voucher_usages_order FOREIGN KEY (order_id) REFERENCES dbo.orders(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_voucher_usages_voucher_user' AND object_id = OBJECT_ID(N'dbo.voucher_usages'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_voucher_usages_voucher_user ON dbo.voucher_usages (voucher_id, user_id);';
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_voucher_usages_order' AND object_id = OBJECT_ID(N'dbo.voucher_usages'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_voucher_usages_order ON dbo.voucher_usages (order_id);';
END;

-- 10. New table: return_requests
IF OBJECT_ID(N'dbo.return_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.return_requests (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_return_requests PRIMARY KEY,
        order_id BIGINT NOT NULL,
        user_id BIGINT NOT NULL,
        reason NVARCHAR(1000) NOT NULL,
        status NVARCHAR(32) NOT NULL CONSTRAINT DF_return_requests_status DEFAULT 'REQUESTED',
        refund_amount DECIMAL(18,2) NOT NULL,
        admin_note NVARCHAR(1000) NULL,
        resolved_by NVARCHAR(150) NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_return_requests_created_at DEFAULT SYSUTCDATETIME(),
        resolved_at DATETIME2 NULL,
        CONSTRAINT FK_return_requests_order FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
        CONSTRAINT FK_return_requests_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_return_requests_order' AND object_id = OBJECT_ID(N'dbo.return_requests'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_return_requests_order ON dbo.return_requests (order_id);';
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_return_requests_status' AND object_id = OBJECT_ID(N'dbo.return_requests'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_return_requests_status ON dbo.return_requests (status, created_at DESC);';
END;

-- 11. New table: shipper_cod_batches + FK from shipments.cod_batch_id
IF OBJECT_ID(N'dbo.shipper_cod_batches', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.shipper_cod_batches (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_shipper_cod_batches PRIMARY KEY,
        shipper_id BIGINT NOT NULL,
        batch_code NVARCHAR(64) NOT NULL,
        total_expected_amount DECIMAL(18,2) NOT NULL,
        total_submitted_amount DECIMAL(18,2) NULL,
        order_count INT NOT NULL,
        status NVARCHAR(32) NOT NULL CONSTRAINT DF_shipper_cod_batches_status DEFAULT 'PENDING_SETTLEMENT',
        note NVARCHAR(500) NULL,
        submitted_at DATETIME2 NOT NULL CONSTRAINT DF_shipper_cod_batches_submitted_at DEFAULT SYSUTCDATETIME(),
        verified_by NVARCHAR(150) NULL,
        verified_at DATETIME2 NULL,
        CONSTRAINT FK_cod_batches_shipper FOREIGN KEY (shipper_id) REFERENCES dbo.shippers(id),
        CONSTRAINT UQ_shipper_cod_batches_code UNIQUE (batch_code)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_cod_batches_shipper' AND object_id = OBJECT_ID(N'dbo.shipper_cod_batches'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_cod_batches_shipper ON dbo.shipper_cod_batches (shipper_id, submitted_at DESC);';
END;

IF OBJECT_ID(N'dbo.FK_shipments_cod_batch', N'F') IS NULL
   AND COL_LENGTH(N'dbo.shipments', N'cod_batch_id') IS NOT NULL
   AND OBJECT_ID(N'dbo.shipper_cod_batches', N'U') IS NOT NULL
BEGIN
    EXEC sp_executesql N'
        ALTER TABLE dbo.shipments
        ADD CONSTRAINT FK_shipments_cod_batch
        FOREIGN KEY (cod_batch_id) REFERENCES dbo.shipper_cod_batches(id);
    ';
END;

-- 12. New table: admin_audit_logs
IF OBJECT_ID(N'dbo.admin_audit_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.admin_audit_logs (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_admin_audit_logs PRIMARY KEY,
        actor_username NVARCHAR(150) NOT NULL,
        action NVARCHAR(80) NOT NULL,
        target_type NVARCHAR(80) NOT NULL,
        target_id BIGINT NULL,
        before_json NVARCHAR(MAX) NULL,
        after_json NVARCHAR(MAX) NULL,
        ip_address NVARCHAR(64) NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_admin_audit_logs_created_at DEFAULT SYSUTCDATETIME()
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_admin_audit_logs_created' AND object_id = OBJECT_ID(N'dbo.admin_audit_logs'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_admin_audit_logs_created ON dbo.admin_audit_logs (created_at DESC);';
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_admin_audit_logs_target' AND object_id = OBJECT_ID(N'dbo.admin_audit_logs'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_admin_audit_logs_target ON dbo.admin_audit_logs (target_type, target_id);';
END;

-- 13. New table: notification_logs
IF OBJECT_ID(N'dbo.notification_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.notification_logs (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_notification_logs PRIMARY KEY,
        user_id BIGINT NULL,
        target_role NVARCHAR(32) NOT NULL CONSTRAINT DF_notification_logs_target_role DEFAULT 'ADMIN',
        title NVARCHAR(200) NOT NULL,
        message NVARCHAR(1000) NOT NULL,
        link_url NVARCHAR(500) NULL,
        severity NVARCHAR(24) NOT NULL CONSTRAINT DF_notification_logs_severity DEFAULT 'INFO',
        is_read BIT NOT NULL CONSTRAINT DF_notification_logs_is_read DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_notification_logs_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_notification_logs_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_notification_logs_role_read' AND object_id = OBJECT_ID(N'dbo.notification_logs'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_notification_logs_role_read ON dbo.notification_logs (target_role, is_read, created_at DESC);';
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_notification_logs_user_read' AND object_id = OBJECT_ID(N'dbo.notification_logs'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_notification_logs_user_read ON dbo.notification_logs (user_id, is_read, created_at DESC);';
END;

-- 14. New table: sse_event_journal
IF OBJECT_ID(N'dbo.sse_event_journal', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.sse_event_journal (
        id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_sse_event_journal PRIMARY KEY,
        channel NVARCHAR(64) NOT NULL,
        target_id BIGINT NULL,
        event_name NVARCHAR(80) NOT NULL,
        payload_json NVARCHAR(MAX) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_sse_event_journal_created_at DEFAULT SYSUTCDATETIME()
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_sse_event_journal_channel_target_id' AND object_id = OBJECT_ID(N'dbo.sse_event_journal'))
BEGIN
    EXEC sp_executesql N'CREATE INDEX IX_sse_event_journal_channel_target_id ON dbo.sse_event_journal (channel, target_id, id);';
END;
