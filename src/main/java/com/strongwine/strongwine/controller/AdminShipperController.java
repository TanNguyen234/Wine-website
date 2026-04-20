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
    public String listShippers(Model model) {
        model.addAttribute("shippers", shipperService.getAllShippers());
        Map<String, Long> stats = shipperService.getShipperOverviewStats();
        model.addAttribute("shipperStats", stats);
        return "admin-shippers";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("users", shipperService.getAvailableUsersForShipper());
        model.addAttribute("statuses", ShipperStatus.values());
        return "admin-shipper-form";
    }

    @PostMapping("/create")
    public String createShipper(@RequestParam(required = false) Long userId,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String vehicleType,
                                @RequestParam(defaultValue = "ACTIVE") ShipperStatus status,
                                @RequestParam(defaultValue = "false") boolean isAvailable,
                                RedirectAttributes redirectAttributes) {
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Vui lòng chọn tài khoản người dùng. Hãy tạo tài khoản có vai trò SHIPPER trước.");
            return "redirect:/admin/shippers/create";
        }
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
                                @RequestParam(required = false) Long userId,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String vehicleType,
                                @RequestParam(defaultValue = "ACTIVE") ShipperStatus status,
                                @RequestParam(defaultValue = "false") boolean isAvailable,
                                RedirectAttributes redirectAttributes) {
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn tài khoản người dùng hợp lệ.");
            return "redirect:/admin/shippers/edit/" + id;
        }
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
