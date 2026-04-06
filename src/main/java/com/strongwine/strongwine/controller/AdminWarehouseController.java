package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.service.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/warehouses")
public class AdminWarehouseController {

    @Autowired
    private WarehouseService warehouseService;

    @GetMapping
    public String listWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Warehouse> warehousePage = warehouseService.getWarehousesPage(pageable);

        model.addAttribute("warehouses", warehousePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
        model.addAttribute("totalPages", warehousePage.getTotalPages());
        model.addAttribute("hasNext", warehousePage.hasNext());
        model.addAttribute("hasPrevious", warehousePage.hasPrevious());
        model.addAttribute("totalEntries", warehousePage.getTotalElements());

        return "admin-warehouses";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("warehouse", new Warehouse());
        return "admin-warehouse-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return warehouseService.getWarehouseById(id).map(warehouse -> {
            model.addAttribute("warehouse", warehouse);
            return "admin-warehouse-form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy nhà kho!");
            return "redirect:/admin/warehouses";
        });
    }

    @PostMapping("/create")
    public String createWarehouse(@ModelAttribute Warehouse warehouse, RedirectAttributes redirectAttributes) {
        try {
            warehouseService.createWarehouse(warehouse);
            redirectAttributes.addFlashAttribute("success", "Tạo nhà kho thành công!");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/warehouses/create";
        }
        return "redirect:/admin/warehouses";
    }

    @PostMapping("/edit/{id}")
    public String updateWarehouse(@PathVariable Long id, @ModelAttribute Warehouse warehouse, RedirectAttributes redirectAttributes) {
        try {
            warehouseService.updateWarehouse(id, warehouse);
            redirectAttributes.addFlashAttribute("success", "Cập nhật nhà kho thành công!");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/warehouses/edit/" + id;
        }
        return "redirect:/admin/warehouses";
    }

    @PostMapping("/toggle/{id}")
    public String toggleWarehouseStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            warehouseService.toggleActiveStatus(id);
            redirectAttributes.addFlashAttribute("success", "Thay đổi trạng thái thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Đã xảy ra lỗi khi thay đổi trạng thái!");
        }
        return "redirect:/admin/warehouses";
    }
}
