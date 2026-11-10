package com.strongwine.strongwine.config;

import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data initializer to ensure default accounts, fine wine catalog,
 * Vietnamese wine collections, cold-chain warehouses, inventory,
 * shipper fleet, and rich payment transactions exist at startup.
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
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ShipperRepository shipperRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Value("${strongwine.startup.initializers-enabled:true}")
    private boolean startupInitializersEnabled;

    @Override
    public void run(String... args) throws Exception {
        if (!startupInitializersEnabled) {
            return;
        }

        ensureDefaultUsers();
        ensureDefaultWines();
        updateExistingWinesToTransparentImages();
        ensureVietnameseCategoriesAndWines();
        ensureWarehouseAndInventory();
        ensureDefaultShippers();
        ensureComprehensivePaymentsAndOrders();
    }

    private void ensureDefaultUsers() {
        ensureUserAccount("admin", "admin123", "ADMIN", "admin@strongwine.vn");
        ensureUserAccount("demo", "demo123", "USER", "khachhang.demo@strongwine.vn");
        ensureUserAccount("nguyen.minh.quan", "demo123", "USER", "quan.nguyen@strongwine.vn");
        ensureUserAccount("tran.bao.ngoc", "demo123", "USER", "ngoc.tran@strongwine.vn");
        ensureUserAccount("le.hoang.nam", "demo123", "USER", "nam.le@strongwine.vn");
        ensureUserAccount("pham.khanh.linh", "demo123", "USER", "linh.pham@strongwine.vn");
        ensureUserAccount("vo.duc.thinh", "demo123", "USER", "thinh.vo@strongwine.vn");
        ensureUserAccount("dang.thu.ha", "demo123", "USER", "ha.dang@strongwine.vn");
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

    private void updateExistingWinesToTransparentImages() {
        // Upgrade legacy uploaded images with solid white backgrounds to transparent cutout SVGs
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Margaux").forEach(wine -> {
            wine.setImageUrl("/images/bottles/margaux-grand-cru.svg");
            wineRepository.save(wine);
        });
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Perignon").forEach(wine -> {
            wine.setImageUrl("/images/bottles/champagne-brut.svg");
            wineRepository.save(wine);
        });
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Sancerre").forEach(wine -> {
            wine.setImageUrl("/images/bottles/sauvignon-blanc.svg");
            wineRepository.save(wine);
        });
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Pinot Noir").forEach(wine -> {
            wine.setImageUrl("/images/bottles/margaux-grand-cru.svg");
            wineRepository.save(wine);
        });
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Prosecco").forEach(wine -> {
            wine.setImageUrl("/images/bottles/champagne-brut.svg");
            wineRepository.save(wine);
        });
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Chardonnay").forEach(wine -> {
            wine.setImageUrl("/images/bottles/sauvignon-blanc.svg");
            wineRepository.save(wine);
        });
        wineRepository.findByNameContainingIgnoreCaseAndDeletedFalse("Syrah").forEach(wine -> {
            if (wine.getImageUrl() == null || wine.getImageUrl().contains("/uploads/")) {
                wine.setImageUrl("/images/bottles/ninhthuan-reserve.svg");
                wineRepository.save(wine);
            }
        });
    }

    private void ensureDefaultWines() {
        if (wineRepository.count() > 0) {
            return;
        }

        List<Wine> wines = new ArrayList<>();
        wines.add(buildWine("Château Margaux Premier Grand Cru Classé", "Red", "France", 2018, "8990000",
                "Biểu tượng Premier Grand Cru Classé vùng Margaux với hương lý chua đen, hoa tím, gỗ tuyết tùng và hậu vị nhung lụa kéo dài.",
                "/images/bottles/margaux-grand-cru.svg"));
        wines.add(buildWine("Château Lafite Rothschild Pauillac", "Red", "France", 2017, "14500000",
                "Tuyệt phẩm Pauillac 1er Grand Cru Classé mang tầng hương phức hợp của xì gà Havana, quả mâm xôi chín, khoáng chì và tannin hoàn mỹ.",
                "/images/bottles/margaux-grand-cru.svg"));
        wines.add(buildWine("Opus One Napa Valley Reserve", "Red", "USA", 2019, "9850000",
                "Sự giao thoa đỉnh cao giữa Baron Philippe de Rothschild và Robert Mondavi; đậm đà hương cassis, sô-cô-la đen, espresso và thảo mộc.",
                "/images/bottles/margaux-grand-cru.svg"));
        wines.add(buildWine("Penfolds Grange Bin 95 Shiraz", "Red", "Australia", 2018, "12900000",
                "Biểu tượng vang Úc từ những gốc nho Shiraz cổ thụ vùng Barossa; vị mận đen cô đặc, cam thảo, tiêu đen và gỗ sồi Mỹ ủ 20 tháng.",
                "/images/bottles/margaux-grand-cru.svg"));
        wines.add(buildWine("Sassicaia Tenuta San Guido Bolgheri", "Red", "Italy", 2019, "7600000",
                "Huyền thoại Super Tuscan từ Cabernet Sauvignon và Cabernet Franc; thanh lịch với hương anh đào đen, hương thảo và vị khoáng biển.",
                "/images/bottles/margaux-grand-cru.svg"));
        wines.add(buildWine("Louis Jadot Puligny-Montrachet Blanc", "White", "France", 2020, "3150000",
                "Chardonnay thượng hạng vùng Côte de Beaune; hương hoa keo, táo nướng bơ, hạt dẻ cười và độ khoáng sắc sảo đặc trưng đá vôi.",
                "/images/bottles/sauvignon-blanc.svg"));
        wines.add(buildWine("Sancerre Blanc Les Monts Damnés", "White", "France", 2021, "1190000",
                "Sauvignon Blanc từ sườn đồi đá phấn Kimmeridgian; hương bưởi hồng, chanh vàng, cỏ mới cắt và vị khoáng đá lửa sống động.",
                "/images/bottles/sauvignon-blanc.svg"));
        wines.add(buildWine("Dom Pérignon Vintage Brut", "Sparkling", "France", 2013, "5490000",
                "Champagne niên vụ huyền thoại với bọt sủi mịn như tơ, hương bánh mì nướng brioche, hạnh nhân rang, cam bergamot và khoáng chất tinh tế.",
                "/images/bottles/champagne-brut.svg"));
        wines.add(buildWine("Moët & Chandon Impérial Brut Champagne", "Sparkling", "France", 2020, "1790000",
                "Dòng Champagne biểu tượng của Moët & Chandon từ năm 1869; hương táo xanh, cam chanh, hoa trắng và bánh quy bơ thanh lịch.",
                "/images/bottles/champagne-brut.svg"));

        wineRepository.saveAll(wines);
    }

    private void ensureVietnameseCategoriesAndWines() {
        // Ensure 3 Vietnamese Categories exist
        Category catDalat = ensureCategory("Vang Đà Lạt (Lâm Đồng)", "Dòng vang thượng phẩm sản xuất tại cao nguyên sương mù Đà Lạt");
        Category catNinhThuan = ensureCategory("Vang Nho Ninh Thuận (Phan Rang)", "Dòng vang đặc trưng từ vùng đất nắng gió Phan Rang Ninh Thuận");
        Category catTradition = ensureCategory("Rượu Tinh Hoa Truyền Thống Thượng Hạng", "Tuyệt phẩm rượu men lá, mơ rừng hạ thổ thủ công Việt Nam");

        List<Wine> newVietnameseWines = new ArrayList<>();

        // 10 Vang Đà Lạt
        addWineIfNotExist(newVietnameseWines, "Château Dalat Signature Cabernet", "Red", "Vietnam", 2021, "850000",
                "Dòng vang thượng hạng xuất khẩu của Ladofoods, ủ thùng sồi Pháp 18 tháng, hương dâu đen và thảo mộc cao nguyên.",
                "/images/bottles/dalat-signature.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Château Dalat Excellence Shiraz", "Red", "Vietnam", 2022, "450000",
                "Sắc đỏ ruby thẫm, hương quả mọng chín, tiêu đen và vị chát đậm đà, rất hợp với thịt nướng cao nguyên.",
                "/images/bottles/dalat-signature.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Château Dalat Special Merlot", "Red", "Vietnam", 2021, "380000",
                "Hương anh đào đỏ, mận chín, mượt mà dễ uống, nồng độ cồn hài hòa 12.5% lý tưởng cho bữa tiệc gia đình.",
                "/images/bottles/dalat-signature.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Vang Đà Lạt Classic Red", "Red", "Vietnam", 2023, "150000",
                "Dòng vang truyền thống trứ danh của xứ sở ngàn hoa, phối trộn từ giống nho Cardinal và dâu tằm Đà Lạt.",
                "/images/bottles/dalat-signature.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Vang Đà Lạt Classic White", "White", "Vietnam", 2023, "150000",
                "Vang trắng thanh nhẹ từ nho Chadonnay và Cardinal, hương bưởi đào và cam chanh thanh khiết sảng khoái.",
                "/images/bottles/sauvignon-blanc.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Vang Đà Lạt Premium Red Reserve", "Red", "Vietnam", 2022, "260000",
                "Hương trái cây nhiệt đới chín, ủ gỗ sồi nhẹ, tannin êm dịu phù hợp tiệc nướng BBQ và bít tết bò.",
                "/images/bottles/dalat-signature.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Château Dalat Reserve Sauvignon Blanc", "White", "Vietnam", 2022, "420000",
                "Vang trắng giòn tươi với hương ổi hồng, chanh leo nhiệt đới và hoa bưởi nở sớm trên sườn đồi Langbiang.",
                "/images/bottles/sauvignon-blanc.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Vang Đà Lạt Đankia Red", "Red", "Vietnam", 2022, "190000",
                "Lấy cảm hứng từ hồ Đankia hoang sơ, hương dâu tằm rừng cô đặc hòa cùng chút vani ngọt ngào quyến rũ.",
                "/images/bottles/dalat-signature.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Château Dalat Sparkling Hội Nghị", "Sparkling", "Vietnam", 2021, "520000",
                "Vang sủi bọt kỷ niệm phục vụ các hội nghị quốc tế, bọt sủi mịn màng, hương táo xanh và hoa lê trắng.",
                "/images/bottles/champagne-brut.svg", catDalat);
        addWineIfNotExist(newVietnameseWines, "Château Dalat Grand Cru Limited Edition", "Red", "Vietnam", 2020, "1250000",
                "Phiên bản giới hạn đánh số chai, ủ 24 tháng trong hầm lạnh Đà Lạt, cấu trúc tannin dày dặn quý phái.",
                "/images/bottles/dalat-signature.svg", catDalat);

        // 10 Vang Nho Ninh Thuận
        addWineIfNotExist(newVietnameseWines, "Vang Nho Phan Rang Reserve Syrah", "Red", "Vietnam", 2022, "580000",
                "Giống nho Syrah trồng trên sỏi đá vùng nắng gió Ninh Thuận, đậm đà vị mận khô, quế và thảo quả.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Đỏ Ba Mọi Cabernet Franc", "Red", "Vietnam", 2021, "490000",
                "Sản phẩm tâm huyết từ trang trại nho Ba Mọi, lên men tự nhiên, hương ớt chuông nướng và gỗ sồi ấm áp.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Trắng Ninh Thuận Coastal Blanc", "White", "Vietnam", 2023, "390000",
                "Nho trắng đón gió biển Ninh Chữ, vị khoáng mặn mòi đặc trưng, thơm ngát hương vỏ quýt và cỏ ngọt.",
                "/images/bottles/sauvignon-blanc.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Nho Đỏ Thái An Single Vineyard", "Red", "Vietnam", 2022, "420000",
                "Thu hoạch từ vườn nho Thái An ven vịnh Vĩnh Hy, vị chát dồi dào, hậu vị trái cây đen kéo dài lắng đọng.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Nho Xanh Bản Địa NH01-48", "White", "Vietnam", 2023, "320000",
                "Giống nho xanh bản địa chọn lọc NH01-48, hương hoa cúc vàng, lê xanh và mật ong hoa cỏ dịu ngọt.",
                "/images/bottles/sauvignon-blanc.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Ninh Thuận Sun-Kissed Sweet Red", "Red", "Vietnam", 2022, "280000",
                "Vang ngọt tráng miệng từ những chùm nho chín mọng đượm nắng hạ Phan Rang, vị ngọt dịu êm thanh thoát.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Nho Đỏ Ủ Thùng Sồi Viethaus", "Red", "Vietnam", 2021, "680000",
                "Dòng vang cao cấp ủ 14 tháng trong thùng gỗ sồi Mỹ, hương cà phê mocha, cacao và tiêu sọ nồng ấm.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Hồng Ninh Thuận Rosé Coastal", "Rose", "Vietnam", 2023, "350000",
                "Sắc hồng cánh sen lãng mạn, hương dâu tây, mâm xôi dại và vị chua thanh khiết của gió biển miền Trung.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Nho Phan Rang Vintage Selection", "Red", "Vietnam", 2020, "790000",
                "Tuyển chọn từ vụ mùa khô nóng hoàn hảo, hàm lượng đường tự nhiên cao và nồng độ đậm sâu 14% vol.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);
        addWineIfNotExist(newVietnameseWines, "Vang Nho Ninh Hải Mộc Truyền Thống", "Red", "Vietnam", 2023, "220000",
                "Hương vị vang thủ công mộc mạc lưu giữ nét văn hóa làm vang lâu đời của bà con diêm dân xứ Nắng.",
                "/images/bottles/ninhthuan-reserve.svg", catNinhThuan);

        // 10 Rượu Tinh Hoa Truyền Thống Thượng Hạng
        addWineIfNotExist(newVietnameseWines, "Rượu Mơ Rừng Yên Tử Thượng Hạng", "Traditional", "Vietnam", 2019, "650000",
                "Mơ rừng Yên Tử ngâm ủ trong chum sành không tráng men 5 năm, chua thanh ngọt hậu, nồng độ 19% vol.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Men Lá Tây Bắc Pà Cò", "Traditional", "Vietnam", 2020, "520000",
                "Chưng cất thủ công bằng 36 vị men lá thuốc bắc của đồng bào H'Mông, êm dịu, thơm nồng ấm áp.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Nếp Cái Hoa Vàng Hạ Thổ", "Traditional", "Vietnam", 2018, "480000",
                "Nếp cái hoa vàng Kinh Môn ủ men thuốc bắc gia truyền hạ thổ 3 năm, màu sánh vàng óng như mật ong rừng.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Mận Máu Tả Van Sa Pa", "Traditional", "Vietnam", 2021, "460000",
                "Chiết xuất từ mận máu Tả Van trên độ cao 1.600m mây mù, sắc tím huyền bí, vị ngọt chua quyến rũ mê hoặc.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Táo Mèo Mù Cang Chải", "Traditional", "Vietnam", 2020, "380000",
                "Quả sơn tra rừng tự nhiên ngâm rượu nếp nương, màu nâu hổ phách ấm áp, bổ huyết và trợ tiêu hóa.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Sâm Ngọc Linh Hoàng Gia", "Traditional", "Vietnam", 2019, "2850000",
                "Sâm Ngọc Linh củ Kon Tum thượng phẩm ngâm rượu gạo truyền thống, vị đắng ngọt sâu lưu luyến khó phai.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Cần Mường Vang Hòa Bình", "Traditional", "Vietnam", 2022, "350000",
                "Rượu cần men trấu truyền thống của người Mường, hương thơm ngát nồng đượm tình thân và văn hóa dân tộc.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Gạo Lứt Huyết Rồng Hạ Thổ", "Traditional", "Vietnam", 2020, "420000",
                "Gạo lứt đỏ huyết rồng hữu cơ lên men men cỏ voi, bổ dưỡng, hậu vị đậm đà không gắt.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Chuối Hột Rừng Tây Nguyên", "Traditional", "Vietnam", 2021, "320000",
                "Chuối hột rừng chín phơi sương ngâm rượu men trấu đất đỏ bazan, màu nâu cánh gián quý phái tốt cho gân cốt.",
                "/images/bottles/mo-yentu.svg", catTradition);
        addWineIfNotExist(newVietnameseWines, "Rượu Ba Kích Tím Rừng Quảng Ninh", "Traditional", "Vietnam", 2019, "720000",
                "Ba kích tím củ to ngâm ủ chum sành 36 tháng, màu tím thẫm đằm thắm, vị ngọt thanh tốt cho sức khỏe.",
                "/images/bottles/mo-yentu.svg", catTradition);

        if (!newVietnameseWines.isEmpty()) {
            wineRepository.saveAll(newVietnameseWines);
        }
    }

    private Category ensureCategory(String name, String description) {
        return categoryRepository.findByName(name).orElseGet(() -> {
            Category c = new Category();
            c.setName(name);
            c.setDescription(description);
            return categoryRepository.save(c);
        });
    }

    private void addWineIfNotExist(List<Wine> targetList, String name, String type, String country, Integer year,
                                   String price, String description, String imageUrl, Category category) {
        if (!wineRepository.existsByName(name)) {
            Wine wine = buildWine(name, type, country, year, price, description, imageUrl);
            wine.setCategory(category);
            targetList.add(wine);
        }
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
                        inv.setCurrentQuantity(80);
                        inv.setReservedQuantity(0);
                        inv.setReorderLevel(15);
                        return inventoryRepository.save(inv);
                    });

            if (inventory.getCurrentQuantity() == null || inventory.getCurrentQuantity() <= 0) {
                inventory.setCurrentQuantity(80);
                inventory.setReservedQuantity(0);
                inventoryRepository.save(inventory);
            }
        }
    }

    private void ensureComprehensivePaymentsAndOrders() {
        if (paymentRepository.count() >= 25) {
            return;
        }

        List<Wine> allWines = wineRepository.findByDeletedFalse();
        if (allWines.isEmpty()) {
            return;
        }

        List<User> users = List.of(
                userRepository.findByUsername("demo").orElse(null),
                userRepository.findByUsername("nguyen.minh.quan").orElse(null),
                userRepository.findByUsername("tran.bao.ngoc").orElse(null),
                userRepository.findByUsername("le.hoang.nam").orElse(null),
                userRepository.findByUsername("pham.khanh.linh").orElse(null),
                userRepository.findByUsername("vo.duc.thinh").orElse(null),
                userRepository.findByUsername("dang.thu.ha").orElse(null)
        ).stream().filter(java.util.Objects::nonNull).toList();

        if (users.isEmpty()) {
            return;
        }

        Shipper primaryShipper = shipperRepository.findAll().stream().findFirst().orElse(null);

        // Simulation parameters for 28 rich transactions
        PaymentMethod[] methods = {
                PaymentMethod.COD, PaymentMethod.STRIPE, PaymentMethod.VNPAY, PaymentMethod.MOMO,
                PaymentMethod.COD, PaymentMethod.STRIPE, PaymentMethod.VNPAY, PaymentMethod.COD,
                PaymentMethod.STRIPE, PaymentMethod.COD, PaymentMethod.MOMO, PaymentMethod.VNPAY,
                PaymentMethod.COD, PaymentMethod.STRIPE, PaymentMethod.COD, PaymentMethod.VNPAY,
                PaymentMethod.COD, PaymentMethod.STRIPE, PaymentMethod.MOMO, PaymentMethod.COD,
                PaymentMethod.VNPAY, PaymentMethod.COD, PaymentMethod.STRIPE, PaymentMethod.COD,
                PaymentMethod.MOMO, PaymentMethod.VNPAY, PaymentMethod.COD, PaymentMethod.STRIPE
        };

        PaymentStatus[] pStatuses = {
                PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS,
                PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.PENDING,
                PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS,
                PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.PENDING, PaymentStatus.SUCCESS,
                PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.FAILED,
                PaymentStatus.SUCCESS, PaymentStatus.SUCCESS, PaymentStatus.CANCELLED, PaymentStatus.SUCCESS,
                PaymentStatus.SUCCESS, PaymentStatus.PENDING, PaymentStatus.SUCCESS, PaymentStatus.SUCCESS
        };

        String[] customerNames = {
                "Nguyễn Thị Thanh Hương", "Lê Văn Phong", "Trần Bảo Ngọc", "Nguyễn Minh Quân",
                "Hoàng Thùy Linh", "Lê Hoàng Nam", "Võ Đức Thịnh", "Đặng Thu Hà",
                "Phạm Khánh Linh", "Đỗ Minh Tuấn", "Bùi Thanh Trúc", "Vũ Hoàng Nam",
                "Dương Hải Yến", "Trương Quốc Bảo", "Nguyễn Thu Thủy", "Đặng Văn Lâm",
                "Lý Hoàng Nam", "Ngô Quỳnh Anh", "Phan Thanh Hưng", "Đào Bá Lộc",
                "Hồ Quang Hiếu", "Trịnh Thăng Bình", "Châu Bùi", "Khánh Vy",
                "Trần Thành Nam", "Nguyễn Cao Kỳ Duyên", "Mai Phương Thúy", "Đỗ Mỹ Linh"
        };

        String[] addresses = {
                "12 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                "88 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh",
                "45 Trần Hưng Đạo, Quận Hoàn Kiếm, Hà Nội",
                "22 Bạch Đằng, Quận Hải Châu, Đà Nẵng",
                "105 Nam Kỳ Khởi Nghĩa, Quận 1, TP. Hồ Chí Minh",
                "32 Lý Thường Kiệt, Quận Hoàn Kiếm, Hà Nội",
                "67 Nguyễn Trãi, Phường 2, Quận 5, TP. Hồ Chí Minh",
                "15 Pasteur, Phường 6, Quận 3, TP. Hồ Chí Minh",
                "94 Nguyễn Du, Quận 1, TP. Hồ Chí Minh",
                "110 Hoàng Diệu, Quận Hải Châu, Đà Nẵng",
                "50 Phố Huế, Quận Hai Bà Trưng, Hà Nội",
                "18 Thi Sách, Quận 1, TP. Hồ Chí Minh",
                "72 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
                "35 Tràng Tiền, Quận Hoàn Kiếm, Hà Nội",
                "28 Lê Duẩn, Quận Hải Châu, Đà Nẵng",
                "19 Tôn Đức Thắng, Quận 1, TP. Hồ Chí Minh",
                "82 Hàm Nghi, Quận 1, TP. Hồ Chí Minh",
                "40 Điện Biên Phủ, Quận Ba Đình, Hà Nội",
                "55 Hai Bà Trưng, Quận 1, TP. Hồ Chí Minh",
                "23 Bà Triệu, Quận Hoàn Kiếm, Hà Nội",
                "14 Lê Duẩn, Quận 1, TP. Hồ Chí Minh",
                "99 Võ Văn Tần, Quận 3, TP. Hồ Chí Minh",
                "16 Phan Bội Châu, Quận Hoàn Kiếm, Hà Nội",
                "77 Hùng Vương, TP. Đà Lạt, Lâm Đồng",
                "102 Thống Nhất, TP. Phan Rang, Ninh Thuận",
                "33 Nguyễn Hữu Thọ, Quận 7, TP. Hồ Chí Minh",
                "64 Nguyễn Đình Chiểu, Quận 3, TP. Hồ Chí Minh",
                "125 Cầu Giấy, Quận Cầu Giấy, Hà Nội"
        };

        int count = Math.min(methods.length, customerNames.length);

        for (int i = 0; i < count; i++) {
            String reference = String.format("PAY-ORD-2026%02d-%04d", (i % 12) + 1, i + 1001);
            if (paymentRepository.existsByPaymentReference(reference)) {
                continue;
            }

            User user = users.get(i % users.size());
            Wine wine1 = allWines.get(i % allWines.size());
            Wine wine2 = allWines.get((i + 3) % allWines.size());

            int qty1 = (i % 2) + 1;
            int qty2 = (i % 3 == 0) ? 1 : 0;

            BigDecimal total = wine1.getPrice().multiply(BigDecimal.valueOf(qty1));
            if (qty2 > 0) {
                total = total.add(wine2.getPrice().multiply(BigDecimal.valueOf(qty2)));
            }

            PaymentMethod method = methods[i];
            PaymentStatus pStatus = pStatuses[i];
            OrderStatus oStatus = (pStatus == PaymentStatus.SUCCESS) ? OrderStatus.PAID :
                    ((pStatus == PaymentStatus.PENDING) ? OrderStatus.PENDING : OrderStatus.CANCELLED);

            LocalDateTime orderTime = LocalDateTime.now().minusDays(count - i).minusHours(i % 12);

            Order order = new Order();
            order.setUser(user);
            order.setOrderDate(orderTime);
            order.setTotalPrice(total);
            order.setStatus(oStatus);
            order.setPaymentStatus(pStatus);
            order.setPaymentMethod(method);
            order.setShippingFullName(customerNames[i]);
            order.setShippingPhone(String.format("09%02d%06d", 30 + (i % 60), 100000 + i * 73));
            order.setShippingEmail(user.getEmail() != null ? user.getEmail() : "khachhang." + i + "@strongwine.vn");
            order.setShippingAddress(addresses[i]);
            order.setPaymentReference(reference);
            if (pStatus == PaymentStatus.SUCCESS) {
                order.setPaidAt(orderTime.plusMinutes(5));
            }
            order = orderRepository.save(order);

            // Save order items
            OrderItem item1 = new OrderItem(order, wine1, qty1, wine1.getPrice());
            orderItemRepository.save(item1);
            if (qty2 > 0) {
                OrderItem item2 = new OrderItem(order, wine2, qty2, wine2.getPrice());
                orderItemRepository.save(item2);
            }

            // Create Payment
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setMethod(method);
            payment.setStatus(pStatus);
            payment.setAmount(total);
            payment.setCurrency("VND");
            payment.setPaymentReference(reference);
            payment.setCreatedAt(orderTime);
            payment.setUpdatedAt(orderTime.plusMinutes(5));

            String gatewayResp;
            if (method == PaymentMethod.STRIPE) {
                gatewayResp = String.format("{\"status\":\"%s\",\"id\":\"ch_3N8x%05d\",\"provider\":\"STRIPE\",\"fee\":15000}",
                        pStatus == PaymentStatus.SUCCESS ? "succeeded" : "requires_payment_method", i);
            } else if (method == PaymentMethod.VNPAY) {
                gatewayResp = String.format("{\"vnp_ResponseCode\":\"%s\",\"vnp_TransactionNo\":\"143928%03d\",\"vnp_BankCode\":\"NCB\"}",
                        pStatus == PaymentStatus.SUCCESS ? "00" : "24", i);
            } else if (method == PaymentMethod.MOMO) {
                gatewayResp = String.format("{\"resultCode\":%d,\"message\":\"%s\",\"transId\":\"283918%03d\"}",
                        pStatus == PaymentStatus.SUCCESS ? 0 : 1001,
                        pStatus == PaymentStatus.SUCCESS ? "Thanh toán MoMo thành công" : "Giao dịch bị từ chối", i);
            } else {
                gatewayResp = (pStatus == PaymentStatus.SUCCESS) ? "{\"status\":\"COD_CONFIRMED\",\"note\":\"Khách đã thanh toán khi nhận hàng\"}" :
                        "{\"status\":\"COD_PENDING\",\"note\":\"Chờ thu tiền mặt khi giao nhận\"}";
            }
            payment.setGatewayResponse(gatewayResp);
            payment = paymentRepository.save(payment);

            // Payment Transaction
            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setPayment(payment);
            transaction.setTransactionType(method.name() + "_CHARGE");
            transaction.setStatus(pStatus.name());
            transaction.setPayload(gatewayResp);
            transaction.setCreatedAt(orderTime.plusMinutes(2));
            paymentTransactionRepository.save(transaction);

            // Create corresponding Shipment if success or pending
            if (pStatus != PaymentStatus.FAILED && pStatus != PaymentStatus.CANCELLED) {
                Shipment shipment = new Shipment();
                shipment.setOrder(order);
                shipment.setShipper(primaryShipper);
                shipment.setShippingName(order.getShippingFullName());
                shipment.setShippingPhone(order.getShippingPhone());
                shipment.setShippingEmail(order.getShippingEmail());
                shipment.setShippingAddress(order.getShippingAddress());
                shipment.setShippingLatitude(10.7769 + (i * 0.005));
                shipment.setShippingLongitude(106.7009 + (i * 0.004));

                String otp = String.format("%06d", (i * 38291 + 123456) % 1000000);
                shipment.setOtpCode(otp);
                shipment.setOtpCreatedAt(orderTime.plusMinutes(10));
                shipment.setOtpExpiresAt(orderTime.plusHours(48));

                if (i < 15) {
                    shipment.setStatus(ShipmentStatus.COMPLETED);
                    shipment.setOtpVerified(true);
                    shipment.setOtpDeliveryStatus(OtpDeliveryStatus.SENT);
                } else if (i < 22) {
                    shipment.setStatus(ShipmentStatus.DELIVERING);
                    shipment.setOtpVerified(false);
                    shipment.setOtpDeliveryStatus(OtpDeliveryStatus.SENT);
                } else {
                    shipment.setStatus(ShipmentStatus.ASSIGNED);
                    shipment.setOtpVerified(false);
                    shipment.setOtpDeliveryStatus(OtpDeliveryStatus.PENDING);
                }

                shipmentRepository.save(shipment);
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
