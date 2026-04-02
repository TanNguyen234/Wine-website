package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperService;
import org.springframework.security.core.Authentication;
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

    public AdminShipmentController(ShipmentService shipmentService,
                                   ShipperService shipperService) {
        this.shipmentService = shipmentService;
        this.shipperService = shipperService;
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
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn giao hàng: " + ex.getMessage());
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
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            shipmentService.updateShipmentForAdmin(id, shipperId, shippingName, shippingPhone, shippingAddress, status, failureNote,
                    authentication == null ? null : authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Cập nhật đơn giao hàng thành công");
            return "redirect:/admin/shipments";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật đơn giao hàng thất bại: " + ex.getMessage());
            return "redirect:/admin/shipments/edit/" + id;
        }
    }

    @PostMapping("/{id}/assign")
    public String assignShipper(@PathVariable Long id,
                                @RequestParam Long shipperId,
                                RedirectAttributes redirectAttributes) {
        try {
            shipmentService.assignShipperByAdmin(id, shipperId);
            redirectAttributes.addFlashAttribute("success", "Đã gán shipper cho đơn giao hàng");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Gán shipper thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam ShipmentStatus targetStatus,
                               @RequestParam(required = false) String failureNote,
                               @RequestParam(required = false) String overrideReason,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            shipmentService.transitionShipmentStatusByAdmin(
                    id,
                    targetStatus,
                    failureNote,
                    authentication == null ? null : authentication.getName(),
                    overrideReason
            );
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái đơn giao hàng");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật trạng thái thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }

    @PostMapping("/{id}/resend-otp")
    public String resendOtp(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            shipmentService.resendOtpForShipmentByAdmin(id,
                    authentication == null ? null : authentication.getName(),
                    "ADMIN_RESEND");
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
            redirectAttributes.addFlashAttribute("success", "Đã xóa đơn giao hàng ở trạng thái chờ phân công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Xóa đơn giao hàng thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }
}
