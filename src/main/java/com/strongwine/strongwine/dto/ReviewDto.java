package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.Review;
import java.time.LocalDateTime;

public record ReviewDto(
        Long id,
        Long wineId,
        String wineName,
        Long userId,
        String username,
        Integer rating,
        String comment,
        LocalDateTime createdAt) {

    public static ReviewDto from(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getWine() == null ? null : review.getWine().getId(),
                review.getWine() == null ? null : review.getWine().getName(),
                review.getUser() == null ? null : review.getUser().getId(),
                review.getUser() == null ? null : review.getUser().getUsername(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
