-- Sample Data for StrongWine Application (full realistic production seed)
-- Safe to run multiple times (idempotent)

/* 1) roles */
IF OBJECT_ID(N'[dbo].[roles]', N'U') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM [dbo].[roles] WHERE [name] = 'ADMIN')
        INSERT INTO [dbo].[roles]([name]) VALUES ('ADMIN');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[roles] WHERE [name] = 'USER')
        INSERT INTO [dbo].[roles]([name]) VALUES ('USER');

    IF NOT EXISTS (SELECT 1 FROM [dbo].[roles] WHERE [name] = 'SHIPPER')
        INSERT INTO [dbo].[roles]([name]) VALUES ('SHIPPER');
END
GO

/* 2) users */
IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    DECLARE @pwd NVARCHAR(255) = N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

    INSERT INTO [dbo].[users]([username], [password], [role])
    SELECT v.[username], @pwd, v.[role]
    FROM (VALUES
        (N'admin',              N'ADMIN'),
        (N'demo',               N'USER'),
        (N'user1',              N'USER'),
        (N'user2',              N'USER'),
        (N'nguyen.minh.quan',   N'USER'),
        (N'tran.bao.ngoc',      N'USER'),
        (N'le.hoang.nam',       N'USER'),
        (N'pham.khanh.linh',    N'USER'),
        (N'vo.duc.thinh',       N'USER'),
        (N'dang.thu.ha',        N'USER'),
        (N'shipper.ops.hcm',    N'SHIPPER'),
        (N'shipper.q7.hcm',     N'SHIPPER'),
        (N'shipper.hn.ops',     N'SHIPPER')
    ) AS v([username], [role])
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[users] u WHERE u.[username] = v.[username]);
END
GO

/* 3) categories */
IF OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[categories]([name], [description], [created_by], [updated_by])
    SELECT v.[name], v.[description], N'system', N'system'
    FROM (VALUES
        (N'Vang đỏ',   N'Các dòng vang đỏ Bordeaux Grand Cru, Napa Valley, Barolo, Rioja và Chile Reserva đậm vị'),
        (N'Vang trắng', N'Các dòng vang trắng Burgundy Chardonnay, Sancerre, Marlborough Sauvignon Blanc và Mosel Riesling thanh mát'),
        (N'Vang hồng', N'Các dòng vang hồng Côtes de Provence thanh lịch với hương dâu rừng và hoa trắng'),
        (N'Vang sủi',  N'Champagne niên vụ Pháp và Prosecco Superiore DOCG Ý sủi bọt mịn màng')
    ) AS v([name], [description])
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[categories] c WHERE c.[name] = v.[name]);
END
GO

/* 4) wines (24 realistic fine wines) */
IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[wines]([name], [type], [country], [year], [price], [description], [image_url], [created_by], [updated_by])
    SELECT v.[name], v.[type], v.[country], v.[year], v.[price], v.[description], v.[image_url], N'system', N'system'
    FROM (VALUES
        (N'Château Margaux Premier Grand Cru Classé', 'Red', 'France', 2018, 8990000.00, N'Biểu tượng Premier Grand Cru Classé vùng Margaux với hương lý chua đen, hoa tím, gỗ tuyết tùng và hậu vị nhung lụa kéo dài.', 'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80'),
        (N'Château Lafite Rothschild Pauillac', 'Red', 'France', 2017, 14500000.00, N'Tuyệt phẩm Pauillac 1er Grand Cru Classé mang tầng hương phức hợp của xì gà Havana, quả mâm xôi chín, khoáng chì và tannin hoàn mỹ.', 'https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=900&q=80'),
        (N'Opus One Napa Valley Reserve', 'Red', 'USA', 2019, 9850000.00, N'Sự giao thoa đỉnh cao giữa Baron Philippe de Rothschild và Robert Mondavi; đậm đà hương cassis, sô-cô-la đen, espresso và thảo mộc.', 'https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=900&q=80'),
        (N'Penfolds Grange Bin 95 Shiraz', 'Red', 'Australia', 2018, 12900000.00, N'Biểu tượng vang Úc từ những gốc nho Shiraz cổ thụ vùng Barossa; vị mận đen cô đặc, cam thảo, tiêu đen và gỗ sồi Mỹ ủ 20 tháng.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
        (N'Sassicaia Tenuta San Guido Bolgheri', 'Red', 'Italy', 2019, 7600000.00, N'Huyền thoại Super Tuscan từ Cabernet Sauvignon và Cabernet Franc; thanh lịch với hương anh đào đen, hương thảo và vị khoáng biển.', 'https://images.unsplash.com/photo-1584916201218-f4242ceb4809?auto=format&fit=crop&w=900&q=80'),
        (N'Barolo Cannubi Marchesi di Barolo DOCG', 'Red', 'Italy', 2018, 2850000.00, N'Vua của các dòng vang Ý từ giống nho Nebbiolo trên đồi Cannubi; hương hoa hồng khô, nấm truffle trắng và tannin săn chắc.', 'https://images.unsplash.com/photo-1568213816046-0ee1c42bd559?auto=format&fit=crop&w=900&q=80'),
        (N'Almaviva Puente Alto Maipo Valley', 'Red', 'Chile', 2020, 4650000.00, N'Dòng vang Primer Orden cao cấp nhất Chile; sự kết hợp Bordeaux blend cổ điển với thổ nhưỡng chân núi Andes, đậm vị dâu rừng và vani.', 'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80'),
        (N'Cabernet Reserva Colchagua', 'Red', 'Chile', 2020, 980000.00, N'Vang đỏ đậm vị từ thung lũng Colchagua với hương lý chua đen, ớt chuông nướng và hậu vị êm mượt, lý tưởng cho tiệc tối và bít tết.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
        (N'Montes Alpha M Santa Cruz', 'Red', 'Chile', 2019, 2950000.00, N'Dòng vang biểu tượng của nhà Montes; cấu trúc dày dặn, hương quả mọng đen chín mọng, hạt nhục đậu khấu, thuốc lá nhẹ và hậu vị sâu.', 'https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=900&q=80'),
        (N'Catena Zapata Malbec Argentino', 'Red', 'Argentina', 2020, 2790000.00, N'Malbec từ các vườn nho độ cao trên 1.400m tại dãy Andes; sắc tím đậm, hương hoa violet, mận chín, mocha và vị chát mượt như nhung.', 'https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=900&q=80'),
        (N'Marqués de Riscal Gran Reserva Rioja', 'Red', 'Spain', 2016, 1890000.00, N'Vang đỏ Tây Ban Nha ủ 30 tháng trong thùng sồi; hương mận khô, quế, đinh hương, da thuộc mềm và cấu trúc cân bằng cổ điển.', 'https://images.unsplash.com/photo-1584916201218-f4242ceb4809?auto=format&fit=crop&w=900&q=80'),
        (N'Pinot Noir Reserve Willamette Valley', 'Red', 'USA', 2020, 1350000.00, N'Pinot Noir tinh tế với hương anh đào đỏ, dâu rừng, nấm rừng ẩm và độ chua thanh thoát, kết hợp hoàn hảo cùng vịt quay hoặc cá hồi.', 'https://images.unsplash.com/photo-1568213816046-0ee1c42bd559?auto=format&fit=crop&w=900&q=80'),
        (N'Louis Jadot Puligny-Montrachet Blanc', 'White', 'France', 2020, 3150000.00, N'Chardonnay thượng hạng vùng Côte de Beaune; hương hoa keo, táo nướng bơ, hạt dẻ cười và độ khoáng sắc sảo đặc trưng đá vôi.', 'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Sancerre Blanc Les Monts Damnés', 'White', 'France', 2021, 1190000.00, N'Sauvignon Blanc từ sườn đồi đá phấn Kimmeridgian; hương bưởi hồng, chanh vàng, cỏ mới cắt và vị khoáng đá lửa sống động.', 'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Cloudy Bay Sauvignon Blanc', 'White', 'New Zealand', 2022, 1120000.00, N'Chuẩn mực Sauvignon Blanc vùng Marlborough với hương chanh dây, lá lý chua, ổi hồng và vị chua giòn sảng khoái cho tiệc hải sản.', 'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Sauvignon Blanc Marlborough', 'White', 'New Zealand', 2021, 760000.00, N'Vang trắng tươi mát hương trái cây nhiệt đới, bưởi xanh và thảo mộc nhẹ, dùng tuyệt vời với hàu sống, tôm hùm và gỏi hải sản.', 'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Chardonnay Barrel Aged Margaret River', 'White', 'Australia', 2021, 950000.00, N'Chardonnay ủ sồi Pháp mang hương đào trắng, kem bơ, vani và hạt phỉ nướng, hậu vị tròn đầy quyến rũ.', 'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Dr. Loosen Wehlener Sonnenuhr Riesling Spätlese', 'White', 'Germany', 2021, 1080000.00, N'Riesling thu hoạch muộn từ vườn nho đồng hồ mặt trời Mosel; vị ngọt thanh tao cân bằng cùng độ chua sắc nét, hương đào chín và mật ong.', 'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Whispering Angel Caves d''Esclans Rosé', 'Rose', 'France', 2022, 920000.00, N'Vang hồng Provence nổi tiếng thế giới với sắc hồng phấn sang trọng, hương dâu tây trắng, đào tiên, vỏ bưởi và hậu vị khô ráo thanh lịch.', 'https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80'),
        (N'Miraval Côtes de Provence Rosé', 'Rose', 'France', 2022, 890000.00, N'Vang hồng thanh lịch từ điền trang Château Miraval; hương hoa trắng, quả mâm xôi tươi, thảo mộc garrigue và vị khoáng mặn mà.', 'https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80'),
        (N'Dom Pérignon Vintage Brut', 'Sparkling', 'France', 2013, 5490000.00, N'Champagne niên vụ huyền thoại với bọt sủi mịn như tơ, hương bánh mì nướng brioche, hạnh nhân rang, cam bergamot và khoáng chất tinh tế.', 'https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80'),
        (N'Moët & Chandon Impérial Brut Champagne', 'Sparkling', 'France', 2020, 1790000.00, N'Dòng Champagne biểu tượng của Moët & Chandon từ năm 1869; hương táo xanh, cam chanh, hoa trắng và bánh quy bơ thanh lịch.', 'https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80'),
        (N'Veuve Clicquot Yellow Label Brut', 'Sparkling', 'France', 2019, 1950000.00, N'Champagne giàu cấu trúc với tỷ lệ Pinot Noir chủ đạo; hương lê chín, mận vàng, vani và bánh nướng brioche quyến rũ.', 'https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80'),
        (N'Valdo Marca Oro Valdobbiadene Prosecco Superiore DOCG', 'Sparkling', 'Italy', 2022, 680000.00, N'Prosecco Superiore DOCG sủi bọt tươi tắn, hương táo vàng Golden, hoa tử đằng và mật ong rừng nhẹ nhàng, hoàn hảo cho khai vị tiệc.', 'https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80')
    ) AS v([name], [type], [country], [year], [price], [description], [image_url])
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[wines] w WHERE w.[name] = v.[name]);
END
GO

/* 5) Map wines -> categories */
IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
   AND COL_LENGTH('dbo.wines', 'category_id') IS NOT NULL
BEGIN
    UPDATE w SET w.[category_id] = c.[id] FROM [dbo].[wines] w JOIN [dbo].[categories] c ON c.[name] = N'Vang đỏ'   WHERE w.[type] = 'Red'       AND w.[category_id] IS NULL;
    UPDATE w SET w.[category_id] = c.[id] FROM [dbo].[wines] w JOIN [dbo].[categories] c ON c.[name] = N'Vang trắng' WHERE w.[type] = 'White'     AND w.[category_id] IS NULL;
    UPDATE w SET w.[category_id] = c.[id] FROM [dbo].[wines] w JOIN [dbo].[categories] c ON c.[name] = N'Vang hồng' WHERE w.[type] = 'Rose'      AND w.[category_id] IS NULL;
    UPDATE w SET w.[category_id] = c.[id] FROM [dbo].[wines] w JOIN [dbo].[categories] c ON c.[name] = N'Vang sủi'  WHERE w.[type] = 'Sparkling' AND w.[category_id] IS NULL;
END
GO

/* 6) warehouse */
IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[warehouse]([name], [location], [active], [created_at])
    SELECT v.[name], v.[location], 1, GETDATE()
    FROM (VALUES
        (N'Kho Tong TP.HCM',               N'12 Nguyễn Huệ, Quận 1, Thành phố Hồ Chí Minh'),
        (N'Kho Hầm Rượu Quận 7 - TP.HCM',  N'88 Nguyễn Đức Cảnh, Phú Mỹ Hưng, Quận 7, TP.HCM'),
        (N'Kho Trung Tâm Hoàn Kiếm - HN',  N'24 Tràng Tiền, Quận Hoàn Kiếm, Thủ đô Hà Nội'),
        (N'Kho Hải Châu - Đà Nẵng',        N'106 Bạch Đằng, Quận Hải Châu, Thành phố Đà Nẵng')
    ) AS v([name], [location])
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[warehouse] w WHERE w.[name] = v.[name]);
END
GO

/* 7) inventory */
IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
BEGIN
    DECLARE @wh1 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] IN (N'Kho Tong TP.HCM', N'Main Warehouse') ORDER BY [id]);
    DECLARE @wh2 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] = N'Kho Hầm Rượu Quận 7 - TP.HCM');

    IF @wh1 IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory]([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at])
        SELECT w.[id], @wh1, 80, 2, 15, GETDATE()
        FROM [dbo].[wines] w
        WHERE NOT EXISTS (SELECT 1 FROM [dbo].[inventory] i WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @wh1);
    END

    IF @wh2 IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory]([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at])
        SELECT w.[id], @wh2, 45, 1, 12, GETDATE()
        FROM [dbo].[wines] w
        WHERE NOT EXISTS (SELECT 1 FROM [dbo].[inventory] i WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @wh2);
    END
END
GO

/* 8) orders, order_items, payments, shipments & reviews */
IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    DECLARE @uQuan BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'nguyen.minh.quan');
    IF @uQuan IS NULL SET @uQuan = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] IN (N'demo', N'user1'));

    IF @uQuan IS NOT NULL AND NOT EXISTS (SELECT 1 FROM [dbo].[orders] WHERE [payment_reference] = N'PAY-SW-2026-M501')
    BEGIN
        INSERT INTO [dbo].[orders](
            [user_id], [order_date], [total_price], [status], [payment_status], [payment_method],
            [shipping_full_name], [shipping_phone], [shipping_address], [order_note], [payment_reference], [paid_at], [updated_at]
        )
        VALUES (
            @uQuan, DATEADD(DAY, -15, GETDATE()), 10780000.00, 'PAID', 'SUCCESS', 'STRIPE',
            N'Nguyễn Minh Quân', '0903112233', N'Tòa nhà Saigon Centre, 65 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM',
            N'Đóng hộp gỗ quà tặng doanh nghiệp kèm thiệp chúc mừng.', 'PAY-SW-2026-M501', DATEADD(DAY, -15, GETDATE()), DATEADD(DAY, -14, GETDATE())
        );
    END
END
GO

/* 9) reviews */
IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[reviews]([wine_id], [user_id], [rating], [comment], [created_at])
    SELECT w.[id], u.[id], v.[rating], v.[comment], DATEADD(DAY, -v.[days_ago], GETDATE())
    FROM (VALUES
        (N'Château Margaux Premier Grand Cru Classé', N'nguyen.minh.quan', 5, N'Chai Margaux 2018 đạt độ chín tuyệt vời. Hương hoa tím, cassis và gỗ tuyết tùng bung tỏa ngay sau khi decant 60 phút.', 42),
        (N'Dom Pérignon Vintage Brut',                N'tran.bao.ngoc',    5, N'Bọt sủi cực mịn và bền, hương bánh nướng brioche quyện cùng cam bergamot rất thanh lịch.', 35),
        (N'Château Lafite Rothschild Pauillac',       N'vo.duc.thinh',     5, N'Đẳng cấp Premier Cru không bàn cãi. Tem nhập khẩu và niêm phong cổ chai nguyên vẹn 100%.', 28),
        (N'Opus One Napa Valley Reserve',             N'dang.thu.ha',      5, N'Sự kết hợp hoàn hảo giữa phong cách Bordeaux và sức sống Napa Valley. Dùng kèm thăn nội bò Wagyu nướng thực sự xuất sắc.', 21),
        (N'Sassicaia Tenuta San Guido Bolgheri',      N'le.hoang.nam',     5, N'Hương thảo mộc Địa Trung Hải, anh đào đen và khoáng chất vô cùng cuốn hút.', 25),
        (N'Cloudy Bay Sauvignon Blanc',               N'pham.khanh.linh',  5, N'Hương chanh dây và bưởi hồng bùng nổ, vị chua giòn sảng khoái cho bữa tiệc hải sản.', 8)
    ) AS v([wine_name], [username], [rating], [comment], [days_ago])
    INNER JOIN [dbo].[wines] w ON w.[name] = v.[wine_name]
    INNER JOIN [dbo].[users] u ON u.[username] = v.[username]
    WHERE NOT EXISTS (
        SELECT 1 FROM [dbo].[reviews] r WHERE r.[wine_id] = w.[id] AND r.[user_id] = u.[id]
    );
END
GO
