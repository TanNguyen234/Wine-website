package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.repository.PaymentRepository;
import com.strongwine.strongwine.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Controller
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "amount", "status", "method", "id");

    private final PaymentService paymentService;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public String listPayments(int page, int size, String sortBy, String sortDir, Model model) {
        return listPayments(page, size, sortBy, sortDir, null, model);
    }

    @GetMapping
    public String listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            Model model) {

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        PaymentStatus parsedStatus = parsePaymentStatus(status);
        Page<Payment> paymentPage;
        if (parsedStatus == null) {
            paymentPage = paymentService.getPaymentsPage(pageable);
            if (paymentPage == null) {
                paymentPage = Page.empty(pageable);
            }
        } else {
            List<Payment> filtered;
            if (paymentRepository != null) {
                filtered = Objects.requireNonNullElse(paymentRepository.findByStatus(parsedStatus), List.of());
            } else if (parsedStatus == PaymentStatus.REFUND_REQUIRED) {
                filtered = Objects.requireNonNullElse(paymentService.getRefundRequiredPayments(), List.of());
            } else {
                Page<Payment> rawPage = paymentService.getPaymentsPage(
                        PageRequest.of(0, 1000, Sort.by(direction, safeSortBy)));
                filtered = rawPage != null
                        ? rawPage.getContent().stream().filter(p -> p != null && p.getStatus() == parsedStatus).toList()
                        : List.of();
            }
            List<Payment> sorted = filtered.stream()
                    .filter(Objects::nonNull)
                    .sorted(buildPaymentComparator(safeSortBy, direction))
                    .toList();
            int total = sorted.size();
            int fromIndex = Math.min(safePage * safeSize, total);
            int toIndex = Math.min(fromIndex + safeSize, total);
            paymentPage = new PageImpl<>(sorted.subList(fromIndex, toIndex), pageable, total);
        }

        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("currentStatus", parsedStatus);
        model.addAttribute("status", parsedStatus != null ? parsedStatus.name() : null);
        model.addAttribute("paymentTransactions", paymentService.getRecentTransactions());
        model.addAttribute("refundRequiredPayments", paymentService.getRefundRequiredPayments());
        model.addAttribute("refundRequiredCount", paymentService.countRefundRequiredPayments());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", direction.name().toLowerCase());
        model.addAttribute("totalPages", paymentPage.getTotalPages());
        model.addAttribute("hasNext", paymentPage.hasNext());
        model.addAttribute("hasPrevious", paymentPage.hasPrevious());
        model.addAttribute("totalEntries", paymentPage.getTotalElements());
        model.addAttribute("totalItems", paymentPage.getTotalElements());

        return "admin-payments";
    }

    private PaymentStatus parsePaymentStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Comparator<Payment> buildPaymentComparator(String sortBy, Sort.Direction direction) {
        Comparator<Payment> comparator = switch (sortBy) {
            case "id" -> Comparator.comparing(Payment::getId, Comparator.nullsLast(Comparator.naturalOrder()));
            case "amount" -> Comparator.comparing(Payment::getAmount, Comparator.nullsLast(Comparator.naturalOrder()));
            case "updatedAt" -> Comparator.comparing(Payment::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(
                    p -> p.getStatus() != null ? p.getStatus().name() : "",
                    Comparator.naturalOrder());
            case "method" -> Comparator.comparing(
                    p -> p.getMethod() != null ? p.getMethod().name() : "",
                    Comparator.naturalOrder());
            default -> Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return direction == Sort.Direction.DESC ? comparator.reversed() : comparator;
    }

    @PostMapping("/{id}/mark-refunded")
    public String markPaymentRefunded(@PathVariable Long id,
                                      @RequestParam(required = false) String externalRefundRef,
                                      @RequestParam(required = false) String refundNote,
                                      Authentication authentication,
                                      HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        String actor = authentication != null ? authentication.getName() : "admin";
        String ipAddress = extractClientIp(request);
        try {
            paymentService.markPaymentRefunded(id, actor, externalRefundRef, refundNote, ipAddress);
            redirectAttributes.addFlashAttribute("success",
                    "Đã đánh dấu giao dịch #" + id + " là đã hoàn tiền (REFUNDED).");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/payments";
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String raw = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.length() > 64 ? raw.substring(0, 64) : raw;
    }
}
