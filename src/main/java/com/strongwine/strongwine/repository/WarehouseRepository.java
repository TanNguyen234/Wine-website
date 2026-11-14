package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByName(String name);

    List<Warehouse> findByActiveTrue();

    Optional<Warehouse> findFirstByIsDefaultTrueAndActiveTrue();
}
