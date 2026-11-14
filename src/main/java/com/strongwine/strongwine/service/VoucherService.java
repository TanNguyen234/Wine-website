package com.strongwine.strongwine.service;

import com.strongwine.strongwine.dto.VoucherValidationResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Voucher;
import com.strongwine.strongwine.entity.VoucherType;
import com.strongwine.strongwine.entity.VoucherUsage;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.VoucherRepository;
import com.strongwine.strongwine.repository.VoucherUsageRepository;
import com.strongwine.strongwine.service.port.VoucherPricingPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Voucher pricing engine, atomic redemption service, and audited admin management.
 */
@Service
public class VoucherService implements VoucherPricingPort {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final AdminAuditLogService adminAuditLogService;

    public VoucherService(VoucherRepository voucherRepository,
                          VoucherUsageRepository voucherUsageRepository) {
        this(voucherRepository, voucherUsageRepository, null);
    }

    @Autowired
    public VoucherService(VoucherRepository voucherRepository,
                          VoucherUsageRepository voucherUsageRepository,
                          @Autowired(required = false) AdminAuditLogService adminAuditLogService) {
        this.voucherRepository = voucherRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherValidationResult validateAndQuote(String code,
                                                    Long userId,
                                                    BigDecimal subtotal,
                                                    BigDecimal shippingFee) {
        BigDecimal safeSubtotal = nonNegativeScale(subtotal);
        BigDecimal safeShipping = nonNegativeScale(shippingFee);
        String normalizedCode = normalizeCode(code);

        if (normalizedCode.isEmpty()) {
            return VoucherValidationResult.invalid("", safeShipping, safeSubtotal, "Vui lòng nhập mã giảm giá.");
        }

        Voucher voucher = voucherRepository.findByCodeIgnoreCase(normalizedCode).orElse(null);
        if (voucher == null) {
            return VoucherValidationResult.invalid(normalizedCode, safeShipping, safeSubtotal, "Mã giảm giá không tồn tại.");
        }

        String canonicalCode = normalizeCode(voucher.getCode());
        String rejection = checkEligibility(voucher, userId, safeSubtotal, LocalDateTime.now());
        if (rejection != null) {
            return VoucherValidationResult.invalid(canonicalCode, safeShipping, safeSubtotal, rejection);
        }

        return computeQuote(voucher, canonicalCode, safeSubtotal, safeShipping);
    }

    @Override
    @Transactional
    public void redeemForOrder(String code, User user, Order order) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Mã giảm giá không được để trống.");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Cần thông tin người dùng hợp lệ để áp dụng mã giảm giá.");
        }
        if (order == null) {
            throw new IllegalArgumentException("Đơn hàng không được để trống.");
        }

        Voucher voucher = voucherRepository.findByCodeIgnoreCaseWithLock(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá: " + normalizedCode));

        if (order.getId() != null && voucherUsageRepository.existsByOrderId(order.getId())) {
            throw new IllegalStateException("Đơn hàng này đã ghi nhận sử dụng mã giảm giá.");
        }

        BigDecimal orderSubtotal = resolveOrderSubtotal(order);
        BigDecimal orderShipping = nonNegativeScale(order.getShippingFee());

        String rejection = checkEligibility(voucher, user.getId(), orderSubtotal, LocalDateTime.now());
        if (rejection != null) {
            throw new IllegalStateException(rejection);
        }

        VoucherValidationResult quote = computeQuote(voucher, normalizeCode(voucher.getCode()), orderSubtotal, orderShipping);
        BigDecimal appliedDiscount = resolveAppliedDiscount(voucher, order, quote);

        int currentUsed = voucher.getUsedCount() != null ? voucher.getUsedCount() : 0;
        voucher.setUsedCount(currentUsed + 1);
        voucherRepository.save(voucher);

        VoucherUsage usage = new VoucherUsage();
        usage.setVoucher(voucher);
        usage.setUser(user);
        usage.setOrder(order);
        usage.setDiscountApplied(appliedDiscount);
        usage.setUsedAt(LocalDateTime.now());
        voucherUsageRepository.save(usage);
    }

    @Transactional(readOnly = true)
    public List<Voucher> listAllVouchers() {
        return voucherRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Voucher> listActiveVouchers() {
        return voucherRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Voucher getVoucherById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Voucher id không được để trống.");
        }
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá #" + id));
    }

    @Transactional
    public Voucher createVoucher(Voucher input, String actorUsername, String ipAddress) {
        if (input == null) {
            throw new IllegalArgumentException("Dữ liệu mã giảm giá không được để trống.");
        }
        String normalizedCode = normalizeCode(input.getCode());
        validateVoucherInput(normalizedCode, input);

        if (voucherRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Mã giảm giá đã tồn tại: " + normalizedCode);
        }

        Voucher voucher = new Voucher();
        applyFields(voucher, normalizedCode, input);
        voucher.setUsedCount(0);
        voucher.setActive(input.getActive() != null ? input.getActive() : Boolean.TRUE);
        if (voucher.getCreatedAt() == null) {
            voucher.setCreatedAt(LocalDateTime.now());
        }

        Voucher saved = voucherRepository.save(voucher);
        logAudit(actorUsername, "CREATE_VOUCHER", saved.getId(), null, snapshot(saved), ipAddress);
        return saved;
    }

    @Transactional
    public Voucher updateVoucher(Long id, Voucher input, String actorUsername, String ipAddress) {
        if (input == null) {
            throw new IllegalArgumentException("Dữ liệu cập nhật mã giảm giá không được để trống.");
        }
        Voucher existing = getVoucherById(id);
        Map<String, Object> beforeState = snapshot(existing);

        String normalizedCode = normalizeCode(input.getCode());
        validateVoucherInput(normalizedCode, input);

        voucherRepository.findByCodeIgnoreCase(normalizedCode)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(conflict -> {
                    throw new IllegalArgumentException("Mã giảm giá đã tồn tại: " + normalizedCode);
                });

        applyFields(existing, normalizedCode, input);
        if (input.getActive() != null) {
            existing.setActive(input.getActive());
        }

        Voucher saved = voucherRepository.save(existing);
        logAudit(actorUsername, "UPDATE_VOUCHER", saved.getId(), beforeState, snapshot(saved), ipAddress);
        return saved;
    }

    @Transactional
    public Voucher toggleActive(Long id, String actorUsername, String ipAddress) {
        Voucher existing = getVoucherById(id);
        Map<String, Object> beforeState = snapshot(existing);

        boolean currentlyActive = Boolean.TRUE.equals(existing.getActive());
        existing.setActive(!currentlyActive);

        Voucher saved = voucherRepository.save(existing);
        logAudit(actorUsername, "TOGGLE_VOUCHER", saved.getId(), beforeState, snapshot(saved), ipAddress);
        return saved;
    }

    private String checkEligibility(Voucher voucher, Long userId, BigDecimal subtotal, LocalDateTime now) {
        if (!Boolean.TRUE.equals(voucher.getActive())) {
            return "Mã giảm giá hiện đang tạm ngưng.";
        }
        if (voucher.getStartsAt() != null && now.isBefore(voucher.getStartsAt())) {
            return "Mã giảm giá chưa đến thời gian áp dụng.";
        }
        if (voucher.getExpiresAt() != null && now.isAfter(voucher.getExpiresAt())) {
            return "Mã giảm giá đã hết hạn sử dụng.";
        }
        BigDecimal minOrderValue = nonNegativeScale(voucher.getMinOrderValue());
        if (subtotal.compareTo(minOrderValue) < 0) {
            return "Giá trị đơn hàng chưa đạt mức tối thiểu để áp dụng mã.";
        }
        Integer usageLimit = voucher.getUsageLimit();
        int usedCount = voucher.getUsedCount() != null ? voucher.getUsedCount() : 0;
        if (usageLimit != null && usedCount >= usageLimit) {
            return "Mã giảm giá đã hết lượt sử dụng.";
        }
        if (userId != null && voucherUsageRepository.existsByVoucherIdAndUserId(voucher.getId(), userId)) {
            return "Bạn đã sử dụng mã giảm giá này trước đó.";
        }
        return null;
    }

    private VoucherValidationResult computeQuote(Voucher voucher,
                                                 String canonicalCode,
                                                 BigDecimal subtotal,
                                                 BigDecimal shippingFee) {
        VoucherType type = voucher.getType();
        if (type == null) {
            return VoucherValidationResult.invalid(canonicalCode, shippingFee, subtotal, "Loại mã giảm giá không hợp lệ.");
        }

        BigDecimal discountAmount = ZERO;
        BigDecimal shippingDiscount = ZERO;
        BigDecimal finalShippingFee = shippingFee;

        switch (type) {
            case PERCENT -> {
                BigDecimal rate = nonNegative(voucher.getValue());
                BigDecimal computed = subtotal.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (voucher.getMaxDiscount() != null && voucher.getMaxDiscount().signum() > 0) {
                    BigDecimal cap = scale(voucher.getMaxDiscount());
                    if (computed.compareTo(cap) > 0) {
                        computed = cap;
                    }
                }
                discountAmount = computed.min(subtotal).setScale(2, RoundingMode.HALF_UP);
            }
            case FIXED -> {
                BigDecimal fixed = nonNegativeScale(voucher.getValue());
                discountAmount = fixed.min(subtotal).setScale(2, RoundingMode.HALF_UP);
            }
            case FREESHIP -> {
                BigDecimal value = voucher.getValue();
                if (value == null || value.signum() <= 0) {
                    shippingDiscount = shippingFee;
                } else {
                    shippingDiscount = shippingFee.min(scale(value)).setScale(2, RoundingMode.HALF_UP);
                }
                finalShippingFee = shippingFee.subtract(shippingDiscount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal discountedSubtotal = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
        BigDecimal finalTotal = discountedSubtotal.add(finalShippingFee).setScale(2, RoundingMode.HALF_UP);

        return VoucherValidationResult.valid(
                canonicalCode,
                type,
                discountAmount,
                shippingDiscount,
                finalShippingFee,
                finalTotal,
                "Áp dụng mã giảm giá thành công."
        );
    }

    private BigDecimal resolveOrderSubtotal(Order order) {
        if (order.getSubtotalAmount() != null && order.getSubtotalAmount().signum() > 0) {
            return scale(order.getSubtotalAmount());
        }
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (OrderItem item : order.getOrderItems()) {
                if (item != null && item.getPrice() != null) {
                    sum = sum.add(item.getPrice().multiply(BigDecimal.valueOf(Math.max(0, item.getQuantity()))));
                }
            }
            if (sum.signum() > 0) {
                return scale(sum);
            }
        }
        return nonNegativeScale(order.getTotalPrice());
    }

    private BigDecimal resolveAppliedDiscount(Voucher voucher, Order order, VoucherValidationResult quote) {
        if (voucher.getType() == VoucherType.FREESHIP) {
            if (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0) {
                return scale(order.getDiscountAmount());
            }
            return quote.shippingDiscount();
        }
        if (quote.discountAmount().signum() > 0) {
            return quote.discountAmount();
        }
        return nonNegativeScale(order.getDiscountAmount());
    }

    private void validateVoucherInput(String normalizedCode, Voucher input) {
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Mã giảm giá không được để trống.");
        }
        if (normalizedCode.length() > 64) {
            throw new IllegalArgumentException("Mã giảm giá không được vượt quá 64 ký tự.");
        }
        if (input.getType() == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại mã giảm giá.");
        }
        BigDecimal rawValue = input.getValue();
        switch (input.getType()) {
            case PERCENT -> {
                if (rawValue == null || rawValue.signum() <= 0 || rawValue.compareTo(HUNDRED) > 0) {
                    throw new IllegalArgumentException("Phần trăm giảm giá phải nằm trong khoảng 0 đến 100.");
                }
            }
            case FIXED -> {
                if (rawValue == null || rawValue.signum() <= 0) {
                    throw new IllegalArgumentException("Số tiền giảm cố định phải lớn hơn 0.");
                }
            }
            case FREESHIP -> {
                if (rawValue != null && rawValue.signum() < 0) {
                    throw new IllegalArgumentException("Mức giảm phí vận chuyển không được âm.");
                }
            }
        }
        if (input.getMinOrderValue() != null && input.getMinOrderValue().signum() < 0) {
            throw new IllegalArgumentException("Giá trị đơn tối thiểu không được âm.");
        }
        if (input.getMaxDiscount() != null && input.getMaxDiscount().signum() < 0) {
            throw new IllegalArgumentException("Mức giảm tối đa không được âm.");
        }
        if (input.getUsageLimit() != null && input.getUsageLimit() <= 0) {
            throw new IllegalArgumentException("Giới hạn lượt sử dụng phải lớn hơn 0.");
        }
        if (input.getStartsAt() != null && input.getExpiresAt() != null
                && input.getExpiresAt().isBefore(input.getStartsAt())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }

    private void applyFields(Voucher target, String normalizedCode, Voucher source) {
        target.setCode(normalizedCode);
        target.setType(source.getType());
        target.setValue(source.getType() == VoucherType.FREESHIP && source.getValue() == null
                ? ZERO
                : scale(source.getValue()));
        target.setMaxDiscount(source.getMaxDiscount() != null && source.getMaxDiscount().signum() > 0
                ? scale(source.getMaxDiscount())
                : null);
        target.setMinOrderValue(nonNegativeScale(source.getMinOrderValue()));
        target.setUsageLimit(source.getUsageLimit());
        target.setStartsAt(source.getStartsAt());
        target.setExpiresAt(source.getExpiresAt());
    }

    private void logAudit(String actorUsername,
                          String action,
                          Long targetId,
                          Object beforeState,
                          Object afterState,
                          String ipAddress) {
        if (adminAuditLogService == null) {
            return;
        }
        String actor = (actorUsername == null || actorUsername.isBlank()) ? "system" : actorUsername.trim();
        adminAuditLogService.log(actor, action, "VOUCHER", targetId, beforeState, afterState, ipAddress);
    }

    private Map<String, Object> snapshot(Voucher v) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", v.getId());
        data.put("code", v.getCode());
        data.put("type", v.getType() != null ? v.getType().name() : null);
        data.put("value", v.getValue());
        data.put("maxDiscount", v.getMaxDiscount());
        data.put("minOrderValue", v.getMinOrderValue());
        data.put("usageLimit", v.getUsageLimit());
        data.put("usedCount", v.getUsedCount());
        data.put("startsAt", v.getStartsAt() != null ? v.getStartsAt().toString() : null);
        data.put("expiresAt", v.getExpiresAt() != null ? v.getExpiresAt().toString() : null);
        data.put("active", v.getActive());
        return data;
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return (value == null || value.signum() < 0) ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegativeScale(BigDecimal value) {
        return nonNegative(value).setScale(2, RoundingMode.HALF_UP);
    }
}
