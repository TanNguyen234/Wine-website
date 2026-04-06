package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.service.WineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/wines")
public class AdminWineController {

    @Autowired
    private WineService wineService;

    @GetMapping
    public String listWines(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Wine> winePage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            winePage = wineService.searchWinesPage(keyword.trim(), null, null, null, null, null, pageable);
        } else {
            winePage = wineService.getAllWinesPage(pageable);
        }

        model.addAttribute("wines", winePage.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
        model.addAttribute("totalPages", winePage.getTotalPages());
        model.addAttribute("hasNext", winePage.hasNext());
        model.addAttribute("hasPrevious", winePage.hasPrevious());
        model.addAttribute("totalEntries", winePage.getTotalElements());

        return "admin-wines";
    }
}
