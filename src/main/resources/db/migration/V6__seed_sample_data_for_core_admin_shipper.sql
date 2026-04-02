-- Seed sample data for admin/shipper screens and core business tables
-- Idempotent by design: every block checks table existence and NOT EXISTS conditions.

IF OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[categories] ([name], [description], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT N'Vang đỏ', N'Danh mục rượu vang đỏ', GETDATE(), GETDATE(), N'system', N'system', 0
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[categories] WHERE [name] = N'Vang đỏ');

    INSERT INTO [dbo].[categories] ([name], [description], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT N'Vang trắng', N'Danh mục rượu vang trắng', GETDATE(), GETDATE(), N'system', N'system', 0
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[categories] WHERE [name] = N'Vang trắng');

    INSERT INTO [dbo].[categories] ([name], [description], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT N'Vang sủi', N'Danh mục vang sủi', GETDATE(), GETDATE(), N'system', N'system', 0
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[categories] WHERE [name] = N'Vang sủi');
END;

IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    DECLARE @SeedPassword NVARCHAR(255);
    SELECT TOP 1 @SeedPassword = [password] FROM [dbo].[users] ORDER BY [id];

    IF @SeedPassword IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[users] ([username], [password], [role])
        SELECT N'shipper_seed', @SeedPassword, N'SHIPPER'
        WHERE NOT EXISTS (SELECT 1 FROM [dbo].[users] WHERE [username] = N'shipper_seed');
    END;
END;

IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @CategoryRedId BIGINT;
    DECLARE @CategoryWhiteId BIGINT;

    SET @CategoryRedId = (SELECT TOP 1 [id] FROM [dbo].[categories] WHERE [name] = N'Vang đỏ');
    SET @CategoryWhiteId = (SELECT TOP 1 [id] FROM [dbo].[categories] WHERE [name] = N'Vang trắng');

    INSERT INTO [dbo].[wines] ([name], [type], [year], [price], [description], [country], [image_url], [category_id], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT N'Seed Cabernet Reserve', N'Red', 2020, 980000, N'Bản mẫu phục vụ kiểm thử quản trị', N'Chile',
           N'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80',
           @CategoryRedId, GETDATE(), GETDATE(), N'system', N'system', 0
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[wines] WHERE [name] = N'Seed Cabernet Reserve');

    INSERT INTO [dbo].[wines] ([name], [type], [year], [price], [description], [country], [image_url], [category_id], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT N'Seed Sauvignon Blanc', N'White', 2021, 760000, N'Bản mẫu phục vụ kiểm thử đặt hàng và shipment', N'New Zealand',
           N'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80',
           @CategoryWhiteId, GETDATE(), GETDATE(), N'system', N'system', 0
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[wines] WHERE [name] = N'Seed Sauvignon Blanc');
END;

IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[warehouse] ([name], [location], [active], [created_at])
    SELECT N'Main Warehouse', N'Thành phố Hồ Chí Minh', 1, GETDATE()
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[warehouse] WHERE [name] = N'Main Warehouse');
END;

IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @MainWarehouseId BIGINT;
    SELECT TOP 1 @MainWarehouseId = [id] FROM [dbo].[warehouse] WHERE [name] = N'Main Warehouse' ORDER BY [id];

    IF @MainWarehouseId IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory] ([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at], [version])
        SELECT w.[id], @MainWarehouseId, 80, 0, 10, GETDATE(), 0
        FROM [dbo].[wines] w
        WHERE NOT EXISTS (
            SELECT 1 FROM [dbo].[inventory] i
            WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @MainWarehouseId
        );
    END;
END;

IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[shippers] ([user_id], [name], [phone], [vehicle_type], [status], [is_available], [created_at], [updated_at])
        SELECT u.[id], N'Shipper mẫu 1', N'0901000001', N'Xe máy', N'ACTIVE', 1, GETDATE(), GETDATE()
    FROM [dbo].[users] u
    WHERE u.[username] = N'shipper1'
      AND NOT EXISTS (SELECT 1 FROM [dbo].[shippers] s WHERE s.[user_id] = u.[id]);

    INSERT INTO [dbo].[shippers] ([user_id], [name], [phone], [vehicle_type], [status], [is_available], [created_at], [updated_at])
        SELECT u.[id], N'Shipper mẫu 2', N'0901000002', N'Xe máy', N'ACTIVE', 1, GETDATE(), GETDATE()
    FROM [dbo].[users] u
    WHERE u.[username] = N'shipper2'
      AND NOT EXISTS (SELECT 1 FROM [dbo].[shippers] s WHERE s.[user_id] = u.[id]);

    INSERT INTO [dbo].[shippers] ([user_id], [name], [phone], [vehicle_type], [status], [is_available], [created_at], [updated_at])
        SELECT TOP 1 u.[id], N'Shipper dự phòng', N'0901000009', N'Xe máy', N'ACTIVE', 1, GETDATE(), GETDATE()
    FROM [dbo].[users] u
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[shippers] s WHERE s.[user_id] = u.[id])
    ORDER BY u.[id];
END;

IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @CustomerId BIGINT;
    DECLARE @WinePaidNoShipmentId BIGINT;
    DECLARE @WineAssignedShipmentId BIGINT;
    DECLARE @WinePaidNoShipmentPrice DECIMAL(10,2);
    DECLARE @WineAssignedShipmentPrice DECIMAL(10,2);

    SELECT TOP 1 @CustomerId = [id] FROM [dbo].[users] WHERE [username] = N'demo' ORDER BY [id];
    IF @CustomerId IS NULL
        SELECT TOP 1 @CustomerId = [id] FROM [dbo].[users] WHERE [role] = N'USER' ORDER BY [id];
    IF @CustomerId IS NULL
        SELECT TOP 1 @CustomerId = [id] FROM [dbo].[users] ORDER BY [id];

    SELECT TOP 1 @WinePaidNoShipmentId = [id], @WinePaidNoShipmentPrice = [price]
    FROM [dbo].[wines]
    WHERE [deleted] = 0
    ORDER BY [id];

    SELECT TOP 1 @WineAssignedShipmentId = [id], @WineAssignedShipmentPrice = [price]
    FROM [dbo].[wines]
    WHERE [deleted] = 0 AND [id] <> @WinePaidNoShipmentId
    ORDER BY [id];

    IF @CustomerId IS NOT NULL AND @WinePaidNoShipmentId IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[orders]
            ([user_id], [order_date], [total_price], [status], [payment_status], [payment_method],
             [shipping_full_name], [shipping_phone], [shipping_address], [order_note], [payment_reference], [paid_at], [updated_at])
         SELECT @CustomerId, DATEADD(DAY, -2, GETDATE()), ISNULL(@WinePaidNoShipmentPrice, 980000), N'PAID', N'SUCCESS', N'COD',
                             N'Khách Demo', N'0909990001', N'1 Nguyễn Huệ, Quận 1, TP.HCM',
                         N'Đơn PAID chưa tạo shipment để kiểm thử tạo từ trang quản trị', N'PAY-SEED-0001', DATEADD(DAY, -2, GETDATE()), DATEADD(DAY, -2, GETDATE())
         WHERE NOT EXISTS (SELECT 1 FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0001');
    END;

    IF @CustomerId IS NOT NULL AND @WineAssignedShipmentId IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[orders]
            ([user_id], [order_date], [total_price], [status], [payment_status], [payment_method],
             [shipping_full_name], [shipping_phone], [shipping_address], [order_note], [payment_reference], [paid_at], [updated_at])
         SELECT @CustomerId, DATEADD(DAY, -1, GETDATE()), ISNULL(@WineAssignedShipmentPrice, 760000), N'PAID', N'SUCCESS', N'COD',
                             N'Khách Demo 2', N'0909990002', N'2 Lê Lợi, Quận 1, TP.HCM',
                         N'Đơn PAID đã có shipment để kiểm thử bảng điều khiển shipper', N'PAY-SEED-0002', DATEADD(DAY, -1, GETDATE()), DATEADD(DAY, -1, GETDATE())
         WHERE NOT EXISTS (SELECT 1 FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0002');
    END;
END;

IF OBJECT_ID(N'[dbo].[order_items]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @OrderNoShipmentId BIGINT;
    DECLARE @OrderWithShipmentId BIGINT;
    DECLARE @Wine1Id BIGINT;
    DECLARE @Wine2Id BIGINT;
    DECLARE @Wine1Price DECIMAL(10,2);
    DECLARE @Wine2Price DECIMAL(10,2);

    SELECT TOP 1 @OrderNoShipmentId = [id] FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0001';
    SELECT TOP 1 @OrderWithShipmentId = [id] FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0002';

    SELECT TOP 1 @Wine1Id = [id], @Wine1Price = [price] FROM [dbo].[wines] WHERE [deleted] = 0 ORDER BY [id];
    SELECT TOP 1 @Wine2Id = [id], @Wine2Price = [price] FROM [dbo].[wines] WHERE [deleted] = 0 AND [id] <> @Wine1Id ORDER BY [id];

    IF @OrderNoShipmentId IS NOT NULL AND @Wine1Id IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[order_items] ([order_id], [wine_id], [quantity], [price])
        SELECT @OrderNoShipmentId, @Wine1Id, 1, ISNULL(@Wine1Price, 980000)
        WHERE NOT EXISTS (
            SELECT 1 FROM [dbo].[order_items]
            WHERE [order_id] = @OrderNoShipmentId AND [wine_id] = @Wine1Id
        );
    END;

    IF @OrderWithShipmentId IS NOT NULL AND @Wine2Id IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[order_items] ([order_id], [wine_id], [quantity], [price])
        SELECT @OrderWithShipmentId, @Wine2Id, 1, ISNULL(@Wine2Price, 760000)
        WHERE NOT EXISTS (
            SELECT 1 FROM [dbo].[order_items]
            WHERE [order_id] = @OrderWithShipmentId AND [wine_id] = @Wine2Id
        );
    END;
END;

IF OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    DECLARE @OrderNoShipmentForPayment BIGINT;
    DECLARE @OrderWithShipmentForPayment BIGINT;

    SELECT TOP 1 @OrderNoShipmentForPayment = [id] FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0001';
    SELECT TOP 1 @OrderWithShipmentForPayment = [id] FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0002';

    IF @OrderNoShipmentForPayment IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[payments]
            ([order_id], [method], [status], [amount], [currency], [payment_reference], [gateway_session_id], [gateway_response], [created_at], [updated_at])
        SELECT o.[id], N'COD', N'SUCCESS', o.[total_price], N'VND', N'PAY-SEED-0001', NULL, N'SEED_SUCCESS', DATEADD(DAY, -2, GETDATE()), DATEADD(DAY, -2, GETDATE())
        FROM [dbo].[orders] o
                WHERE o.[id] = @OrderNoShipmentForPayment
                    AND NOT EXISTS (SELECT 1 FROM [dbo].[payments] WHERE [payment_reference] = N'PAY-SEED-0001');
    END;

    IF @OrderWithShipmentForPayment IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[payments]
            ([order_id], [method], [status], [amount], [currency], [payment_reference], [gateway_session_id], [gateway_response], [created_at], [updated_at])
        SELECT o.[id], N'COD', N'SUCCESS', o.[total_price], N'VND', N'PAY-SEED-0002', NULL, N'SEED_SUCCESS', DATEADD(DAY, -1, GETDATE()), DATEADD(DAY, -1, GETDATE())
        FROM [dbo].[orders] o
                WHERE o.[id] = @OrderWithShipmentForPayment
                    AND NOT EXISTS (SELECT 1 FROM [dbo].[payments] WHERE [payment_reference] = N'PAY-SEED-0002');
    END;
END;

IF OBJECT_ID(N'[dbo].[payment_transactions]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[payment_transactions] ([payment_id], [transaction_type], [status], [payload], [created_at])
    SELECT p.[id], N'SEED_PAYMENT', N'SUCCESS', N'SEED_PAYLOAD_1', DATEADD(DAY, -2, GETDATE())
    FROM [dbo].[payments] p
    WHERE p.[payment_reference] = N'PAY-SEED-0001'
      AND NOT EXISTS (
          SELECT 1 FROM [dbo].[payment_transactions] t
          WHERE t.[payment_id] = p.[id] AND t.[payload] = N'SEED_PAYLOAD_1'
      );

    INSERT INTO [dbo].[payment_transactions] ([payment_id], [transaction_type], [status], [payload], [created_at])
    SELECT p.[id], N'SEED_PAYMENT', N'SUCCESS', N'SEED_PAYLOAD_2', DATEADD(DAY, -1, GETDATE())
    FROM [dbo].[payments] p
    WHERE p.[payment_reference] = N'PAY-SEED-0002'
      AND NOT EXISTS (
          SELECT 1 FROM [dbo].[payment_transactions] t
          WHERE t.[payment_id] = p.[id] AND t.[payload] = N'SEED_PAYLOAD_2'
      );
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    DECLARE @ShipmentOrderId BIGINT;
    DECLARE @SeedShipperId BIGINT;

    SELECT TOP 1 @ShipmentOrderId = [id] FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SEED-0002';
    SELECT TOP 1 @SeedShipperId = [id] FROM [dbo].[shippers] WHERE [status] = N'ACTIVE' ORDER BY [id];

    IF @ShipmentOrderId IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[shipments]
            ([order_id], [shipper_id], [status], [shipping_name], [shipping_phone], [shipping_address], [otp_code], [otp_verified], [failure_note], [created_at], [updated_at], [picked_up_at], [delivering_at], [completed_at])
        SELECT @ShipmentOrderId, @SeedShipperId, N'ASSIGNED', N'Khách Demo 2', N'0909990002', N'2 Lê Lợi, Quận 1, TP.HCM',
               N'123456', 0, NULL, DATEADD(HOUR, -20, GETDATE()), DATEADD(HOUR, -20, GETDATE()), NULL, NULL, NULL
        WHERE NOT EXISTS (SELECT 1 FROM [dbo].[shipments] WHERE [order_id] = @ShipmentOrderId);
    END;
END;

IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @ReviewUserId BIGINT;
    DECLARE @ReviewWineId BIGINT;

    SELECT TOP 1 @ReviewUserId = [id] FROM [dbo].[users] WHERE [username] = N'demo' ORDER BY [id];
    IF @ReviewUserId IS NULL
        SELECT TOP 1 @ReviewUserId = [id] FROM [dbo].[users] ORDER BY [id];

    SELECT TOP 1 @ReviewWineId = [id] FROM [dbo].[wines] WHERE [deleted] = 0 ORDER BY [id];

    IF @ReviewUserId IS NOT NULL AND @ReviewWineId IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[reviews] ([wine_id], [user_id], [rating], [comment], [created_at])
        SELECT @ReviewWineId, @ReviewUserId, 5, N'Đánh giá mẫu phục vụ hiển thị giao diện', DATEADD(DAY, -1, GETDATE())
        WHERE NOT EXISTS (
            SELECT 1 FROM [dbo].[reviews]
            WHERE [wine_id] = @ReviewWineId AND [user_id] = @ReviewUserId
        );
    END;
END;
