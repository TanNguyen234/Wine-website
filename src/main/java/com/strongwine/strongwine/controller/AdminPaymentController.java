package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentTransaction;
import com.strongwine.strongwine.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public String listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<Payment> paymentPage = paymentService.getPaymentsPage(pageable);

        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("paymentTransactions", paymentService.getRecentTransactions());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
        model.addAttribute("totalPages", paymentPage.getTotalPages());
        model.addAttribute("hasNext", paymentPage.hasNext());
        model.addAttribute("hasPrevious", paymentPage.hasPrevious());
        model.addAttribute("totalEntries", paymentPage.getTotalElements());

        return "admin-payments";
    }
}
