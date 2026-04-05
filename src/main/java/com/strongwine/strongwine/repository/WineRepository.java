package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Wine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository interface for Wine entity
 */
@Repository
public interface WineRepository extends JpaRepository<Wine, Long> {

    @Query("SELECT w.name, SUM(oi.quantity) " +
           "FROM OrderItem oi " +
           "JOIN oi.wine w " +
           "GROUP BY w.id, w.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> getTopSellingWines(Pageable pageable);
    
    Page<Wine> findByDeletedFalse(Pageable pageable);
    List<Wine> findByDeletedFalse();

    /**
     * Search wines by name (case-insensitive)
     */
    List<Wine> findByNameContainingIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long id);
    
    /**
     * Find wines by type
     */
    List<Wine> findByTypeAndDeletedFalse(String type);
    
    /**
     * Find wines by year
     */
    List<Wine> findByYearAndDeletedFalse(Integer year);
    
    /**
     * Find wines by price range
     */
    List<Wine> findByPriceBetweenAndDeletedFalse(BigDecimal minPrice, BigDecimal maxPrice);
    
    /**
     * Search wines by name, type, and year
     */
    @Query("SELECT w FROM Wine w WHERE w.deleted = false AND " +
           "(:keyword IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(COALESCE(w.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:type IS NULL OR w.type = :type) AND " +
           "(:country IS NULL OR LOWER(COALESCE(w.country, '')) = LOWER(:country)) AND " +
           "(:year IS NULL OR w.year = :year) AND " +
           "(:minPrice IS NULL OR w.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR w.price <= :maxPrice)")
    List<Wine> searchWines(@Param("keyword") String keyword,
                          @Param("type") String type,
                          @Param("country") String country,
                          @Param("year") Integer year,
                          @Param("minPrice") BigDecimal minPrice,
                          @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT w FROM Wine w WHERE w.deleted = false AND " +
            "(:keyword IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(COALESCE(w.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:type IS NULL OR w.type = :type) AND " +
            "(:country IS NULL OR LOWER(COALESCE(w.country, '')) = LOWER(:country)) AND " +
            "(:year IS NULL OR w.year = :year) AND " +
            "(:minPrice IS NULL OR w.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR w.price <= :maxPrice)")
    Page<Wine> searchWines(@Param("keyword") String keyword,
                           @Param("type") String type,
                           @Param("country") String country,
                           @Param("year") Integer year,
                           @Param("minPrice") BigDecimal minPrice,
                           @Param("maxPrice") BigDecimal maxPrice,
                           Pageable pageable);
}





