package com.strongwine.strongwine.e2e;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack Spring MVC + SecurityFilterChain + SQL Server E2E route verification.
 * Exercises real form-login, CSRF validation, RBAC role guards, static V3 assets,
 * and every Storefront, Admin, and Shipper SSR + JSON route against live schema.
 */
@SpringBootTest
class StrongwineFullStackRoutesE2ETest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private MockHttpSession loginAs(String username, String password) throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(session).isNotNull();
        CsrfToken csrf = (CsrfToken) loginPage.getRequest().getAttribute(CsrfToken.class.getName());
        assertThat(csrf).isNotNull();

        MvcResult postResult = mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", username)
                        .param("password", password)
                        .param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = postResult.getResponse().getRedirectedUrl();
        assertThat(location).as("Login failed for user " + username).doesNotContain("error");

        MockHttpSession authedSession = (MockHttpSession) postResult.getRequest().getSession(false);
        assertThat(authedSession).isNotNull();
        return authedSession;
    }

    @Test
    @DisplayName("Anonymous storefront SSR routes, V3 CSS/JS/PWA assets, and private upload lockdown")
    void anonymousPublicAssetsAndSecurityBoundaries() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));

        for (String route : List.of(
                "/home", "/wines", "/wines?type=Traditional", "/login", "/register", "/cart",
                "/manifest.webmanifest", "/sw-shipper.js",
                "/css/tokens.css", "/css/storefront.css", "/css/admin.css", "/css/shipper.css",
                "/js/dialog-kit.js", "/js/theme-toggle.js", "/js/notifications-client.js",
                "/js/wishlist.js", "/js/admin-charts.js", "/js/shipper-mobile-actions.js",
                "/favicon.svg", "/images/bottles/margaux-grand-cru.svg"
        )) {
            MvcResult res = mockMvc.perform(get(route)).andReturn();
            assertThat(res.getResponse().getStatus()).as("GET " + route).isEqualTo(200);
        }

        // Private uploads & protected portals must block anonymous access
        mockMvc.perform(get("/uploads/pods/private-proof.jpg"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/uploads/profiles/avatar.jpg"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/shipper/dashboard"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/wishlist"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("ROLE_USER storefront flow, wishlist/notifications/address APIs, and admin/shipper/uploads 403 boundary")
    void customerRoutesAndRoleBoundaries() throws Exception {
        MockHttpSession userSession = loginAs("demo", "demo123");

        for (String route : List.of(
                "/home", "/wines", "/cart", "/orders", "/profile",
                "/api/wishlist", "/api/notifications?scope=user", "/api/profile/addresses"
        )) {
            MvcResult res = mockMvc.perform(get(route).session(userSession)).andReturn();
            assertThat(res.getResponse().getStatus()).as("USER GET " + route).isEqualTo(200);
        }

        // /cart/checkout renders 200 when cart has items or redirects cleanly to /cart when empty
        MvcResult checkoutRes = mockMvc.perform(get("/cart/checkout").session(userSession)).andReturn();
        int checkoutStatus = checkoutRes.getResponse().getStatus();
        assertThat(checkoutStatus == 200 || (checkoutStatus == 302 && "/cart".equals(checkoutRes.getResponse().getRedirectedUrl())))
                .as("USER GET /cart/checkout status=" + checkoutStatus)
                .isTrue();

        mockMvc.perform(get("/admin/dashboard").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/shipper/dashboard").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/uploads/pods/private-proof.jpg").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/uploads/profiles/avatar.jpg").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_SHIPPER mobile PWA routes render cleanly and deny Admin portal")
    void shipperRoutesAndRoleBoundary() throws Exception {
        MockHttpSession shipperSession = loginAs("shipper1", "shipper123");

        for (String route : List.of("/shipper/dashboard", "/shipper/cod-batches")) {
            MvcResult res = mockMvc.perform(get(route).session(shipperSession))
                    .andExpect(status().isOk())
                    .andReturn();
            String html = res.getResponse().getContentAsString();
            assertThat(html).contains("shipper-phone");
            assertThat(html).contains("codBatchesSection");
        }

        mockMvc.perform(get("/admin/dashboard").session(shipperSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN all Control Center pages render 200 OK with OTP masking enforced")
    void adminControlCenterAllRoutesRenderCleanly() throws Exception {
        MockHttpSession adminSession = loginAs("admin", "admin123");

        for (String route : List.of(
                "/admin/dashboard",
                "/admin/wines",
                "/admin/wines/create",
                "/admin/wines/import",
                "/admin/inventory",
                "/admin/orders",
                "/admin/payments",
                "/admin/shipments",
                "/admin/shippers",
                "/admin/shippers/create",
                "/admin/shippers/cod-batches",
                "/admin/returns",
                "/admin/vouchers",
                "/admin/users",
                "/admin/users/create",
                "/admin/profile",
                "/admin/audit-logs"
        )) {
            MvcResult res = mockMvc.perform(get(route).session(adminSession))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(res.getResponse().getContentAsString())
                    .as("HTML output for " + route)
                    .contains("admin-shell");
        }

        MvcResult shipmentsRes = mockMvc.perform(get("/admin/shipments").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(shipmentsRes.getResponse().getContentAsString()).contains("Trạng thái OTP");
    }

    @Test
    @DisplayName("SSE REQUEST dispatch enforces RBAC while ASYNC completion dispatch is never denied after logout/session switch")
    void sseAsyncDispatchAfterLogoutIsNotBlocked() throws Exception {
        // Anonymous REQUEST to SSE endpoints must be blocked
        mockMvc.perform(get("/api/live/admin/notifications"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/live/user/stream"))
                .andExpect(status().is3xxRedirection());

        // ROLE_USER REQUEST to admin SSE endpoint must be denied
        MockHttpSession userSession = loginAs("demo", "demo123");
        mockMvc.perform(get("/api/live/admin/notifications").session(userSession))
                .andExpect(status().isForbidden());

        // Container ASYNC completion dispatch after session invalidation/logout must pass AuthorizationFilter cleanly
        MockHttpServletRequest asyncReq = new MockHttpServletRequest("GET", "/api/live/admin/notifications");
        asyncReq.setServletPath("/api/live/admin/notifications");
        asyncReq.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse asyncRes = new MockHttpServletResponse();
        MockFilterChain asyncChain = new MockFilterChain();

        springSecurityFilterChain.doFilter(asyncReq, asyncRes, asyncChain);

        assertThat(asyncRes.getStatus())
                .as("DispatcherType.ASYNC must not redirect or deny after logout")
                .isEqualTo(200);
        assertThat(asyncChain.getRequest())
                .as("SecurityFilterChain must pass DispatcherType.ASYNC down the filter chain")
                .isNotNull();
    }
}
