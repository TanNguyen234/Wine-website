package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ShipmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for order-related pages
 */
@Controller
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ShipmentService shipmentService;

    @GetMapping("/orders")
    public String myOrders(Authentication authentication, Model model) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<Order> orders = orderService.getOrdersByUserId(currentUser.getId());
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Shipment> shipmentByOrderId = shipmentService.getShipmentMapByOrderIds(orderIds);

        model.addAttribute("orders", orders);
        model.addAttribute("shipmentByOrderId", shipmentByOrderId);
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable("id") Long orderId,
                              Authentication authentication,
                              Model model) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        Optional<Order> orderOpt = isAdmin(currentUser)
                ? orderService.getOrderById(orderId)
                : orderService.getOrderByIdForUser(orderId, currentUser.getId());
        if (orderOpt.isEmpty()) {
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        model.addAttribute("shipment", shipmentService.getShipmentByOrderId(orderId).orElse(null));
        model.addAttribute("order", order);
        return "order-detail";
    }
    
    /**
     * Order confirmation page
     */
    @GetMapping("/order-confirmation")
    public String orderConfirmation(@ModelAttribute("orderId") Long orderId,
                                   @ModelAttribute("fullName") String fullName,
                                   @ModelAttribute("phone") String phone,
                                   @ModelAttribute("address") String address,
                                   Authentication authentication,
                                   Model model) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (orderId == null) {
            return "redirect:/orders";
        }

        Optional<Order> orderOpt = isAdmin(currentUser)
                ? orderService.getOrderById(orderId)
                : orderService.getOrderByIdForUser(orderId, currentUser.getId());
        if (orderOpt.isEmpty()) {
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        if (fullName == null || fullName.isBlank()) {
            fullName = order.getShippingFullName();
        }
        if (phone == null || phone.isBlank()) {
            phone = order.getShippingPhone();
        }
        if (address == null || address.isBlank()) {
            address = order.getShippingAddress();
        }
        model.addAttribute("orderStatus", order.getStatus());
        model.addAttribute("paymentStatus", order.getPaymentStatus());
        
        model.addAttribute("orderId", orderId);
        model.addAttribute("fullName", fullName);
        model.addAttribute("phone", phone);
        model.addAttribute("address", address);
        
        return "order-confirmation";
    }

    @PostMapping("/orders/{id}/pay")
    public String repayOrder(@PathVariable("id") Long orderId,
                             Authentication authentication,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để thanh toán");
            return "redirect:/login";
        }

        Optional<Order> orderOpt = orderService.getOrderByIdForUser(orderId, currentUser.getId());
        if (orderOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn hàng");
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        if (!isPayable(order)) {
            redirectAttributes.addFlashAttribute("error", "Đơn hàng này không thể thanh toán lại");
            return "redirect:/orders/" + orderId;
        }

        try {
            String baseUrl = request.getScheme() + "://" + request.getServerName()
                    + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
            String method = order.getPaymentMethod() == null ? PaymentMethod.STRIPE.name() : order.getPaymentMethod().name();
            String paymentRedirectUrl = paymentService.createPaymentSession(order, method, baseUrl);
            return "redirect:" + paymentRedirectUrl;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo phiên thanh toán: " + rootCauseMessage(ex));
            return "redirect:/orders/" + orderId;
        }
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        String username;
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            username = authentication.getName();
        }

        if (username == null || username.isBlank()) {
            return null;
        }

        return userRepository.findByUsername(username).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private boolean isPayable(Order order) {
        return order != null
                && order.getPaymentStatus() != PaymentStatus.SUCCESS
                && order.getStatus() != OrderStatus.CANCELLED;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return "Lỗi hệ thống";
        }
        return message;
    }
}


