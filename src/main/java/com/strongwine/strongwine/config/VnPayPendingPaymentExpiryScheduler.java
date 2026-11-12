package com.strongwine.strongwine.config;

import com.strongwine.strongwine.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Releases the stock held by VNPay checkouts that were abandoned before VNPay sent any result.
 * VNPay refuses payment after vnp_ExpireDate (15 minutes by default), so a payment still pending after the
 * longer timeout below is treated as abandoned.
 */
@Component
public class VnPayPendingPaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(VnPayPendingPaymentExpiryScheduler.class);

    private final PaymentService paymentService;

    @Value("${app.payment.vnpay.pending-timeout-minutes:60}")
    private long pendingTimeoutMinutes;

    public VnPayPendingPaymentExpiryScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 300_000)
    public void expireAbandonedVnPayPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, pendingTimeoutMinutes));
        int expired = paymentService.expireStaleVnPayPayments(cutoff);
        if (expired > 0) {
            log.info("Expired {} abandoned VNPay payments and released their reserved stock", expired);
        }
    }
}
