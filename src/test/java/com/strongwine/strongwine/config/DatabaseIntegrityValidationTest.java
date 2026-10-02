package com.strongwine.strongwine.config;

import com.strongwine.strongwine.entity.*;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 6.1: Strict Database Integrity Verification Test
 * Xác thực tính toàn vẹn 100% của CSDL:
 * 1. Chế độ DDL-auto trong application.properties bắt buộc là 'validate'
 * 2. Cấm hoàn toàn tự ý sửa đổi schema: spring.sql.init.mode = 'never'
 * 3. Tất cả các Entity cốt lõi bảo toàn đúng tên bảng, có khóa chính @Id hợp lệ.
 */
class DatabaseIntegrityValidationTest {

    @Test
    @DisplayName("Xác thực cấu hình application.properties: ddl-auto=validate và init.mode=never")
    void testApplicationPropertiesDatabaseIntegrityRules() throws IOException {
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        String ddlAuto = props.getProperty("spring.jpa.hibernate.ddl-auto");
        assertEquals("validate", ddlAuto, "DDL auto phải nghiêm ngặt đặt ở chế độ 'validate' để bảo vệ toàn vẹn CSDL");

        String initMode = props.getProperty("spring.sql.init.mode");
        assertEquals("never", initMode, "SQL init mode phải đặt ở 'never' để ngăn chặn bất kỳ hành vi drop/create bảng nào");
    }

    @Test
    @DisplayName("Xác thực tất cả Entity cốt lõi có annotation @Entity, @Table và @Id hợp lệ")
    void testCoreEntitiesSchemaIntegrity() {
        List<Class<?>> entityClasses = List.of(
                Wine.class,
                Category.class,
                Inventory.class,
                Warehouse.class,
                Order.class,
                OrderItem.class,
                Shipment.class,
                Shipper.class,
                User.class,
                Payment.class
        );

        for (Class<?> clazz : entityClasses) {
            assertTrue(clazz.isAnnotationPresent(Entity.class), 
                    clazz.getSimpleName() + " phải được đánh dấu @Entity");
            assertTrue(clazz.isAnnotationPresent(Table.class), 
                    clazz.getSimpleName() + " phải có annotation @Table chỉ định tên bảng CSDL");

            Table table = clazz.getAnnotation(Table.class);
            assertFalse(table.name().trim().isEmpty(), 
                    clazz.getSimpleName() + " @Table name không được để trống");

            // Kiểm tra có trường khóa chính @Id
            boolean hasId = false;
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        hasId = true;
                        break;
                    }
                }
                if (hasId) break;
                current = current.getSuperclass();
            }
            assertTrue(hasId, clazz.getSimpleName() + " phải có ít nhất 1 trường đánh dấu @Id");
        }
    }

    @Test
    @DisplayName("Xác thực bảng dữ liệu cốt lõi không bị thay đổi hoặc xóa bỏ")
    void testProtectedTableNamesPreserved() {
        Set<String> expectedTables = Set.of(
                "wines", "categories", "inventory", "warehouse",
                "orders", "order_items", "shipments", "shippers", "users", "payments"
        );

        List<Class<?>> entityClasses = List.of(
                Wine.class, Category.class, Inventory.class, Warehouse.class,
                Order.class, OrderItem.class, Shipment.class, Shipper.class, User.class, Payment.class
        );

        Set<String> actualTables = entityClasses.stream()
                .map(c -> c.getAnnotation(Table.class).name().toLowerCase())
                .collect(Collectors.toSet());

        for (String expected : expectedTables) {
            assertTrue(actualTables.contains(expected), 
                    "Bảng được bảo vệ '" + expected + "' phải tồn tại trong cấu trúc Entity");
        }
    }
}
