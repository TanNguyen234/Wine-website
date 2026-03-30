-- Sample Data for StrongWine Application (full seed)
-- Safe to run multiple times (idempotent)

-- Password hash used for demo users (BCrypt for plain text: password)
DECLARE @pwd NVARCHAR(255) = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
GO

/* 1) roles */
IF OBJECT_ID(N'[dbo].[roles]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[roles] WHERE [name] = 'ADMIN')
        INSERT INTO [dbo].[roles]([name]) VALUES ('ADMIN');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[roles] WHERE [name] = 'USER')
        INSERT INTO [dbo].[roles]([name]) VALUES ('USER');
END
GO

/* 2) users */
IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[users] WHERE [username] = 'admin')
        INSERT INTO [dbo].[users]([username], [password], [role])
        VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[users] WHERE [username] = 'user1')
        INSERT INTO [dbo].[users]([username], [password], [role])
        VALUES ('user1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[users] WHERE [username] = 'user2')
        INSERT INTO [dbo].[users]([username], [password], [role])
        VALUES ('user2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER');
END
GO

/* 3) user_roles */
IF OBJECT_ID(N'[dbo].[user_roles]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[roles]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    DECLARE @adminId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = 'admin');
    DECLARE @user1Id BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = 'user1');
    DECLARE @user2Id BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = 'user2');
    DECLARE @adminRoleId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[roles] WHERE [name] = 'ADMIN');
    DECLARE @userRoleId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[roles] WHERE [name] = 'USER');

    IF @adminId IS NOT NULL AND @adminRoleId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[user_roles] WHERE [user_id] = @adminId AND [role_id] = @adminRoleId)
        INSERT INTO [dbo].[user_roles]([user_id], [role_id]) VALUES (@adminId, @adminRoleId);

    IF @user1Id IS NOT NULL AND @userRoleId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[user_roles] WHERE [user_id] = @user1Id AND [role_id] = @userRoleId)
        INSERT INTO [dbo].[user_roles]([user_id], [role_id]) VALUES (@user1Id, @userRoleId);

    IF @user2Id IS NOT NULL AND @userRoleId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[user_roles] WHERE [user_id] = @user2Id AND [role_id] = @userRoleId)
        INSERT INTO [dbo].[user_roles]([user_id], [role_id]) VALUES (@user2Id, @userRoleId);
END
GO

/* 4) categories */
IF OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[categories] WHERE [name] = 'Red Wine')
        INSERT INTO [dbo].[categories]([name], [description], [created_by], [updated_by])
        VALUES ('Red Wine', 'Full-bodied and medium-bodied red wines', 'system', 'system');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[categories] WHERE [name] = 'White Wine')
        INSERT INTO [dbo].[categories]([name], [description], [created_by], [updated_by])
        VALUES ('White Wine', 'Crisp and aromatic white wines', 'system', 'system');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[categories] WHERE [name] = 'Sparkling Wine')
        INSERT INTO [dbo].[categories]([name], [description], [created_by], [updated_by])
        VALUES ('Sparkling Wine', 'Champagne and sparkling selections', 'system', 'system');
END
GO

/* 5) wines */
IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[wines] WHERE [name] = N'Château Margaux')
        INSERT INTO [dbo].[wines]([name], [type], [country], [year], [price], [description], [image_url], [created_by], [updated_by])
        VALUES (N'Château Margaux', 'Red', 'France', 2018, 899.99, N'A prestigious Bordeaux wine with rich blackcurrant and cedar notes.', '/images/wines/chateau-margaux.jpg', 'system', 'system');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[wines] WHERE [name] = N'Dom Pérignon')
        INSERT INTO [dbo].[wines]([name], [type], [country], [year], [price], [description], [image_url], [created_by], [updated_by])
        VALUES (N'Dom Pérignon', 'Sparkling', 'France', 2012, 199.99, N'Luxury champagne with citrus, white flowers, and brioche.', '/images/wines/dom-perignon.jpg', 'system', 'system');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[wines] WHERE [name] = N'Sancerre Blanc')
        INSERT INTO [dbo].[wines]([name], [type], [country], [year], [price], [description], [image_url], [created_by], [updated_by])
        VALUES (N'Sancerre Blanc', 'White', 'France', 2020, 29.99, N'Crisp white wine with citrus and mineral notes.', '/images/wines/sancerre-blanc.jpg', 'system', 'system');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[wines] WHERE [name] = N'Cabernet Sauvignon Chile')
        INSERT INTO [dbo].[wines]([name], [type], [country], [year], [price], [description], [image_url], [created_by], [updated_by])
        VALUES (N'Cabernet Sauvignon Chile', 'Red', 'Chile', 2019, 55.99, N'Full-bodied red wine with dark fruit and spice.', '/images/wines/cabernet-chile.jpg', 'system', 'system');
END
GO

/* Optional mapping wines -> categories when column category_id exists */
IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.wines', 'category_id') IS NOT NULL
BEGIN
    UPDATE w
    SET w.[category_id] = c.[id]
    FROM [dbo].[wines] w
    JOIN [dbo].[categories] c ON c.[name] = 'Red Wine'
    WHERE w.[name] IN (N'Château Margaux', N'Cabernet Sauvignon Chile')
      AND (w.[category_id] IS NULL OR w.[category_id] <> c.[id]);

    UPDATE w
    SET w.[category_id] = c.[id]
    FROM [dbo].[wines] w
    JOIN [dbo].[categories] c ON c.[name] = 'White Wine'
    WHERE w.[name] = N'Sancerre Blanc'
      AND (w.[category_id] IS NULL OR w.[category_id] <> c.[id]);

    UPDATE w
    SET w.[category_id] = c.[id]
    FROM [dbo].[wines] w
    JOIN [dbo].[categories] c ON c.[name] = 'Sparkling Wine'
    WHERE w.[name] = N'Dom Pérignon'
      AND (w.[category_id] IS NULL OR w.[category_id] <> c.[id]);
END
GO

/* 6) warehouse / warehouses */
IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[warehouse] WHERE [name] = 'Main Warehouse')
        INSERT INTO [dbo].[warehouse]([name], [location], [active], [created_at])
        VALUES ('Main Warehouse', N'Ho Chi Minh City', 1, GETDATE());
END
GO

IF OBJECT_ID(N'[dbo].[warehouses]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[warehouses] WHERE [name] = 'Main Warehouse')
        INSERT INTO [dbo].[warehouses]([name], [location], [active])
        VALUES ('Main Warehouse', N'Ho Chi Minh City', 1);
END
GO

/* 7) inventory */
IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
BEGIN
    DECLARE @warehouseId BIGINT = (
        SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] = 'Main Warehouse'
    );

    IF @warehouseId IS NULL AND OBJECT_ID(N'[dbo].[warehouses]', N'U') IS NOT NULL
        SELECT TOP 1 @warehouseId = [id] FROM [dbo].[warehouses] WHERE [name] = 'Main Warehouse';

    IF @warehouseId IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory]([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at])
        SELECT w.[id], @warehouseId, 100, 5, 20, GETDATE()
        FROM [dbo].[wines] w
        WHERE NOT EXISTS (
            SELECT 1
            FROM [dbo].[inventory] i
            WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @warehouseId
        );
    END
END
GO

/* 8) carts */
IF OBJECT_ID(N'[dbo].[carts]', N'U') IS NOT NULL
BEGIN
    DECLARE @cartUserId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = 'user1');
    IF @cartUserId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[carts] WHERE [user_id] = @cartUserId)
    BEGIN
        INSERT INTO [dbo].[carts]([user_id]) VALUES (@cartUserId);
    END
END
GO

/* 9) cart_items */
IF OBJECT_ID(N'[dbo].[cart_items]', N'U') IS NOT NULL
BEGIN
    DECLARE @cartId BIGINT = (
        SELECT TOP 1 c.[id]
        FROM [dbo].[carts] c
        JOIN [dbo].[users] u ON u.[id] = c.[user_id]
        WHERE u.[username] = 'user1'
    );

    DECLARE @wineId1 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[wines] WHERE [name] = N'Château Margaux');
    DECLARE @wineId2 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[wines] WHERE [name] = N'Sancerre Blanc');

    IF @cartId IS NOT NULL AND @wineId1 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[cart_items] WHERE [cart_id] = @cartId AND [wine_id] = @wineId1)
        INSERT INTO [dbo].[cart_items]([cart_id], [wine_id], [quantity]) VALUES (@cartId, @wineId1, 1);

    IF @cartId IS NOT NULL AND @wineId2 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[cart_items] WHERE [cart_id] = @cartId AND [wine_id] = @wineId2)
        INSERT INTO [dbo].[cart_items]([cart_id], [wine_id], [quantity]) VALUES (@cartId, @wineId2, 2);
END
GO

/* 10) orders */
IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    DECLARE @orderUserId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = 'user1');

    IF @orderUserId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[orders] WHERE [payment_reference] = 'ORD-DEMO-1001')
    BEGIN
        INSERT INTO [dbo].[orders](
            [user_id], [order_date], [total_price], [status], [payment_status], [payment_method],
            [shipping_full_name], [shipping_phone], [shipping_address], [order_note], [payment_reference], [paid_at], [updated_at]
        )
        VALUES (
            @orderUserId, GETDATE(), 929.98, 'CONFIRMED', 'PAID', 'STRIPE',
            N'Nguyen Van A', '0900000001', N'123 Le Loi, District 1, Ho Chi Minh City',
            N'Demo order for testing', 'ORD-DEMO-1001', GETDATE(), GETDATE()
        );
    END
END
GO

/* 11) order_items */
IF OBJECT_ID(N'[dbo].[order_items]', N'U') IS NOT NULL
BEGIN
    DECLARE @orderId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[orders] WHERE [payment_reference] = 'ORD-DEMO-1001');
    DECLARE @orderWine1 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[wines] WHERE [name] = N'Château Margaux');
    DECLARE @orderWine2 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[wines] WHERE [name] = N'Sancerre Blanc');

    IF @orderId IS NOT NULL AND @orderWine1 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[order_items] WHERE [order_id] = @orderId AND [wine_id] = @orderWine1)
        INSERT INTO [dbo].[order_items]([order_id], [wine_id], [quantity], [price]) VALUES (@orderId, @orderWine1, 1, 899.99);

    IF @orderId IS NOT NULL AND @orderWine2 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[order_items] WHERE [order_id] = @orderId AND [wine_id] = @orderWine2)
        INSERT INTO [dbo].[order_items]([order_id], [wine_id], [quantity], [price]) VALUES (@orderId, @orderWine2, 1, 29.99);
END
GO

/* 12) reviews */
IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NOT NULL
BEGIN
    DECLARE @reviewUserId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = 'user2');
    DECLARE @reviewWine1 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[wines] WHERE [name] = N'Château Margaux');
    DECLARE @reviewWine2 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[wines] WHERE [name] = N'Dom Pérignon');

    IF @reviewUserId IS NOT NULL AND @reviewWine1 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[reviews] WHERE [wine_id] = @reviewWine1 AND [user_id] = @reviewUserId)
        INSERT INTO [dbo].[reviews]([wine_id], [user_id], [rating], [comment], [created_at])
        VALUES (@reviewWine1, @reviewUserId, 5, N'Excellent wine, rich taste and long finish.', GETDATE());

    IF @reviewUserId IS NOT NULL AND @reviewWine2 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[reviews] WHERE [wine_id] = @reviewWine2 AND [user_id] = @reviewUserId)
        INSERT INTO [dbo].[reviews]([wine_id], [user_id], [rating], [comment], [created_at])
        VALUES (@reviewWine2, @reviewUserId, 5, N'Perfect for celebrations. Highly recommended.', GETDATE());
END
GO

/* 13) payments */
IF OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
BEGIN
    DECLARE @payOrderId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[orders] WHERE [payment_reference] = 'ORD-DEMO-1001');

    IF @payOrderId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[payments] WHERE [payment_reference] = 'PAY-DEMO-1001')
    BEGIN
        INSERT INTO [dbo].[payments](
            [order_id], [amount], [currency], [method], [status], [payment_reference], [gateway_session_id], [gateway_response], [created_at], [updated_at]
        )
        VALUES (
            @payOrderId, 929.98, 'VND', 'STRIPE', 'SUCCESS', 'PAY-DEMO-1001', 'sess_demo_1001', 'Payment captured successfully', GETDATE(), GETDATE()
        );
    END
END
GO

/* 14) payment_transactions */
IF OBJECT_ID(N'[dbo].[payment_transactions]', N'U') IS NOT NULL
BEGIN
    DECLARE @paymentId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[payments] WHERE [payment_reference] = 'PAY-DEMO-1001');

    IF @paymentId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[payment_transactions] WHERE [payment_id] = @paymentId AND [transaction_type] = 'CHARGE')
    BEGIN
        INSERT INTO [dbo].[payment_transactions]([payment_id], [transaction_type], [status], [payload], [created_at])
        VALUES (@paymentId, 'CHARGE', 'SUCCESS', N'{"gateway":"stripe","status":"captured"}', GETDATE());
    END
END
GO

/* 15) inventory_transactions */
IF OBJECT_ID(N'[dbo].[inventory_transactions]', N'U') IS NOT NULL
BEGIN
    DECLARE @invId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[inventory]);
    DECLARE @invWineId BIGINT = (SELECT TOP 1 [wine_id] FROM [dbo].[inventory] WHERE [id] = @invId);
    DECLARE @invWhId BIGINT = (SELECT TOP 1 [warehouse_id] FROM [dbo].[inventory] WHERE [id] = @invId);

    IF @invId IS NOT NULL AND @invWineId IS NOT NULL AND @invWhId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[inventory_transactions] WHERE [inventory_id] = @invId AND [reference_type] = 'SEED')
    BEGIN
        INSERT INTO [dbo].[inventory_transactions](
            [inventory_id], [product_id], [warehouse_id], [quantity], [operation_type], [reference_type], [reference_id], [user_id], [note], [created_at]
        )
        VALUES (
            @invId, @invWineId, @invWhId, 100, 'IMPORT', 'SEED', NULL, NULL, N'Initial stock seeded by sample-data.sql', GETDATE()
        );
    END
END
GO

/* 16) stock_logs */
IF OBJECT_ID(N'[dbo].[stock_logs]', N'U') IS NOT NULL
BEGIN
    DECLARE @stockInvId BIGINT = (SELECT TOP 1 [id] FROM [dbo].[inventory]);
    DECLARE @stockQty INT = (SELECT TOP 1 [current_quantity] - [reserved_quantity] FROM [dbo].[inventory] WHERE [id] = @stockInvId);

    IF @stockInvId IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[stock_logs] WHERE [inventory_id] = @stockInvId AND [message] = 'Initial sample stock log')
    BEGIN
        INSERT INTO [dbo].[stock_logs]([inventory_id], [available_quantity], [message], [created_at])
        VALUES (@stockInvId, ISNULL(@stockQty, 0), 'Initial sample stock log', GETDATE());
    END
END
GO

