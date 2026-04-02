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
 * Data initializer to create default admin user if it doesn't exist
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
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            userRepository.save(admin);
        }

        if (!userRepository.existsByUsername("demo")) {
            User user = new User();
            user.setUsername("demo");
            user.setPassword(passwordEncoder.encode("demo123"));
            user.setRole("USER");
            userRepository.save(user);
        }

        if (!userRepository.existsByUsername("shipper1")) {
            User shipper = new User();
            shipper.setUsername("shipper1");
            shipper.setPassword(passwordEncoder.encode("shipper123"));
            shipper.setRole("SHIPPER");
            userRepository.save(shipper);
        }

        if (!userRepository.existsByUsername("shipper2")) {
            User shipper = new User();
            shipper.setUsername("shipper2");
            shipper.setPassword(passwordEncoder.encode("shipper123"));
            shipper.setRole("SHIPPER");
            userRepository.save(shipper);
        }
    }

    private void ensureDefaultWines() {
        if (wineRepository.count() > 0) {
            return;
        }

        List<Wine> wines = new ArrayList<>();
        wines.add(buildWine("Chateau Margaux", "Red", "France", 2018, "8990000", "Vang đỏ cao cấp đến từ Bordeaux."));
        wines.add(buildWine("Dom Perignon", "Sparkling", "France", 2012, "4990000", "Champagne cao cấp cho dịp đặc biệt."));
        wines.add(buildWine("Sancerre Blanc", "White", "France", 2020, "790000", "Vang trắng thanh mát với hương citrus."));
        wines.add(buildWine("Pinot Noir Reserve", "Red", "USA", 2019, "1150000", "Hương cherry và vị mềm mại."));
        wines.add(buildWine("Prosecco DOCG", "Sparkling", "Italy", 2021, "650000", "Vang sủi nhẹ, dễ uống."));
        wines.add(buildWine("Chardonnay Barrel Aged", "White", "Australia", 2020, "880000", "Vị bơ và hương gỗ sồi."));
        wines.add(buildWine("Rose de Provence", "Rose", "France", 2021, "720000", "Vang hồng thanh lịch, hương trái đỏ."));
        wines.add(buildWine("Cabernet Sauvignon", "Red", "Chile", 2018, "980000", "Đậm vị, hậu vị dài."));
        wines.add(buildWine("Sauvignon Blanc", "White", "New Zealand", 2021, "760000", "Thơm mùi nhiệt đới, vị chua nhẹ."));
        wines.add(buildWine("Champagne Brut", "Sparkling", "France", 2019, "1590000", "Bọt mịn và cân bằng vị tốt."));

        wineRepository.saveAll(wines);
    }

    private void ensureWarehouseAndInventory() {
        Warehouse warehouse = warehouseRepository.findByName("Main Warehouse")
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

    private Wine buildWine(String name, String type, String country, Integer year, String price, String description) {
        Wine wine = new Wine();
        wine.setName(name);
        wine.setType(type);
        wine.setCountry(country);
        wine.setYear(year);
        wine.setPrice(new BigDecimal(price));
        wine.setDescription(description);
        wine.setImageUrl("https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80");
        return wine;
    }

    private void ensureDefaultShippers() {
        ensureShipperProfile("shipper1", "Shipper mẫu 1", "0900000001", "Xe máy");
        ensureShipperProfile("shipper2", "Shipper mẫu 2", "0900000002", "Xe máy");
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
        shipper.setCreatedAt(LocalDateTime.now());
        shipper.setUpdatedAt(LocalDateTime.now());
        shipperRepository.save(shipper);
    }
}





