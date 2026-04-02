-- Seed data for StrongWine demo environment
-- Safe to run multiple times (idempotent style with IF NOT EXISTS)

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');
END
GO

IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'demo')
BEGIN
    INSERT INTO users (username, password, role)
    VALUES ('demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER');
END
GO

IF NOT EXISTS (SELECT 1 FROM warehouse WHERE name = 'Main Warehouse')
BEGIN
    INSERT INTO warehouse (name, location, active)
    VALUES ('Main Warehouse', N'Thành phố Hồ Chí Minh', 1);
END
GO

UPDATE warehouse
SET location = N'Thành phố Hồ Chí Minh'
WHERE name = 'Main Warehouse' AND location = N'Ho Chi Minh City';
GO

IF NOT EXISTS (SELECT 1 FROM wines)
BEGIN
    INSERT INTO wines (name, type, country, year, price, description, image_url) VALUES
    ('Chateau Margaux', 'Red', 'France', 2018, 8990000, N'Vang đỏ cao cấp đến từ Bordeaux.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Dom Perignon', 'Sparkling', 'France', 2012, 4990000, N'Champagne cao cấp cho dịp đặc biệt.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Sancerre Blanc', 'White', 'France', 2020, 790000, N'Vang trắng thanh mát với hương citrus.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Pinot Noir Reserve', 'Red', 'USA', 2019, 1150000, N'Hương cherry và vị mềm mại.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Prosecco DOCG', 'Sparkling', 'Italy', 2021, 650000, N'Vang sủi nhẹ, dễ uống.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Chardonnay Barrel Aged', 'White', 'Australia', 2020, 880000, N'Vị bơ và hương gỗ sồi.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Rose de Provence', 'Rose', 'France', 2021, 720000, N'Vang hồng thanh lịch, hương trái đỏ.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Cabernet Sauvignon', 'Red', 'Chile', 2018, 980000, N'Đậm vị, hậu vị dài.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Sauvignon Blanc', 'White', 'New Zealand', 2021, 760000, N'Thơm mùi nhiệt đới, vị chua nhẹ.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Champagne Brut', 'Sparkling', 'France', 2019, 1590000, N'Bọt mịn và cân bằng vị tốt.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80');
END
GO

DECLARE @warehouseId BIGINT;
SELECT TOP 1 @warehouseId = id FROM warehouse WHERE name = 'Main Warehouse';

INSERT INTO inventory (wine_id, warehouse_id, current_quantity, reserved_quantity, reorder_level)
SELECT w.id, @warehouseId, 60, 0, 10
FROM wines w
WHERE NOT EXISTS (
    SELECT 1 FROM inventory i WHERE i.wine_id = w.id AND i.warehouse_id = @warehouseId
);
GO

UPDATE inventory
SET current_quantity = CASE WHEN current_quantity IS NULL OR current_quantity <= 0 THEN 60 ELSE current_quantity END,
    reserved_quantity = CASE WHEN reserved_quantity IS NULL OR reserved_quantity < 0 THEN 0 ELSE reserved_quantity END,
    reorder_level = CASE WHEN reorder_level IS NULL OR reorder_level <= 0 THEN 10 ELSE reorder_level END
WHERE warehouse_id = (SELECT TOP 1 id FROM warehouse WHERE name = 'Main Warehouse');
GO
