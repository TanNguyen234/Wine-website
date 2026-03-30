package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.Authentication;

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

    @GetMapping("/orders")
    public String myOrders(Authentication authentication, Model model) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("orders", orderService.getOrdersByUserId(currentUser.getId()));
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

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }
}


