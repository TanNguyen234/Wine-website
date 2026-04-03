package com.strongwine.strongwine.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    getRedirectStrategy().sendRedirect(request, response, "/admin");
                    return;
                }
                if ("ROLE_SHIPPER".equals(authority.getAuthority())) {
                    getRedirectStrategy().sendRedirect(request, response, "/shipper/dashboard");
                    return;
                }
            }
        }
        getRedirectStrategy().sendRedirect(request, response, "/home");
    }
}
