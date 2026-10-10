package com.strongwine.strongwine.config;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data initializer to ensure default accounts, realistic fine wine catalog,
 * cold-chain warehouses, inventory, and shipper fleet exist at startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WineRepository wineRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ShipperRepository shipperRepository;

    @Override
    public void run(String... args) throws Exception {
        ensureDefaultUsers();
        ensureDefaultWines();
        ensureWarehouseAndInventory();
        ensureDefaultShippers();
    }

    private void ensureDefaultUsers() {
        ensureUserAccount("admin", "admin123", "ADMIN", "admin@strongwine.vn");
        ensureUserAccount("demo", "demo123", "USER", "khachhang.demo@strongwine.vn");
        ensureUserAccount("nguyen.minh.quan", "demo123", "USER", "quan.nguyen@strongwine.vn");
        ensureUserAccount("tran.bao.ngoc", "demo123", "USER", "ngoc.tran@strongwine.vn");
        ensureUserAccount("le.hoang.nam", "demo123", "USER", "nam.le@strongwine.vn");
        ensureUserAccount("pham.khanh.linh", "demo123", "USER", "linh.pham@strongwine.vn");
        ensureUserAccount("shipper1", "shipper123", "SHIPPER", "shipper1.ops@strongwine.vn");
        ensureUserAccount("shipper2", "shipper123", "SHIPPER", "shipper2.ops@strongwine.vn");
        ensureUserAccount("shipper.ops.hcm", "shipper123", "SHIPPER", "hung.tran.ops@strongwine.vn");
        ensureUserAccount("shipper.q7.hcm", "shipper123", "SHIPPER", "kiet.vo.ops@strongwine.vn");
        ensureUserAccount("shipper.hn.ops", "shipper123", "SHIPPER", "tuan.ngo.ops@strongwine.vn");
    }

    private void ensureUserAccount(String username, String rawPassword, String role, String email) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            if (email != null && !userRepository.existsByEmail(email)) {
                user.setEmail(email);
            }
            userRepository.save(user);
        }
    }

    private void ensureDefaultWines() {
        if (wineRepository.count() > 0) {
            return;
        }

        List<Wine> wines = new ArrayList<>();
        wines.add(buildWine("Château Margaux Premier Grand Cru Classé", "Red", "France", 2018, "8990000",
                "Biểu tượng Premier Grand Cru Classé vùng Margaux với hương lý chua đen, hoa tím, gỗ tuyết tùng và hậu vị nhung lụa kéo dài.",
                "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Château Lafite Rothschild Pauillac", "Red", "France", 2017, "14500000",
                "Tuyệt phẩm Pauillac 1er Grand Cru Classé mang tầng hương phức hợp của xì gà Havana, quả mâm xôi chín, khoáng chì và tannin hoàn mỹ.",
                "https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Opus One Napa Valley Reserve", "Red", "USA", 2019, "9850000",
                "Sự giao thoa đỉnh cao giữa Baron Philippe de Rothschild và Robert Mondavi; đậm đà hương cassis, sô-cô-la đen, espresso và thảo mộc.",
                "https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Penfolds Grange Bin 95 Shiraz", "Red", "Australia", 2018, "12900000",
                "Biểu tượng vang Úc từ những gốc nho Shiraz cổ thụ vùng Barossa; vị mận đen cô đặc, cam thảo, tiêu đen và gỗ sồi Mỹ ủ 20 tháng.",
                "https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Sassicaia Tenuta San Guido Bolgheri", "Red", "Italy", 2019, "7600000",
                "Huyền thoại Super Tuscan từ Cabernet Sauvignon và Cabernet Franc; thanh lịch với hương anh đào đen, hương thảo và vị khoáng biển.",
                "https://images.unsplash.com/photo-1584916201218-f4242ceb4809?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Barolo Cannubi Marchesi di Barolo DOCG", "Red", "Italy", 2018, "2850000",
                "Vua của các dòng vang Ý từ giống nho Nebbiolo trên đồi Cannubi; hương hoa hồng khô, nấm truffle trắng và tannin săn chắc.",
                "https://images.unsplash.com/photo-1568213816046-0ee1c42bd559?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Almaviva Puente Alto Maipo Valley", "Red", "Chile", 2020, "4650000",
                "Dòng vang Primer Orden cao cấp nhất Chile; sự kết hợp Bordeaux blend cổ điển với thổ nhưỡng chân núi Andes, đậm vị dâu rừng và vani.",
                "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Cabernet Reserva Colchagua", "Red", "Chile", 2020, "980000",
                "Vang đỏ đậm vị từ thung lũng Colchagua với hương lý chua đen, ớt chuông nướng và hậu vị êm mượt, lý tưởng cho tiệc tối và bít tết.",
                "https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Montes Alpha M Santa Cruz", "Red", "Chile", 2019, "2950000",
                "Dòng vang biểu tượng của nhà Montes; cấu trúc dày dặn, hương quả mọng đen chín mọng, hạt nhục đậu khấu, thuốc lá nhẹ và hậu vị sâu.",
                "https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Catena Zapata Malbec Argentino", "Red", "Argentina", 2020, "2790000",
                "Malbec từ các vườn nho độ cao trên 1.400m tại dãy Andes; sắc tím đậm, hương hoa violet, mận chín, mocha và vị chát mượt như nhung.",
                "https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Marqués de Riscal Gran Reserva Rioja", "Red", "Spain", 2016, "1890000",
                "Vang đỏ Tây Ban Nha ủ 30 tháng trong thùng sồi; hương mận khô, quế, đinh hương, da thuộc mềm và cấu trúc cân bằng cổ điển.",
                "https://images.unsplash.com/photo-1584916201218-f4242ceb4809?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Pinot Noir Reserve Willamette Valley", "Red", "USA", 2020, "1350000",
                "Pinot Noir tinh tế với hương anh đào đỏ, dâu rừng, nấm rừng ẩm và độ chua thanh thoát, kết hợp hoàn hảo cùng vịt quay hoặc cá hồi.",
                "https://images.unsplash.com/photo-1568213816046-0ee1c42bd559?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Louis Jadot Puligny-Montrachet Blanc", "White", "France", 2020, "3150000",
                "Chardonnay thượng hạng vùng Côte de Beaune; hương hoa keo, táo nướng bơ, hạt dẻ cười và độ khoáng sắc sảo đặc trưng đá vôi.",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Sancerre Blanc Les Monts Damnés", "White", "France", 2021, "1190000",
                "Sauvignon Blanc từ sườn đồi đá phấn Kimmeridgian; hương bưởi hồng, chanh vàng, cỏ mới cắt và vị khoáng đá lửa sống động.",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Cloudy Bay Sauvignon Blanc", "White", "New Zealand", 2022, "1120000",
                "Chuẩn mực Sauvignon Blanc vùng Marlborough với hương chanh dây, lá lý chua, ổi hồng và vị chua giòn sảng khoái cho tiệc hải sản.",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Sauvignon Blanc Marlborough", "White", "New Zealand", 2021, "760000",
                "Vang trắng tươi mát hương trái cây nhiệt đới, bưởi xanh và thảo mộc nhẹ, dùng tuyệt vời với hàu sống, tôm hùm và gỏi hải sản.",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Chardonnay Barrel Aged Margaret River", "White", "Australia", 2021, "950000",
                "Chardonnay ủ sồi Pháp mang hương đào trắng, kem bơ, vani và hạt phỉ nướng, hậu vị tròn đầy quyến rũ.",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Dr. Loosen Wehlener Sonnenuhr Riesling Spätlese", "White", "Germany", 2021, "1080000",
                "Riesling thu hoạch muộn từ vườn nho đồng hồ mặt trời Mosel; vị ngọt thanh tao cân bằng cùng độ chua sắc nét, hương đào chín và mật ong.",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Whispering Angel Caves d'Esclans Rosé", "Rose", "France", 2022, "920000",
                "Vang hồng Provence nổi tiếng thế giới với sắc hồng phấn sang trọng, hương dâu tây trắng, đào tiên, vỏ bưởi và hậu vị khô ráo thanh lịch.",
                "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Miraval Côtes de Provence Rosé", "Rose", "France", 2022, "890000",
                "Vang hồng thanh lịch từ điền trang Château Miraval; hương hoa trắng, quả mâm xôi tươi, thảo mộc garrigue và vị khoáng mặn mà.",
                "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Dom Pérignon Vintage Brut", "Sparkling", "France", 2013, "5490000",
                "Champagne niên vụ huyền thoại với bọt sủi mịn như tơ, hương bánh mì nướng brioche, hạnh nhân rang, cam bergamot và khoáng chất tinh tế.",
                "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Moët & Chandon Impérial Brut Champagne", "Sparkling", "France", 2020, "1790000",
                "Dòng Champagne biểu tượng của Moët & Chandon từ năm 1869; hương táo xanh, cam chanh, hoa trắng và bánh quy bơ thanh lịch.",
                "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Veuve Clicquot Yellow Label Brut", "Sparkling", "France", 2019, "1950000",
                "Champagne giàu cấu trúc với tỷ lệ Pinot Noir chủ đạo; hương lê chín, mận vàng, vani và bánh nướng brioche quyến rũ.",
                "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80"));
        wines.add(buildWine("Valdo Marca Oro Valdobbiadene Prosecco Superiore DOCG", "Sparkling", "Italy", 2022, "680000",
                "Prosecco Superiore DOCG sủi bọt tươi tắn, hương táo vàng Golden, hoa tử đằng và mật ong rừng nhẹ nhàng, hoàn hảo cho khai vị tiệc.",
                "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80"));

        wineRepository.saveAll(wines);
    }

    private void ensureWarehouseAndInventory() {
        Warehouse warehouse = warehouseRepository.findByName("Main Warehouse")
                .or(() -> warehouseRepository.findByName("Kho Tong TP.HCM"))
                .orElseGet(() -> {
                    Warehouse w = new Warehouse();
                    w.setName("Main Warehouse");
                    w.setLocation("Thành phố Hồ Chí Minh");
                    w.setActive(true);
                    return warehouseRepository.save(w);
                });

        if ("Ho Chi Minh City".equalsIgnoreCase(warehouse.getLocation())) {
            warehouse.setLocation("Thành phố Hồ Chí Minh");
            warehouseRepository.save(warehouse);
        }

        List<Wine> wines = wineRepository.findByDeletedFalse();
        for (Wine wine : wines) {
            Inventory inventory = inventoryRepository.findByWineIdAndWarehouseId(wine.getId(), warehouse.getId())
                    .orElseGet(() -> {
                        Inventory inv = new Inventory();
                        inv.setWine(wine);
                        inv.setWarehouse(warehouse);
                        inv.setCurrentQuantity(60);
                        inv.setReservedQuantity(0);
                        inv.setReorderLevel(10);
                        return inventoryRepository.save(inv);
                    });

            if (inventory.getCurrentQuantity() == null || inventory.getCurrentQuantity() <= 0) {
                inventory.setCurrentQuantity(60);
                inventory.setReservedQuantity(0);
                inventoryRepository.save(inventory);
            }
        }
    }

    private Wine buildWine(String name, String type, String country, Integer year, String price, String description, String imageUrl) {
        Wine wine = new Wine();
        wine.setName(name);
        wine.setType(type);
        wine.setCountry(country);
        wine.setYear(year);
        wine.setPrice(new BigDecimal(price));
        wine.setDescription(description);
        wine.setImageUrl(imageUrl);
        return wine;
    }

    private void ensureDefaultShippers() {
        ensureShipperProfile("shipper1", "Trần Minh Hùng", "0909123456", "Xe máy bảo ôn");
        ensureShipperProfile("shipper2", "Phạm Thị Linh", "0932555789", "Xe máy bảo ôn");
        ensureShipperProfile("shipper.ops.hcm", "Trần Minh Hùng", "0909123456", "Xe máy bảo ôn chuyên dụng");
        ensureShipperProfile("shipper.q7.hcm", "Võ Tuấn Kiệt", "0903888912", "Xe van lạnh 500kg");
        ensureShipperProfile("shipper.hn.ops", "Ngô Quốc Tuấn", "0912666839", "Xe máy bảo ôn chuyên dụng");
    }

    private void ensureShipperProfile(String username, String name, String phone, String vehicleType) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || shipperRepository.existsByUserId(user.getId())) {
            return;
        }

        Shipper shipper = new Shipper();
        shipper.setUser(user);
        shipper.setName(name);
        shipper.setPhone(phone);
        shipper.setVehicleType(vehicleType);
        shipper.setStatus(ShipperStatus.ACTIVE);
        shipper.setIsAvailable(true);
        shipper.setMaxConcurrentShipments(5);
        shipper.setCreatedAt(LocalDateTime.now());
        shipper.setUpdatedAt(LocalDateTime.now());
        shipperRepository.save(shipper);
    }
}
