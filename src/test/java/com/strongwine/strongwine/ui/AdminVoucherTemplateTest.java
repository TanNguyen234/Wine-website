package com.strongwine.strongwine.ui;

import com.strongwine.strongwine.entity.Voucher;
import com.strongwine.strongwine.entity.VoucherType;
import com.strongwine.strongwine.service.EnumDisplayService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for F-SW-08: admin-vouchers.html used unwrapped #numbers/#temporals expressions in th:text,
 * so the voucher list failed to render once a voucher had a discount cap or validity window.
 */
class AdminVoucherTemplateTest {

    private static SpringTemplateEngine templateEngine;
    private static StaticApplicationContext applicationContext;

    @BeforeAll
    static void setUpEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("enumDisplayService", EnumDisplayService.class);
        applicationContext.refresh();

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    private String render(Map<String, Object> variables) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaServletWebApplication webApp = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(webApp.buildExchange(request, response), Locale.forLanguageTag("vi-VN"));
        context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(applicationContext, null));
        variables.forEach(context::setVariable);
        return templateEngine.process("admin-vouchers", context);
    }

    @Test
    void voucherListRendersDiscountCapAndValidityWindow() {
        Voucher voucher = new Voucher();
        voucher.setCode("QA10");
        voucher.setType(VoucherType.PERCENT);
        voucher.setValue(new BigDecimal("10"));
        voucher.setMaxDiscount(new BigDecimal("2000000"));
        voucher.setMinOrderValue(new BigDecimal("1000000"));
        voucher.setUsageLimit(100);
        voucher.setUsedCount(0);
        voucher.setActive(true);
        voucher.setStartsAt(LocalDateTime.of(2026, 11, 1, 0, 0));
        voucher.setExpiresAt(LocalDateTime.of(2026, 12, 31, 23, 59));

        String html = render(Map.of("vouchers", List.of(voucher)));

        assertTrue(html.contains("QA10"), "voucher code should be listed");
        assertTrue(html.contains("Tối đa: 2,000,000 ₫"), "discount cap should be formatted, not left as raw expression");
        assertTrue(html.contains("Từ: 01/11/2026 00:00"), "validity start should be formatted");
        assertTrue(html.contains("Đến: 31/12/2026 23:59"), "validity end should be formatted");
        assertFalse(html.contains("#numbers") || html.contains("#temporals"), "no raw expression text may leak into the page");
    }
}
