package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.ReviewRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service class for Review business logic
 */
@Service
@Transactional
public class ReviewService {
    
    @Autowired
    private ReviewRepository reviewRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private WineRepository wineRepository;
    
    /**
     * Get all reviews
     */
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }
    
    /**
     * Get review by ID
     */
    public Optional<Review> getReviewById(Long id) {
        return reviewRepository.findById(id);
    }
    
    /**
     * Get all reviews for a wine
     */
    public List<Review> getReviewsByWineId(Long wineId) {
        return reviewRepository.findByWineId(wineId);
    }
    
    /**
     * Create a new review
     */
    public Review createReview(Long wineId, Long userId, Long actorUserId, Review review) {
        if (userId == null || !userId.equals(actorUserId)) {
            throw new AccessDeniedException("A user can only create reviews for their own account");
        }

        Wine wine = wineRepository.findById(wineId)
            .orElseThrow(() -> new RuntimeException("Wine not found with id: " + wineId));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        review.setWine(wine);
        review.setUser(user);
        
        return reviewRepository.save(review);
    }
    
    /**
     * Update an existing review
     */
    public Review updateReview(Long id, Review reviewDetails, Long actorUserId, boolean admin) {
        Review review = reviewRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));
        requireOwnerOrAdmin(review, actorUserId, admin);
        
        review.setRating(reviewDetails.getRating());
        review.setComment(reviewDetails.getComment());
        
        return reviewRepository.save(review);
    }
    
    /**
     * Delete a review
     */
    public void deleteReview(Long id, Long actorUserId, boolean admin) {
        Review review = reviewRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Review not found with id: " + id));
        requireOwnerOrAdmin(review, actorUserId, admin);
        reviewRepository.delete(review);
    }

    private void requireOwnerOrAdmin(Review review, Long actorUserId, boolean admin) {
        Long ownerId = review.getUser() == null ? null : review.getUser().getId();
        if (!admin && (actorUserId == null || !actorUserId.equals(ownerId))) {
            throw new AccessDeniedException("A user can only manage their own reviews");
        }
    }
}





