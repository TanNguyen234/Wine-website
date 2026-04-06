package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.dto.StockOperationRequest;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequestMapping("/admin/inventory")
public class AdminInventoryController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "currentQuantity", "reservedQuantity", "reorderLevel", "updatedAt");

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping({"", "/"})
    public String listInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, safeSortBy));

        Page<Inventory> inventoryPage = inventoryService.getInventoryOverviewPage(pageable);

        model.addAttribute("inventories", inventoryPage.getContent());
        model.addAttribute("inventoryTransactions", inventoryService.getRecentTransactions());
        
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", safeSortBy);
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
            return "redirect:/admin/inventory";
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
        return "redirect:/admin/inventory";
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
            return "redirect:/admin/inventory";
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
        return "redirect:/admin/inventory";
    }

    @PostMapping("/adjust/{wineId}")
    public String adjustStock(@PathVariable Long wineId,
                              @Valid StockOperationRequest request,
                              BindingResult bindingResult,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldError() != null ? bindingResult.getFieldError().getDefaultMessage() : "Dữ liệu không hợp lệ";
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin/inventory";
        }
        try {
            Long userId = extractUserId(authentication);
            inventoryService.adjustStock(wineId, request.getWarehouseId(), request.getQuantity(),
                    resolveUsername(userId),
                    request.getNote());
            redirectAttributes.addFlashAttribute("success", "Điều chỉnh tồn kho thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Điều chỉnh tồn kho thất bại: " + e.getMessage());
        }
        return "redirect:/admin/inventory";
    }

    @PostMapping("/reorder-level/{inventoryId}")
    public String updateReorderLevel(@PathVariable Long inventoryId,
                                     @RequestParam Integer reorderLevel,
                                     RedirectAttributes redirectAttributes) {
        try {
            inventoryService.updateReorderLevel(inventoryId, reorderLevel);
            redirectAttributes.addFlashAttribute("success", "Cập nhật mức đặt lại thành công");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật mức đặt lại thất bại: " + e.getMessage());
        }
        return "redirect:/admin/inventory";
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
