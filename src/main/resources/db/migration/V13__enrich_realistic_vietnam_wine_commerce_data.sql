-- V13: Enrich realistic Vietnamese fine wine e-commerce and cold-chain logistics data
-- 100% idempotent: ensures base tables/columns exist and seeds 24 fine wines (with 24 unique images),
-- 4 warehouses, multi-warehouse inventory, shippers, 6-month orders, payments, shipments, and reviews.

/* =========================================================================
   0. ENSURE BASE TABLES & COLUMNS EXIST (SAFE FOR EMPTY OR EXISTING DB)
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[categories]', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[categories] (
        [id] BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [name] NVARCHAR(255) NOT NULL UNIQUE,
        [description] NVARCHAR(500) NULL,
        [created_at] DATETIME2 NULL CONSTRAINT [DF_v13_categories_created_at] DEFAULT GETDATE(),
        [updated_at] DATETIME2 NULL CONSTRAINT [DF_v13_categories_updated_at] DEFAULT GETDATE(),
        [created_by] NVARCHAR(255) NULL,
        [updated_by] NVARCHAR(255) NULL,
        [deleted] BIT NOT NULL CONSTRAINT [DF_v13_categories_deleted] DEFAULT 0
    );
END;

IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.wines', 'category_id') IS NULL
        ALTER TABLE [dbo].[wines] ADD [category_id] BIGINT NULL;
END;

/* =========================================================================
   1. CATEGORIES
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[categories]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[categories] ([name], [description], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT v.[name], v.[description], GETDATE(), GETDATE(), N'system', N'system', 0
    FROM (VALUES
        (N'Vang đỏ',   N'Các dòng vang đỏ Bordeaux Grand Cru, Napa Valley, Barolo, Rioja và Chile Reserva đậm vị.'),
        (N'Vang trắng', N'Các dòng vang trắng Burgundy Chardonnay, Sancerre, Marlborough Sauvignon Blanc và Mosel Riesling thanh mát.'),
        (N'Vang hồng', N'Các dòng vang hồng Côtes de Provence thanh lịch với hương dâu rừng và hoa trắng.'),
        (N'Vang sủi',  N'Champagne niên vụ Pháp và Prosecco Superiore DOCG Ý sủi bọt mịn màng cho tiệc sang trọng.')
    ) AS v([name], [description])
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[categories] c WHERE c.[name] = v.[name]);
END;

/* =========================================================================
   2. USERS (CUSTOMERS & SHIPPERS)
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    DECLARE @DefaultPwdHash NVARCHAR(255) = N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
    SELECT TOP 1 @DefaultPwdHash = [password] FROM [dbo].[users] WHERE [username] IN (N'demo', N'admin') ORDER BY [id];

    IF COL_LENGTH('dbo.users', 'email') IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[users] ([username], [email], [password], [role])
        SELECT v.[username], v.[email], @DefaultPwdHash, v.[role]
        FROM (VALUES
            (N'admin',              N'admin@strongwine.vn',           N'ADMIN'),
            (N'demo',               N'khachhang.demo@strongwine.vn',  N'USER'),
            (N'nguyen.minh.quan',   N'quan.nguyen@strongwine.vn',     N'USER'),
            (N'tran.bao.ngoc',      N'ngoc.tran@strongwine.vn',       N'USER'),
            (N'le.hoang.nam',       N'nam.le@strongwine.vn',          N'USER'),
            (N'pham.khanh.linh',    N'linh.pham@strongwine.vn',       N'USER'),
            (N'vo.duc.thinh',       N'thinh.vo@strongwine.vn',        N'USER'),
            (N'dang.thu.ha',        N'ha.dang@strongwine.vn',         N'USER'),
            (N'shipper.ops.hcm',    N'hung.tran.ops@strongwine.vn',   N'SHIPPER'),
            (N'shipper.q7.hcm',     N'kiet.vo.ops@strongwine.vn',     N'SHIPPER'),
            (N'shipper.hn.ops',     N'tuan.ngo.ops@strongwine.vn',    N'SHIPPER')
        ) AS v([username], [email], [role])
        WHERE NOT EXISTS (SELECT 1 FROM [dbo].[users] u WHERE u.[username] = v.[username])
          AND NOT EXISTS (SELECT 1 FROM [dbo].[users] u2 WHERE u2.[email] = v.[email]);
    END
    ELSE
    BEGIN
        INSERT INTO [dbo].[users] ([username], [password], [role])
        SELECT v.[username], @DefaultPwdHash, v.[role]
        FROM (VALUES
            (N'admin',              N'ADMIN'),
            (N'demo',               N'USER'),
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
    END;
END;

/* =========================================================================
   3. WAREHOUSES (COLD-CHAIN WINE CELLARS)
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[warehouse] ([name], [location], [active], [created_at])
    SELECT v.[name], v.[location], 1, GETDATE()
    FROM (VALUES
        (N'Kho Tong TP.HCM',               N'12 Nguyễn Huệ, Quận 1, Thành phố Hồ Chí Minh'),
        (N'Kho Hầm Rượu Quận 7 - TP.HCM',  N'88 Nguyễn Đức Cảnh, Phú Mỹ Hưng, Quận 7, TP.HCM'),
        (N'Kho Trung Tâm Hoàn Kiếm - HN',  N'24 Tràng Tiền, Quận Hoàn Kiếm, Thủ đô Hà Nội'),
        (N'Kho Hải Châu - Đà Nẵng',        N'106 Bạch Đằng, Quận Hải Châu, Thành phố Đà Nẵng')
    ) AS v([name], [location])
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[warehouse] w WHERE w.[name] = v.[name]);
END;

/* =========================================================================
   4. WINES (24 REALISTIC FINE WINE LABELS WITH 24 DISTINCT BOTTLE IMAGES)
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @CatRed BIGINT       = (SELECT TOP 1 [id] FROM [dbo].[categories] WHERE [name] = N'Vang đỏ');
    DECLARE @CatWhite BIGINT     = (SELECT TOP 1 [id] FROM [dbo].[categories] WHERE [name] = N'Vang trắng');
    DECLARE @CatRose BIGINT      = (SELECT TOP 1 [id] FROM [dbo].[categories] WHERE [name] = N'Vang hồng');
    DECLARE @CatSparkling BIGINT = (SELECT TOP 1 [id] FROM [dbo].[categories] WHERE [name] = N'Vang sủi');

    DECLARE @WineSeed TABLE (
        [name] NVARCHAR(255),
        [type] NVARCHAR(50),
        [year] INT,
        [price] DECIMAL(10,2),
        [description] NVARCHAR(1000),
        [country] NVARCHAR(100),
        [image_url] NVARCHAR(500)
    );

    INSERT INTO @WineSeed ([name], [type], [year], [price], [description], [country], [image_url])
    VALUES
        (N'Château Margaux Premier Grand Cru Classé', N'Red', 2018, CAST(8990000.00 AS DECIMAL(10,2)),
         N'Biểu tượng Premier Grand Cru Classé vùng Margaux với hương lý chua đen, hoa tím, gỗ tuyết tùng và hậu vị nhung lụa kéo dài.',
         N'France', N'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80'),
        (N'Château Lafite Rothschild Pauillac', N'Red', 2017, CAST(14500000.00 AS DECIMAL(10,2)),
         N'Tuyệt phẩm Pauillac 1er Grand Cru Classé mang tầng hương phức hợp của xì gà Havana, quả mâm xôi chín, khoáng chì và tannin hoàn mỹ.',
         N'France', N'https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=900&q=80'),
        (N'Opus One Napa Valley Reserve', N'Red', 2019, CAST(9850000.00 AS DECIMAL(10,2)),
         N'Sự giao thoa đỉnh cao giữa Baron Philippe de Rothschild và Robert Mondavi; đậm đà hương cassis, sô-cô-la đen, espresso và thảo mộc.',
         N'USA', N'https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=900&q=80'),
        (N'Penfolds Grange Bin 95 Shiraz', N'Red', 2018, CAST(12900000.00 AS DECIMAL(10,2)),
         N'Biểu tượng vang Úc từ những gốc nho Shiraz cổ thụ vùng Barossa; vị mận đen cô đặc, cam thảo, tiêu đen và gỗ sồi Mỹ ủ 20 tháng.',
         N'Australia', N'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
        (N'Sassicaia Tenuta San Guido Bolgheri', N'Red', 2019, CAST(7600000.00 AS DECIMAL(10,2)),
         N'Huyền thoại Super Tuscan từ Cabernet Sauvignon và Cabernet Franc; thanh lịch với hương anh đào đen, hương thảo và vị khoáng biển.',
         N'Italy', N'https://images.unsplash.com/photo-1584916201218-f4242ceb4809?auto=format&fit=crop&w=900&q=80'),
        (N'Barolo Cannubi Marchesi di Barolo DOCG', N'Red', 2018, CAST(2850000.00 AS DECIMAL(10,2)),
         N'Vua của các dòng vang Ý từ giống nho Nebbiolo trên đồi Cannubi; hương hoa hồng khô, nấm truffle trắng và tannin săn chắc.',
         N'Italy', N'https://images.unsplash.com/photo-1568213816046-0ee1c42bd559?auto=format&fit=crop&w=900&q=80'),
        (N'Almaviva Puente Alto Maipo Valley', N'Red', 2020, CAST(4650000.00 AS DECIMAL(10,2)),
         N'Dòng vang Primer Orden cao cấp nhất Chile; sự kết hợp Bordeaux blend cổ điển với thổ nhưỡng chân núi Andes, đậm vị dâu rừng và vani.',
         N'Chile', N'https://images.unsplash.com/photo-1578911373434-0cb395d2cbfb?auto=format&fit=crop&w=900&q=80'),
        (N'Cabernet Reserva Colchagua', N'Red', 2020, CAST(980000.00 AS DECIMAL(10,2)),
         N'Vang đỏ đậm vị từ thung lũng Colchagua với hương lý chua đen, ớt chuông nướng và hậu vị êm mượt, lý tưởng cho tiệc tối và bít tết.',
         N'Chile', N'https://images.unsplash.com/photo-1586370434639-0fe43b2d32e6?auto=format&fit=crop&w=900&q=80'),
        (N'Montes Alpha M Santa Cruz', N'Red', 2019, CAST(2950000.00 AS DECIMAL(10,2)),
         N'Dòng vang biểu tượng của nhà Montes; cấu trúc dày dặn, hương quả mọng đen chín mọng, hạt nhục đậu khấu, thuốc lá nhẹ và hậu vị sâu.',
         N'Chile', N'https://images.unsplash.com/photo-1560148218-1a83060f7b32?auto=format&fit=crop&w=900&q=80'),
        (N'Catena Zapata Malbec Argentino', N'Red', 2020, CAST(2790000.00 AS DECIMAL(10,2)),
         N'Malbec từ các vườn nho độ cao trên 1.400m tại dãy Andes; sắc tím đậm, hương hoa violet, mận chín, mocha và vị chát mượt như nhung.',
         N'Argentina', N'https://images.unsplash.com/photo-1513618827672-0d7c5ad591b1?auto=format&fit=crop&w=900&q=80'),
        (N'Marqués de Riscal Gran Reserva Rioja', N'Red', 2016, CAST(1890000.00 AS DECIMAL(10,2)),
         N'Vang đỏ Tây Ban Nha ủ 30 tháng trong thùng sồi; hương mận khô, quế, đinh hương, da thuộc mềm và cấu trúc cân bằng cổ điển.',
         N'Spain', N'https://images.unsplash.com/photo-1547595628-c61a29f496f0?auto=format&fit=crop&w=900&q=80'),
        (N'Pinot Noir Reserve Willamette Valley', N'Red', 2020, CAST(1350000.00 AS DECIMAL(10,2)),
         N'Pinot Noir tinh tế với hương anh đào đỏ, dâu rừng, nấm rừng ẩm và độ chua thanh thoát, kết hợp hoàn hảo cùng vịt quay hoặc cá hồi.',
         N'USA', N'https://images.unsplash.com/photo-1528823872057-9c018a7a7553?auto=format&fit=crop&w=900&q=80'),
        (N'Louis Jadot Puligny-Montrachet Blanc', N'White', 2020, CAST(3150000.00 AS DECIMAL(10,2)),
         N'Chardonnay thượng hạng vùng Côte de Beaune; hương hoa keo, táo nướng bơ, hạt dẻ cười và độ khoáng sắc sảo đặc trưng đá vôi.',
         N'France', N'https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80'),
        (N'Sancerre Blanc Les Monts Damnés', N'White', 2021, CAST(1190000.00 AS DECIMAL(10,2)),
         N'Sauvignon Blanc từ sườn đồi đá phấn Kimmeridgian; hương bưởi hồng, chanh vàng, cỏ mới cắt và vị khoáng đá lửa sống động.',
         N'France', N'https://images.unsplash.com/photo-1566754436893-98224ee05be4?auto=format&fit=crop&w=900&q=80'),
        (N'Cloudy Bay Sauvignon Blanc', N'White', 2022, CAST(1120000.00 AS DECIMAL(10,2)),
         N'Chuẩn mực Sauvignon Blanc vùng Marlborough với hương chanh dây, lá lý chua, ổi hồng và vị chua giòn sảng khoái cho tiệc hải sản.',
         N'New Zealand', N'https://images.unsplash.com/photo-1569919659476-f0852f6834b7?auto=format&fit=crop&w=900&q=80'),
        (N'Sauvignon Blanc Marlborough', N'White', 2021, CAST(760000.00 AS DECIMAL(10,2)),
         N'Vang trắng tươi mát hương trái cây nhiệt đới, bưởi xanh và thảo mộc nhẹ, dùng tuyệt vời với hàu sống, tôm hùm và gỏi hải sản.',
         N'New Zealand', N'https://images.unsplash.com/photo-1585553616435-2dc0a54e271d?auto=format&fit=crop&w=900&q=80'),
        (N'Chardonnay Barrel Aged Margaret River', N'White', 2021, CAST(950000.00 AS DECIMAL(10,2)),
         N'Chardonnay ủ sồi Pháp mang hương đào trắng, kem bơ, vani và hạt phỉ nướng, hậu vị tròn đầy quyến rũ.',
         N'Australia', N'https://images.unsplash.com/photo-1519671482749-fd09be7ccebf?auto=format&fit=crop&w=900&q=80'),
        (N'Dr. Loosen Wehlener Sonnenuhr Riesling Spätlese', N'White', 2021, CAST(1080000.00 AS DECIMAL(10,2)),
         N'Riesling thu hoạch muộn từ vườn nho đồng hồ mặt trời Mosel; vị ngọt thanh tao cân bằng cùng độ chua sắc nét, hương đào chín và mật ong.',
         N'Germany', N'https://images.unsplash.com/photo-1562601579-599dec564e06?auto=format&fit=crop&w=900&q=80'),
        (N'Whispering Angel Caves d''Esclans Rosé', N'Rose', 2022, CAST(920000.00 AS DECIMAL(10,2)),
         N'Vang hồng Provence nổi tiếng thế giới với sắc hồng phấn sang trọng, hương dâu tây trắng, đào tiên, vỏ bưởi và hậu vị khô ráo thanh lịch.',
         N'France', N'https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80'),
        (N'Miraval Côtes de Provence Rosé', N'Rose', 2022, CAST(890000.00 AS DECIMAL(10,2)),
         N'Vang hồng thanh lịch từ điền trang Château Miraval; hương hoa trắng, quả mâm xôi tươi, thảo mộc garrigue và vị khoáng mặn mà.',
         N'France', N'https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=900&q=80'),
        (N'Dom Pérignon Vintage Brut', N'Sparkling', 2013, CAST(5490000.00 AS DECIMAL(10,2)),
         N'Champagne niên vụ huyền thoại với bọt sủi mịn như tơ, hương bánh mì nướng brioche, hạnh nhân rang, cam bergamot và khoáng chất tinh tế.',
         N'France', N'https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80'),
        (N'Moët & Chandon Impérial Brut Champagne', N'Sparkling', 2020, CAST(1790000.00 AS DECIMAL(10,2)),
         N'Dòng Champagne biểu tượng của Moët & Chandon từ năm 1869; hương táo xanh, cam chanh, hoa trắng và bánh quy bơ thanh lịch.',
         N'France', N'https://images.unsplash.com/photo-1571613316887-6f8d5cbf7ef7?auto=format&fit=crop&w=900&q=80'),
        (N'Veuve Clicquot Yellow Label Brut', N'Sparkling', 2019, CAST(1950000.00 AS DECIMAL(10,2)),
         N'Champagne giàu cấu trúc với tỷ lệ Pinot Noir chủ đạo; hương lê chín, mận vàng, vani và bánh nướng brioche quyến rũ.',
         N'France', N'https://images.unsplash.com/photo-1546171753-97d7676e4602?auto=format&fit=crop&w=900&q=80'),
        (N'Valdo Marca Oro Valdobbiadene Prosecco Superiore DOCG', N'Sparkling', 2022, CAST(680000.00 AS DECIMAL(10,2)),
         N'Prosecco Superiore DOCG sủi bọt tươi tắn, hương táo vàng Golden, hoa tử đằng và mật ong rừng nhẹ nhàng, hoàn hảo cho khai vị tiệc.',
         N'Italy', N'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=900&q=80');

    INSERT INTO [dbo].[wines]
        ([name], [type], [year], [price], [description], [country], [image_url], [category_id], [created_at], [updated_at], [created_by], [updated_by], [deleted])
    SELECT
        v.[name], v.[type], v.[year], v.[price], v.[description], v.[country], v.[image_url],
        CASE v.[type]
            WHEN N'Red'       THEN @CatRed
            WHEN N'White'     THEN @CatWhite
            WHEN N'Rose'      THEN @CatRose
            WHEN N'Sparkling' THEN @CatSparkling
            ELSE @CatRed
        END,
        DATEADD(DAY, -30, GETDATE()), GETDATE(), N'system', N'system', 0
    FROM @WineSeed v
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[wines] w WHERE w.[name] = v.[name]);

    -- Ensure all 24 wines have their own distinct 1-to-1 bottle image URL
    UPDATE w
    SET w.[image_url] = v.[image_url]
    FROM [dbo].[wines] w
    INNER JOIN @WineSeed v ON v.[name] = w.[name];

    -- Backfill category_id for any existing wines missing category mapping
    UPDATE w SET w.[category_id] = @CatRed       FROM [dbo].[wines] w WHERE w.[category_id] IS NULL AND w.[type] = N'Red'       AND @CatRed IS NOT NULL;
    UPDATE w SET w.[category_id] = @CatWhite     FROM [dbo].[wines] w WHERE w.[category_id] IS NULL AND w.[type] = N'White'     AND @CatWhite IS NOT NULL;
    UPDATE w SET w.[category_id] = @CatRose      FROM [dbo].[wines] w WHERE w.[category_id] IS NULL AND w.[type] = N'Rose'      AND @CatRose IS NOT NULL;
    UPDATE w SET w.[category_id] = @CatSparkling FROM [dbo].[wines] w WHERE w.[category_id] IS NULL AND w.[type] = N'Sparkling' AND @CatSparkling IS NOT NULL;
END;

/* =========================================================================
   5. MULTI-WAREHOUSE INVENTORY, TRANSACTIONS & STOCK LOGS
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[warehouse]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @WhHcm1 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] IN (N'Kho Tong TP.HCM', N'Main Warehouse') ORDER BY [id]);
    DECLARE @WhHcm7 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] = N'Kho Hầm Rượu Quận 7 - TP.HCM');
    DECLARE @WhHn   BIGINT = (SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] = N'Kho Trung Tâm Hoàn Kiếm - HN');
    DECLARE @WhDn   BIGINT = (SELECT TOP 1 [id] FROM [dbo].[warehouse] WHERE [name] = N'Kho Hải Châu - Đà Nẵng');

    IF @WhHcm1 IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory] ([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at], [version])
        SELECT w.[id], @WhHcm1,
               CASE WHEN w.[price] >= 8000000 THEN 28 WHEN w.[price] >= 3000000 THEN 48 ELSE 85 END,
               CASE WHEN w.[id] % 4 = 0 THEN 4 WHEN w.[id] % 3 = 0 THEN 2 ELSE 0 END,
               15, GETDATE(), 0
        FROM [dbo].[wines] w
        WHERE w.[deleted] = 0
          AND NOT EXISTS (SELECT 1 FROM [dbo].[inventory] i WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @WhHcm1);
    END;

    IF @WhHcm7 IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory] ([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at], [version])
        SELECT w.[id], @WhHcm7,
               CASE WHEN w.[price] >= 8000000 THEN 12 WHEN w.[price] >= 3000000 THEN 30 ELSE 60 END,
               CASE WHEN w.[id] % 5 = 0 THEN 3 ELSE 1 END,
               12, GETDATE(), 0
        FROM [dbo].[wines] w
        WHERE w.[deleted] = 0
          AND NOT EXISTS (SELECT 1 FROM [dbo].[inventory] i WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @WhHcm7);
    END;

    IF @WhHn IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory] ([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at], [version])
        SELECT TOP 14 w.[id], @WhHn,
               CASE WHEN w.[price] >= 8000000 THEN 9 WHEN w.[price] >= 3000000 THEN 24 ELSE 50 END,
               2, 10, GETDATE(), 0
        FROM [dbo].[wines] w
        WHERE w.[deleted] = 0
          AND NOT EXISTS (SELECT 1 FROM [dbo].[inventory] i WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @WhHn)
        ORDER BY w.[id];
    END;

    IF @WhDn IS NOT NULL
    BEGIN
        INSERT INTO [dbo].[inventory] ([wine_id], [warehouse_id], [current_quantity], [reserved_quantity], [reorder_level], [updated_at], [version])
        SELECT TOP 8 w.[id], @WhDn,
               32, 1, 10, GETDATE(), 0
        FROM [dbo].[wines] w
        WHERE w.[deleted] = 0
          AND NOT EXISTS (SELECT 1 FROM [dbo].[inventory] i WHERE i.[wine_id] = w.[id] AND i.[warehouse_id] = @WhDn)
        ORDER BY w.[id] DESC;
    END;
END;

IF OBJECT_ID(N'[dbo].[inventory_transactions]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[inventory_transactions]
        ([inventory_id], [product_id], [warehouse_id], [quantity], [operation_type], [reference_type], [reference_id], [user_id], [note], [created_at])
    SELECT TOP 16
        i.[id], i.[wine_id], i.[warehouse_id], i.[current_quantity],
        N'IMPORT', N'PO_CELLAR_2026', 202601 + (i.[id] % 10), NULL,
        N'Nhập kho lô rượu vang nhập khẩu chính ngạch bảo quản lạnh 14°C',
        DATEADD(DAY, -(i.[id] % 25) - 3, GETDATE())
    FROM [dbo].[inventory] i
    WHERE NOT EXISTS (
        SELECT 1 FROM [dbo].[inventory_transactions] t
        WHERE t.[inventory_id] = i.[id] AND t.[reference_type] = N'PO_CELLAR_2026'
    )
    ORDER BY i.[id];
END;

IF OBJECT_ID(N'[dbo].[stock_logs]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[inventory]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[stock_logs] ([inventory_id], [available_quantity], [message], [created_at])
    SELECT TOP 16
        i.[id], (i.[current_quantity] - i.[reserved_quantity]),
        N'Kiểm kê định kỳ hầm rượu tiêu chuẩn nhiệt độ 14°C - Độ ẩm 70%',
        DATEADD(DAY, -(i.[id] % 15) - 1, GETDATE())
    FROM [dbo].[inventory] i
    WHERE NOT EXISTS (
        SELECT 1 FROM [dbo].[stock_logs] sl WHERE sl.[inventory_id] = i.[id]
    )
    ORDER BY i.[id];
END;

/* =========================================================================
   6. SHIPPERS (COLD-CHAIN DELIVERY FLEET)
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
BEGIN
    DECLARE @UserShipperHcm1 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'shipper.ops.hcm');
    DECLARE @UserShipperHcm7 BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'shipper.q7.hcm');
    DECLARE @UserShipperHn   BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'shipper.hn.ops');

    IF @UserShipperHcm1 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[shippers] WHERE [user_id] = @UserShipperHcm1)
    BEGIN
        INSERT INTO [dbo].[shippers]
            ([user_id], [name], [phone], [vehicle_type], [status], [is_available], [current_latitude], [current_longitude], [location_updated_at], [max_concurrent_shipments], [active_shipment_count], [created_at], [updated_at])
        VALUES
            (@UserShipperHcm1, N'Trần Minh Hùng', N'0909123456', N'Xe máy bảo ôn chuyên dụng', N'ACTIVE', 1, 10.7756, 106.7019, GETDATE(), 5, 2, GETDATE(), GETDATE());
    END;

    IF @UserShipperHcm7 IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[shippers] WHERE [user_id] = @UserShipperHcm7)
    BEGIN
        INSERT INTO [dbo].[shippers]
            ([user_id], [name], [phone], [vehicle_type], [status], [is_available], [current_latitude], [current_longitude], [location_updated_at], [max_concurrent_shipments], [active_shipment_count], [created_at], [updated_at])
        VALUES
            (@UserShipperHcm7, N'Võ Tuấn Kiệt', N'0903888912', N'Xe van lạnh 500kg', N'ACTIVE', 1, 10.7295, 106.7218, GETDATE(), 5, 1, GETDATE(), GETDATE());
    END;

    IF @UserShipperHn IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM [dbo].[shippers] WHERE [user_id] = @UserShipperHn)
    BEGIN
        INSERT INTO [dbo].[shippers]
            ([user_id], [name], [phone], [vehicle_type], [status], [is_available], [current_latitude], [current_longitude], [location_updated_at], [max_concurrent_shipments], [active_shipment_count], [created_at], [updated_at])
        VALUES
            (@UserShipperHn, N'Ngô Quốc Tuấn', N'0912666839', N'Xe máy bảo ôn chuyên dụng', N'ACTIVE', 1, 21.0245, 105.8532, GETDATE(), 5, 1, GETDATE(), GETDATE());
    END;

    UPDATE [dbo].[shippers]
    SET [max_concurrent_shipments] = CASE WHEN [max_concurrent_shipments] < 5 THEN 5 ELSE [max_concurrent_shipments] END,
        [current_latitude]  = ISNULL([current_latitude], 10.7756),
        [current_longitude] = ISNULL([current_longitude], 106.7019),
        [location_updated_at] = ISNULL([location_updated_at], GETDATE())
    WHERE [status] = N'ACTIVE';
END;

/* =========================================================================
   7. REALISTIC MULTI-MONTH ORDERS, ORDER ITEMS, PAYMENTS & TRANSACTIONS
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    DECLARE @U_Demo  BIGINT = (SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'demo');
    DECLARE @U_Quan  BIGINT = ISNULL((SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'nguyen.minh.quan'), @U_Demo);
    DECLARE @U_Ngoc  BIGINT = ISNULL((SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'tran.bao.ngoc'), @U_Demo);
    DECLARE @U_Nam   BIGINT = ISNULL((SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'le.hoang.nam'), @U_Demo);
    DECLARE @U_Linh  BIGINT = ISNULL((SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'pham.khanh.linh'), @U_Demo);
    DECLARE @U_Thinh BIGINT = ISNULL((SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'vo.duc.thinh'), @U_Demo);
    DECLARE @U_Ha    BIGINT = ISNULL((SELECT TOP 1 [id] FROM [dbo].[users] WHERE [username] = N'dang.thu.ha'), @U_Demo);

    INSERT INTO [dbo].[orders]
        ([user_id], [order_date], [total_price], [status], [payment_status], [payment_method],
         [shipping_full_name], [shipping_phone], [shipping_email], [shipping_address],
         [shipping_latitude], [shipping_longitude], [order_note], [payment_reference], [paid_at], [updated_at])
    SELECT
        v.[user_id], v.[order_date], v.[total_price], v.[status], v.[payment_status], v.[payment_method],
        v.[shipping_full_name], v.[shipping_phone], v.[shipping_email], v.[shipping_address],
        v.[lat], v.[lng], v.[order_note], v.[payment_reference], v.[paid_at], v.[updated_at]
    FROM (VALUES
        (@U_Demo,  DATEADD(DAY, -2, GETDATE()), CAST(980000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'COD',
         N'Nguyễn Thị Thanh Hương', N'0908765432', N'khachhang.demo@strongwine.vn',
         N'1 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM', 10.7745, 106.7035,
         N'Giao trong giờ hành chính, liên hệ trước khi giao (Chờ tạo vận đơn).', N'PAY-ORD-202604-0001',
         DATEADD(DAY, -2, GETDATE()), DATEADD(DAY, -2, GETDATE())),

        (@U_Demo,  DATEADD(DAY, -1, GETDATE()), CAST(760000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'COD',
         N'Lê Văn Phong', N'0935200111', N'khachhang.demo@strongwine.vn',
         N'2 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7728, 106.7012,
         N'Khách ưu tiên giao trước 19h, gọi trước khi đến.', N'PAY-ORD-202604-0002',
         DATEADD(DAY, -1, GETDATE()), DATEADD(DAY, -1, GETDATE())),

        (@U_Quan,  DATEADD(MONTH, -5, DATEADD(DAY, -4, GETDATE())), CAST(10780000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'STRIPE',
         N'Nguyễn Minh Quân', N'0903112233', N'quan.nguyen@strongwine.vn',
         N'Tòa nhà Saigon Centre, 65 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7731, 106.7011,
         N'Đóng hộp gỗ quà tặng doanh nghiệp kèm thiệp chúc mừng.', N'PAY-SW-2026-M501',
         DATEADD(MONTH, -5, DATEADD(DAY, -4, GETDATE())), DATEADD(MONTH, -5, DATEADD(DAY, -3, GETDATE()))),

        (@U_Ngoc,  DATEADD(MONTH, -4, DATEADD(DAY, -6, GETDATE())), CAST(7440000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'VNPAY',
         N'Trần Bảo Ngọc', N'0908334455', N'ngoc.tran@strongwine.vn',
         N'Biệt thự Chateau, Khu đô thị Phú Mỹ Hưng, Quận 7, TP.HCM', 10.7248, 106.7263,
         N'Giao buổi chiều sau 15h, ướp lạnh sẵn dòng Champagne.', N'PAY-SW-2026-M401',
         DATEADD(MONTH, -4, DATEADD(DAY, -6, GETDATE())), DATEADD(MONTH, -4, DATEADD(DAY, -5, GETDATE()))),

        (@U_Nam,   DATEADD(MONTH, -3, DATEADD(DAY, -8, GETDATE())), CAST(15200000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'STRIPE',
         N'Lê Hoàng Nam', N'0913556677', N'nam.le@strongwine.vn',
         N'Vinhomes Central Park, Landmark 2, Bình Thạnh, TP.HCM', 10.7951, 106.7218,
         N'Phục vụ tiệc kỷ niệm 10 năm, liên hệ lễ tân sảnh L2.', N'PAY-SW-2026-M301',
         DATEADD(MONTH, -3, DATEADD(DAY, -8, GETDATE())), DATEADD(MONTH, -3, DATEADD(DAY, -7, GETDATE()))),

        (@U_Linh,  DATEADD(MONTH, -2, DATEADD(DAY, -5, GETDATE())), CAST(6300000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'MOMO',
         N'Phạm Khánh Linh', N'0938778899', N'linh.pham@strongwine.vn',
         N'28 Thảo Điền, Phường Thảo Điền, TP. Thủ Đức, TP.HCM', 10.8035, 106.7345,
         N'Giao kèm túi đá giữ nhiệt cho dòng Puligny-Montrachet.', N'PAY-SW-2026-M201',
         DATEADD(MONTH, -2, DATEADD(DAY, -5, GETDATE())), DATEADD(MONTH, -2, DATEADD(DAY, -4, GETDATE()))),

        (@U_Thinh, DATEADD(MONTH, -2, DATEADD(DAY, -1, GETDATE())), CAST(14500000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'VNPAY',
         N'Võ Đức Thịnh', N'0989223344', N'thinh.vo@strongwine.vn',
         N'82 Nguyễn Du, Phường Bến Nghé, Quận 1, TP.HCM', 10.7782, 106.6995,
         N'Kiểm tra kỹ niêm phong chì trên cổ chai Lafite Rothschild.', N'PAY-SW-2026-M202',
         DATEADD(MONTH, -2, DATEADD(DAY, -1, GETDATE())), DATEADD(MONTH, -2, GETDATE())),

        (@U_Demo,  DATEADD(MONTH, -1, DATEADD(DAY, -10, GETDATE())), CAST(5700000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'VNPAY',
         N'Nguyễn Thành An', N'0909888777', N'khachhang.demo@strongwine.vn',
         N'15 Đồng Khởi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7748, 106.7042,
         N'Khách VIP yêu cầu túi quà nhung đỏ.', N'PAY-SW-2026-M101',
         DATEADD(MONTH, -1, DATEADD(DAY, -10, GETDATE())), DATEADD(MONTH, -1, DATEADD(DAY, -9, GETDATE()))),

        (@U_Ha,    DATEADD(MONTH, -1, DATEADD(DAY, -3, GETDATE())), CAST(9850000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'STRIPE',
         N'Đặng Thu Hà', N'0918445566', N'ha.dang@strongwine.vn',
         N'54 Lý Thường Kiệt, Quận Hoàn Kiếm, Thủ đô Hà Nội', 21.0235, 105.8485,
         N'Giao giờ hành chính tại văn phòng tầng 8.', N'PAY-SW-2026-M102',
         DATEADD(MONTH, -1, DATEADD(DAY, -3, GETDATE())), DATEADD(MONTH, -1, DATEADD(DAY, -2, GETDATE()))),

        (@U_Demo,  DATEADD(DAY, -5, GETDATE()), CAST(4650000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'MOMO',
         N'Nguyễn Thành An', N'0909888777', N'khachhang.demo@strongwine.vn',
         N'15 Đồng Khởi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7748, 106.7042,
         N'Giao nhanh trong ngày, gọi trước 15 phút.', N'PAY-SW-2026-M001',
         DATEADD(DAY, -5, GETDATE()), DATEADD(DAY, -4, GETDATE())),

        (@U_Quan,  DATEADD(DAY, -2, GETDATE()), CAST(3740000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'VNPAY',
         N'Nguyễn Minh Quân', N'0903112233', N'quan.nguyen@strongwine.vn',
         N'Tòa nhà Bitexco Financial Tower, 2 Hải Triều, Quận 1, TP.HCM', 10.7717, 106.7044,
         N'Đơn hàng đang trên tuyến giao đến sảnh văn phòng.', N'PAY-SW-2026-M002',
         DATEADD(DAY, -2, GETDATE()), DATEADD(HOUR, -3, GETDATE())),

        (@U_Ngoc,  DATEADD(DAY, -1, GETDATE()), CAST(2790000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'COD',
         N'Trần Bảo Ngọc', N'0908334455', N'ngoc.tran@strongwine.vn',
         N'102 Nguyễn Thị Minh Khai, Phường Võ Thị Sáu, Quận 3, TP.HCM', 10.7789, 106.6918,
         N'Đã lấy hàng tại hầm rượu, chuẩn bị xuất phát.', N'PAY-SW-2026-M003',
         DATEADD(DAY, -1, GETDATE()), DATEADD(HOUR, -2, GETDATE())),

        (@U_Linh,  DATEADD(HOUR, -6, GETDATE()), CAST(2240000.00 AS DECIMAL(10,2)), N'PAID', N'SUCCESS', N'VNPAY',
         N'Phạm Khánh Linh', N'0938778899', N'linh.pham@strongwine.vn',
         N'88 Song Hành, Phường An Phú, TP. Thủ Đức, TP.HCM', 10.7992, 106.7421,
         N'Đơn mới thanh toán, chờ điều phối shipper.', N'PAY-SW-2026-M004',
         DATEADD(HOUR, -6, GETDATE()), DATEADD(HOUR, -5, GETDATE())),

        (@U_Nam,   DATEADD(HOUR, -2, GETDATE()), CAST(1840000.00 AS DECIMAL(10,2)), N'PENDING', N'PENDING', N'COD',
         N'Lê Hoàng Nam', N'0913556677', N'nam.le@strongwine.vn',
         N'210 Nguyễn Công Trứ, Phường Nguyễn Thái Bình, Quận 1, TP.HCM', 10.7689, 106.7024,
         N'Khách đặt COD, xác nhận qua điện thoại trước khi xuất kho.', N'PAY-SW-2026-M005',
         NULL, DATEADD(HOUR, -2, GETDATE()))
    ) AS v([user_id], [order_date], [total_price], [status], [payment_status], [payment_method],
           [shipping_full_name], [shipping_phone], [shipping_email], [shipping_address],
           [lat], [lng], [order_note], [payment_reference], [paid_at], [updated_at])
    WHERE v.[user_id] IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM [dbo].[orders] o WHERE o.[payment_reference] = v.[payment_reference]);
END;

/* =========================================================================
   8. ORDER ITEMS FOR ENRICHED ORDERS
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[order_items]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[order_items] ([order_id], [wine_id], [quantity], [price])
    SELECT o.[id], w.[id], v.[qty], v.[unit_price]
    FROM (VALUES
        (N'PAY-ORD-202604-0001', N'Cabernet Reserva Colchagua',                 1, CAST(980000.00 AS DECIMAL(10,2))),
        (N'PAY-ORD-202604-0002', N'Sauvignon Blanc Marlborough',                1, CAST(760000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M501',    N'Château Margaux Premier Grand Cru Classé',   1, CAST(8990000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M501',    N'Moët & Chandon Impérial Brut Champagne',     1, CAST(1790000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M401',    N'Dom Pérignon Vintage Brut',                  1, CAST(5490000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M401',    N'Veuve Clicquot Yellow Label Brut',           1, CAST(1950000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M301',    N'Sassicaia Tenuta San Guido Bolgheri',        2, CAST(7600000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M201',    N'Louis Jadot Puligny-Montrachet Blanc',       2, CAST(3150000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M202',    N'Château Lafite Rothschild Pauillac',         1, CAST(14500000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M101',    N'Barolo Cannubi Marchesi di Barolo DOCG',     2, CAST(2850000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M102',    N'Opus One Napa Valley Reserve',               1, CAST(9850000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M001',    N'Almaviva Puente Alto Maipo Valley',          1, CAST(4650000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M002',    N'Moët & Chandon Impérial Brut Champagne',     1, CAST(1790000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M002',    N'Veuve Clicquot Yellow Label Brut',           1, CAST(1950000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M003',    N'Catena Zapata Malbec Argentino',             1, CAST(2790000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M004',    N'Cloudy Bay Sauvignon Blanc',                 2, CAST(1120000.00 AS DECIMAL(10,2))),
        (N'PAY-SW-2026-M005',    N'Whispering Angel Caves d''Esclans Rosé',     2, CAST(920000.00 AS DECIMAL(10,2)))
    ) AS v([pay_ref], [wine_name], [qty], [unit_price])
    INNER JOIN [dbo].[orders] o ON o.[payment_reference] = v.[pay_ref]
    INNER JOIN [dbo].[wines]  w ON w.[name] = v.[wine_name]
    WHERE NOT EXISTS (
        SELECT 1 FROM [dbo].[order_items] oi
        WHERE oi.[order_id] = o.[id] AND oi.[wine_id] = w.[id]
    );
END;

/* =========================================================================
   9. PAYMENTS & PAYMENT TRANSACTIONS
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[payments]
        ([order_id], [method], [status], [amount], [currency], [payment_reference], [gateway_session_id], [gateway_response], [created_at], [updated_at])
    SELECT
        o.[id], o.[payment_method], o.[payment_status], o.[total_price], N'VND',
        o.[payment_reference],
        CASE WHEN o.[payment_method] IN (N'STRIPE', N'VNPAY', N'MOMO') THEN CONCAT(N'sess_', LOWER(o.[payment_reference])) ELSE NULL END,
        CASE WHEN o.[payment_status] = N'SUCCESS' THEN N'GATEWAY_CONFIRMED_00' ELSE N'AWAITING_COLLECTION' END,
        o.[order_date], o.[updated_at]
    FROM [dbo].[orders] o
    WHERE (o.[payment_reference] LIKE N'PAY-SW-2026-%' OR o.[payment_reference] LIKE N'PAY-ORD-202604-%')
      AND NOT EXISTS (SELECT 1 FROM [dbo].[payments] p WHERE p.[payment_reference] = o.[payment_reference]);
END;

IF OBJECT_ID(N'[dbo].[payment_transactions]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[payments]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[payment_transactions] ([payment_id], [transaction_type], [status], [payload], [created_at])
    SELECT
        p.[id],
        CASE WHEN p.[method] = N'COD' THEN N'COD_COLLECTION' ELSE N'GATEWAY_CAPTURE' END,
        p.[status],
        CONCAT(N'{"reference":"', p.[payment_reference], N'","method":"', p.[method], N'","currency":"VND"}'),
        p.[updated_at]
    FROM [dbo].[payments] p
    WHERE (p.[payment_reference] LIKE N'PAY-SW-2026-%' OR p.[payment_reference] LIKE N'PAY-ORD-202604-%')
      AND NOT EXISTS (
          SELECT 1 FROM [dbo].[payment_transactions] pt WHERE pt.[payment_id] = p.[id]
      );
END;

/* =========================================================================
   10. SHIPMENTS & STATUS HISTORY (DELIVERY LIFECYCLE + OTP VAULT)
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[orders]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shippers]', N'U') IS NOT NULL
BEGIN
    DECLARE @PrimaryShipperId BIGINT = (
        SELECT TOP 1 s.[id]
        FROM [dbo].[shippers] s
        INNER JOIN [dbo].[users] u ON u.[id] = s.[user_id]
        WHERE u.[username] = N'shipper.ops.hcm'
    );
    IF @PrimaryShipperId IS NULL
        SELECT TOP 1 @PrimaryShipperId = [id] FROM [dbo].[shippers] WHERE [status] = N'ACTIVE' ORDER BY [id];

    DECLARE @SecondaryShipperId BIGINT = (
        SELECT TOP 1 s.[id]
        FROM [dbo].[shippers] s
        WHERE s.[status] = N'ACTIVE' AND s.[id] <> ISNULL(@PrimaryShipperId, -1)
        ORDER BY s.[id]
    );
    IF @SecondaryShipperId IS NULL SET @SecondaryShipperId = @PrimaryShipperId;

    INSERT INTO [dbo].[shipments]
        ([order_id], [shipper_id], [status], [shipping_name], [shipping_phone], [shipping_email], [shipping_address],
         [shipping_latitude], [shipping_longitude], [otp_code], [otp_created_at], [otp_expires_at],
         [otp_delivery_status], [otp_user_id], [otp_verified],
         [created_at], [updated_at], [assigned_at], [picked_up_at], [delivering_at], [completed_at])
    SELECT
        o.[id],
        CASE WHEN v.[ship_status] = N'PENDING_ASSIGNMENT' THEN NULL
             WHEN v.[use_primary] = 1 THEN @PrimaryShipperId
             ELSE @SecondaryShipperId
        END,
        v.[ship_status],
        o.[shipping_full_name], o.[shipping_phone], o.[shipping_email], o.[shipping_address],
        o.[shipping_latitude], o.[shipping_longitude],
        v.[otp_code],
        DATEADD(HOUR, -1, GETDATE()),
        DATEADD(DAY, 7, GETDATE()),
        N'SENT',
        o.[user_id],
        CASE WHEN v.[ship_status] = N'COMPLETED' THEN 1 ELSE 0 END,
        o.[order_date],
        o.[updated_at],
        CASE WHEN v.[ship_status] <> N'PENDING_ASSIGNMENT' THEN DATEADD(HOUR, 1, o.[order_date]) ELSE NULL END,
        CASE WHEN v.[ship_status] IN (N'PICKED_UP', N'DELIVERING', N'COMPLETED') THEN DATEADD(HOUR, 2, o.[order_date]) ELSE NULL END,
        CASE WHEN v.[ship_status] IN (N'DELIVERING', N'COMPLETED') THEN DATEADD(HOUR, 3, o.[order_date]) ELSE NULL END,
        CASE WHEN v.[ship_status] = N'COMPLETED' THEN DATEADD(HOUR, 5, o.[order_date]) ELSE NULL END
    FROM (VALUES
        (N'PAY-ORD-202604-0002', N'ASSIGNED',           N'123456', 1),
        (N'PAY-SW-2026-M501',    N'COMPLETED',          N'810245', 1),
        (N'PAY-SW-2026-M401',    N'COMPLETED',          N'639104', 0),
        (N'PAY-SW-2026-M301',    N'COMPLETED',          N'528901', 1),
        (N'PAY-SW-2026-M201',    N'COMPLETED',          N'417823', 0),
        (N'PAY-SW-2026-M202',    N'COMPLETED',          N'904112', 1),
        (N'PAY-SW-2026-M101',    N'COMPLETED',          N'772319', 1),
        (N'PAY-SW-2026-M102',    N'COMPLETED',          N'351890', 0),
        (N'PAY-SW-2026-M001',    N'COMPLETED',          N'664208', 1),
        (N'PAY-SW-2026-M002',    N'DELIVERING',         N'886420', 1),
        (N'PAY-SW-2026-M003',    N'PICKED_UP',          N'450912', 1),
        (N'PAY-SW-2026-M004',    N'PENDING_ASSIGNMENT', N'719305', 1)
    ) AS v([pay_ref], [ship_status], [otp_code], [use_primary])
    INNER JOIN [dbo].[orders] o ON o.[payment_reference] = v.[pay_ref]
    WHERE NOT EXISTS (SELECT 1 FROM [dbo].[shipments] s WHERE s.[order_id] = o.[id]);
END;

IF OBJECT_ID(N'[dbo].[shipment_status_history]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[shipments]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[shipment_status_history]
        ([shipment_id], [from_status], [to_status], [reason], [metadata], [actor_username], [created_at])
    SELECT
        s.[id], N'PENDING_ASSIGNMENT', s.[status],
        N'Điều phối tự động từ trung tâm hầm rượu StrongWine',
        CONCAT(N'{"orderId":', s.[order_id], N',"otpDeliveryStatus":"', s.[otp_delivery_status], N'"}'),
        N'system',
        s.[updated_at]
    FROM [dbo].[shipments] s
    WHERE NOT EXISTS (
        SELECT 1 FROM [dbo].[shipment_status_history] h WHERE h.[shipment_id] = s.[id]
    );
END;

/* =========================================================================
   11. REALISTIC SOMMELIER & CUSTOMER REVIEWS
   ========================================================================= */
IF OBJECT_ID(N'[dbo].[reviews]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[users]', N'U') IS NOT NULL
   AND OBJECT_ID(N'[dbo].[wines]', N'U') IS NOT NULL
BEGIN
    INSERT INTO [dbo].[reviews] ([wine_id], [user_id], [rating], [comment], [created_at])
    SELECT w.[id], u.[id], v.[rating], v.[comment], DATEADD(DAY, -v.[days_ago], GETDATE())
    FROM (VALUES
        (N'Château Margaux Premier Grand Cru Classé', N'nguyen.minh.quan', 5, N'Chai Margaux 2018 đạt độ chín tuyệt vời. Hương hoa tím, cassis và gỗ tuyết tùng bung tỏa ngay sau khi decant 60 phút. Đóng gói bảo ôn của StrongWine cực kỳ chuyên nghiệp.', 42),
        (N'Château Margaux Premier Grand Cru Classé', N'demo',             5, N'Cấu trúc tannin mượt như nhung lụa, hậu vị kéo dài ấn tượng. Quà biếu đối tác rất sang trọng.', 18),
        (N'Dom Pérignon Vintage Brut',                N'tran.bao.ngoc',    5, N'Bọt sủi cực mịn và bền, hương bánh nướng brioche quyện cùng cam bergamot rất thanh lịch. Giao đến Quận 7 vẫn giữ nguyên độ lạnh chuẩn.', 35),
        (N'Château Lafite Rothschild Pauillac',       N'vo.duc.thinh',     5, N'Đẳng cấp Premier Cru không bàn cãi. Tem nhập khẩu và niêm phong cổ chai nguyên vẹn 100%, rất yên tâm khi sưu tầm.', 28),
        (N'Opus One Napa Valley Reserve',             N'dang.thu.ha',      5, N'Sự kết hợp hoàn hảo giữa phong cách Bordeaux và sức sống Napa Valley. Dùng kèm thăn nội bò Wagyu nướng than hoa thực sự xuất sắc.', 21),
        (N'Sassicaia Tenuta San Guido Bolgheri',      N'le.hoang.nam',     5, N'Hương thảo mộc Địa Trung Hải, anh đào đen và khoáng chất vô cùng cuốn hút. Một trong những chai Super Tuscan đáng giá nhất.', 25),
        (N'Barolo Cannubi Marchesi di Barolo DOCG',   N'demo',             5, N'Hương hoa hồng khô và nấm truffle đặc trưng vùng Piedmont. Tannin chắc khỏe, rất hợp với các món Âu đậm vị.', 15),
        (N'Almaviva Puente Alto Maipo Valley',        N'nguyen.minh.quan', 5, N'Đỉnh cao vang Chile. Vị quả mọng đen chín đậm, vani gỗ sồi Pháp cân bằng, hậu vị sâu lắng.', 12),
        (N'Louis Jadot Puligny-Montrachet Blanc',     N'pham.khanh.linh',  5, N'Chardonnay vùng Burgundy có độ khoáng đá vôi sắc sảo và hương bơ hạt dẻ tinh tế, không hề bị ngấy.', 19),
        (N'Cloudy Bay Sauvignon Blanc',               N'pham.khanh.linh',  5, N'Hương chanh dây và bưởi hồng bùng nổ, vị chua giòn sảng khoái. Tuyệt hảo cho bữa tiệc hải sản cuối tuần.', 8),
        (N'Whispering Angel Caves d''Esclans Rosé',   N'tran.bao.ngoc',    4, N'Màu hồng phấn cực đẹp, vị khô thanh lịch, hương dâu rừng nhẹ nhàng dễ uống.', 10),
        (N'Moët & Chandon Impérial Brut Champagne',   N'le.hoang.nam',     5, N'Lựa chọn hoàn hảo để khai tiệc, bọt mịn, hương táo xanh và hoa trắng tươi mới.', 6)
    ) AS v([wine_name], [username], [rating], [comment], [days_ago])
    INNER JOIN [dbo].[wines] w ON w.[name] = v.[wine_name]
    INNER JOIN [dbo].[users] u ON u.[username] = v.[username]
    WHERE NOT EXISTS (
        SELECT 1 FROM [dbo].[reviews] r
        WHERE r.[wine_id] = w.[id] AND r.[user_id] = u.[id]
    );
END;
