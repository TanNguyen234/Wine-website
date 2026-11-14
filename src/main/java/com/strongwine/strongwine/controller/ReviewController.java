package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Review;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Controller for SSR review operations
 */
@Controller
@RequestMapping("/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/create")
    public String createReview(
            @RequestParam Long wineId,
            @RequestParam Integer rating,
            @RequestParam String comment,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = resolveAuthenticatedUser(authentication);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để gửi đánh giá");
            return "redirect:/login";
        }

        Review review = new Review();
        review.setRating(rating);
        review.setComment(comment);

        try {
            reviewService.createReview(wineId, userOpt.get().getId(), userOpt.get().getId(), review);
            redirectAttributes.addFlashAttribute("success", "Gửi đánh giá thành công!");
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi đánh giá: " + ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi đánh giá lúc này");
        }

        return "redirect:/wines/" + wineId;
    }

    @PostMapping("/{id}/edit")
    public String editReview(
            @PathVariable Long id,
            @RequestParam Long wineId,
            @RequestParam Integer rating,
            @RequestParam String comment,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = resolveAuthenticatedUser(authentication);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để chỉnh sửa đánh giá");
            return "redirect:/login";
        }

        Review details = new Review();
        details.setRating(rating);
        details.setComment(comment);

        try {
            reviewService.updateReview(id, details, userOpt.get().getId(), isAdmin(authentication));
            redirectAttributes.addFlashAttribute("success", "Cập nhật đánh giá thành công!");
        } catch (IllegalArgumentException | AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể cập nhật đánh giá: " + ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đánh giá cần cập nhật");
        }

        return "redirect:/wines/" + wineId;
    }

    @PostMapping("/{id}/delete")
    public String deleteReview(
            @PathVariable Long id,
            @RequestParam Long wineId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOpt = resolveAuthenticatedUser(authentication);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để xóa đánh giá");
            return "redirect:/login";
        }

        try {
            reviewService.deleteReview(id, userOpt.get().getId(), isAdmin(authentication));
            redirectAttributes.addFlashAttribute("success", "Đã xóa đánh giá.");
        } catch (AccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("error", "Bạn không có quyền xóa đánh giá này");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Không thể xóa đánh giá lúc này");
        }

        return "redirect:/wines/" + wineId;
    }

    private Optional<User> resolveAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}

