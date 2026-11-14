package com.strongwine.strongwine.service;

import com.strongwine.strongwine.dto.VoucherValidationResult;
import com.strongwine.strongwine.entity.*;
import com.strongwine.strongwine.event.AdminAlertEvent;
import com.strongwine.strongwine.event.LowStockAlertEvent;
import com.strongwine.strongwine.repository.AdminAuditLogRepository;
import com.strongwine.strongwine.service.port.CodSettlementPort;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import com.strongwine.strongwine.service.port.VoucherPricingPort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V3FoundationSchemaAndServicesTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @Test
    @DisplayName("V18 money columns in JPA entities must declare precision=18, scale=2 and nullable=false")
    void moneyColumnsDeclarePrecision18Scale2() throws NoSuchFieldException {
        assertMoneyColumn(Order.class, "totalPrice", false);
        assertMoneyColumn(OrderItem.class, "price", false);
        assertMoneyColumn(Wine.class, "price", false);
        assertMoneyColumn(Payment.class, "amount", false);
    }

    @Test
    @DisplayName("OpenPDF 3.0.5 org.openpdf.text.Document is available on classpath")
    void openPdfAvailableOnClasspath() throws ClassNotFoundException {
        Class<?> docClass = Class.forName("org.openpdf.text.Document");
        assertThat(docClass).isNotNull();
    }

    @Test
    @DisplayName("V17 and V18 Flyway migrations exist without GO separators and cover required objects")
    void migrationScriptsExistAndFollowTSqlRules() throws Exception {
        ClassPathResource v17 = new ClassPathResource("db/migration/V17__strongwine_v3_domain_foundation.sql");
        ClassPathResource v18 = new ClassPathResource("db/migration/V18__widen_money_columns_precision_18.sql");
        assertThat(v17.exists()).isTrue();
        assertThat(v18.exists()).isTrue();

        String v17Sql = v17.getContentAsString(StandardCharsets.UTF_8);
        String v18Sql = v18.getContentAsString(StandardCharsets.UTF_8);

        assertThat(v17Sql).doesNotContainPattern("(?im)^\\s*GO\\s*$");
        assertThat(v18Sql).doesNotContainPattern("(?im)^\\s*GO\\s*$");

        for (String table : List.of(
                "user_addresses", "wishlist_items", "vouchers", "voucher_usages",
                "return_requests", "shipper_cod_batches", "admin_audit_logs",
                "notification_logs", "sse_event_journal"
        )) {
            assertThat(v17Sql).containsIgnoringCase(table);
        }

        assertThat(v18Sql).contains("DECIMAL(18,2) NOT NULL");
    }

    @Test
    @DisplayName("All 9 V3 entities have @Entity, @Table, @Id and V3 enum constants exist")
    void v3EntitiesAndEnumsAreValid() {
        List<Class<?>> v3Entities = List.of(
                UserAddress.class, WishlistItem.class, Voucher.class, VoucherUsage.class,
                ReturnRequest.class, ShipperCodBatch.class, AdminAuditLog.class,
                NotificationLog.class, SseEventJournal.class
        );
        for (Class<?> clazz : v3Entities) {
            assertThat(clazz.isAnnotationPresent(Entity.class)).as(clazz.getSimpleName()).isTrue();
            assertThat(clazz.isAnnotationPresent(Table.class)).as(clazz.getSimpleName()).isTrue();
            boolean hasId = false;
            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(Id.class)) {
                    hasId = true;
                    break;
                }
            }
            assertThat(hasId).as(clazz.getSimpleName() + " @Id").isTrue();
        }

        assertThat(OrderStatus.values()).contains(OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED);
        assertThat(PaymentStatus.values()).contains(PaymentStatus.REFUND_REQUIRED, PaymentStatus.REFUNDED);
        assertThat(ShipmentStatus.values()).contains(ShipmentStatus.CANCELLED);
        assertThat(VoucherType.values()).containsExactlyInAnyOrder(VoucherType.PERCENT, VoucherType.FIXED, VoucherType.FREESHIP);
        assertThat(ReturnStatus.values()).containsExactlyInAnyOrder(ReturnStatus.REQUESTED, ReturnStatus.APPROVED, ReturnStatus.REJECTED, ReturnStatus.REFUNDED);
        assertThat(CodBatchStatus.values()).containsExactlyInAnyOrder(CodBatchStatus.PENDING_SETTLEMENT, CodBatchStatus.VERIFIED, CodBatchStatus.DISPUTED);
    }

    @Test
    @DisplayName("ShippingFeeService calculates Haversine distance and all 5 fee tiers accurately")
    void shippingFeeServiceTiers() {
        ShippingFeeService service = new ShippingFeeService();

        double samePoint = service.calculateDistanceKm(10.7769, 106.7009, 10.7769, 106.7009);
        assertThat(samePoint).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));

        double hcmToHanoi = service.calculateDistanceKm(10.7769, 106.7009, 21.0285, 105.8542);
        assertThat(hcmToHanoi).isBetween(1100.0, 1200.0);

        assertThat(service.calculateFee(25.0, new BigDecimal("3000000"))).isEqualByComparingTo("0");
        assertThat(service.calculateFee(null, new BigDecimal("3500000"))).isEqualByComparingTo("0");
        assertThat(service.calculateFee(0.5, new BigDecimal("1000000"))).isEqualByComparingTo("25000");
        assertThat(service.calculateFee(5.0, new BigDecimal("2999999"))).isEqualByComparingTo("25000");
        assertThat(service.calculateFee(5.1, new BigDecimal("1500000"))).isEqualByComparingTo("45000");
        assertThat(service.calculateFee(15.0, new BigDecimal("1500000"))).isEqualByComparingTo("45000");
        assertThat(service.calculateFee(15.1, new BigDecimal("1500000"))).isEqualByComparingTo("75000");
        assertThat(service.calculateFee(null, new BigDecimal("1500000"))).isEqualByComparingTo("35000");
    }

    @Test
    @DisplayName("ShipmentOtpCryptoService encrypts OTP with AES-256-GCM and decrypts cleanly")
    void shipmentOtpCryptoRoundTripAndTamperRejection() {
        ShipmentOtpCryptoService crypto = new ShipmentOtpCryptoService("strongwine-test-secret-key-2026");

        String cipher1 = crypto.encrypt("482910");
        String cipher2 = crypto.encrypt("482910");

        assertThat(cipher1).isNotBlank();
        assertThat(cipher1).doesNotContain("482910");
        assertThat(cipher1).isNotEqualTo(cipher2);
        assertThat(crypto.decrypt(cipher1)).contains("482910");
        assertThat(crypto.decrypt(cipher2)).contains("482910");

        // Wrong key or tampered ciphertext fails cleanly
        ShipmentOtpCryptoService otherKeyCrypto = new ShipmentOtpCryptoService("different-key");
        assertThat(otherKeyCrypto.decrypt(cipher1)).isEmpty();
        assertThat(crypto.decrypt("not-valid-cipher")).isEmpty();
        assertThat(crypto.decrypt(null)).isEmpty();
        assertThatThrownBy(() -> crypto.decryptOrThrow("corrupt-payload"))
                .isInstanceOf(RuntimeException.class);

        // Blank key falls back safely for local dev without crashing startup
        ShipmentOtpCryptoService devFallbackCrypto = new ShipmentOtpCryptoService("");
        String devCipher = devFallbackCrypto.encrypt("123456");
        assertThat(devFallbackCrypto.decrypt(devCipher)).contains("123456");
    }

    @Test
    @DisplayName("AdminAuditLogService serializes states to JSON safely and persists AdminAuditLog")
    void adminAuditLogServicePersistsSerializedAuditLog() {
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        AdminAuditLogService auditLogService = new AdminAuditLogService(adminAuditLogRepository);

        auditLogService.log(
                "admin.ops",
                "UPDATE_PRICE",
                "Wine",
                42L,
                Map.of("price", 980000),
                Map.of("price", 1250000),
                "10.0.0.5"
        );

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(captor.capture());

        AdminAuditLog saved = captor.getValue();
        assertThat(saved.getActorUsername()).isEqualTo("admin.ops");
        assertThat(saved.getAction()).isEqualTo("UPDATE_PRICE");
        assertThat(saved.getTargetType()).isEqualTo("Wine");
        assertThat(saved.getTargetId()).isEqualTo(42L);
        assertThat(saved.getBeforeJson()).contains("980000");
        assertThat(saved.getAfterJson()).contains("1250000");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.5");
        assertThat(saved.getCreatedAt()).isNotNull();

        // Null states do not crash
        auditLogService.log(null, "DELETE_VOUCHER", "Voucher", 7L, null, null, null);
    }

    @Test
    @DisplayName("Frozen cross-worker ports, DTOs, and shared events expose expected contracts")
    void portsAndEventsExistWithContracts() {
        assertThat(CodSettlementPort.class.isInterface()).isTrue();
        assertThat(ShipmentOrderLifecyclePort.class.isInterface()).isTrue();
        assertThat(VoucherPricingPort.class.isInterface()).isTrue();

        VoucherValidationResult result = VoucherValidationResult.invalid(
                "EXPIRED10", new BigDecimal("25000"), new BigDecimal("500000"), "Voucher expired"
        );
        assertThat(result.valid()).isFalse();
        assertThat(result.finalTotal()).isEqualByComparingTo("525000");

        LowStockAlertEvent lowStock = new LowStockAlertEvent(this, 1L, 2L, "Margaux", 3L, "HCM", 2, 10);
        assertThat(lowStock.getWineId()).isEqualTo(2L);
        assertThat(lowStock.getAvailableQuantity()).isEqualTo(2);

        AdminAlertEvent adminAlert = new AdminAlertEvent(this, "Late payment", "Refund required", "/admin/orders/5", "WARN", 5L);
        assertThat(adminAlert.getTitle()).isEqualTo("Late payment");
        assertThat(adminAlert.getReferenceOrderId()).isEqualTo(5L);
    }

    private void assertMoneyColumn(Class<?> entityClass, String fieldName, boolean nullable) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column col = field.getAnnotation(Column.class);
        assertThat(col).as(entityClass.getSimpleName() + "." + fieldName + " @Column").isNotNull();
        assertThat(col.precision()).as(entityClass.getSimpleName() + "." + fieldName + " precision").isEqualTo(18);
        assertThat(col.scale()).as(entityClass.getSimpleName() + "." + fieldName + " scale").isEqualTo(2);
        assertThat(col.nullable()).as(entityClass.getSimpleName() + "." + fieldName + " nullable").isEqualTo(nullable);
    }
}
