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

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public String listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<Order> orderPage = orderService.getAllOrdersPage(pageable);

        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
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

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelPendingOrder(id);
            redirectAttributes.addFlashAttribute("message", "Đã hủy đơn hàng #" + id + " thành công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi hủy đơn hàng: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }
}
