package com.strongwine.strongwine.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for F-SW-02: a denied request showed Spring's raw "Whitelabel Error Page".
 * The branded error.html must render a readable message for 403 and 404 and never leak the stack/framework text.
 */
class ErrorPageTemplateTest {

    private static SpringTemplateEngine templateEngine;

    @BeforeAll
    static void setUpEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    private String render(Map<String, Object> variables) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        WebContext context = new WebContext(
                JakartaServletWebApplication.buildApplication(servletContext).buildExchange(request, response),
                Locale.forLanguageTag("vi-VN"));
        variables.forEach(context::setVariable);
        return templateEngine.process("error", context);
    }

    @Test
    void forbiddenErrorShowsBrandedMessageAndHomeLink() {
        String html = render(Map.of("status", 403));

        assertTrue(html.contains("Bạn không có quyền truy cập trang này"));
        assertTrue(html.contains("HTTP 403"));
        assertTrue(html.contains("href=\"/home\""));
        assertFalse(html.contains("Whitelabel"), "framework fallback text must not be shown");
    }

    @Test
    void notFoundErrorShowsNotFoundMessage() {
        String html = render(Map.of("status", 404));

        assertTrue(html.contains("Không tìm thấy trang"));
    }

    @Test
    void unknownStatusShowsGenericMessage() {
        String html = render(Map.of("status", 500));

        assertTrue(html.contains("Hệ thống gặp lỗi"));
    }
}
