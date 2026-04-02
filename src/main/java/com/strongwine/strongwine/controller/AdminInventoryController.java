package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.dto.StockOperationRequest;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/inventory")
public class AdminInventoryController {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/import/{wineId}")
    public String importStock(@PathVariable Long wineId,
                              @Valid StockOperationRequest request,
                              BindingResult bindingResult,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldError() != null ? bindingResult.getFieldError().getDefaultMessage() : "Dữ liệu không hợp lệ";
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin";
        }
        try {
            Long userId = extractUserId(authentication);
            inventoryService.importStock(wineId, request.getWarehouseId(), request.getQuantity(),
                    resolveUsername(userId),
                    request.getNote());
            redirectAttributes.addFlashAttribute("success", "Nhập kho thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Nhập kho thất bại: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/export/{wineId}")
    public String exportStock(@PathVariable Long wineId,
                              @Valid StockOperationRequest request,
                              BindingResult bindingResult,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldError() != null ? bindingResult.getFieldError().getDefaultMessage() : "Dữ liệu không hợp lệ";
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin";
        }
        try {
            Long userId = extractUserId(authentication);
            inventoryService.exportStock(wineId, request.getWarehouseId(), request.getQuantity(),
                    resolveUsername(userId),
                    request.getNote());
            redirectAttributes.addFlashAttribute("success", "Xuất kho thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Xuất kho thất bại: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        return user != null ? user.getId() : null;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "system";
        }
        return userRepository.findById(userId).map(User::getUsername).orElse("system");
    }
}
