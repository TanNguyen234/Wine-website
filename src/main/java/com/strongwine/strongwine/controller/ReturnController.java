package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.ReturnRequest;
import com.strongwine.strongwine.entity.ReturnStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.ReturnRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReturnController {

    private final ReturnRequestService returnRequestService;
    private final UserRepository userRepository;

    @Autowired
    public ReturnController(ReturnRequestService returnRequestService, UserRepository userRepository) {
        this.returnRequestService = returnRequestService;
        this.userRepository = userRepository;
    }

    @PostMapping("/orders/{id}/returns")
    public String submitReturnRequest(@PathVariable("id") Long orderId,
                                      @RequestParam("reason") String reason,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để gửi yêu cầu trả hàng");
            return "redirect:/login";
        }

        try {
            returnRequestService.requestReturn(orderId, currentUser.getId(), reason);
            redirectAttributes.addFlashAttribute("message", "Đã gửi yêu cầu trả hàng cho đơn #" + orderId);
            return "redirect:/orders/" + orderId;
        } catch (ResourceNotFoundException | SecurityException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/orders";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/orders/" + orderId;
        }
    }

    @GetMapping("/admin/returns")
    @ResponseBody
    public List<Map<String, Object>> listReturnRequests(@RequestParam(required = false) ReturnStatus status) {
        return returnRequestService.getReturnRequestsByStatus(status).stream()
                .map(this::toSummary)
                .toList();
    }

    @PostMapping("/admin/returns/{id}/approve")
    public String approveReturn(@PathVariable Long id,
                                @RequestParam(required = false) String adminNote,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            returnRequestService.approveReturn(id, resolveActor(authentication), adminNote);
            redirectAttributes.addFlashAttribute("message", "Đã duyệt yêu cầu trả hàng #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi duyệt yêu cầu trả hàng: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/returns/{id}/reject")
    public String rejectReturn(@PathVariable Long id,
                               @RequestParam(required = false) String adminNote,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            returnRequestService.rejectReturn(id, resolveActor(authentication), adminNote);
            redirectAttributes.addFlashAttribute("message", "Đã từ chối yêu cầu trả hàng #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi từ chối yêu cầu trả hàng: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    @PostMapping("/admin/returns/{id}/refund")
    public String completeRefund(@PathVariable Long id,
                                 @RequestParam(required = false) String adminNote,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            returnRequestService.completeRefund(id, resolveActor(authentication), adminNote);
            redirectAttributes.addFlashAttribute("message", "Đã hoàn tiền & nhập lại kho cho yêu cầu #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi hoàn tiền yêu cầu trả hàng: " + ex.getMessage());
        }
        return "redirect:/admin/orders";
    }

    private Map<String, Object> toSummary(ReturnRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", request.getId());
        map.put("orderId", request.getOrder() != null ? request.getOrder().getId() : null);
        map.put("userId", request.getUser() != null ? request.getUser().getId() : null);
        map.put("reason", request.getReason());
        map.put("status", request.getStatus() != null ? request.getStatus().name() : null);
        map.put("refundAmount", request.getRefundAmount());
        map.put("adminNote", request.getAdminNote());
        map.put("resolvedBy", request.getResolvedBy());
        map.put("createdAt", request.getCreatedAt());
        map.put("resolvedAt", request.getResolvedAt());
        return map;
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String username = authentication.getPrincipal() instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : authentication.getName();
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }

    private String resolveActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "admin";
        }
        return authentication.getName();
    }
}
