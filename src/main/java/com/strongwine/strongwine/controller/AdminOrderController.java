package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.service.OrderService;
import org.springframework.data.domain.Page;
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

import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "orderDate", "totalPrice", "status", "paymentStatus", "paymentMethod", "updatedAt");

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public String viewOrderDetail(@PathVariable Long id) {
        return "redirect:/orders/" + id;
    }

    @GetMapping
    public String listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) com.strongwine.strongwine.entity.OrderStatus status,
            Model model) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "orderDate";
        String safeSortDir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        Sort.Direction direction = "asc".equalsIgnoreCase(safeSortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        Page<Order> orderPage = orderService.getOrdersPage(status, pageable);

        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("currentStatus", status);
        model.addAttribute("totalAllOrders", orderService.countOrders());
        model.addAttribute("pendingCount", orderService.countOrdersByStatus(com.strongwine.strongwine.entity.OrderStatus.PENDING));
        model.addAttribute("paidCount", orderService.countOrdersByStatus(com.strongwine.strongwine.entity.OrderStatus.PAID));
        model.addAttribute("cancelledCount", orderService.countOrdersByStatus(com.strongwine.strongwine.entity.OrderStatus.CANCELLED));

        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", safeSortDir);
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("hasNext", orderPage.hasNext());
        model.addAttribute("hasPrevious", orderPage.hasPrevious());
        model.addAttribute("totalEntries", orderPage.getTotalElements());

        return "admin-orders";
    }

    @PostMapping("/{id}/mark-paid")
    public String markOrderPaid(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            orderService.markOrderPaid(id, "ADMIN_CONFIRM_" + actor);
            redirectAttributes.addFlashAttribute("message", "Đã xác nhận thanh toán thành công cho đơn #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xác nhận thanh toán: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/{id}/settle-cod")
    public String settleCodOrder(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            String actor = authentication != null ? authentication.getName() : "admin";
            orderService.settleCodOrder(id, actor);
            redirectAttributes.addFlashAttribute("message", "Đã xác nhận thu hộ COD thành công cho đơn #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xác nhận thu hộ COD: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            Optional<Order> existingOpt = orderService.getOrderById(id);
            if (existingOpt.isPresent()
                    && existingOpt.get().getStatus() != com.strongwine.strongwine.entity.OrderStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error",
                        "Chỉ có thể hủy trực tiếp đơn hàng đang chờ thanh toán (PENDING). Đơn #"
                                + id + " hiện ở trạng thái " + existingOpt.get().getStatus()
                                + " — vui lòng xử lý qua quy trình trả hàng / hoàn tiền.");
                return "redirect:/admin/orders";
            }
            String actor = authentication != null ? authentication.getName() : "admin";
            String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "ADMIN_CANCEL";
            orderService.cancelPendingOrder(id, actor, effectiveReason);
            redirectAttributes.addFlashAttribute("message", "Đã hủy đơn hàng #" + id + " thành công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi hủy đơn hàng: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }
}
