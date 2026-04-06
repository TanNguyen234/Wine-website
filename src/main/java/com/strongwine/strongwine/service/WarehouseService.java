package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing warehouses
 */
@Service
@Transactional
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    public Page<Warehouse> getWarehousesPage(Pageable pageable) {
        return warehouseRepository.findAll(pageable);
    }

    public Optional<Warehouse> getWarehouseById(Long id) {
        if (id == null || id <= 0) return Optional.empty();
        return warehouseRepository.findById(id);
    }

    public Warehouse createWarehouse(Warehouse warehouse) {
        validateWarehouse(warehouse, null);
        warehouse.setName(warehouse.getName().trim());
        warehouse.setLocation(warehouse.getLocation() != null ? warehouse.getLocation().trim() : null);
        warehouse.setActive(Boolean.TRUE.equals(warehouse.getActive()));
        return warehouseRepository.save(warehouse);
    }

    public Warehouse updateWarehouse(Long id, Warehouse warehouseDetails) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));
        
        validateWarehouse(warehouseDetails, id);
        
        warehouse.setName(warehouseDetails.getName().trim());
        warehouse.setLocation(warehouseDetails.getLocation() != null ? warehouseDetails.getLocation().trim() : null);
        warehouse.setActive(Boolean.TRUE.equals(warehouseDetails.getActive()));
        
        return warehouseRepository.save(warehouse);
    }

    public void toggleActiveStatus(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));
        warehouse.setActive(!Boolean.TRUE.equals(warehouse.getActive()));
        warehouseRepository.save(warehouse);
    }

    public void deleteWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found"));

        if (inventoryRepository.existsByWarehouseId(id)) {
            throw new IllegalArgumentException("Không thể xóa kho vì đã có dữ liệu tồn kho liên quan");
        }

        warehouseRepository.delete(warehouse);
    }

    private void validateWarehouse(Warehouse warehouse, Long currentId) {
        if (warehouse.getName() == null || warehouse.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên nhà kho là bắt buộc");
        }
        
        // Optional uniqueness check logic can go here (findByName)
        warehouseRepository.findByName(warehouse.getName().trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("Tên nhà kho đã tồn tại");
            }
        });
    }
}
