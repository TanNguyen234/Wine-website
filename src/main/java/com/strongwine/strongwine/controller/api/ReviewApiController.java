package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * REST API Controller for Review operations with sanitization, ownership checks,
 * and verified-purchase metadata.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewResponse>> getAllReviews() {
        List<Review> reviews = reviewService.getAllReviews();
        Map<String, Boolean> verifiedFlags = reviewService.resolveVerifiedPurchaseFlags(reviews);
        return ResponseEntity.ok(toResponses(reviews, verifiedFlags));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ReviewResponse> getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id)
                .map(review -> {
                    boolean verified = review.getUser() != null && review.getWine() != null
                            && reviewService.isVerifiedPurchase(review.getUser().getId(), review.getWine().getId());
                    return ResponseEntity.ok(ReviewResponse.from(review, verified));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/wine/{wineId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReviewResponse>> getReviewsByWineId(@PathVariable Long wineId) {
        List<Review> reviews = reviewService.getReviewsByWineId(wineId);
        Map<String, Boolean> verifiedFlags = reviewService.resolveVerifiedPurchaseFlags(reviews);
        return ResponseEntity.ok(toResponses(reviews, verifiedFlags));
    }

    @PostMapping("/wine/{wineId}")
    public ResponseEntity<ReviewResponse> createAuthenticatedReview(
            @PathVariable Long wineId,
            @RequestBody Review review,
            Authentication authentication) {
        Long actorUserId = authenticatedUserId(authentication);
        if (actorUserId == null) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        return handleCreateReview(wineId, actorUserId, actorUserId, review);
    }

    @PostMapping("/wine/{wineId}/user/{userId}")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long wineId,
            @PathVariable Long userId,
            @RequestBody Review review,
            Authentication authentication) {
        Long actorUserId = authenticatedUserId(authentication);
        if (actorUserId == null) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        return handleCreateReview(wineId, userId, actorUserId, review);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable Long id,
            @RequestBody Review review,
            Authentication authentication) {
        Long actorUserId = authenticatedUserId(authentication);
        if (actorUserId == null) {
            return ResponseEntity.status(UNAUTHORIZED).build();
        }
        try {
            Review updatedReview = reviewService.updateReview(id, review, actorUserId, isAdmin(authentication));
            boolean verified = updatedReview.getUser() != null && updatedReview.getWine() != null
                    && reviewService.isVerifiedPurchase(updatedReview.getUser().getId(), updatedReview.getWine().getId());
            return ResponseEntity.ok(ReviewResponse.from(updatedReview, verified));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(FORBIDDEN).build();
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(NOT_FOUND).build();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

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
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(NOT_FOUND).build();
        }
    }

    private ResponseEntity<ReviewResponse> handleCreateReview(Long wineId,
                                                              Long userId,
                                                              Long actorUserId,
                                                              Review review) {
        try {
            Review createdReview = reviewService.createReview(wineId, userId, actorUserId, review);
            boolean verified = createdReview.getUser() != null && createdReview.getWine() != null
                    && reviewService.isVerifiedPurchase(createdReview.getUser().getId(), createdReview.getWine().getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(createdReview, verified));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(FORBIDDEN).build();
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(NOT_FOUND).build();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private List<ReviewResponse> toResponses(List<Review> reviews, Map<String, Boolean> verifiedFlags) {
        return reviews.stream()
                .map(review -> {
                    boolean verified = false;
                    if (review.getUser() != null && review.getWine() != null
                            && review.getUser().getId() != null && review.getWine().getId() != null) {
                        String key = reviewService.verificationKey(review.getUser().getId(), review.getWine().getId());
                        verified = Boolean.TRUE.equals(verifiedFlags.get(key));
                    }
                    return ReviewResponse.from(review, verified);
                })
                .toList();
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

    public record ReviewResponse(
            Long id,
            Long wineId,
            String wineName,
            Long userId,
            String username,
            Integer rating,
            String comment,
            LocalDateTime createdAt,
            boolean verifiedPurchase
    ) {
        public static ReviewResponse from(Review review, boolean verifiedPurchase) {
            return new ReviewResponse(
                    review.getId(),
                    review.getWine() != null ? review.getWine().getId() : null,
                    review.getWine() != null ? review.getWine().getName() : null,
                    review.getUser() != null ? review.getUser().getId() : null,
                    review.getUser() != null ? review.getUser().getUsername() : null,
                    review.getRating(),
                    review.getComment(),
                    review.getCreatedAt(),
                    verifiedPurchase
            );
        }
    }
}





