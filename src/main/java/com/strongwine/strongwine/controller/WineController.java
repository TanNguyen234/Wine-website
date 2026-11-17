package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.ReviewService;
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

    @Autowired(required = false)
    private AdminWineController adminWineController;

    @Autowired
    private InventoryService inventoryService;
    
    public String wineList(
            String name,
            String type,
            String country,
            Integer year,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Model model) {
        return wineList(name, type, country, year, minPrice, maxPrice, inStock, page, size, sortBy, sortDir, null, model);
    }

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
            @RequestParam(required = false) String sort,
            Model model) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? 12 : Math.min(size, 50);

        String resolvedSortBy = sortBy;
        String resolvedSortDir = sortDir;
        if (sort != null && !sort.isBlank()) {
            switch (sort.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "price-asc", "price_asc" -> {
                    resolvedSortBy = "price";
                    resolvedSortDir = "asc";
                }
                case "price-desc", "price_desc" -> {
                    resolvedSortBy = "price";
                    resolvedSortDir = "desc";
                }
                case "newest" -> {
                    resolvedSortBy = "createdAt";
                    resolvedSortDir = "desc";
                }
                case "name-asc", "name_asc" -> {
                    resolvedSortBy = "name";
                    resolvedSortDir = "asc";
                }
                case "name-desc", "name_desc" -> {
                    resolvedSortBy = "name";
                    resolvedSortDir = "desc";
                }
                case "year-desc", "year_desc" -> {
                    resolvedSortBy = "year";
                    resolvedSortDir = "desc";
                }
                case "year-asc", "year_asc" -> {
                    resolvedSortBy = "year";
                    resolvedSortDir = "asc";
                }
                default -> {
                    // keep sortBy / sortDir
                }
            }
        }

        String safeSortBy = ALLOWED_SORT_FIELDS.contains(resolvedSortBy) ? resolvedSortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(resolvedSortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        boolean hasFilter = name != null || type != null || country != null || year != null || minPrice != null || maxPrice != null;

        List<Wine> wines;
        int totalPages;
        boolean hasNext;
        boolean hasPrevious;
        int totalEntries; // total matching products (across all pages)

        try {
            if (Boolean.TRUE.equals(inStock)) {
                List<Wine> allFiltered = hasFilter
                        ? wineService.searchWines(name, type, country, year, minPrice, maxPrice)
                        : wineService.getAllWines();

                Map<Long, Integer> stockMapAll = inventoryService.getAvailableStockByWineIds(allFiltered.stream().map(Wine::getId).toList());
                java.util.Comparator<Wine> wineComparator = switch (safeSortBy) {
                    case "price" -> java.util.Comparator.comparing(Wine::getPrice, java.util.Comparator.nullsLast(java.math.BigDecimal::compareTo));
                    case "name" -> java.util.Comparator.comparing(Wine::getName, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "year" -> java.util.Comparator.comparing(Wine::getYear, java.util.Comparator.nullsLast(Integer::compareTo));
                    case "id" -> java.util.Comparator.comparing(Wine::getId, java.util.Comparator.nullsLast(Long::compareTo));
                    default -> java.util.Comparator.comparing(Wine::getCreatedAt, java.util.Comparator.nullsLast(java.time.LocalDateTime::compareTo))
                            .thenComparing(Wine::getId, java.util.Comparator.nullsLast(Long::compareTo));
                };
                if (direction == Sort.Direction.DESC) {
                    wineComparator = wineComparator.reversed();
                }
                List<Wine> inStockWines = allFiltered.stream()
                        .filter(w -> stockMapAll.getOrDefault(w.getId(), 0) > 0)
                        .sorted(wineComparator)
                        .toList();

                int fromIndex = Math.min(safePage * safeSize, inStockWines.size());
                int toIndex = Math.min(fromIndex + safeSize, inStockWines.size());
                wines = inStockWines.subList(fromIndex, toIndex);

                totalPages = Math.max(1, (int) Math.ceil((double) inStockWines.size() / safeSize));
                hasPrevious = safePage > 0;
                hasNext = safePage + 1 < totalPages;
                totalEntries = inStockWines.size();
            } else {
                Page<Wine> winePage = hasFilter
                        ? wineService.searchWinesPage(name, type, country, year, minPrice, maxPrice, pageable)
                        : wineService.getAllWinesPage(pageable);
                wines = winePage.getContent();
                totalPages = Math.max(1, winePage.getTotalPages());
                hasNext = winePage.hasNext();
                hasPrevious = winePage.hasPrevious();
                totalEntries = (int) winePage.getTotalElements();
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            wines = List.of();
            totalPages = 1;
            hasNext = false;
            hasPrevious = false;
            totalEntries = 0;
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
        model.addAttribute("sort", sort);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("hasPrevious", hasPrevious);
        model.addAttribute("totalEntries", totalEntries);
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
     * Redirect legacy create wine form URL to canonical /admin/wines/create
     */
    @GetMapping("/admin/create")
    public String showCreateForm() {
        return "redirect:/admin/wines/create";
    }

    /**
     * Redirect legacy edit wine form URL to canonical /admin/wines/edit/{id}
     */
    @GetMapping("/admin/edit/{id}")
    public String showEditForm(@PathVariable Long id) {
        return "redirect:/admin/wines/edit/" + id;
    }

    /**
     * Delegate legacy admin wine POST create to AdminWineController
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
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request) {
        if (adminWineController != null) {
            return adminWineController.createWine(name, type, country, year, price, description, imageUrl, imageFile,
                    redirectAttributes, authentication, request);
        }
        return "redirect:/admin/wines/create";
    }

    /**
     * Delegate legacy admin wine POST edit to AdminWineController
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
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request) {
        if (adminWineController != null) {
            return adminWineController.updateWine(id, name, type, country, year, price, description, imageUrl, imageFile,
                    redirectAttributes, authentication, request);
        }
        return "redirect:/admin/wines/edit/" + id;
    }

    /**
     * Delegate legacy admin wine POST delete to AdminWineController
     */
    @PostMapping("/admin/delete/{id}")
    public String deleteWine(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request) {
        if (adminWineController != null) {
            return adminWineController.deleteWine(id, redirectAttributes, authentication, request);
        }
        return "redirect:/admin";
    }
}

