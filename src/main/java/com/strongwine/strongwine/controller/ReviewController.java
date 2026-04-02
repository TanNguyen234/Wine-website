package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Controller for review operations
 */
@Controller
@RequestMapping("/reviews")
public class ReviewController {
    
    @Autowired
    private ReviewService reviewService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Create a new review
     */
    @PostMapping("/create")
    public String createReview(
            @RequestParam Long wineId,
            @RequestParam Integer rating,
            @RequestParam String comment,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để gửi đánh giá");
            return "redirect:/login";
        }
        
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng");
            return "redirect:/wines/" + wineId;
        }
        
        com.strongwine.strongwine.entity.Review review = new com.strongwine.strongwine.entity.Review();
        review.setRating(rating);
        review.setComment(comment);
        
        try {
            reviewService.createReview(wineId, userOpt.get().getId(), review);
            redirectAttributes.addFlashAttribute("success", "Gửi đánh giá thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Không thể gửi đánh giá: " + e.getMessage());
        }
        
        return "redirect:/wines/" + wineId;
    }
}

