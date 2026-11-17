package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Voucher;
import com.strongwine.strongwine.entity.VoucherType;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.service.VoucherService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Admin SSR controller for managing promotional vouchers.
 */
@Controller
@RequestMapping("/admin/vouchers")
public class VoucherAdminController {

    private final VoucherService voucherService;

    public VoucherAdminController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public String listVouchers(@RequestParam(required = false) Long editId, Model model) {
        List<Voucher> vouchers = voucherService.listAllVouchers();
        long activeCount = vouchers.stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .count();

        model.addAttribute("vouchers", vouchers);
        model.addAttribute("voucherTypes", VoucherType.values());
        model.addAttribute("types", VoucherType.values());
        model.addAttribute("totalVouchers", vouchers.size());
        model.addAttribute("activeVouchers", activeCount);

        if (editId != null) {
            try {
                Voucher editing = voucherService.getVoucherById(editId);
                model.addAttribute("editingVoucher", editing);
            } catch (ResourceNotFoundException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        return "admin-vouchers";
    }

    @PostMapping
    public String saveVoucher(@RequestParam(required = false) Long id,
                              @RequestParam String code,
                              @RequestParam VoucherType type,
                              @RequestParam(required = false) BigDecimal value,
                              @RequestParam(required = false) BigDecimal maxDiscount,
                              @RequestParam(required = false) BigDecimal minOrderValue,
                              @RequestParam(required = false) Integer usageLimit,
                              @RequestParam(required = false) String startsAt,
                              @RequestParam(required = false) String expiresAt,
                              @RequestParam(defaultValue = "false") boolean active,
                              Authentication authentication,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        try {
            Voucher input = new Voucher();
            input.setCode(code);
            input.setType(type);
            input.setValue(value);
            input.setMaxDiscount(maxDiscount);
            input.setMinOrderValue(minOrderValue);
            input.setUsageLimit(usageLimit);
            input.setStartsAt(parseDateTime(startsAt, LocalTime.MIDNIGHT));
            input.setExpiresAt(parseDateTime(expiresAt, LocalTime.of(23, 59, 59)));
            input.setActive(active);

            String actor = resolveActor(authentication);
            String ipAddress = resolveClientIp(request);

            if (id == null) {
                Voucher created = voucherService.createVoucher(input, actor, ipAddress);
                redirectAttributes.addFlashAttribute("success", "Đã tạo mã giảm giá " + created.getCode() + ".");
            } else {
                Voucher updated = voucherService.updateVoucher(id, input, actor, ipAddress);
                redirectAttributes.addFlashAttribute("success", "Đã cập nhật mã giảm giá " + updated.getCode() + ".");
            }
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    @PostMapping("/{id}/toggle")
    public String toggleVoucher(@PathVariable Long id,
                                Authentication authentication,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            Voucher updated = voucherService.toggleActive(id, resolveActor(authentication), resolveClientIp(request));
            String stateText = Boolean.TRUE.equals(updated.getActive()) ? "kích hoạt" : "tạm ngưng";
            redirectAttributes.addFlashAttribute("success",
                    "Đã " + stateText + " mã giảm giá " + updated.getCode() + ".");
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/vouchers";
    }

    private LocalDateTime parseDateTime(String raw, LocalTime fallbackTime) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(trimmed).atTime(fallbackTime);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Định dạng ngày giờ không hợp lệ: " + trimmed);
            }
        }
    }

    private String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return "admin";
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIdx = forwarded.indexOf(',');
            return (commaIdx > 0 ? forwarded.substring(0, commaIdx) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
