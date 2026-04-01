package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.service.ShipmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shipper")
@PreAuthorize("hasAnyRole('SHIPPER','ADMIN')")
public class ShipperController {

    private final ShipmentService shipmentService;

    public ShipperController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping({"", "/dashboard", "/shipments"})
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAttribute("shipments", shipmentService.getMyShipments(username));
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
