package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.service.ShipmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/shipper")
@PreAuthorize("hasAnyRole('SHIPPER', 'ADMIN')")
public class ShipperController {

    private final ShipmentService shipmentService;

    public ShipperController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping({"", "/dashboard", "/shipments"})
    public String dashboard(Authentication authentication, Model model) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("isAdminView", isAdmin);
        List<Shipment> shipments;
        if (isAdmin) {
            shipments = shipmentService.getActiveShipmentsForAdminView();
            model.addAttribute("currentShipperName", "Quản trị viên (Giám sát)");
            model.addAttribute("currentShipperId", "");
        } else {
            String username = authentication.getName();
            shipments = shipmentService.getMyShipments(username);
            String shipperName = username;
            Long shipperId = null;
            if (shipments != null && !shipments.isEmpty() && shipments.get(0).getShipper() != null) {
                shipperName = shipments.get(0).getShipper().getName();
                shipperId = shipments.get(0).getShipper().getId();
            }
            model.addAttribute("currentShipperName", shipperName);
            model.addAttribute("currentShipperId", shipperId != null ? String.valueOf(shipperId) : "");
        }
        model.addAttribute("shipments", shipments);
        long deliveringCount = shipments == null ? 0 : shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.PICKED_UP || s.getStatus() == ShipmentStatus.DELIVERING)
                .count();
        long assignedCount = shipments == null ? 0 : shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.ASSIGNED || s.getStatus() == ShipmentStatus.PENDING_ASSIGNMENT)
                .count();
        long doneCount = shipments == null ? 0 : shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.COMPLETED || s.getStatus() == ShipmentStatus.FAILED)
                .count();
        model.addAttribute("deliveringCount", deliveringCount);
        model.addAttribute("assignedCount", assignedCount);
        model.addAttribute("doneCount", doneCount);
        return "shipper-dashboard";
    }

    @PostMapping("/shipments/{id}/pickup")
    public String pickup(@PathVariable Long id,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            shipmentService.markPickedUp(id, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái: Đã lấy hàng");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể xác nhận lấy hàng: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    @PostMapping("/shipments/{id}/start")
    public String start(@PathVariable Long id,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            shipmentService.startDelivering(id, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái: Đang giao");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể bắt đầu giao: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    @PostMapping("/shipments/{id}/complete")
    public String complete(@PathVariable Long id,
                           @RequestParam String otp,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            shipmentService.completeDelivery(id, authentication.getName(), otp);
            redirectAttributes.addFlashAttribute("success", "Giao hàng thành công");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể hoàn tất giao hàng: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    @PostMapping("/shipments/{id}/resend-otp")
    public String resendOtp(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            shipmentService.resendOtpForShipmentByShipper(id, authentication.getName(), "SHIPPER_RESEND");
            redirectAttributes.addFlashAttribute("success", "Đã tạo lại OTP và gửi lại email cho khách hàng");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi lại OTP: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    @PostMapping("/shipments/{id}/fail")
    public String fail(@PathVariable Long id,
                       @RequestParam(required = false) String note,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            shipmentService.markFailed(id, authentication.getName(), note);
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật trạng thái: Thất bại");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể đánh dấu thất bại: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }
}
