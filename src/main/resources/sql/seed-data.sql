-- Seed data for StrongWine production-grade demo environment
-- Safe to run multiple times (idempotent style with IF NOT EXISTS)

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
END
GO

/* =========================================================================
   2. USERS
   ========================================================================= */
DECLARE @DefaultHash NVARCHAR(255) = N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('admin', @DefaultHash, 'ADMIN');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'demo')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('demo', @DefaultHash, 'USER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'nguyen.minh.quan')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('nguyen.minh.quan', @DefaultHash, 'USER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'tran.bao.ngoc')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('tran.bao.ngoc', @DefaultHash, 'USER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'le.hoang.nam')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('le.hoang.nam', @DefaultHash, 'USER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'pham.khanh.linh')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('pham.khanh.linh', @DefaultHash, 'USER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'shipper.ops.hcm')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('shipper.ops.hcm', @DefaultHash, 'SHIPPER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'shipper.q7.hcm')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('shipper.q7.hcm', @DefaultHash, 'SHIPPER');
END

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'shipper.hn.ops')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('shipper.hn.ops', @DefaultHash, 'SHIPPER');
END
GO

/* =========================================================================
   3. WAREHOUSES
   ========================================================================= */
IF NOT EXISTS (SELECT 1 FROM warehouse WHERE name IN (N'Main Warehouse', N'Kho Tong TP.HCM'))
BEGIN
    INSERT INTO warehouse (name, location, active)
    VALUES (N'Kho Tong TP.HCM', N'12 Nguyễn Huệ, Quận 1, Thành phố Hồ Chí Minh', 1);
END

IF NOT EXISTS (SELECT 1 FROM warehouse WHERE name = N'Kho Hầm Rượu Quận 7 - TP.HCM')
BEGIN
    INSERT INTO warehouse (name, location, active)
    VALUES (N'Kho Hầm Rượu Quận 7 - TP.HCM', N'88 Nguyễn Đức Cảnh, Phú Mỹ Hưng, Quận 7, TP.HCM', 1);
END

IF NOT EXISTS (SELECT 1 FROM warehouse WHERE name = N'Kho Trung Tâm Hoàn Kiếm - HN')
BEGIN
    INSERT INTO warehouse (name, location, active)
    VALUES (N'Kho Trung Tâm Hoàn Kiếm - HN', N'24 Tràng Tiền, Quận Hoàn Kiếm, Thủ đô Hà Nội', 1);
END

IF NOT EXISTS (SELECT 1 FROM warehouse WHERE name = N'Kho Hải Châu - Đà Nẵng')
BEGIN
    INSERT INTO warehouse (name, location, active)
    VALUES (N'Kho Hải Châu - Đà Nẵng', N'106 Bạch Đằng, Quận Hải Châu, Thành phố Đà Nẵng', 1);
END
GO

/* =========================================================================
   4. WINES (24 REALISTIC FINE WINES)
   ========================================================================= */
INSERT INTO wines (name, type, country, year, price, description, image_url)
SELECT v.name, v.type, v.country, v.year, v.price, v.description, v.image_url
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
) AS v(name, type, country, year, price, description, image_url)
WHERE NOT EXISTS (SELECT 1 FROM wines w WHERE w.name = v.name);
GO

/* =========================================================================
   5. INVENTORY ACROSS WAREHOUSES
   ========================================================================= */
DECLARE @whHcm1 BIGINT = (SELECT TOP 1 id FROM warehouse WHERE name IN (N'Kho Tong TP.HCM', N'Main Warehouse') ORDER BY id);
DECLARE @whHcm7 BIGINT = (SELECT TOP 1 id FROM warehouse WHERE name = N'Kho Hầm Rượu Quận 7 - TP.HCM');
DECLARE @whHn   BIGINT = (SELECT TOP 1 id FROM warehouse WHERE name = N'Kho Trung Tâm Hoàn Kiếm - HN');

IF @whHcm1 IS NOT NULL
BEGIN
    INSERT INTO inventory (wine_id, warehouse_id, current_quantity, reserved_quantity, reorder_level)
    SELECT w.id, @whHcm1, 75, 2, 15
    FROM wines w
    WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.wine_id = w.id AND i.warehouse_id = @whHcm1);
END

IF @whHcm7 IS NOT NULL
BEGIN
    INSERT INTO inventory (wine_id, warehouse_id, current_quantity, reserved_quantity, reorder_level)
    SELECT w.id, @whHcm7, 45, 1, 12
    FROM wines w
    WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.wine_id = w.id AND i.warehouse_id = @whHcm7);
END

IF @whHn IS NOT NULL
BEGIN
    INSERT INTO inventory (wine_id, warehouse_id, current_quantity, reserved_quantity, reorder_level)
    SELECT TOP 12 w.id, @whHn, 35, 1, 10
    FROM wines w
    WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.wine_id = w.id AND i.warehouse_id = @whHn)
    ORDER BY w.id;
END
GO
