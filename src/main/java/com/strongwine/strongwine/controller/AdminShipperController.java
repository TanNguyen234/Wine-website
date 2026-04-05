package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.service.ShipperService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/shippers")
public class AdminShipperController {

    private final ShipperService shipperService;

    public AdminShipperController(ShipperService shipperService) {
        this.shipperService = shipperService;
    }

    @GetMapping
    public String listShippers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {
            
        org.springframework.data.domain.Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(direction, sortBy));
        org.springframework.data.domain.Page<Shipper> shipperPage = shipperService.getShippersPage(pageable);
        
        model.addAttribute("shippers", shipperPage.getContent());
        Map<String, Long> stats = shipperService.getShipperOverviewStats();
        model.addAttribute("shipperStats", stats);
        
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
        model.addAttribute("totalPages", shipperPage.getTotalPages());
        model.addAttribute("hasNext", shipperPage.hasNext());
        model.addAttribute("hasPrevious", shipperPage.hasPrevious());
        model.addAttribute("totalEntries", shipperPage.getTotalElements());
        
        return "admin-shippers";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("users", shipperService.getAvailableUsersForShipper());
        model.addAttribute("statuses", ShipperStatus.values());
        return "admin-shipper-form";
    }

    @PostMapping("/create")
    public String createShipper(@RequestParam Long userId,
                                @RequestParam String name,
                                @RequestParam String phone,
                                @RequestParam(required = false) String vehicleType,
                                @RequestParam(defaultValue = "ACTIVE") ShipperStatus status,
                                @RequestParam(defaultValue = "false") boolean isAvailable,
                                RedirectAttributes redirectAttributes) {
        try {
            shipperService.createShipper(userId, name, phone, vehicleType, status, isAvailable);
            redirectAttributes.addFlashAttribute("success", "Tạo shipper thành công");
            return "redirect:/admin/shippers";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Tạo shipper thất bại: " + ex.getMessage());
            return "redirect:/admin/shippers/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Shipper shipper = shipperService.getShipperById(id);
            model.addAttribute("editingShipper", shipper);
            model.addAttribute("users", shipperService.getAvailableUsersForShipper(id));
            model.addAttribute("statuses", ShipperStatus.values());
            return "admin-shipper-form";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy shipper");
            return "redirect:/admin/shippers";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateShipper(@PathVariable Long id,
                                @RequestParam Long userId,
                                @RequestParam String name,
                                @RequestParam String phone,
                                @RequestParam(required = false) String vehicleType,
                                @RequestParam(defaultValue = "ACTIVE") ShipperStatus status,
                                @RequestParam(defaultValue = "false") boolean isAvailable,
                                RedirectAttributes redirectAttributes) {
        try {
            shipperService.updateShipper(id, userId, name, phone, vehicleType, status, isAvailable);
            redirectAttributes.addFlashAttribute("success", "Cập nhật shipper thành công");
            return "redirect:/admin/shippers";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật shipper thất bại: " + ex.getMessage());
            return "redirect:/admin/shippers/edit/" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteShipper(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            shipperService.deleteShipper(id);
            redirectAttributes.addFlashAttribute("success", "Xóa shipper thành công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Xóa shipper thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shippers";
    }
}
