package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WishlistItemRepository;
import com.strongwine.strongwine.service.NotificationService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    @Autowired(required = false)
    private WishlistItemRepository wishlistItemRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private UserRepository userRepository;

    @ModelAttribute("cartCount")
    public int cartCount(HttpServletRequest request,
                         HttpServletResponse response,
                         Authentication authentication) {
        if (isAsyncOrCommitted(request, response)) {
            return 0;
        }
        try {
            HttpSession session = request != null ? request.getSession(false) : null;
            if (session == null) {
                return 0;
            }
            Object attr = session.getAttribute("cartCount");
            if (attr instanceof Number num) {
                return Math.max(0, num.intValue());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @ModelAttribute("wishlistCount")
    public long wishlistCount(HttpServletRequest request,
                              HttpServletResponse response,
                              Authentication authentication) {
        if (wishlistItemRepository == null || isAsyncOrCommitted(request, response)) {
            return 0L;
        }
        User user = resolveUser(authentication);
        if (user == null || user.getId() == null) {
            return 0L;
        }
        try {
            return wishlistItemRepository.countByUserId(user.getId());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) {
        if (notificationService == null || isAsyncOrCommitted(request, response)
                || authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return 0L;
        }
        try {
            boolean isAdmin = authentication.getAuthorities() != null
                    && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (isAdmin) {
                return notificationService.countUnreadForAdmin();
            }
            User user = resolveUser(authentication);
            if (user != null && user.getId() != null) {
                return notificationService.countUnreadForUser(user.getId());
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private boolean isAsyncOrCommitted(HttpServletRequest request, HttpServletResponse response) {
        if (response != null) {
            if (response.isCommitted()) {
                return true;
            }
            String contentType = response.getContentType();
            if (contentType != null && contentType.contains("text/event-stream")) {
                return true;
            }
        }
        return request != null && request.getDispatcherType() == DispatcherType.ASYNC;
    }

    private User resolveUser(Authentication authentication) {
        if (userRepository == null || authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }
}
