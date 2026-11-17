package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.AdminAuditLogService;
import com.strongwine.strongwine.service.FileStorageService;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.WineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/admin/wines")
public class AdminWineController {

    private static final List<String> ADMIN_WINE_TYPES = List.of("Red", "White", "Rose", "Sparkling", "Traditional");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "type", "country", "year", "price", "createdAt");

    private final WineService wineService;
    private final FileStorageService fileStorageService;

    @Autowired(required = false)
    private AdminAuditLogService adminAuditLogService;

    @Autowired(required = false)
    private InventoryService inventoryService;

    @Autowired
    public AdminWineController(WineService wineService, FileStorageService fileStorageService) {
        this.wineService = wineService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping({"", "/"})
    public String listWines(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "id";
        String safeSortDir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        Sort.Direction direction = "asc".equalsIgnoreCase(safeSortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String trimmedType = (type != null && !type.isBlank()) ? type.trim() : null;

        Page<Wine> winePage = (trimmedKeyword == null && trimmedType == null)
                ? wineService.getAllWinesPage(pageable)
                : wineService.searchWinesPage(trimmedKeyword, trimmedType, null, null, null, null, pageable);

        if (winePage == null) {
            winePage = Page.empty(pageable);
        }

        List<Wine> wines = winePage.getContent();
        Map<Long, Integer> availableStockByWineId = Collections.emptyMap();
        if (inventoryService != null && !wines.isEmpty()) {
            List<Long> wineIds = wines.stream()
                    .map(Wine::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!wineIds.isEmpty()) {
                availableStockByWineId = Objects.requireNonNullElse(
                        inventoryService.getAvailableStockByWineIds(wineIds), Collections.emptyMap());
            }
        }

        model.addAttribute("wines", wines);
        model.addAttribute("availableStockByWineId", availableStockByWineId);
        model.addAttribute("currentPage", winePage.getNumber());
        model.addAttribute("pageSize", winePage.getSize());
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", safeSortDir);
        model.addAttribute("totalPages", winePage.getTotalPages());
        model.addAttribute("hasNext", winePage.hasNext());
        model.addAttribute("hasPrevious", winePage.hasPrevious());
        model.addAttribute("totalEntries", winePage.getTotalElements());
        model.addAttribute("keyword", trimmedKeyword);
        model.addAttribute("type", trimmedType);
        model.addAttribute("types", ADMIN_WINE_TYPES);
        return "admin-wines";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("wine")) {
            model.addAttribute("wine", new Wine());
        }
        return "wine-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Wine wine = wineService.getWineById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Wine not found"));
        model.addAttribute("wine", wine);
        return "wine-form";
    }

    @PostMapping("/create")
    public String createWine(
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam(required = false) String country,
            @RequestParam Integer year,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) MultipartFile imageFile,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request) {

        Wine wine = new Wine();
        try {
            wine.setName(name);
            wine.setType(type);
            wine.setCountry(country);
            wine.setYear(year);
            wine.setPrice(price);
            wine.setDescription(description);

            String finalImageUrl = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!fileStorageService.isValidImageFile(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Tệp không hợp lệ. Chỉ hỗ trợ JPG và PNG.");
                    redirectAttributes.addFlashAttribute("wine", wine);
                    return "redirect:/admin/wines/create";
                }
                if (!fileStorageService.isValidFileSize(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Kích thước tệp quá lớn. Tối đa 5MB.");
                    redirectAttributes.addFlashAttribute("wine", wine);
                    return "redirect:/admin/wines/create";
                }
                finalImageUrl = fileStorageService.saveFile(imageFile);
            } else if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                finalImageUrl = imageUrl.trim();
            }

            if (finalImageUrl == null || finalImageUrl.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng tải ảnh lên hoặc nhập URL ảnh.");
                redirectAttributes.addFlashAttribute("wine", wine);
                return "redirect:/admin/wines/create";
            }

            wine.setImageUrl(finalImageUrl);
            Wine saved = wineService.createWine(wine);
            auditWineAction(authentication, "WINE_CREATE", saved.getId(), null,
                    Map.of("name", saved.getName(), "type", saved.getType(), "price", saved.getPrice()), request);
            redirectAttributes.addFlashAttribute("success", "Tạo sản phẩm rượu thành công!");
            return "redirect:/admin/wines";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo sản phẩm: " + e.getMessage());
            redirectAttributes.addFlashAttribute("wine", wine);
            return "redirect:/admin/wines/create";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateWine(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam(required = false) String country,
            @RequestParam Integer year,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) MultipartFile imageFile,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request) {

        try {
            Wine wine = wineService.getWineByIdOrThrow(id);
            String oldImageUrl = wine.getImageUrl();
            Map<String, Object> beforeState = Map.of(
                    "name", String.valueOf(wine.getName()),
                    "type", String.valueOf(wine.getType()),
                    "price", String.valueOf(wine.getPrice())
            );

            wine.setName(name);
            wine.setType(type);
            wine.setCountry(country);
            wine.setYear(year);
            wine.setPrice(price);
            wine.setDescription(description);

            String finalImageUrl = oldImageUrl;
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!fileStorageService.isValidImageFile(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Tệp không hợp lệ. Chỉ hỗ trợ JPG và PNG.");
                    return "redirect:/admin/wines/edit/" + id;
                }
                if (!fileStorageService.isValidFileSize(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Kích thước tệp quá lớn. Tối đa 5MB.");
                    return "redirect:/admin/wines/edit/" + id;
                }
                finalImageUrl = fileStorageService.saveFile(imageFile);
                if (oldImageUrl != null && oldImageUrl.startsWith("/uploads/")) {
                    fileStorageService.deleteFile(oldImageUrl);
                }
            } else if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                finalImageUrl = imageUrl.trim();
                if (!Objects.equals(finalImageUrl, oldImageUrl)
                        && oldImageUrl != null && oldImageUrl.startsWith("/uploads/")) {
                    fileStorageService.deleteFile(oldImageUrl);
                }
            }

            wine.setImageUrl(finalImageUrl);
            Wine updated = wineService.updateWine(id, wine);
            auditWineAction(authentication, "WINE_UPDATE", id, beforeState,
                    Map.of("name", updated.getName(), "type", updated.getType(), "price", updated.getPrice()), request);
            redirectAttributes.addFlashAttribute("success", "Cập nhật sản phẩm rượu thành công!");
            return "redirect:/admin/wines";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật sản phẩm: " + e.getMessage());
            return "redirect:/admin/wines/edit/" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteWine(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request) {
        try {
            Wine wine = wineService.getWineByIdOrThrow(id);
            wineService.deleteWine(id);
            auditWineAction(authentication, "WINE_DELETE", id,
                    Map.of("name", String.valueOf(wine.getName())), Map.of("deleted", true), request);
            redirectAttributes.addFlashAttribute("success", "Xóa sản phẩm rượu thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa sản phẩm: " + e.getMessage());
        }
        return "redirect:/admin/wines";
    }

    private void auditWineAction(Authentication authentication,
                                 String action,
                                 Long targetId,
                                 Object beforeState,
                                 Object afterState,
                                 HttpServletRequest request) {
        if (adminAuditLogService == null) {
            return;
        }
        String actor = authentication != null && authentication.getName() != null
                ? authentication.getName() : "admin";
        String ipAddress = request != null ? request.getRemoteAddr() : null;
        adminAuditLogService.log(actor, action, "WINE", targetId, beforeState, afterState, ipAddress);
    }
}
