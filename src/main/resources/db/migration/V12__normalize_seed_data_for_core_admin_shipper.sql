-- Normalize seeded business data to realistic values without mutating historical migration checksums.
-- This migration is idempotent and safe for environments where V6 has already been applied.

IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM [dbo].[users] WHERE [username] = N'shipper_seed')
       AND NOT EXISTS (SELECT 1 FROM [dbo].[users] WHERE [username] = N'shipper.ops.hcm')
    BEGIN
        UPDATE [dbo].[users]
        SET [username] = N'shipper.ops.hcm'
        WHERE [username] = N'shipper_seed';
    END;
END;

IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    UPDATE [dbo].[wines]
    SET [name] = N'Cabernet Reserva Colchagua',
        [description] = N'Vang do dam vi phu hop tiec toi va bit tet',
        [updated_at] = GETDATE(),
        [updated_by] = N'system'
    WHERE [name] = N'Seed Cabernet Reserve';

    UPDATE [dbo].[wines]
    SET [name] = N'Sauvignon Blanc Marlborough',
        [description] = N'Vang trang tuoi mat dung tot voi hai san',
        [updated_at] = GETDATE(),
        [updated_by] = N'system'
    WHERE [name] = N'Seed Sauvignon Blanc';
END;

IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM [dbo].[warehouse] WHERE [name] = N'Main Warehouse')
       AND NOT EXISTS (SELECT 1 FROM [dbo].[warehouse] WHERE [name] = N'Kho Tong TP.HCM')
    BEGIN
        UPDATE [dbo].[warehouse]
        SET [name] = N'Kho Tong TP.HCM',
            [location] = N'Thanh pho Ho Chi Minh'
        WHERE [name] = N'Main Warehouse';
    END;
END;

IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
BEGIN
    UPDATE [dbo].[shippers]
    SET [name] = N'Tran Minh Hung',
        [phone] = N'0909123456',
        [vehicle_type] = N'Xe may',
        [updated_at] = GETDATE()
    WHERE [name] IN (N'Shipper mau 1', N'Shipper mẫu 1')
       OR [phone] = N'0901000001';

    UPDATE [dbo].[shippers]
    SET [name] = N'Pham Thi Linh',
        [phone] = N'0932555789',
        [vehicle_type] = N'Xe may',
        [updated_at] = GETDATE()
    WHERE [name] IN (N'Shipper mau 2', N'Shipper mẫu 2')
       OR [phone] = N'0901000002';

    UPDATE [dbo].[shippers]
    SET [name] = N'Le Quoc Thang',
        [phone] = N'0918444222',
        [vehicle_type] = N'Xe may',
        [updated_at] = GETDATE()
    WHERE [name] IN (N'Shipper du phong', N'Shipper dự phòng')
       OR [phone] = N'0901000009';
END;

IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    UPDATE [dbo].[orders]
    SET [shipping_full_name] = N'Nguyen Thi Thanh Huong',
        [shipping_phone] = N'0908765432',
        [shipping_address] = N'1 Nguyen Hue, Quan 1, TP.HCM',
        [order_note] = N'Giao trong gio hanh chinh, lien he truoc khi giao',
        [payment_reference] = N'PAY-ORD-202604-0001',
        [updated_at] = GETDATE()
    WHERE [payment_reference] = N'PAY-SEED-0001';

    UPDATE [dbo].[orders]
    SET [shipping_full_name] = N'Le Van Phong',
        [shipping_phone] = N'0935200111',
        [shipping_address] = N'2 Le Loi, Quan 1, TP.HCM',
        [order_note] = N'Khach uu tien giao truoc 19h, goi truoc khi den',
        [payment_reference] = N'PAY-ORD-202604-0002',
        [updated_at] = GETDATE()
    WHERE [payment_reference] = N'PAY-SEED-0002';
END;

IF OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
BEGIN
    UPDATE [dbo].[payments]
    SET [payment_reference] = N'PAY-ORD-202604-0001',
        [gateway_response] = N'COD_CONFIRMED',
        [updated_at] = GETDATE()
    WHERE [payment_reference] = N'PAY-SEED-0001';

    UPDATE [dbo].[payments]
    SET [payment_reference] = N'PAY-ORD-202604-0002',
        [gateway_response] = N'COD_CONFIRMED',
        [updated_at] = GETDATE()
    WHERE [payment_reference] = N'PAY-SEED-0002';
END;

IF OBJECT_ID(N'[dbo].[payment_transactions]', N'U') IS NOT NULL
BEGIN
    UPDATE [dbo].[payment_transactions]
    SET [transaction_type] = N'COD_PAYMENT',
        [payload] = N'TXN-202604-0001'
    WHERE [payload] = N'SEED_PAYLOAD_1';

    UPDATE [dbo].[payment_transactions]
    SET [transaction_type] = N'COD_PAYMENT',
        [payload] = N'TXN-202604-0002'
    WHERE [payload] = N'SEED_PAYLOAD_2';
END;

IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    UPDATE s
    SET s.[shipping_name] = N'Le Van Phong',
        s.[shipping_phone] = N'0935200111',
        s.[shipping_address] = N'2 Le Loi, Quan 1, TP.HCM',
        s.[updated_at] = GETDATE()
    FROM [dbo].[shipments] s
    INNER JOIN [dbo].[orders] o ON o.[id] = s.[order_id]
    WHERE o.[payment_reference] IN (N'PAY-SEED-0002', N'PAY-ORD-202604-0002');
END;

IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NOT NULL
BEGIN
    UPDATE [dbo].[reviews]
    SET [comment] = N'Huong vi can bang, hau vi em va giao hang dung hen.'
    WHERE [comment] IN (
        N'Danh gia mau phuc vu hien thi giao dien',
        N'Đánh giá mẫu phục vụ hiển thị giao diện'
    );
END;
