package com.strongwine.strongwine.controller.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strongwine.strongwine.dto.VoucherValidationResult;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.ShippingFeeService;
import com.strongwine.strongwine.service.port.VoucherPricingPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * REST endpoint for real-time voucher quote validation on cart and checkout pages.
 */
@RestController
@RequestMapping("/api/vouchers")
public class VoucherApiController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final VoucherPricingPort voucherPricingPort;
    private final UserRepository userRepository;
    private final ShippingFeeService shippingFeeService;

    public VoucherApiController(VoucherPricingPort voucherPricingPort,
                                UserRepository userRepository) {
        this(voucherPricingPort, userRepository, new ShippingFeeService());
    }

    @Autowired
    public VoucherApiController(VoucherPricingPort voucherPricingPort,
                                UserRepository userRepository,
                                @Autowired(required = false) ShippingFeeService shippingFeeService) {
        this.voucherPricingPort = voucherPricingPort;
        this.userRepository = userRepository;
        this.shippingFeeService = shippingFeeService != null ? shippingFeeService : new ShippingFeeService();
    }

    @PostMapping("/validate")
    public ResponseEntity<VoucherValidationResult> validate(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) BigDecimal subtotal,
            @RequestParam(required = false) BigDecimal shippingFee,
            @RequestParam(required = false) Double distanceKm,
            HttpServletRequest request,
            Authentication authentication) {
        ValidateVoucherRequest payload = readJsonPayloadIfPresent(request);
        String resolvedCode = payload != null && payload.code() != null ? payload.code() : code;
        BigDecimal resolvedSubtotal = payload != null && payload.subtotal() != null ? payload.subtotal() : subtotal;
        BigDecimal resolvedShippingFee = payload != null && payload.shippingFee() != null
                ? payload.shippingFee()
                : shippingFee;
        Double resolvedDistanceKm = payload != null && payload.distanceKm() != null ? payload.distanceKm() : distanceKm;

        return ResponseEntity.ok(executeValidation(
                resolvedCode, resolvedSubtotal, resolvedShippingFee, resolvedDistanceKm, authentication));
    }

    private ValidateVoucherRequest readJsonPayloadIfPresent(HttpServletRequest request) {
        if (request == null || !isJsonContentType(request.getContentType())) {
            return null;
        }
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length == 0) {
                return null;
            }
            return OBJECT_MAPPER.readValue(body, ValidateVoucherRequest.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Dữ liệu JSON kiểm tra mã giảm giá không hợp lệ.", ex);
        }
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private VoucherValidationResult executeValidation(String code,
                                                      BigDecimal subtotal,
                                                      BigDecimal shippingFee,
                                                      Double distanceKm,
                                                      Authentication authentication) {
        BigDecimal safeSubtotal = (subtotal != null && subtotal.signum() >= 0) ? subtotal : BigDecimal.ZERO;
        BigDecimal effectiveShipping = (shippingFee != null && shippingFee.signum() >= 0)
                ? shippingFee
                : shippingFeeService.calculateFee(distanceKm, safeSubtotal);
        Long userId = resolveUserId(authentication);
        return voucherPricingPort.validateAndQuote(code, userId, safeSubtotal, effectiveShipping);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            username = authentication.getName();
        }
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    public record ValidateVoucherRequest(
            String code,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            Double distanceKm
    ) {
    }
}
