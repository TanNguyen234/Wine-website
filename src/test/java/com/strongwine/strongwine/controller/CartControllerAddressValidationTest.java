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
        when(session.getAttribute("checkout:token")).thenReturn("valid-token");
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
}
