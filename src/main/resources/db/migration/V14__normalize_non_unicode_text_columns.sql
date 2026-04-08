-- Normalize known user-facing columns that may still be VARCHAR/TEXT in legacy SQL Server databases.
-- The migration is intentionally deterministic and skips indexed columns to reduce ALTER COLUMN risk.

DECLARE @targets TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    column_name SYSNAME NOT NULL,
    target_type NVARCHAR(64) NOT NULL
);

INSERT INTO @targets (schema_name, table_name, column_name, target_type)
VALUES
    (N'dbo', N'categories', N'description', N'NVARCHAR(500)'),
    (N'dbo', N'wines', N'description', N'NVARCHAR(1000)'),
    (N'dbo', N'wines', N'country', N'NVARCHAR(100)'),
    (N'dbo', N'reviews', N'comment', N'NVARCHAR(1000)'),
    (N'dbo', N'orders', N'shipping_full_name', N'NVARCHAR(255)'),
    (N'dbo', N'orders', N'shipping_email', N'NVARCHAR(255)'),
    (N'dbo', N'orders', N'shipping_address', N'NVARCHAR(1000)'),
    (N'dbo', N'orders', N'order_note', N'NVARCHAR(1000)'),
    (N'dbo', N'shipments', N'shipping_name', N'NVARCHAR(255)'),
    (N'dbo', N'shipments', N'shipping_email', N'NVARCHAR(255)'),
    (N'dbo', N'shipments', N'shipping_address', N'NVARCHAR(1000)'),
    (N'dbo', N'shipments', N'admin_override_reason', N'NVARCHAR(500)'),
    (N'dbo', N'shipments', N'failure_note', N'NVARCHAR(500)'),
    (N'dbo', N'shipments', N'status_reason', N'NVARCHAR(500)'),
    (N'dbo', N'shippers', N'name', N'NVARCHAR(255)'),
    (N'dbo', N'inventory_transactions', N'note', N'NVARCHAR(500)'),
    (N'dbo', N'stock_logs', N'message', N'NVARCHAR(500)');

DECLARE @schema_name SYSNAME;
DECLARE @table_name SYSNAME;
DECLARE @column_name SYSNAME;
DECLARE @target_type NVARCHAR(64);
DECLARE @is_nullable BIT;
DECLARE @sql NVARCHAR(MAX);

DECLARE target_cursor CURSOR FAST_FORWARD FOR
SELECT schema_name, table_name, column_name, target_type
FROM @targets;

OPEN target_cursor;

FETCH NEXT FROM target_cursor
INTO @schema_name, @table_name, @column_name, @target_type;

WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.columns c
        INNER JOIN sys.types ty ON ty.user_type_id = c.user_type_id
        INNER JOIN sys.tables t ON t.object_id = c.object_id
        INNER JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE s.name = @schema_name
          AND t.name = @table_name
          AND c.name = @column_name
          AND ty.name IN (N'varchar', N'char', N'text')
          AND c.is_computed = 0
          AND NOT EXISTS (
              SELECT 1
              FROM sys.index_columns ic
              INNER JOIN sys.indexes i ON i.object_id = ic.object_id AND i.index_id = ic.index_id
              WHERE ic.object_id = c.object_id
                AND ic.column_id = c.column_id
                AND i.is_hypothetical = 0
          )
    )
    BEGIN
        SELECT @is_nullable = c.is_nullable
        FROM sys.columns c
        INNER JOIN sys.tables t ON t.object_id = c.object_id
        INNER JOIN sys.schemas s ON s.schema_id = t.schema_id
        WHERE s.name = @schema_name
          AND t.name = @table_name
          AND c.name = @column_name;

        SET @sql = N'ALTER TABLE [' + @schema_name + N'].[' + @table_name + N'] ALTER COLUMN [' + @column_name + N'] '
            + @target_type + N' ' + CASE WHEN @is_nullable = 1 THEN N'NULL' ELSE N'NOT NULL' END + N';';

        EXEC sp_executesql @sql;
    END;

    FETCH NEXT FROM target_cursor
    INTO @schema_name, @table_name, @column_name, @target_type;
END;

CLOSE target_cursor;
DEALLOCATE target_cursor;
