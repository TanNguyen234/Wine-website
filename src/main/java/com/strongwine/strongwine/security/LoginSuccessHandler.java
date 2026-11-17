package com.strongwine.strongwine.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginSuccessHandler() {
        setDefaultTargetUrl("/home");
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    requestCache.removeRequest(request, response);
                    clearAuthenticationAttributes(request);
                    getRedirectStrategy().sendRedirect(request, response, "/admin");
                    return;
                }
                if ("ROLE_SHIPPER".equals(authority.getAuthority())) {
                    requestCache.removeRequest(request, response);
                    clearAuthenticationAttributes(request);
                    getRedirectStrategy().sendRedirect(request, response, "/shipper/dashboard");
                    return;
                }
            }
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && isSafeCustomerPageTarget(savedRequest.getRedirectUrl())) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        requestCache.removeRequest(request, response);
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, "/home");
    }

    private boolean isSafeCustomerPageTarget(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return false;
        }
        try {
            String path = URI.create(redirectUrl).getPath();
            if (path == null || path.isBlank()) {
                return false;
            }
            return !path.startsWith("/api/")
                    && !path.startsWith("/admin")
                    && !path.startsWith("/shipper")
                    && !path.startsWith("/css/")
                    && !path.startsWith("/js/")
                    && !path.startsWith("/images/")
                    && !path.startsWith("/uploads/")
                    && !path.startsWith("/favicon")
                    && !path.startsWith("/error")
                    && !path.startsWith("/login")
                    && !path.startsWith("/logout")
                    && !path.startsWith("/register")
                    && !path.startsWith("/forgot-password")
                    && !path.startsWith("/reset-password");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
