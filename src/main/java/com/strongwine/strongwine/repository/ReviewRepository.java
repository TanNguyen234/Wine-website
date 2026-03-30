package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.entity.Wine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Review entity
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    
    /**
     * Find all reviews for a specific wine
     */
    List<Review> findByWine(Wine wine);
    
    /**
     * Find all reviews by wine ID
     */
    List<Review> findByWineId(Long wineId);
}





