package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import com.strongwine.strongwine.service.FileStorageService;
import com.strongwine.strongwine.service.ShipmentService;
import com.strongwine.strongwine.service.ShipperCodBatchService;
import com.strongwine.strongwine.service.ShipperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/shipper")
@PreAuthorize("hasAnyRole('SHIPPER', 'ADMIN')")
public class ShipperController {

    private final ShipmentService shipmentService;
    private final ShipperService shipperService;
    private final FileStorageService fileStorageService;
    private final ShipperCodBatchService shipperCodBatchService;

    public ShipperController(ShipmentService shipmentService) {
        this(shipmentService, null, null, null);
    }

    @Autowired
    public ShipperController(ShipmentService shipmentService,
                             @Autowired(required = false) ShipperService shipperService,
                             @Autowired(required = false) FileStorageService fileStorageService,
                             @Autowired(required = false) ShipperCodBatchService shipperCodBatchService) {
        this.shipmentService = shipmentService;
        this.shipperService = shipperService;
        this.fileStorageService = fileStorageService;
        this.shipperCodBatchService = shipperCodBatchService;
    }

    @GetMapping({"", "/dashboard", "/shipments"})
    public String dashboard(Authentication authentication, Model model) {
        populateDashboardModel(authentication, model);
        return "shipper-dashboard";
    }

    @GetMapping("/cod-batches")
    public String viewCodBatches(Authentication authentication, Model model) {
        populateDashboardModel(authentication, model);
        return "shipper-dashboard";
    }

    private void populateDashboardModel(Authentication authentication, Model model) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("isAdminView", isAdmin);
        List<Shipment> shipments;
        if (isAdmin) {
            shipments = shipmentService.getActiveShipmentsForAdminView();
            model.addAttribute("currentShipperName", "Quản trị viên (Giám sát)");
            model.addAttribute("currentShipperId", "");
            if (shipperCodBatchService != null) {
                model.addAttribute("unbatchedCodShipments", List.of());
                model.addAttribute("codBatches", shipperCodBatchService.getAllBatches());
            }
        } else {
            String username = authentication.getName();
            try {
                shipments = shipmentService.getMyShipments(username);
            } catch (IllegalArgumentException ex) {
                shipments = List.of();
                if (!model.containsAttribute("error")) {
                    model.addAttribute("error", "Tài khoản chưa được liên kết hồ sơ Shipper. Vui lòng liên hệ quản trị viên để kích hoạt hồ sơ giao nhận.");
                }
            }
            String shipperName = username;
            Long shipperId = null;
            if (shipperService != null) {
                Optional<Shipper> shipperOpt = shipperService.findShipperByUsername(username);
                if (shipperOpt.isPresent()) {
                    shipperName = shipperOpt.get().getName();
                    shipperId = shipperOpt.get().getId();
                }
            }
            if (shipperId == null && shipments != null && !shipments.isEmpty() && shipments.get(0).getShipper() != null) {
                shipperName = shipments.get(0).getShipper().getName();
                shipperId = shipments.get(0).getShipper().getId();
            }
            model.addAttribute("currentShipperName", shipperName);
            model.addAttribute("currentShipperId", shipperId != null ? String.valueOf(shipperId) : "");
            if (shipperCodBatchService != null) {
                List<Shipment> unbatchedCod = shipperCodBatchService.getUnbatchedCompletedCodShipmentsByUsername(username);
                List<ShipperCodBatch> batches = shipperCodBatchService.getBatchesForShipperUsername(username);
                model.addAttribute("unbatchedCodShipments", unbatchedCod);
                model.addAttribute("codBatches", batches);
            }
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
                           @RequestParam(value = "podPhoto", required = false) MultipartFile podPhoto,
                           @RequestParam(value = "deliveryNote", required = false) String deliveryNote,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        String uploadedUrl = null;
        try {
            if (podPhoto != null && !podPhoto.isEmpty()) {
                uploadedUrl = storeValidatedPodImage(podPhoto);
            }
            shipmentService.completeDelivery(id, authentication.getName(), otp, uploadedUrl, deliveryNote);
            redirectAttributes.addFlashAttribute("success", "Giao hàng thành công");
        } catch (Exception ex) {
            cleanupUploadedFileOnFailure(uploadedUrl);
            redirectAttributes.addFlashAttribute("error", "Không thể hoàn tất giao hàng: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    @PostMapping("/shipments/{id}/pod")
    public String uploadProofOfDelivery(@PathVariable Long id,
                                        @RequestParam("podPhoto") MultipartFile podPhoto,
                                        @RequestParam(value = "deliveryNote", required = false) String deliveryNote,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        String uploadedUrl = null;
        try {
            uploadedUrl = storeValidatedPodImage(podPhoto);
            shipmentService.recordProofOfDelivery(id, authentication.getName(), uploadedUrl, deliveryNote);
            redirectAttributes.addFlashAttribute("success", "Đã tải lên ảnh bằng chứng giao hàng");
        } catch (Exception ex) {
            cleanupUploadedFileOnFailure(uploadedUrl);
            redirectAttributes.addFlashAttribute("error", "Không thể tải ảnh giao hàng: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    @PostMapping("/cod-batches")
    public String submitCodBatch(@RequestParam(value = "shipmentIds", required = false) List<Long> shipmentIds,
                                 @RequestParam(value = "submittedAmount", required = false) BigDecimal submittedAmount,
                                 @RequestParam(value = "note", required = false) String note,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        if (shipperCodBatchService == null) {
            redirectAttributes.addFlashAttribute("error", "Dịch vụ bảng kê COD chưa sẵn sàng");
            return "redirect:/shipper/dashboard";
        }
        try {
            ShipperCodBatch batch = shipperCodBatchService.createBatchForShipperByUsername(
                    authentication.getName(), shipmentIds, submittedAmount, note);
            redirectAttributes.addFlashAttribute("success",
                    "Đã nộp bảng kê COD " + batch.getBatchCode() + " (" + batch.getOrderCount() + " đơn)");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể tạo bảng kê COD: " + ex.getMessage());
        }
        return "redirect:/shipper/dashboard";
    }

    private String storeValidatedPodImage(MultipartFile podPhoto) throws Exception {
        if (fileStorageService == null) {
            throw new IllegalStateException("Dịch vụ lưu trữ ảnh chưa sẵn sàng");
        }
        if (podPhoto == null || podPhoto.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh bằng chứng giao hàng");
        }
        if (!fileStorageService.isValidImageFile(podPhoto)) {
            throw new IllegalArgumentException("Chỉ chấp nhận tệp ảnh PNG, JPG, JPEG, GIF hoặc WEBP");
        }
        if (!fileStorageService.isValidFileSize(podPhoto)) {
            throw new IllegalArgumentException("Kích thước ảnh không được vượt quá 5MB");
        }
        return fileStorageService.saveFile(podPhoto);
    }

    private void cleanupUploadedFileOnFailure(String uploadedUrl) {
        if (uploadedUrl != null && fileStorageService != null) {
            fileStorageService.deleteFile(uploadedUrl);
        }
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
                       @RequestParam(name = "reason", required = false) String note,
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
