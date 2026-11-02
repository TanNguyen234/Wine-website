package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.ReviewDto;
import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.ReviewService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * REST API Controller for Review operations
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {
    
    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;
    
    /**
     * Get all reviews
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewDto>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews().stream().map(ReviewDto::from).toList());
    }
    
    /**
     * Get review by ID
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ReviewDto> getReviewById(@PathVariable Long id) {
        Optional<Review> review = reviewService.getReviewById(id);
        return review.map(ReviewDto::from).map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get reviews by wine ID
     */
    @GetMapping("/wine/{wineId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewDto>> getReviewsByWineId(@PathVariable Long wineId) {
        return ResponseEntity.ok(reviewService.getReviewsByWineId(wineId).stream().map(ReviewDto::from).toList());
    }
    
    /**
     * Create a new review
     */
    @PostMapping("/wine/{wineId}/user/{userId}")
    public ResponseEntity<ReviewDto> createReview(
            @PathVariable Long wineId,
            @PathVariable Long userId,
            @RequestBody Review review,
            Authentication authentication) {
        Long actorUserId = authenticatedUserId(authentication);
        if (actorUserId == null) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        try {
            Review createdReview = reviewService.createReview(wineId, userId, actorUserId, review);
            return ResponseEntity.status(HttpStatus.CREATED).body(ReviewDto.from(createdReview));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Update a review
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReviewDto> updateReview(@PathVariable Long id,
                                               @RequestBody Review review,
                                               Authentication authentication) {
        Long actorUserId = authenticatedUserId(authentication);
        if (actorUserId == null) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        try {
            Review updatedReview = reviewService.updateReview(id, review, actorUserId, isAdmin(authentication));
            return ResponseEntity.ok(ReviewDto.from(updatedReview));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Delete a review
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id, Authentication authentication) {
        Long actorUserId = authenticatedUserId(authentication);
        if (actorUserId == null) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        try {
            reviewService.deleteReview(id, actorUserId, isAdmin(authentication));
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).map(User::getId).orElse(null);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}





