package com.strongwine.strongwine.service;

import com.strongwine.strongwine.controller.VoucherAdminController;
import com.strongwine.strongwine.controller.api.VoucherApiController;
import com.strongwine.strongwine.dto.VoucherValidationResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Voucher;
import com.strongwine.strongwine.entity.VoucherType;
import com.strongwine.strongwine.entity.VoucherUsage;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.VoucherRepository;
import com.strongwine.strongwine.repository.VoucherUsageRepository;
import com.strongwine.strongwine.service.port.VoucherPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private UserRepository userRepository;

    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherService(voucherRepository, voucherUsageRepository, adminAuditLogService);
    }

    private Voucher activeVoucher(Long id, String code, VoucherType type, BigDecimal value) {
        Voucher v = new Voucher();
        v.setId(id);
        v.setCode(code);
        v.setType(type);
        v.setValue(value);
        v.setMinOrderValue(BigDecimal.ZERO);
        v.setUsedCount(0);
        v.setActive(true);
        v.setStartsAt(LocalDateTime.now().minusDays(1));
        v.setExpiresAt(LocalDateTime.now().plusDays(7));
        return v;
    }

    @Test
    @DisplayName("VoucherService implements VoucherPricingPort and rejects blank, missing, and inactive codes")
    void validateAndQuoteRejectsBlankMissingAndInactiveCodes() {
        assertThat(voucherService).isInstanceOf(VoucherPricingPort.class);

        BigDecimal subtotal = new BigDecimal("500000");
        BigDecimal shipping = new BigDecimal("25000");

        VoucherValidationResult blankResult = voucherService.validateAndQuote("   ", 1L, subtotal, shipping);
        assertThat(blankResult.valid()).isFalse();
        assertThat(blankResult.discountAmount()).isEqualByComparingTo("0");
        assertThat(blankResult.finalShippingFee()).isEqualByComparingTo("25000");
        assertThat(blankResult.finalTotal()).isEqualByComparingTo("525000");

        when(voucherRepository.findByCodeIgnoreCase("UNKNOWN10")).thenReturn(Optional.empty());
        VoucherValidationResult missingResult = voucherService.validateAndQuote(" unknown10 ", 1L, subtotal, shipping);
        assertThat(missingResult.valid()).isFalse();
        assertThat(missingResult.finalTotal()).isEqualByComparingTo("525000");

        Voucher inactive = activeVoucher(1L, "PAUSED10", VoucherType.PERCENT, new BigDecimal("10"));
        inactive.setActive(false);
        when(voucherRepository.findByCodeIgnoreCase("PAUSED10")).thenReturn(Optional.of(inactive));
        VoucherValidationResult inactiveResult = voucherService.validateAndQuote("paused10", 1L, subtotal, shipping);
        assertThat(inactiveResult.valid()).isFalse();
        assertThat(inactiveResult.finalTotal()).isEqualByComparingTo("525000");
    }

    @Test
    @DisplayName("validateAndQuote enforces date window, minOrderValue, usageLimit, and per-user duplicate usage")
    void validateAndQuoteEnforcesWindowsLimitsAndPerUserCheck() {
        BigDecimal subtotal = new BigDecimal("500000");
        BigDecimal shipping = new BigDecimal("35000");

        Voucher notStarted = activeVoucher(1L, "FUTURE10", VoucherType.FIXED, new BigDecimal("50000"));
        notStarted.setStartsAt(LocalDateTime.now().plusHours(2));
        when(voucherRepository.findByCodeIgnoreCase("FUTURE10")).thenReturn(Optional.of(notStarted));
        assertThat(voucherService.validateAndQuote("FUTURE10", 10L, subtotal, shipping).valid()).isFalse();

        Voucher expired = activeVoucher(2L, "OLD10", VoucherType.FIXED, new BigDecimal("50000"));
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        when(voucherRepository.findByCodeIgnoreCase("OLD10")).thenReturn(Optional.of(expired));
        assertThat(voucherService.validateAndQuote("OLD10", 10L, subtotal, shipping).valid()).isFalse();

        Voucher minOrder = activeVoucher(3L, "MIN1M", VoucherType.FIXED, new BigDecimal("100000"));
        minOrder.setMinOrderValue(new BigDecimal("1000000"));
        when(voucherRepository.findByCodeIgnoreCase("MIN1M")).thenReturn(Optional.of(minOrder));
        assertThat(voucherService.validateAndQuote("MIN1M", 10L, new BigDecimal("999999"), shipping).valid()).isFalse();

        Voucher exhausted = activeVoucher(4L, "MAXED", VoucherType.FIXED, new BigDecimal("50000"));
        exhausted.setUsageLimit(5);
        exhausted.setUsedCount(5);
        when(voucherRepository.findByCodeIgnoreCase("MAXED")).thenReturn(Optional.of(exhausted));
        assertThat(voucherService.validateAndQuote("MAXED", 10L, subtotal, shipping).valid()).isFalse();

        Voucher usedByUser = activeVoucher(5L, "ONCE", VoucherType.FIXED, new BigDecimal("50000"));
        when(voucherRepository.findByCodeIgnoreCase("ONCE")).thenReturn(Optional.of(usedByUser));
        when(voucherUsageRepository.existsByVoucherIdAndUserId(5L, 10L)).thenReturn(true);
        assertThat(voucherService.validateAndQuote("ONCE", 10L, subtotal, shipping).valid()).isFalse();

        // Anonymous quote (userId == null) skips per-user usage lookup and succeeds
        VoucherValidationResult anonQuote = voucherService.validateAndQuote("ONCE", null, subtotal, shipping);
        assertThat(anonQuote.valid()).isTrue();
        assertThat(anonQuote.discountAmount()).isEqualByComparingTo("50000.00");
        verify(voucherUsageRepository, never()).existsByVoucherIdAndUserId(eq(5L), isNull());
    }

    @Test
    @DisplayName("validateAndQuote calculates PERCENT with maxDiscount cap, RoundingMode.HALF_UP, and subtotal ceiling")
    void validateAndQuoteCalculatesPercentWithMaxCapAndHalfUp() {
        Voucher cappedPercent = activeVoucher(10L, "VIP15", VoucherType.PERCENT, new BigDecimal("15"));
        cappedPercent.setMaxDiscount(new BigDecimal("100000"));
        when(voucherRepository.findByCodeIgnoreCase("VIP15")).thenReturn(Optional.of(cappedPercent));

        VoucherValidationResult capped = voucherService.validateAndQuote(
                "  vip15 ", 7L, new BigDecimal("1000000"), new BigDecimal("25000"));
        assertThat(capped.valid()).isTrue();
        assertThat(capped.code()).isEqualTo("VIP15");
        assertThat(capped.voucherType()).isEqualTo(VoucherType.PERCENT);
        assertThat(capped.discountAmount()).isEqualByComparingTo("100000.00");
        assertThat(capped.shippingDiscount()).isEqualByComparingTo("0.00");
        assertThat(capped.finalShippingFee()).isEqualByComparingTo("25000.00");
        assertThat(capped.finalTotal()).isEqualByComparingTo("925000.00");

        Voucher halfUpPercent = activeVoucher(11L, "ODD5", VoucherType.PERCENT, new BigDecimal("5"));
        when(voucherRepository.findByCodeIgnoreCase("ODD5")).thenReturn(Optional.of(halfUpPercent));

        // 12345 * 5 / 100 = 617.25; 101 * 5 / 100 = 5.05 -> test .xx5 rounding: 1111 * 15 / 100 = 166.65
        VoucherValidationResult odd = voucherService.validateAndQuote(
                "ODD5", 7L, new BigDecimal("1005"), new BigDecimal("0"));
        // 1005 * 5 / 100 = 50.25 -> HALF_UP = 50.25
        assertThat(odd.discountAmount()).isEqualByComparingTo("50.25");
        assertThat(odd.finalTotal()).isEqualByComparingTo("954.75");

        Voucher halfUpExact = activeVoucher(12L, "HALF5", VoucherType.PERCENT, new BigDecimal("5"));
        when(voucherRepository.findByCodeIgnoreCase("HALF5")).thenReturn(Optional.of(halfUpExact));
        // 10050.10 * 5 / 100 = 502.505 -> HALF_UP = 502.51
        VoucherValidationResult rounded = voucherService.validateAndQuote(
                "HALF5", 7L, new BigDecimal("10050.10"), BigDecimal.ZERO);
        assertThat(rounded.discountAmount()).isEqualByComparingTo("502.51");
        assertThat(rounded.finalTotal()).isEqualByComparingTo("9547.59");

        Voucher over100 = activeVoucher(13L, "MEGA120", VoucherType.PERCENT, new BigDecimal("120"));
        when(voucherRepository.findByCodeIgnoreCase("MEGA120")).thenReturn(Optional.of(over100));
        VoucherValidationResult floored = voucherService.validateAndQuote(
                "MEGA120", 7L, new BigDecimal("200000"), new BigDecimal("25000"));
        assertThat(floored.discountAmount()).isEqualByComparingTo("200000.00");
        assertThat(floored.finalTotal()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("validateAndQuote calculates FIXED discount floored at zero subtotal and keeps shipping unchanged")
    void validateAndQuoteCalculatesFixedDiscount() {
        Voucher fixed = activeVoucher(20L, "FLAT200K", VoucherType.FIXED, new BigDecimal("200000"));
        when(voucherRepository.findByCodeIgnoreCase("FLAT200K")).thenReturn(Optional.of(fixed));

        VoucherValidationResult normal = voucherService.validateAndQuote(
                "flat200k", 3L, new BigDecimal("1000000"), new BigDecimal("45000"));
        assertThat(normal.valid()).isTrue();
        assertThat(normal.voucherType()).isEqualTo(VoucherType.FIXED);
        assertThat(normal.discountAmount()).isEqualByComparingTo("200000.00");
        assertThat(normal.shippingDiscount()).isEqualByComparingTo("0.00");
        assertThat(normal.finalShippingFee()).isEqualByComparingTo("45000.00");
        assertThat(normal.finalTotal()).isEqualByComparingTo("845000.00");

        VoucherValidationResult largerThanSubtotal = voucherService.validateAndQuote(
                "FLAT200K", 3L, new BigDecimal("120000"), new BigDecimal("25000"));
        assertThat(largerThanSubtotal.valid()).isTrue();
        assertThat(largerThanSubtotal.discountAmount()).isEqualByComparingTo("120000.00");
        assertThat(largerThanSubtotal.finalShippingFee()).isEqualByComparingTo("25000.00");
        assertThat(largerThanSubtotal.finalTotal()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("validateAndQuote calculates FREESHIP uncapped (value=0) and capped (value>0)")
    void validateAndQuoteCalculatesFreeshipUncappedAndCapped() {
        Voucher fullFree = activeVoucher(30L, "FREESHIP", VoucherType.FREESHIP, BigDecimal.ZERO);
        when(voucherRepository.findByCodeIgnoreCase("FREESHIP")).thenReturn(Optional.of(fullFree));

        VoucherValidationResult fullResult = voucherService.validateAndQuote(
                "freeship", 5L, new BigDecimal("800000"), new BigDecimal("45000"));
        assertThat(fullResult.valid()).isTrue();
        assertThat(fullResult.voucherType()).isEqualTo(VoucherType.FREESHIP);
        assertThat(fullResult.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(fullResult.shippingDiscount()).isEqualByComparingTo("45000.00");
        assertThat(fullResult.finalShippingFee()).isEqualByComparingTo("0.00");
        assertThat(fullResult.finalTotal()).isEqualByComparingTo("800000.00");

        Voucher cappedFree = activeVoucher(31L, "SHIP30K", VoucherType.FREESHIP, new BigDecimal("30000"));
        when(voucherRepository.findByCodeIgnoreCase("SHIP30K")).thenReturn(Optional.of(cappedFree));

        VoucherValidationResult partial = voucherService.validateAndQuote(
                "SHIP30K", 5L, new BigDecimal("800000"), new BigDecimal("75000"));
        assertThat(partial.valid()).isTrue();
        assertThat(partial.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(partial.shippingDiscount()).isEqualByComparingTo("30000.00");
        assertThat(partial.finalShippingFee()).isEqualByComparingTo("45000.00");
        assertThat(partial.finalTotal()).isEqualByComparingTo("845000.00");

        VoucherValidationResult smallerFee = voucherService.validateAndQuote(
                "SHIP30K", 5L, new BigDecimal("800000"), new BigDecimal("25000"));
        assertThat(smallerFee.shippingDiscount()).isEqualByComparingTo("25000.00");
        assertThat(smallerFee.finalShippingFee()).isEqualByComparingTo("0.00");
        assertThat(smallerFee.finalTotal()).isEqualByComparingTo("800000.00");
    }

    @Test
    @DisplayName("redeemForOrder uses pessimistic lock, increments usedCount, and persists VoucherUsage")
    void redeemForOrderAcquiresPessimisticLockAndPersistsUsage() {
        Voucher voucher = activeVoucher(40L, "VIP200K", VoucherType.FIXED, new BigDecimal("200000"));
        voucher.setUsageLimit(10);
        voucher.setUsedCount(2);

        User user = new User("buyer", "pw", "USER");
        user.setId(9L);

        Order order = new Order(user, new BigDecimal("825000"));
        order.setId(55L);
        order.setSubtotalAmount(new BigDecimal("1000000"));
        order.setDiscountAmount(new BigDecimal("200000"));
        order.setShippingFee(new BigDecimal("25000"));

        when(voucherRepository.findByCodeIgnoreCaseWithLock("VIP200K")).thenReturn(Optional.of(voucher));
        when(voucherUsageRepository.existsByOrderId(55L)).thenReturn(false);
        when(voucherUsageRepository.existsByVoucherIdAndUserId(40L, 9L)).thenReturn(false);
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voucherUsageRepository.save(any(VoucherUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        voucherService.redeemForOrder(" vip200k ", user, order);

        verify(voucherRepository).findByCodeIgnoreCaseWithLock("VIP200K");
        assertThat(voucher.getUsedCount()).isEqualTo(3);
        verify(voucherRepository).save(voucher);

        ArgumentCaptor<VoucherUsage> usageCaptor = ArgumentCaptor.forClass(VoucherUsage.class);
        verify(voucherUsageRepository).save(usageCaptor.capture());
        VoucherUsage savedUsage = usageCaptor.getValue();
        assertThat(savedUsage.getVoucher()).isSameAs(voucher);
        assertThat(savedUsage.getUser()).isSameAs(user);
        assertThat(savedUsage.getOrder()).isSameAs(order);
        assertThat(savedUsage.getDiscountApplied()).isEqualByComparingTo("200000.00");
        assertThat(savedUsage.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("redeemForOrder rejects exhausted limit, duplicate user, and duplicate order redemption")
    void redeemForOrderRejectsLimitOrDuplicateUsage() {
        User user = new User("buyer", "pw", "USER");
        user.setId(9L);
        Order order = new Order(user, new BigDecimal("900000"));
        order.setId(56L);
        order.setSubtotalAmount(new BigDecimal("1000000"));

        Voucher maxed = activeVoucher(41L, "LIMITED", VoucherType.FIXED, new BigDecimal("100000"));
        maxed.setUsageLimit(3);
        maxed.setUsedCount(3);
        when(voucherRepository.findByCodeIgnoreCaseWithLock("LIMITED")).thenReturn(Optional.of(maxed));

        assertThatThrownBy(() -> voucherService.redeemForOrder("LIMITED", user, order))
                .isInstanceOf(IllegalStateException.class);
        verify(voucherUsageRepository, never()).save(any());

        Voucher alreadyUsed = activeVoucher(42L, "ONEPERUSER", VoucherType.FIXED, new BigDecimal("100000"));
        when(voucherRepository.findByCodeIgnoreCaseWithLock("ONEPERUSER")).thenReturn(Optional.of(alreadyUsed));
        when(voucherUsageRepository.existsByVoucherIdAndUserId(42L, 9L)).thenReturn(true);

        assertThatThrownBy(() -> voucherService.redeemForOrder("ONEPERUSER", user, order))
                .isInstanceOf(IllegalStateException.class);
        verify(voucherUsageRepository, never()).save(any());

        Voucher dupOrder = activeVoucher(43L, "VALID", VoucherType.FIXED, new BigDecimal("100000"));
        when(voucherRepository.findByCodeIgnoreCaseWithLock("VALID")).thenReturn(Optional.of(dupOrder));
        when(voucherUsageRepository.existsByOrderId(56L)).thenReturn(true);

        assertThatThrownBy(() -> voucherService.redeemForOrder("VALID", user, order))
                .isInstanceOf(IllegalStateException.class);
        verify(voucherUsageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin operations create, update, toggleActive, and listAllVouchers persist and audit")
    void adminOperationsCreateUpdateToggleAndAudit() {
        Voucher input = new Voucher();
        input.setCode("  summer26 ");
        input.setType(VoucherType.PERCENT);
        input.setValue(new BigDecimal("15"));
        input.setMaxDiscount(new BigDecimal("250000"));
        input.setMinOrderValue(new BigDecimal("500000"));
        input.setUsageLimit(100);
        input.setStartsAt(LocalDateTime.now().minusDays(1));
        input.setExpiresAt(LocalDateTime.now().plusDays(30));

        when(voucherRepository.existsByCodeIgnoreCase("SUMMER26")).thenReturn(false);
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> {
            Voucher v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(77L);
            }
            return v;
        });

        Voucher created = voucherService.createVoucher(input, "admin01", "127.0.0.1");
        assertThat(created.getId()).isEqualTo(77L);
        assertThat(created.getCode()).isEqualTo("SUMMER26");
        assertThat(created.getActive()).isTrue();
        assertThat(created.getUsedCount()).isZero();
        verify(adminAuditLogService).log(eq("admin01"), eq("CREATE_VOUCHER"), eq("VOUCHER"), eq(77L), isNull(), any(), eq("127.0.0.1"));

        Voucher updateReq = new Voucher();
        updateReq.setCode("SUMMER26");
        updateReq.setType(VoucherType.FIXED);
        updateReq.setValue(new BigDecimal("300000"));
        updateReq.setMinOrderValue(new BigDecimal("1000000"));
        updateReq.setUsageLimit(50);

        created.setUsedCount(12);
        when(voucherRepository.findById(77L)).thenReturn(Optional.of(created));
        when(voucherRepository.findByCodeIgnoreCase("SUMMER26")).thenReturn(Optional.of(created));

        Voucher updated = voucherService.updateVoucher(77L, updateReq, "admin01", "127.0.0.1");
        assertThat(updated.getType()).isEqualTo(VoucherType.FIXED);
        assertThat(updated.getValue()).isEqualByComparingTo("300000.00");
        assertThat(updated.getUsedCount()).isEqualTo(12);
        verify(adminAuditLogService).log(eq("admin01"), eq("UPDATE_VOUCHER"), eq("VOUCHER"), eq(77L), any(), any(), eq("127.0.0.1"));

        Voucher toggled = voucherService.toggleActive(77L, "admin01", "127.0.0.1");
        assertThat(toggled.getActive()).isFalse();
        verify(adminAuditLogService).log(eq("admin01"), eq("TOGGLE_VOUCHER"), eq("VOUCHER"), eq(77L), any(), any(), eq("127.0.0.1"));

        when(voucherRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(toggled));
        assertThat(voucherService.listAllVouchers()).containsExactly(toggled);
    }

    @Test
    @DisplayName("VoucherAdminController lists vouchers/types, creates/updates, and toggles active status")
    void voucherAdminControllerRoutesWork() throws Exception {
        Voucher v = activeVoucher(1L, "VIP10", VoucherType.PERCENT, new BigDecimal("10"));
        when(voucherRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(v));
        when(voucherRepository.existsByCodeIgnoreCase("NEW20")).thenReturn(false);
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> {
            Voucher saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(2L);
            }
            return saved;
        });
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(v));

        VoucherAdminController controller = new VoucherAdminController(voucherService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/vouchers"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-vouchers"))
                .andExpect(model().attributeExists("vouchers", "voucherTypes"));

        mockMvc.perform(post("/admin/vouchers")
                        .param("code", "new20")
                        .param("type", "PERCENT")
                        .param("value", "20")
                        .param("minOrderValue", "500000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/vouchers"))
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(post("/admin/vouchers/1/toggle"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/vouchers"))
                .andExpect(flash().attributeExists("success"));
        assertThat(v.getActive()).isFalse();
    }

    @Test
    @DisplayName("VoucherApiController accepts JSON and query/form params, extracts auth userId, and defaults shippingFee")
    void voucherApiControllerValidatesJsonAndParams() throws Exception {
        Voucher vip = activeVoucher(99L, "VIP10", VoucherType.PERCENT, new BigDecimal("10"));
        when(voucherRepository.findByCodeIgnoreCase("VIP10")).thenReturn(Optional.of(vip));
        when(voucherUsageRepository.existsByVoucherIdAndUserId(99L, 42L)).thenReturn(false);

        User user = new User("demo", "pw", "USER");
        user.setId(42L);
        lenient().when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        VoucherApiController controller = new VoucherApiController(
                voucherService, userRepository, new ShippingFeeService());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "demo", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // JSON request omitting shippingFee -> defaults server-side via ShippingFeeService (35,000 for 1,000,000 subtotal)
        mockMvc.perform(post("/api/vouchers/validate")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"vip10\",\"subtotal\":1000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value("VIP10"))
                .andExpect(jsonPath("$.discountAmount").value(100000.0))
                .andExpect(jsonPath("$.finalShippingFee").value(35000.0))
                .andExpect(jsonPath("$.finalTotal").value(935000.0));

        verify(voucherUsageRepository).existsByVoucherIdAndUserId(99L, 42L);

        // Form/query param request with explicit shippingFee and anonymous auth
        mockMvc.perform(post("/api/vouchers/validate")
                        .param("code", "VIP10")
                        .param("subtotal", "2000000")
                        .param("shippingFee", "45000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.discountAmount").value(200000.0))
                .andExpect(jsonPath("$.finalShippingFee").value(45000.0))
                .andExpect(jsonPath("$.finalTotal").value(1845000.0));
    }
}
