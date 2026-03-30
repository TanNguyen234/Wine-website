package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.StockOperationRequest;
import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryApiController {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Inventory>> getInventoryOverview() {
        return ResponseEntity.ok(inventoryService.getInventoryOverview());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Inventory>> getLowStock() {
        return ResponseEntity.ok(inventoryService.getLowStockInventories());
    }

    @PostMapping("/import/{wineId}")
    public ResponseEntity<Inventory> importStock(@PathVariable Long wineId,
                                                 @Valid @RequestBody StockOperationRequest request,
                                                 Authentication authentication) {
        if (request.getWineId() != null && !wineId.equals(request.getWineId())) {
            throw new IllegalArgumentException("wineId in path and body do not match");
        }
        String actor = extractUsername(authentication);
        Inventory updated = inventoryService.importStock(wineId, request.getWarehouseId(), request.getQuantity(), actor, request.getNote());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/export/{wineId}")
    public ResponseEntity<Inventory> exportStock(@PathVariable Long wineId,
                                                 @Valid @RequestBody StockOperationRequest request,
                                                 Authentication authentication) {
        if (request.getWineId() != null && !wineId.equals(request.getWineId())) {
            throw new IllegalArgumentException("wineId in path and body do not match");
        }
        String actor = extractUsername(authentication);
        Inventory updated = inventoryService.exportStock(wineId, request.getWarehouseId(), request.getQuantity(), actor, request.getNote());
        return ResponseEntity.ok(updated);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return "system";
        }
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        User user = userRepository.findByUsername(username).orElse(null);
        return user != null ? user.getUsername() : "system";
    }
}
