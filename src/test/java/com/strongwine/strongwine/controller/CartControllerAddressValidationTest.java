package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.dto.CartDto;
import com.strongwine.strongwine.dto.CartItemDto;
import com.strongwine.strongwine.dto.CheckoutRequest;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.CartService;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.UserShippingProfileService;
import com.strongwine.strongwine.service.VietnamGeoValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartControllerAddressValidationTest {

    @Mock
    private CartService cartService;

    @Mock
    private OrderService orderService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private VietnamGeoValidationService vietnamGeoValidationService;

    @Mock
    private UserShippingProfileService userShippingProfileService;

    @Mock
    private HttpSession session;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CartController cartController;

    private Authentication auth;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("buyer", "pass", "USER");
        user.setId(50L);
        user.setEmail("buyer@example.com");

        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User.withUsername("buyer")
                        .password("pass")
                        .authorities("ROLE_USER")
                        .build();

        auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                "pass",
                userDetails.getAuthorities()
        );
        when(userRepository.findByUsername("buyer")).thenReturn(Optional.of(user));
        lenient().when(session.getAttribute("checkout:token")).thenReturn("valid-token");
    }

    @Test
    @DisplayName("processCheckout should reject island address with 400 Bad Request")
    void testProcessCheckoutRejectsIslandAddress() {
        CheckoutRequest req = new CheckoutRequest();
        req.setCheckoutToken("valid-token");
        req.setFullName("Nguyễn Văn A");
        req.setPhone("0901234567");
        req.setEmail("buyer@example.com");
        req.setAddress("Huyện đảo Côn Đảo, Bà Rịa Vũng Tàu");
        req.setPaymentMethod("COD");

        when(vietnamGeoValidationService.resolveDefaultCoordinates(any()))
                .thenReturn(new double[]{10.7769, 106.7009});
        doThrow(new DeliveryAddressNotSupportedException("Không hỗ trợ giao tới huyện đảo/đảo xa"))
                .when(vietnamGeoValidationService).validateMainlandDeliveryAddress(eq(req.getAddress()), any(), any());

        ResponseEntity<Map<String, String>> response = cartController.processCheckout(req, session, auth, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("Không hỗ trợ giao tới huyện đảo/đảo xa");
    }

    @Test
    @DisplayName("processCheckout should succeed for valid mainland address and persist shipping profile")
    void testProcessCheckoutSucceedsForMainland() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.setCheckoutToken("valid-token");
        req.setFullName("Nguyễn Văn A");
        req.setPhone("0901234567");
        req.setEmail("buyer@example.com");
        req.setAddress("72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP. Hồ Chí Minh");
        req.setPaymentMethod("COD");
        req.setDeliveryLat(10.7769);
        req.setDeliveryLng(106.7009);

        com.strongwine.strongwine.entity.Wine wine = new com.strongwine.strongwine.entity.Wine();
        wine.setId(1L);
        wine.setName("Vang Demo");

        CartItemDto itemDto = new CartItemDto();
        itemDto.setWine(wine);
        itemDto.setQuantity(2);
        itemDto.setLineTotal(BigDecimal.valueOf(1000000));

        CartDto cartDto = new CartDto();
        cartDto.setItems(List.of(itemDto));

        when(cartService.validateCheckoutCart(any())).thenReturn(cartDto);
        when(cartService.toItemMap(cartDto)).thenReturn(Map.of(1L, 2));

        Order order = new Order();
        order.setId(123L);
        when(orderService.createPendingOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(order);
        when(paymentService.createPaymentSession(any(), any(), any(), any())).thenReturn("/orders/123");

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<Map<String, String>> response = cartController.processCheckout(req, session, auth, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("redirectUrl");
        assertThat(response.getBody().get("redirectUrl")).isEqualTo("/orders/123");

        // Verify that shipping profile was saved for user
        verify(userShippingProfileService, times(1)).saveProfile(
                eq(50L),
                eq("Nguyễn Văn A"),
                eq("0901234567"),
                eq("72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP. Hồ Chí Minh"),
                eq(10.7769),
                eq(106.7009)
        );
    }

    @Test
    @DisplayName("processCheckout V3 computes server-side shipping from nearest warehouse, validates & redeems voucher, and uses 15-arg createPendingOrder")
    void testProcessCheckoutComputesServerPricingFromWarehouseAndAppliesVoucher() throws Exception {
        com.strongwine.strongwine.service.ShippingFeeService shippingFeeService =
                new com.strongwine.strongwine.service.ShippingFeeService();
        com.strongwine.strongwine.repository.WarehouseRepository warehouseRepository =
                mock(com.strongwine.strongwine.repository.WarehouseRepository.class);
        com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort =
                mock(com.strongwine.strongwine.service.port.VoucherPricingPort.class);
        com.strongwine.strongwine.service.UserAddressService userAddressService =
                mock(com.strongwine.strongwine.service.UserAddressService.class);

        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "shippingFeeService", shippingFeeService);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "warehouseRepository", warehouseRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "voucherPricingPort", voucherPricingPort);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "userAddressService", userAddressService);

        com.strongwine.strongwine.entity.Warehouse warehouse = new com.strongwine.strongwine.entity.Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Kho HCM");
        warehouse.setActive(true);
        warehouse.setIsDefault(true);
        warehouse.setLatitude(10.7800);
        warehouse.setLongitude(106.7050);
        when(warehouseRepository.findByActiveTrue()).thenReturn(List.of(warehouse));

        com.strongwine.strongwine.entity.UserAddress savedAddress = new com.strongwine.strongwine.entity.UserAddress();
        savedAddress.setId(77L);
        savedAddress.setUser(user);
        savedAddress.setRecipientName("Trần Thị Hoa");
        savedAddress.setPhone("0911223344");
        savedAddress.setAddressLine("10 Đồng Khởi");
        savedAddress.setWard("Bến Nghé");
        savedAddress.setDistrict("Quận 1");
        savedAddress.setProvince("TP. Hồ Chí Minh");
        savedAddress.setLatitude(10.7769);
        savedAddress.setLongitude(106.7009);
        when(userAddressService.getOwnedAddress(50L, 77L)).thenReturn(savedAddress);
        when(userAddressService.formatFullAddress(savedAddress))
                .thenReturn("10 Đồng Khởi, Bến Nghé, Quận 1, TP. Hồ Chí Minh");

        CheckoutRequest req = new CheckoutRequest();
        req.setCheckoutToken("valid-token");
        req.setPaymentMethod("COD");
        req.setSavedAddressId(77L);
        req.setVoucherCode("VANG10");

        CartDto cartDto = new CartDto();
        cartDto.setSubtotal(BigDecimal.valueOf(1_000_000));
        CartItemDto itemDto = new CartItemDto();
        com.strongwine.strongwine.entity.Wine wine = new com.strongwine.strongwine.entity.Wine();
        wine.setId(2L);
        itemDto.setWine(wine);
        itemDto.setQuantity(1);
        itemDto.setLineTotal(BigDecimal.valueOf(1_000_000));
        cartDto.setItems(List.of(itemDto));

        when(cartService.validateCheckoutCart(any())).thenReturn(cartDto);
        when(cartService.toItemMap(cartDto)).thenReturn(Map.of(2L, 1));

        com.strongwine.strongwine.dto.VoucherValidationResult quoteResult =
                new com.strongwine.strongwine.dto.VoucherValidationResult(
                        true,
                        "VANG10",
                        com.strongwine.strongwine.entity.VoucherType.PERCENT,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(25_000),
                        BigDecimal.valueOf(925_000),
                        "Áp dụng mã thành công"
                );
        when(voucherPricingPort.validateAndQuote(
                eq("VANG10"),
                eq(50L),
                eq(BigDecimal.valueOf(1_000_000)),
                eq(BigDecimal.valueOf(25_000))
        )).thenReturn(quoteResult);

        Order order = new Order();
        order.setId(321L);
        order.setUser(user);
        when(orderService.createPendingOrder(
                eq(50L),
                eq(Map.of(2L, 1)),
                eq("Trần Thị Hoa"),
                eq("0911223344"),
                eq("buyer@example.com"),
                eq("10 Đồng Khởi, Bến Nghé, Quận 1, TP. Hồ Chí Minh"),
                eq(10.7769),
                eq(106.7009),
                any(),
                eq("COD"),
                eq(BigDecimal.valueOf(1_000_000)),
                eq(BigDecimal.valueOf(100_000)),
                eq(BigDecimal.valueOf(25_000)),
                eq("VANG10"),
                eq(warehouse)
        )).thenReturn(order);
        when(paymentService.createPaymentSession(any(), any(), any(), any())).thenReturn("/orders/321");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<Map<String, String>> response = cartController.processCheckout(req, session, auth, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("redirectUrl", "/orders/321");
        verify(voucherPricingPort).redeemForOrder("VANG10", user, order);
    }

    @Test
    @DisplayName("processCheckout V3 rejects invalid voucher with 400 Bad Request")
    void testProcessCheckoutRejectsInvalidVoucherWith400() {
        com.strongwine.strongwine.service.ShippingFeeService shippingFeeService =
                new com.strongwine.strongwine.service.ShippingFeeService();
        com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort =
                mock(com.strongwine.strongwine.service.port.VoucherPricingPort.class);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "shippingFeeService", shippingFeeService);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "voucherPricingPort", voucherPricingPort);

        CheckoutRequest req = new CheckoutRequest();
        req.setCheckoutToken("valid-token");
        req.setFullName("Nguyễn Văn A");
        req.setPhone("0901234567");
        req.setAddress("72 Lê Thánh Tôn, Quận 1, TP. Hồ Chí Minh");
        req.setDeliveryLat(10.7769);
        req.setDeliveryLng(106.7009);
        req.setPaymentMethod("COD");
        req.setVoucherCode("EXPIRED99");

        CartDto cartDto = new CartDto();
        cartDto.setSubtotal(BigDecimal.valueOf(500_000));
        when(cartService.validateCheckoutCart(any())).thenReturn(cartDto);
        when(voucherPricingPort.validateAndQuote(eq("EXPIRED99"), eq(50L), eq(BigDecimal.valueOf(500_000)), any()))
                .thenReturn(com.strongwine.strongwine.dto.VoucherValidationResult.invalid(
                        "EXPIRED99", BigDecimal.valueOf(500_000), BigDecimal.valueOf(35_000), "Mã giảm giá đã hết hạn"));

        ResponseEntity<Map<String, String>> response = cartController.processCheckout(req, session, auth, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Mã giảm giá đã hết hạn");
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("quoteCheckout returns server-computed subtotal, shippingFee, discountAmount, finalTotal, and voucher status")
    void testQuoteCheckoutReturnsServerBreakdown() {
        com.strongwine.strongwine.service.ShippingFeeService shippingFeeService =
                new com.strongwine.strongwine.service.ShippingFeeService();
        com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort =
                mock(com.strongwine.strongwine.service.port.VoucherPricingPort.class);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "shippingFeeService", shippingFeeService);
        org.springframework.test.util.ReflectionTestUtils.setField(cartController, "voucherPricingPort", voucherPricingPort);

        CartDto cartDto = new CartDto();
        cartDto.setSubtotal(BigDecimal.valueOf(1_200_000));
        when(cartService.getCartDetails()).thenReturn(cartDto);
        when(voucherPricingPort.validateAndQuote(eq("SAVE50"), eq(50L), eq(BigDecimal.valueOf(1_200_000)), eq(BigDecimal.valueOf(35_000))))
                .thenReturn(new com.strongwine.strongwine.dto.VoucherValidationResult(
                        true,
                        "SAVE50",
                        com.strongwine.strongwine.entity.VoucherType.FIXED,
                        BigDecimal.valueOf(50_000),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(35_000),
                        BigDecimal.valueOf(1_185_000),
                        "Hợp lệ"
                ));

        CheckoutRequest req = new CheckoutRequest();
        req.setVoucherCode("SAVE50");

        ResponseEntity<Map<String, Object>> quoteResp = cartController.quoteCheckout(req, auth);

        assertThat(quoteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(quoteResp.getBody()).containsEntry("voucherValid", true);
        assertThat((BigDecimal) quoteResp.getBody().get("subtotal")).isEqualByComparingTo("1200000");
        assertThat((BigDecimal) quoteResp.getBody().get("shippingFee")).isEqualByComparingTo("35000");
        assertThat((BigDecimal) quoteResp.getBody().get("discountAmount")).isEqualByComparingTo("50000");
        assertThat((BigDecimal) quoteResp.getBody().get("finalTotal")).isEqualByComparingTo("1185000");
    }
}
