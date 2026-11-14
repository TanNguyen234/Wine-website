package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.invoice.InvoiceFormat;
import com.strongwine.strongwine.service.invoice.OrderInvoiceService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

@Controller
public class InvoiceController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderInvoiceService orderInvoiceService;

    public InvoiceController(OrderRepository orderRepository,
                             UserRepository userRepository,
                             OrderInvoiceService orderInvoiceService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderInvoiceService = orderInvoiceService;
    }

    @GetMapping("/orders/{id}/invoice")
    public ResponseEntity<byte[]> downloadUserInvoice(
            @PathVariable Long id,
            @RequestParam(name = "format", defaultValue = "pdf") String format,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!hasAdminRole(authentication) && !isOrderOwner(order, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return buildInvoiceResponse(order, format);
    }

    @GetMapping("/admin/orders/{id}/invoice")
    public ResponseEntity<byte[]> downloadAdminInvoice(
            @PathVariable Long id,
            @RequestParam(name = "format", defaultValue = "pdf") String format,
            Authentication authentication) {
        if (!hasAdminRole(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return buildInvoiceResponse(order, format);
    }

    private ResponseEntity<byte[]> buildInvoiceResponse(Order order, String formatParam) {
        final InvoiceFormat invoiceFormat;
        try {
            invoiceFormat = InvoiceFormat.fromParam(formatParam);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        byte[] content = orderInvoiceService.generateInvoice(order, invoiceFormat);
        String filename = "strongwine-invoice-" + order.getId() + invoiceFormat.getExtension();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(invoiceFormat.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private boolean hasAdminRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()) || "ADMIN".equals(auth.getAuthority()));
    }

    private boolean isOrderOwner(Order order, Authentication authentication) {
        if (order.getUser() == null || authentication == null || authentication.getName() == null) {
            return false;
        }
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null && currentUser.getId() != null && order.getUser().getId() != null) {
            return currentUser.getId().equals(order.getUser().getId());
        }
        return authentication.getName().equals(order.getUser().getUsername());
    }
}
