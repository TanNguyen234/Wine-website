package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.service.ShipmentOtpEmailService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/shipments")
public class AdminShipmentController {

    private final ShipmentService shipmentService;
    private final ShipperService shipperService;
    private final ShipmentOtpEmailService shipmentOtpEmailService;

    public AdminShipmentController(ShipmentService shipmentService,
                                   ShipperService shipperService,
                                   ShipmentOtpEmailService shipmentOtpEmailService) {
        this.shipmentService = shipmentService;
        this.shipperService = shipperService;
        this.shipmentOtpEmailService = shipmentOtpEmailService;
    }

    @GetMapping
    public String listShipments(@RequestParam(required = false) Long orderId,
                                @RequestParam(required = false) Long shipperId,
                                @RequestParam(required = false) ShipmentStatus status,
                                @RequestParam(required = false) String keyword,
                                Model model) {
        List<Shipment> shipments = shipmentService.getShipmentsForAdmin(orderId, shipperId, status, keyword);
        Map<ShipmentStatus, Long> shipmentStats = shipmentService.getShipmentStatusStats();

        model.addAttribute("shipments", shipments);
        model.addAttribute("shipmentStats", shipmentStats);
        model.addAttribute("statuses", ShipmentStatus.values());
        model.addAttribute("shippers", shipperService.getAllShippersForSelection());
        model.addAttribute("filterOrderId", orderId);
        model.addAttribute("filterShipperId", shipperId);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterKeyword", keyword);
        return "admin-shipments";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        List<Order> eligibleOrders = shipmentService.getEligibleOrdersForShipment();
        model.addAttribute("eligibleOrders", eligibleOrders);
        model.addAttribute("shippers", shipperService.getActiveShippers());
        model.addAttribute("statuses", ShipmentStatus.values());
        return "admin-shipment-form";
    }

    @PostMapping("/create")
    public String createShipment(@RequestParam Long orderId,
                                 @RequestParam(required = false) Long shipperId,
                                 @RequestParam(required = false) String shippingName,
                                 @RequestParam(required = false) String shippingPhone,
                                 @RequestParam(required = false) String shippingAddress,
                                 RedirectAttributes redirectAttributes) {
        try {
            Shipment shipment = shipmentService.createShipmentForAdmin(orderId, shipperId, shippingName, shippingPhone, shippingAddress);
            String message = "Tạo shipment thành công";
            try {
                shipmentOtpEmailService.sendShipmentOtp(shipment);
                message += " và đã gửi OTP qua email";
            } catch (Exception mailEx) {
                message += ". Cảnh báo email: " + mailEx.getMessage();
            }
            redirectAttributes.addFlashAttribute("success", message);
            return "redirect:/admin/shipments";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Tạo shipment thất bại: " + ex.getMessage());
            return "redirect:/admin/shipments/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Shipment shipment = shipmentService.getShipmentByIdForAdmin(id);
            model.addAttribute("editingShipment", shipment);
            model.addAttribute("shippers", shipperService.getAllShippersForSelection());
            model.addAttribute("statuses", ShipmentStatus.values());
            model.addAttribute("canDelete", shipment.getStatus() == ShipmentStatus.PENDING_ASSIGNMENT);
            return "admin-shipment-form";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy shipment: " + ex.getMessage());
            return "redirect:/admin/shipments";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateShipment(@PathVariable Long id,
                                 @RequestParam(required = false) Long shipperId,
                                 @RequestParam String shippingName,
                                 @RequestParam String shippingPhone,
                                 @RequestParam String shippingAddress,
                                 @RequestParam ShipmentStatus status,
                                 @RequestParam(required = false) String failureNote,
                                 RedirectAttributes redirectAttributes) {
        try {
            shipmentService.updateShipmentForAdmin(id, shipperId, shippingName, shippingPhone, shippingAddress, status, failureNote);
            redirectAttributes.addFlashAttribute("success", "Cập nhật shipment thành công");
            return "redirect:/admin/shipments";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật shipment thất bại: " + ex.getMessage());
            return "redirect:/admin/shipments/edit/" + id;
        }
    }

    @PostMapping("/{id}/assign")
    public String assignShipper(@PathVariable Long id,
                                @RequestParam Long shipperId,
                                RedirectAttributes redirectAttributes) {
        try {
            shipmentService.assignShipperByAdmin(id, shipperId);
            redirectAttributes.addFlashAttribute("success", "Đã gán shipper cho shipment");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Gán shipper thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam ShipmentStatus targetStatus,
                               @RequestParam(required = false) String failureNote,
                               RedirectAttributes redirectAttributes) {
        try {
            shipmentService.transitionShipmentStatusByAdmin(id, targetStatus, failureNote);
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái shipment");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật trạng thái thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }

    @PostMapping("/{id}/resend-otp")
    public String resendOtp(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Shipment shipment = shipmentService.regenerateOtpForShipment(id);
            shipmentOtpEmailService.sendShipmentOtp(shipment);
            redirectAttributes.addFlashAttribute("success", "Đã tạo lại OTP và gửi email thành công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Gửi lại OTP thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }

    @PostMapping("/{id}/delete")
    public String deleteShipment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            shipmentService.deletePendingShipment(id);
            redirectAttributes.addFlashAttribute("success", "Đã xóa shipment trạng thái chờ phân công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Xóa shipment thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }
}
