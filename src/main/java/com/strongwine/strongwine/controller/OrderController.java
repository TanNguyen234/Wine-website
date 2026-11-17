package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.port.ShipmentOrderLifecyclePort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    @Autowired(required = false)
    private ShipmentService shipmentService;

    @Lazy
    @Autowired(required = false)
    private ShipmentOrderLifecyclePort shipmentOrderLifecyclePort;

    @GetMapping("/orders")
    public String myOrders(Authentication authentication, Model model) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<Order> orders = orderService.getOrdersByUserId(currentUser.getId());
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Shipment> shipmentByOrderId = resolveShipmentMapByOrderIds(orderIds);

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

        boolean admin = isAdmin(currentUser);
        Optional<Order> orderOpt = admin
                ? orderService.getOrderById(orderId)
                : orderService.getOrderByIdForUser(orderId, currentUser.getId());
        if (orderOpt.isEmpty()) {
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        Shipment shipment = resolveShipmentByOrderId(orderId).orElse(null);
        boolean isOwner = order.getUser() != null
                && currentUser.getId() != null
                && currentUser.getId().equals(order.getUser().getId());

        model.addAttribute("isAdmin", admin);
        model.addAttribute("shipment", shipment);
        model.addAttribute("order", order);
        if (!admin && isOwner && shipment != null && shipmentOrderLifecyclePort != null) {
            shipmentOrderLifecyclePort.decryptOtpForOrderView(shipment)
                    .ifPresent(otp -> model.addAttribute("decryptedOtp", otp));
        }
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
            String paymentRedirectUrl = paymentService.createPaymentSession(order, method, baseUrl, request.getRemoteAddr());
            return "redirect:" + paymentRedirectUrl;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo phiên thanh toán: " + rootCauseMessage(ex));
            return "redirect:/orders/" + orderId;
        }
    }

    /**
     * Lets the customer cancel an order that is unpaid and has not been handed to shipping yet.
     * Releases the reserved stock through OrderService.
     */
    @PostMapping("/orders/{id}/cancel")
    public String cancelUnpaidOrder(@PathVariable("id") Long orderId,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để hủy đơn hàng");
            return "redirect:/login";
        }

        Optional<Order> orderOpt = orderService.getOrderByIdForUser(orderId, currentUser.getId());
        if (orderOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn hàng");
            return "redirect:/orders";
        }

        Order order = orderOpt.get();
        if (!isCustomerCancellable(order, resolveShipmentByOrderId(orderId))) {
            redirectAttributes.addFlashAttribute("error", "Đơn hàng này không thể hủy vì đã thanh toán hoặc đã được chuyển vận chuyển");
            return "redirect:/orders/" + orderId;
        }

        orderService.cancelPendingOrder(orderId, currentUser.getUsername(), "CUSTOMER_CANCEL");
        redirectAttributes.addFlashAttribute("message", "Đã hủy đơn hàng #" + orderId + ". Số lượng rượu đã được hoàn lại kho.");
        return "redirect:/orders";
    }

    @PostMapping("/orders/{id}/reorder")
    public String reorderOrder(@PathVariable("id") Long orderId,
                               Authentication authentication,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để đặt lại đơn hàng");
            return "redirect:/login";
        }

        try {
            OrderService.ReorderSummary summary = orderService.reorderOrder(orderId, currentUser.getId());
            if (summary.addedCount() == 0) {
                String skipped = String.join(", ", summary.skippedItems());
                redirectAttributes.addFlashAttribute("error",
                        skipped.isEmpty()
                                ? "Không có sản phẩm nào trong đơn hàng #" + orderId + " còn khả dụng để đặt lại."
                                : "Các sản phẩm đã ngừng bán hoặc hết hàng: " + skipped);
                return "redirect:/orders/" + orderId;
            }

            if (session != null) {
                session.setAttribute("reorderCartItems", summary.addedItems());
            }
            redirectAttributes.addFlashAttribute("reorderItems", summary.addedItems());
            redirectAttributes.addFlashAttribute("reorderSkippedItems", summary.skippedItems());
            redirectAttributes.addFlashAttribute("message",
                    "Đã thêm " + summary.addedCount() + " sản phẩm từ đơn hàng #" + orderId + " vào giỏ hàng.");
            if (!summary.skippedItems().isEmpty()) {
                redirectAttributes.addFlashAttribute("warning",
                        "Một số sản phẩm đã hết hàng hoặc ngừng kinh doanh và được bỏ qua: "
                                + String.join(", ", summary.skippedItems()));
            }
            return "redirect:/cart";
        } catch (ResourceNotFoundException | SecurityException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/orders";
        }
    }

    private Map<Long, Shipment> resolveShipmentMapByOrderIds(List<Long> orderIds) {
        if (shipmentOrderLifecyclePort != null) {
            return shipmentOrderLifecyclePort.getShipmentMapByOrderIds(orderIds);
        }
        if (shipmentService != null) {
            return shipmentService.getShipmentMapByOrderIds(orderIds);
        }
        return Map.of();
    }

    private Optional<Shipment> resolveShipmentByOrderId(Long orderId) {
        if (shipmentOrderLifecyclePort != null) {
            return shipmentOrderLifecyclePort.getShipmentByOrderId(orderId);
        }
        if (shipmentService != null) {
            return shipmentService.getShipmentByOrderId(orderId);
        }
        return Optional.empty();
    }

    /**
     * Customer may cancel only an unpaid PENDING order whose shipment, if any, is still PENDING_ASSIGNMENT.
     * Checkout creates that shipment immediately, so it must not block cancellation (regression: F-SW-04).
     */
    static boolean isCustomerCancellable(Order order, Optional<Shipment> shipment) {
        boolean notHandedToShipping = shipment
                .map(s -> s.getStatus() == ShipmentStatus.PENDING_ASSIGNMENT)
                .orElse(true);
        return isCancellableByCustomer(order) && notHandedToShipping;
    }

    private static boolean isCancellableByCustomer(Order order) {
        return order.getStatus() == OrderStatus.PENDING
                && order.getPaymentStatus() != PaymentStatus.SUCCESS;
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
        // COD is settled on delivery; a second payment session would duplicate the payment and the shipment.
        return order != null
                && order.getPaymentMethod() != PaymentMethod.COD
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


