package com.bookticket.booking_service.dto;

/**
 * Response DTO for Checkout Session creation
 * Contains the URL where user should be redirected to complete payment
 */
public record CheckoutSessionResponse(
        String sessionId,           // Stripe Checkout Session ID
        String paymentUrl,          // URL to redirect user for payment
        Long bookingId,
        Double amount,
        String status,              // "pending", "created"
        String message,
        Long expiresAt              // Unix timestamp when session expires (24 hours)
) {
}

