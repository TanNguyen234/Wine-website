package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.service.WineService;
import com.strongwine.strongwine.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for home page and general navigation
 */
@Controller
public class HomeController {
    
    @Autowired
    private WineService wineService;

    @Autowired
    private InventoryService inventoryService;
    
    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    /**
     * Home page
     */
    @GetMapping("/home")
    public String home(Model model) {
        // Get featured wines (first 6 wines)
        var featuredWines = wineService.getAllWines().stream().limit(6).toList();
        model.addAttribute("featuredWines", featuredWines);
        model.addAttribute("availableStockByWineId", inventoryService.getAvailableStockByWineIds(featuredWines.stream().map(w -> w.getId()).toList()));
        return "home";
    }
}






