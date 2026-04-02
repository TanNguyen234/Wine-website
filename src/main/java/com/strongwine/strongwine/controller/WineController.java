package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.FileStorageService;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.ReviewService;
import com.strongwine.strongwine.service.WineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Controller for wine-related pages
 */
@Controller
@RequestMapping("/wines")
public class WineController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "price", "year", "createdAt");
    
    @Autowired
    private WineService wineService;
    
    @Autowired
    private ReviewService reviewService;
    
    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private InventoryService inventoryService;
    
    /**
     * Wine list page with search and filter
     */
    @GetMapping
    public String wineList(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 12 : Math.min(size, 50);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        boolean hasFilter = name != null || type != null || country != null || year != null || minPrice != null || maxPrice != null;

        List<Wine> wines;
        int totalPages;
        boolean hasNext;
        boolean hasPrevious;

        if (Boolean.TRUE.equals(inStock)) {
            List<Wine> allFiltered = hasFilter
                    ? wineService.searchWines(name, type, country, year, minPrice, maxPrice)
                    : wineService.getAllWines();

            Map<Long, Integer> stockMapAll = inventoryService.getAvailableStockByWineIds(allFiltered.stream().map(Wine::getId).toList());
            List<Wine> inStockWines = allFiltered.stream()
                    .filter(w -> stockMapAll.getOrDefault(w.getId(), 0) > 0)
                    .toList();

            int fromIndex = Math.min(safePage * safeSize, inStockWines.size());
            int toIndex = Math.min(fromIndex + safeSize, inStockWines.size());
            wines = inStockWines.subList(fromIndex, toIndex);

            totalPages = Math.max(1, (int) Math.ceil((double) inStockWines.size() / safeSize));
            hasPrevious = safePage > 0;
            hasNext = safePage + 1 < totalPages;
        } else {
            Page<Wine> winePage = hasFilter
                    ? wineService.searchWinesPage(name, type, country, year, minPrice, maxPrice, pageable)
                    : wineService.getAllWinesPage(pageable);
            wines = winePage.getContent();
            totalPages = Math.max(1, winePage.getTotalPages());
            hasNext = winePage.hasNext();
            hasPrevious = winePage.hasPrevious();
        }

        Map<Long, Integer> availableStockByWineId = inventoryService.getAvailableStockByWineIds(wines.stream().map(Wine::getId).toList());
        
        model.addAttribute("wines", wines);
        model.addAttribute("name", name);
        model.addAttribute("type", type);
        model.addAttribute("country", country);
        model.addAttribute("year", year);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("inStock", inStock);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", direction.name().toLowerCase());
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("hasPrevious", hasPrevious);
        model.addAttribute("countries", wineService.getAllWines().stream().map(Wine::getCountry).filter(c -> c != null && !c.isBlank()).distinct().sorted().toList());
        model.addAttribute("availableStockByWineId", availableStockByWineId);
        
        return "wine-list";
    }
    
    /**
     * Wine details page
     */
    @GetMapping("/{id}")
    public String wineDetails(@PathVariable Long id, Model model) {
        Wine wine = wineService.getWineById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Wine not found"));
        
        model.addAttribute("wine", wine);
        model.addAttribute("reviews", reviewService.getReviewsByWineId(id));
        model.addAttribute("availableStock", inventoryService.getAvailableStockByWineIds(List.of(id)).getOrDefault(id, 0));
        return "wine-details";
    }
    
    /**
     * Show create wine form (Admin only)
     */
    @GetMapping("/admin/create")
    public String showCreateForm(Model model) {
        model.addAttribute("wine", new Wine());
        return "wine-form";
    }
    
    /**
     * Show edit wine form (Admin only)
     */
    @GetMapping("/admin/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Wine wine = wineService.getWineById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Wine not found"));
        model.addAttribute("wine", wine);
        return "wine-form";
    }
    
    /**
     * Create a new wine (Admin only)
     */
    @PostMapping("/admin/create")
    public String createWine(
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam(required = false) String country,
            @RequestParam Integer year,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {
        
        try {
            Wine wine = new Wine();
            wine.setName(name);
            wine.setType(type);
            wine.setCountry(country);
            wine.setYear(year);
            wine.setPrice(price);
            wine.setDescription(description);
            
            // Handle image: either upload file or use URL
            String finalImageUrl = null;
            if (imageFile != null && !imageFile.isEmpty()) {
                // Validate file
                if (!fileStorageService.isValidImageFile(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Tệp không hợp lệ. Chỉ hỗ trợ JPG và PNG.");
                    return "redirect:/wines/admin/create";
                }
                if (!fileStorageService.isValidFileSize(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Kích thước tệp quá lớn. Tối đa 5MB.");
                    return "redirect:/wines/admin/create";
                }
                // Save uploaded file
                finalImageUrl = fileStorageService.saveFile(imageFile);
            } else if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                // Use provided URL
                finalImageUrl = imageUrl.trim();
            }
            
            // Validate that at least image URL or file is provided
            if (finalImageUrl == null || finalImageUrl.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng tải ảnh lên hoặc nhập URL ảnh.");
                return "redirect:/wines/admin/create";
            }
            
            wine.setImageUrl(finalImageUrl);
            wineService.createWine(wine);
            redirectAttributes.addFlashAttribute("success", "Tạo sản phẩm rượu thành công!");
            return "redirect:/admin";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo sản phẩm: " + e.getMessage());
            return "redirect:/wines/admin/create";
        }
    }
    
    /**
     * Update an existing wine (Admin only)
     */
    @PostMapping("/admin/edit/{id}")
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
            RedirectAttributes redirectAttributes) {
        
        try {
            Wine wine = wineService.getWineByIdOrThrow(id);
            String oldImageUrl = wine.getImageUrl();
            
            wine.setName(name);
            wine.setType(type);
            wine.setCountry(country);
            wine.setYear(year);
            wine.setPrice(price);
            wine.setDescription(description);
            
            // Handle image: either upload file or use URL
            String finalImageUrl = oldImageUrl; // Keep existing if no new image provided
            if (imageFile != null && !imageFile.isEmpty()) {
                // Validate file
                if (!fileStorageService.isValidImageFile(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Tệp không hợp lệ. Chỉ hỗ trợ JPG và PNG.");
                    return "redirect:/wines/admin/edit/" + id;
                }
                if (!fileStorageService.isValidFileSize(imageFile)) {
                    redirectAttributes.addFlashAttribute("error", "Kích thước tệp quá lớn. Tối đa 5MB.");
                    return "redirect:/wines/admin/edit/" + id;
                }
                // Save new uploaded file
                finalImageUrl = fileStorageService.saveFile(imageFile);
                // Delete old file if it was an uploaded file
                if (oldImageUrl != null && oldImageUrl.startsWith("/uploads/")) {
                    fileStorageService.deleteFile(oldImageUrl);
                }
            } else if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                // Use provided URL
                finalImageUrl = imageUrl.trim();
                // Delete old file if it was an uploaded file
                if (oldImageUrl != null && oldImageUrl.startsWith("/uploads/")) {
                    fileStorageService.deleteFile(oldImageUrl);
                }
            }
            
            wine.setImageUrl(finalImageUrl);
            wineService.updateWine(id, wine);
            redirectAttributes.addFlashAttribute("success", "Cập nhật sản phẩm rượu thành công!");
            return "redirect:/admin";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật sản phẩm: " + e.getMessage());
            return "redirect:/wines/admin/edit/" + id;
        }
    }
    
    /**
     * Delete a wine (Admin only)
     */
    @PostMapping("/admin/delete/{id}")
    public String deleteWine(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Wine wine = wineService.getWineByIdOrThrow(id);
            String imageUrl = wine.getImageUrl();
            
            // Delete image file if it was uploaded
            if (imageUrl != null && imageUrl.startsWith("/uploads/")) {
                fileStorageService.deleteFile(imageUrl);
            }
            
            wineService.deleteWine(id);
            redirectAttributes.addFlashAttribute("success", "Xóa sản phẩm rượu thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa sản phẩm: " + e.getMessage());
        }
        return "redirect:/admin";
    }
}

