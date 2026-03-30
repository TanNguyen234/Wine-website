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
    VALUES ('Main Warehouse', N'Ho Chi Minh City', 1);
END
GO

IF NOT EXISTS (SELECT 1 FROM wines)
BEGIN
    INSERT INTO wines (name, type, country, year, price, description, image_url) VALUES
    ('Chateau Margaux', 'Red', 'France', 2018, 8990000, N'Vang do cao cap den tu Bordeaux.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Dom Perignon', 'Sparkling', 'France', 2012, 4990000, N'Champagne cao cap cho dip dac biet.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Sancerre Blanc', 'White', 'France', 2020, 790000, N'Vang trang thanh mat voi huong citrus.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Pinot Noir Reserve', 'Red', 'USA', 2019, 1150000, N'Huong cherry va vi mem mai.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Prosecco DOCG', 'Sparkling', 'Italy', 2021, 650000, N'Vang sui nhe, de uong.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Chardonnay Barrel Aged', 'White', 'Australia', 2020, 880000, N'Vi bo va huong go soi.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Rose de Provence', 'Rose', 'France', 2021, 720000, N'Vang hong thanh lich, huong trai do.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Cabernet Sauvignon', 'Red', 'Chile', 2018, 980000, N'Dam vi, hau vi dai.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Sauvignon Blanc', 'White', 'New Zealand', 2021, 760000, N'Thom mui nhiet doi, vi chua nhe.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80'),
    ('Champagne Brut', 'Sparkling', 'France', 2019, 1590000, N'Bot min va can bang vi tot.', 'https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80');
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
