-- V15: Fix remaining Vietnamese text with question marks in seed data
UPDATE warehouse 
SET location = N'Thành phố Hồ Chí Minh'
WHERE location LIKE '%Thành ph?%';

UPDATE reviews
SET comment = N'Đánh giá mẫu phục vụ hiển thị giao diện'
WHERE comment LIKE '%Ðánh giá m?u%';

UPDATE orders
SET shipping_full_name = REPLACE(shipping_full_name, 'Nguy?n', N'Nguyễn')
WHERE shipping_full_name LIKE '%Nguy?n%';

UPDATE shipments
SET shipping_name = REPLACE(shipping_name, 'Nguy?n', N'Nguyễn')
WHERE shipping_name LIKE '%Nguy?n%';
