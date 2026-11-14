package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.StockOperationRequest;
import com.strongwine.strongwine.dto.AdminInventoryDto;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.AdminAuditLogService;
import com.strongwine.strongwine.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryApiController {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private AdminAuditLogService adminAuditLogService;

    @GetMapping
    public ResponseEntity<List<AdminInventoryDto>> getInventoryOverview() {
        return ResponseEntity.ok(inventoryService.getInventoryOverview().stream()
                .map(AdminInventoryDto::from)
                .toList());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<AdminInventoryDto>> getLowStock() {
        return ResponseEntity.ok(inventoryService.getLowStockInventories().stream()
                .map(AdminInventoryDto::from)
                .toList());
    }

    @PostMapping("/import/{wineId}")
    public ResponseEntity<AdminInventoryDto> importStock(@PathVariable Long wineId,
                                                          @Valid @RequestBody StockOperationRequest request,
                                                          Authentication authentication) {
        if (request.getWineId() != null && !wineId.equals(request.getWineId())) {
            throw new IllegalArgumentException("wineId in path and body do not match");
        }
        String actor = extractUsername(authentication);
        Inventory updated = inventoryService.importStock(wineId, request.getWarehouseId(), request.getQuantity(), actor, request.getNote());
        auditStockAction(actor, "INVENTORY_IMPORT", wineId, request, updated);
        return ResponseEntity.ok(AdminInventoryDto.from(updated));
    }

    @PostMapping("/export/{wineId}")
    public ResponseEntity<AdminInventoryDto> exportStock(@PathVariable Long wineId,
                                                          @Valid @RequestBody StockOperationRequest request,
                                                          Authentication authentication) {
        if (request.getWineId() != null && !wineId.equals(request.getWineId())) {
            throw new IllegalArgumentException("wineId in path and body do not match");
        }
        String actor = extractUsername(authentication);
        Inventory updated = inventoryService.exportStock(wineId, request.getWarehouseId(), request.getQuantity(), actor, request.getNote());
        auditStockAction(actor, "INVENTORY_EXPORT", wineId, request, updated);
        return ResponseEntity.ok(AdminInventoryDto.from(updated));
    }

    private void auditStockAction(String actor, String action, Long wineId, StockOperationRequest request, Inventory updated) {
        if (adminAuditLogService == null) {
            return;
        }
        Long targetId = updated != null && updated.getId() != null ? updated.getId() : wineId;
        adminAuditLogService.log(actor, action, "INVENTORY", targetId, null,
                Map.of(
                        "wineId", wineId,
                        "warehouseId", request.getWarehouseId() != null ? request.getWarehouseId() : 1L,
                        "quantity", request.getQuantity() != null ? request.getQuantity() : 0,
                        "currentQuantity", updated != null && updated.getCurrentQuantity() != null ? updated.getCurrentQuantity() : 0
                ), null);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null) {
            return "system";
        }
        String candidate = authentication.getPrincipal() instanceof UserDetails ud
                ? ud.getUsername()
                : authentication.getName();
        if (candidate == null || candidate.isBlank()) {
            return "system";
        }
        User user = userRepository != null ? userRepository.findByUsername(candidate).orElse(null) : null;
        return user != null ? user.getUsername() : candidate;
    }
}
