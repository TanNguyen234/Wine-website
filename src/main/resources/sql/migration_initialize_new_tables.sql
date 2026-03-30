-- One-time migration to initialize new tables from existing data
-- Safe to execute repeatedly. Changes are applied only once via migration_history marker.

IF OBJECT_ID(N'[dbo].[migration_history]', N'U') IS NULL
BEGIN
    CREATE TABLE migration_history (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        migration_key NVARCHAR(200) NOT NULL UNIQUE,
        description NVARCHAR(1000),
        executed_at DATETIME2 NOT NULL DEFAULT GETDATE()
    );
END
GO

IF EXISTS (SELECT 1 FROM migration_history WHERE migration_key = 'initialize_new_tables_v1')
BEGIN
    PRINT 'Migration initialize_new_tables_v1 already executed. Skipping.';
    RETURN;
END
GO

DECLARE @warehouseId BIGINT;
SELECT TOP 1 @warehouseId = id FROM warehouse WHERE name = 'Main Warehouse';

IF @warehouseId IS NULL
BEGIN
    INSERT INTO warehouse (name, location, active, created_at)
    VALUES ('Main Warehouse', N'Ho Chi Minh City', 1, GETDATE());
    SET @warehouseId = SCOPE_IDENTITY();
END
GO

DECLARE @warehouseId2 BIGINT;
SELECT TOP 1 @warehouseId2 = id FROM warehouse WHERE name = 'Main Warehouse';

INSERT INTO inventory (wine_id, warehouse_id, current_quantity, reserved_quantity, reorder_level, updated_at)
SELECT w.id, @warehouseId2, 50, 0, 10, GETDATE()
FROM wines w
WHERE NOT EXISTS (
    SELECT 1 FROM inventory i WHERE i.wine_id = w.id AND i.warehouse_id = @warehouseId2
);

UPDATE inventory
SET current_quantity = CASE WHEN current_quantity IS NULL OR current_quantity <= 0 THEN 50 ELSE current_quantity END,
    reserved_quantity = CASE WHEN reserved_quantity IS NULL OR reserved_quantity < 0 THEN 0 ELSE reserved_quantity END,
    reorder_level = CASE WHEN reorder_level IS NULL OR reorder_level <= 0 THEN 10 ELSE reorder_level END,
    updated_at = GETDATE()
WHERE warehouse_id = @warehouseId2;
GO

INSERT INTO payments (order_id, method, status, amount, currency, payment_reference, gateway_session_id, gateway_response, created_at, updated_at)
SELECT o.id,
       COALESCE(NULLIF(o.payment_method, ''), 'COD'),
       COALESCE(NULLIF(o.payment_status, ''), 'PENDING'),
       o.total_price,
       'VND',
       CONCAT('MIG-ORDER-', o.id),
       NULL,
       'MIGRATED_FROM_ORDERS',
       GETDATE(),
       GETDATE()
FROM orders o
WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);
GO

INSERT INTO inventory_transactions (inventory_id, product_id, warehouse_id, quantity, operation_type, reference_type, reference_id, user_id, note, created_at)
SELECT i.id,
       i.wine_id,
       i.warehouse_id,
       i.current_quantity,
       'IMPORT',
       'MIGRATION',
       NULL,
       NULL,
       'Initial migration stock',
       GETDATE()
FROM inventory i
WHERE NOT EXISTS (
    SELECT 1
    FROM inventory_transactions t
    WHERE t.inventory_id = i.id
      AND t.reference_type = 'MIGRATION'
);
GO

INSERT INTO migration_history (migration_key, description, executed_at)
VALUES ('initialize_new_tables_v1', 'One-time initialization for warehouse, inventory, payments, and inventory transactions', GETDATE());
GO
