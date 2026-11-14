package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.CategoryService;
import com.strongwine.strongwine.service.WineService;
import com.strongwine.strongwine.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Controller for home page and general navigation
 */
@Controller
public class HomeController {
    
    @Autowired
    private WineService wineService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired(required = false)
    private CategoryService categoryService;
    
    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    /**
     * Home page
     */
    @GetMapping("/home")
    public String home(Model model) {
        List<Wine> allWines = wineService.getAllWines();
        List<Wine> traditionalWines = allWines.stream()
                .filter(w -> {
                    String t = w.getType() == null ? "" : w.getType().trim().toLowerCase(Locale.ROOT);
                    return t.contains("traditional") || t.contains("truyền thống") || t.contains("truyen thong");
                })
                .limit(4)
                .toList();

        List<Wine> featuredWines = new ArrayList<>();
        Set<Long> seenIds = new LinkedHashSet<>();
        for (Wine w : allWines) {
            if (featuredWines.size() >= 4) {
                break;
            }
            if (w.getId() == null || seenIds.add(w.getId())) {
                featuredWines.add(w);
            }
        }
        for (Wine tw : traditionalWines) {
            if (featuredWines.size() >= 6) {
                break;
            }
            if (tw.getId() == null || seenIds.add(tw.getId())) {
                featuredWines.add(tw);
            }
        }
        for (Wine w : allWines) {
            if (featuredWines.size() >= 6) {
                break;
            }
            if (w.getId() == null || seenIds.add(w.getId())) {
                featuredWines.add(w);
            }
        }

        List<Long> stockWineIds = allWines.stream()
                .map(Wine::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        model.addAttribute("featuredWines", featuredWines);
        model.addAttribute("traditionalWines", traditionalWines);
        model.addAttribute("availableStockByWineId", inventoryService.getAvailableStockByWineIds(stockWineIds));
        if (categoryService != null) {
            model.addAttribute("categories", categoryService.getAllCategories());
        } else {
            model.addAttribute("categories", List.of());
        }
        return "home";
    }
}






