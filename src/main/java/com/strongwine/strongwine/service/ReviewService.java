package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.entity.Shipment;
import com.strongwine.strongwine.entity.ShipmentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ReviewRepository;
import com.strongwine.strongwine.repository.ShipmentRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service class for Review business logic with XSS sanitization, ownership enforcement,
 * and verified-purchase resolution.
 */
@Service
@Transactional
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final Pattern SCRIPT_STYLE_BLOCK_PATTERN = Pattern.compile(
            "(?is)<(script|style)\\b[^>]*>.*?</\\1>"
    );
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern DANGEROUS_URI_PATTERN = Pattern.compile(
            "(?i)\\b(?:javascript|vbscript|data:text/html)\\s*:"
    );
    private static final Pattern INLINE_EVENT_PATTERN = Pattern.compile(
            "(?i)\\bon[a-z]{2,20}\\s*="
    );

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WineRepository wineRepository;

    @Autowired(required = false)
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private ShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Review> getReviewById(Long id) {
        return reviewRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Review> getReviewsByWineId(Long wineId) {
        return reviewRepository.findByWineId(wineId);
    }

    public Review createReview(Long wineId, Long userId, Long actorUserId, Review review) {
        if (userId == null || !userId.equals(actorUserId)) {
            throw new AccessDeniedException("A user can only create reviews for their own account");
        }
        String sanitizedComment = validateAndSanitize(review);

        Wine wine = wineRepository.findById(wineId)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Wine not found with id: " + wineId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        review.setWine(wine);
        review.setUser(user);
        review.setComment(sanitizedComment);

        return reviewRepository.save(review);
    }

    public Review updateReview(Long id, Review reviewDetails, Long actorUserId, boolean admin) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        requireOwnerOnly(review, actorUserId);

        String sanitizedComment = validateAndSanitize(reviewDetails);
        review.setRating(reviewDetails.getRating());
        review.setComment(sanitizedComment);

        return reviewRepository.save(review);
    }

    public void deleteReview(Long id, Long actorUserId, boolean admin) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
        requireOwnerOrAdmin(review, actorUserId, admin);
        reviewRepository.delete(review);
    }

    public String sanitizeReviewText(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = SCRIPT_STYLE_BLOCK_PATTERN.matcher(raw).replaceAll(" ");
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = DANGEROUS_URI_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = INLINE_EVENT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("<", "").replace(">", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_COMMENT_LENGTH) {
            return cleaned.substring(0, MAX_COMMENT_LENGTH);
        }
        return cleaned;
    }

    @Transactional(readOnly = true)
    public boolean isVerifiedPurchase(Long userId, Long wineId) {
        if (userId == null || wineId == null || orderRepository == null || shipmentRepository == null) {
            return false;
        }
        List<Order> userOrders = orderRepository.findByUserId(userId);
        if (userOrders == null || userOrders.isEmpty()) {
            return false;
        }
        List<Long> paidWineOrderIds = userOrders.stream()
                .filter(order -> order != null && order.getId() != null && order.getStatus() == OrderStatus.PAID)
                .filter(order -> orderContainsWine(order, wineId))
                .map(Order::getId)
                .toList();
        if (paidWineOrderIds.isEmpty()) {
            return false;
        }
        List<Shipment> shipments = shipmentRepository.findByOrderIdIn(paidWineOrderIds);
        return shipments != null && shipments.stream()
                .anyMatch(shipment -> shipment != null && shipment.getStatus() == ShipmentStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> resolveVerifiedPurchaseFlags(Collection<Review> reviews) {
        Map<String, Boolean> result = new HashMap<>();
        if (reviews == null || reviews.isEmpty() || orderRepository == null || shipmentRepository == null) {
            return result;
        }
        for (Review review : reviews) {
            if (review == null || review.getUser() == null || review.getWine() == null) {
                continue;
            }
            Long userId = review.getUser().getId();
            Long wineId = review.getWine().getId();
            if (userId != null && wineId != null) {
                String key = verificationKey(userId, wineId);
                result.computeIfAbsent(key, k -> isVerifiedPurchase(userId, wineId));
            }
        }
        return result;
    }

    public String verificationKey(Long userId, Long wineId) {
        return userId + ":" + wineId;
    }

    private String validateAndSanitize(Review details) {
        if (details == null) {
            throw new IllegalArgumentException("Review details must not be null");
        }
        Integer rating = details.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        String sanitized = sanitizeReviewText(details.getComment());
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Review comment must not be blank");
        }
        return sanitized;
    }

    private boolean orderContainsWine(Order order, Long wineId) {
        if (order.getOrderItems() == null) {
            return false;
        }
        return order.getOrderItems().stream()
                .anyMatch(item -> item != null && item.getWine() != null
                        && Objects.equals(wineId, item.getWine().getId()));
    }

    private void requireOwnerOnly(Review review, Long actorUserId) {
        Long ownerId = review.getUser() == null ? null : review.getUser().getId();
        if (actorUserId == null || !actorUserId.equals(ownerId)) {
            throw new AccessDeniedException("Only the review author can edit this review");
        }
    }

    private void requireOwnerOrAdmin(Review review, Long actorUserId, boolean admin) {
        Long ownerId = review.getUser() == null ? null : review.getUser().getId();
        if (!admin && (actorUserId == null || !actorUserId.equals(ownerId))) {
            throw new AccessDeniedException("A user can only manage their own reviews");
        }
    }
}





