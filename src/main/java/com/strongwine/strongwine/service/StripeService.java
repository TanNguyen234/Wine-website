package com.strongwine.strongwine.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.exception.StrongWineException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of("VND");

    @Value("${stripe.secretKey:}")
    private String stripeSecretKey;

    @Value("${stripe.webhookSecret:}")
    private String stripeWebhookSecret;

    private volatile boolean configured;

    public static class StripeNotConfiguredException extends StrongWineException {
        public StripeNotConfiguredException(String message) {
            super(message);
        }
    }

    public static class StripeWebhookVerificationException extends StrongWineException {
        public StripeWebhookVerificationException(String message) {
            super(message);
        }

        public StripeWebhookVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            this.configured = false;
            log.warn("Stripe secret key is missing or blank; Stripe checkout and webhook calls are disabled (configured=false).");
            return;
        }
        Stripe.apiKey = stripeSecretKey.trim();
        this.configured = true;
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            log.warn("Stripe webhook secret is missing or blank; Stripe webhook verification will fail closed.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean isWebhookConfigured() {
        return configured && stripeWebhookSecret != null && !stripeWebhookSecret.isBlank();
    }

    public Session createCheckoutSession(Payment payment, String baseUrl) throws StripeException {
        requireConfigured("Cannot create Stripe checkout session");
        long unitAmount = toStripeAmount(payment.getAmount(), payment.getCurrency());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(baseUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/payment/cancel")
                .setClientReferenceId(payment.getPaymentReference())
                .putMetadata("orderId", String.valueOf(payment.getOrder().getId()))
                .putMetadata("paymentReference", payment.getPaymentReference())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(payment.getCurrency().toLowerCase())
                                .setUnitAmount(unitAmount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("StrongWine Order #" + payment.getOrder().getId())
                                        .build())
                                .build())
                        .build())
                .build();

        return Session.create(params);
    }

    public Session retrieveSession(String sessionId) throws StripeException {
        requireConfigured("Cannot retrieve Stripe checkout session");
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Stripe session id is required");
        }
        return Session.retrieve(sessionId.trim());
    }

    public Event constructWebhookEvent(String payload, String signature) {
        requireConfigured("Cannot verify Stripe webhook event");
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new StripeNotConfiguredException("Stripe webhook secret is not configured");
        }
        if (payload == null || payload.isBlank()) {
            throw new StripeWebhookVerificationException("Stripe webhook payload is empty");
        }
        if (signature == null || signature.isBlank()) {
            throw new StripeWebhookVerificationException("Missing Stripe-Signature header");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature.trim(), stripeWebhookSecret.trim());
        } catch (SignatureVerificationException ex) {
            throw new StripeWebhookVerificationException("Invalid Stripe webhook signature", ex);
        } catch (RuntimeException ex) {
            throw new StripeWebhookVerificationException("Malformed Stripe webhook JSON payload", ex);
        }
        if (event == null || event.getId() == null || event.getId().isBlank()
                || event.getType() == null || event.getType().isBlank()) {
            throw new StripeWebhookVerificationException("Malformed Stripe webhook event payload");
        }
        return event;
    }

    private void requireConfigured(String action) {
        if (!configured) {
            throw new StripeNotConfiguredException(action + ": Stripe secret key is not configured");
        }
    }

    private long toStripeAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase();
        BigDecimal stripeAmount = ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)
                ? amount
                : amount.multiply(BigDecimal.valueOf(100));
        return stripeAmount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
