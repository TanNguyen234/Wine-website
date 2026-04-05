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

    @org.springframework.web.bind.annotation.GetMapping
    public String listInventory(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "id") String sortBy,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "desc") String sortDir,
            org.springframework.ui.Model model) {

        // For inventory, typically we just want to paginate transactions OR the inventory overview.
        // We will paginate the overview here.
        org.springframework.data.domain.Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(direction, sortBy));
        
        org.springframework.data.domain.Page<com.strongwine.strongwine.entity.Inventory> inventoryPage = inventoryService.getInventoryOverviewPage(pageable);

        model.addAttribute("inventories", inventoryPage.getContent());
        model.addAttribute("inventoryTransactions", inventoryService.getRecentTransactions());
        
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
        model.addAttribute("totalPages", inventoryPage.getTotalPages());
        model.addAttribute("hasNext", inventoryPage.hasNext());
        model.addAttribute("hasPrevious", inventoryPage.hasPrevious());
        model.addAttribute("totalEntries", inventoryPage.getTotalElements());

        return "admin-inventory";
    }

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
