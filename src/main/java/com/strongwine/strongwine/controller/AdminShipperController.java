package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.CodBatchStatus;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.ShipperCodBatch;
import com.strongwine.strongwine.entity.ShipperStatus;
import com.strongwine.strongwine.service.ShipperCodBatchService;
import com.strongwine.strongwine.service.ShipperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/shippers")
public class AdminShipperController {

    private final ShipperService shipperService;
    private final ShipperCodBatchService shipperCodBatchService;

    public AdminShipperController(ShipperService shipperService) {
        this(shipperService, null);
    }

    @Autowired
    public AdminShipperController(ShipperService shipperService,
                                  @Autowired(required = false) ShipperCodBatchService shipperCodBatchService) {
        this.shipperService = shipperService;
        this.shipperCodBatchService = shipperCodBatchService;
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
        model.addAttribute("activeLoadByShipperId", shipperService.getActiveLoadByShipperId(shipperPage.getContent()));
        Map<String, Long> stats = shipperService.getShipperOverviewStats();
        model.addAttribute("shipperStats", stats);
        if (shipperCodBatchService != null) {
            model.addAttribute("codBatches", shipperCodBatchService.getAllBatches());
            model.addAttribute("codBatchStatuses", CodBatchStatus.values());
        } else {
            model.addAttribute("codBatches", List.of());
            model.addAttribute("codBatchStatuses", CodBatchStatus.values());
        }
        
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

    public String createShipper(Long userId,
                                String name,
                                String phone,
                                String vehicleType,
                                ShipperStatus status,
                                boolean isAvailable,
                                RedirectAttributes redirectAttributes) {
        return createShipper(userId, name, phone, vehicleType, status, isAvailable, null, redirectAttributes);
    }

    @PostMapping("/create")
    public String createShipper(@RequestParam Long userId,
                                @RequestParam String name,
                                @RequestParam String phone,
                                @RequestParam(required = false) String vehicleType,
                                @RequestParam(defaultValue = "ACTIVE") ShipperStatus status,
                                @RequestParam(defaultValue = "false") boolean isAvailable,
                                @RequestParam(required = false) Integer maxConcurrentShipments,
                                RedirectAttributes redirectAttributes) {
        try {
            shipperService.createShipper(userId, name, phone, vehicleType, status, isAvailable, maxConcurrentShipments);
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

    public String updateShipper(Long id,
                                Long userId,
                                String name,
                                String phone,
                                String vehicleType,
                                ShipperStatus status,
                                boolean isAvailable,
                                RedirectAttributes redirectAttributes) {
        return updateShipper(id, userId, name, phone, vehicleType, status, isAvailable, null, redirectAttributes);
    }

    @PostMapping("/edit/{id}")
    public String updateShipper(@PathVariable Long id,
                                @RequestParam Long userId,
                                @RequestParam String name,
                                @RequestParam String phone,
                                @RequestParam(required = false) String vehicleType,
                                @RequestParam(defaultValue = "ACTIVE") ShipperStatus status,
                                @RequestParam(defaultValue = "false") boolean isAvailable,
                                @RequestParam(required = false) Integer maxConcurrentShipments,
                                RedirectAttributes redirectAttributes) {
        try {
            shipperService.updateShipper(id, userId, name, phone, vehicleType, status, isAvailable, maxConcurrentShipments);
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

    @PostMapping("/{id}/availability")
    public String updateAvailability(@PathVariable Long id,
                                     @RequestParam(defaultValue = "false") boolean available,
                                     RedirectAttributes redirectAttributes) {
        try {
            shipperService.updateAvailability(id, available);
            redirectAttributes.addFlashAttribute("success", available
                    ? "Đã bật nhận phân công cho shipper"
                    : "Đã tắt nhận phân công cho shipper");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật trạng thái sẵn sàng: " + ex.getMessage());
        }
        return "redirect:/admin/shippers";
    }

    @GetMapping("/cod-batches")
    public String listCodBatches(@RequestParam(required = false) CodBatchStatus status, Model model) {
        List<ShipperCodBatch> allBatches = shipperCodBatchService == null
                ? List.of()
                : safeBatchList(shipperCodBatchService.getAllBatches());
        List<ShipperCodBatch> filteredBatches = status == null
                ? allBatches
                : (shipperCodBatchService == null
                        ? List.of()
                        : safeBatchList(shipperCodBatchService.getBatchesByStatus(status)));
        if (status != null && allBatches.isEmpty() && !filteredBatches.isEmpty()) {
            allBatches = filteredBatches;
        }

        long pendingCount = allBatches.stream().filter(b -> b.getStatus() == CodBatchStatus.PENDING_SETTLEMENT).count();
        long verifiedCount = allBatches.stream().filter(b -> b.getStatus() == CodBatchStatus.VERIFIED).count();
        long disputedCount = allBatches.stream().filter(b -> b.getStatus() == CodBatchStatus.DISPUTED).count();

        model.addAttribute("codBatches", filteredBatches);
        model.addAttribute("codBatchStatuses", CodBatchStatus.values());
        model.addAttribute("filterBatchStatus", status);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("verifiedCount", verifiedCount);
        model.addAttribute("disputedCount", disputedCount);
        model.addAttribute("totalBatches", allBatches.size());
        return "admin-cod-batches";
    }

    @PostMapping("/cod-batches/{id}/verify")
    public String verifyCodBatch(@PathVariable Long id,
                                 @RequestParam(required = false) String note,
                                 @RequestParam(required = false) String returnTo,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        if (shipperCodBatchService == null) {
            redirectAttributes.addFlashAttribute("error", "Dịch vụ bảng kê COD chưa sẵn sàng");
            return resolveCodBatchRedirect(returnTo);
        }
        try {
            String actor = authentication == null ? "admin" : authentication.getName();
            shipperCodBatchService.verifyBatch(id, actor, note);
            redirectAttributes.addFlashAttribute("success", "Đã xác nhận quyết toán bảng kê COD #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Xác nhận bảng kê COD thất bại: " + ex.getMessage());
        }
        return resolveCodBatchRedirect(returnTo);
    }

    @PostMapping("/cod-batches/{id}/dispute")
    public String disputeCodBatch(@PathVariable Long id,
                                  @RequestParam String reason,
                                  @RequestParam(required = false) String returnTo,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        if (shipperCodBatchService == null) {
            redirectAttributes.addFlashAttribute("error", "Dịch vụ bảng kê COD chưa sẵn sàng");
            return resolveCodBatchRedirect(returnTo);
        }
        try {
            String actor = authentication == null ? "admin" : authentication.getName();
            shipperCodBatchService.disputeBatch(id, actor, reason);
            redirectAttributes.addFlashAttribute("success", "Đã ghi nhận tranh chấp bảng kê COD #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật tranh chấp thất bại: " + ex.getMessage());
        }
        return resolveCodBatchRedirect(returnTo);
    }

    private String resolveCodBatchRedirect(String returnTo) {
        return "cod-batches".equalsIgnoreCase(returnTo) ? "redirect:/admin/shippers/cod-batches" : "redirect:/admin/shippers";
    }

    private List<ShipperCodBatch> safeBatchList(List<ShipperCodBatch> batches) {
        return batches != null ? batches : List.of();
    }
}
