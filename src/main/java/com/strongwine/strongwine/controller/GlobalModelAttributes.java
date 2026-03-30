package com.strongwine.strongwine.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("cartCount")
    public int cartCount(HttpSession session, Authentication authentication) {
        return 0;
    }
}
