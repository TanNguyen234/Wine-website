package com.strongwine.strongwine.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ShipmentReconciliationInterceptor implements HandlerInterceptor {

    private final ShipmentReconciliationCoordinator shipmentReconciliationCoordinator;

    public ShipmentReconciliationInterceptor(ShipmentReconciliationCoordinator shipmentReconciliationCoordinator) {
        this.shipmentReconciliationCoordinator = shipmentReconciliationCoordinator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/uploads/")
                || uri.startsWith("/api/")) {
            return true;
        }

        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null
                && !acceptHeader.contains("text/html")
                && !acceptHeader.contains("*/*")) {
            return true;
        }

        shipmentReconciliationCoordinator.tryReconcileOnWebEntry();
        return true;
    }
}
