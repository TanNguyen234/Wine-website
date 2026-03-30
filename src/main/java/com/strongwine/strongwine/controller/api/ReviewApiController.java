package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST API Controller for Review operations
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {
    
    @Autowired
    private ReviewService reviewService;
    
    /**
     * Get all reviews
     */
    @GetMapping
    public ResponseEntity<List<Review>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }
    
    /**
     * Get review by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Review> getReviewById(@PathVariable Long id) {
        Optional<Review> review = reviewService.getReviewById(id);
        return review.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get reviews by wine ID
     */
    @GetMapping("/wine/{wineId}")
    public ResponseEntity<List<Review>> getReviewsByWineId(@PathVariable Long wineId) {
        return ResponseEntity.ok(reviewService.getReviewsByWineId(wineId));
    }
    
    /**
     * Create a new review
     */
    @PostMapping("/wine/{wineId}/user/{userId}")
    public ResponseEntity<Review> createReview(
            @PathVariable Long wineId,
            @PathVariable Long userId,
            @RequestBody Review review) {
        try {
            Review createdReview = reviewService.createReview(wineId, userId, review);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdReview);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update a review
     */
    @PutMapping("/{id}")
    public ResponseEntity<Review> updateReview(@PathVariable Long id, @RequestBody Review review) {
        try {
            Review updatedReview = reviewService.updateReview(id, review);
            return ResponseEntity.ok(updatedReview);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Delete a review
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        try {
            reviewService.deleteReview(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}





