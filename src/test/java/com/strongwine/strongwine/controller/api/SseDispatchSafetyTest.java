package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.config.WebConfig;
import com.strongwine.strongwine.controller.GlobalModelAttributes;
import com.strongwine.strongwine.exception.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class SseDispatchSafetyTest {

    @Controller
    static class DummyViewController {
        @GetMapping("/dummy/view")
        @ResponseBody
        public String view() {
            return "ok";
        }
    }

    @RestController
    static class DummySseApiController {
        @GetMapping("/api/live/test-sse")
        public void sseFailure(HttpServletResponse response) throws Exception {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.getWriter().write("event:ping\ndata:{}\n\n");
            response.flushBuffer();
            throw new IllegalStateException("Cannot create a session after the response has been committed");
        }
    }

    @Test
    void globalModelAttributesDoesNotForceSessionCreation() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyViewController())
                .setControllerAdvice(new GlobalModelAttributes())
                .build();

        MvcResult result = mockMvc.perform(get("/dummy/view")).andReturn();

        assertNull(result.getRequest().getSession(false),
                "GlobalModelAttributes must inspect session via getSession(false) and never force session creation");
    }

    @Test
    void apiExceptionHandlerSkipsJsonBodyForCommittedEventStreamResponses() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummySseApiController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        MvcResult result = mockMvc.perform(get("/api/live/test-sse")).andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .contains("event:ping")
                .doesNotContain("Cannot create a session");

        MockHttpServletResponse directSseResponse = new MockHttpServletResponse();
        directSseResponse.setContentType("text/event-stream");
        var directEntity = new ApiExceptionHandler().handleIllegalArgument(
                new IllegalStateException("Cannot create a session after the response has been committed"),
                directSseResponse
        );
        assertThat(directEntity.getBody()).isNull();
    }

    @Test
    void webConfigUsesNoCacheForCssAndJsAssets() {
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration defaultReg = mock(ResourceHandlerRegistration.class);
        ResourceHandlerRegistration cssReg = mock(ResourceHandlerRegistration.class);
        ResourceHandlerRegistration jsReg = mock(ResourceHandlerRegistration.class);

        when(registry.addResourceHandler(any(String[].class))).thenReturn(defaultReg);
        when(registry.addResourceHandler("/css/**")).thenReturn(cssReg);
        when(registry.addResourceHandler("/js/**")).thenReturn(jsReg);

        for (ResourceHandlerRegistration reg : List.of(defaultReg, cssReg, jsReg)) {
            when(reg.addResourceLocations(any(String[].class))).thenReturn(reg);
            when(reg.setCacheControl(any(CacheControl.class))).thenReturn(reg);
        }

        new WebConfig().addResourceHandlers(registry);

        ArgumentCaptor<CacheControl> cssCaptor = ArgumentCaptor.forClass(CacheControl.class);
        ArgumentCaptor<CacheControl> jsCaptor = ArgumentCaptor.forClass(CacheControl.class);
        verify(cssReg).setCacheControl(cssCaptor.capture());
        verify(jsReg).setCacheControl(jsCaptor.capture());

        assertThat(cssCaptor.getValue().getHeaderValue()).contains("no-cache").doesNotContain("604800");
        assertThat(jsCaptor.getValue().getHeaderValue()).contains("no-cache").doesNotContain("604800");
    }
}
