package com.bookticket.booking_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating Stripe Checkout Session
 */
public record CheckoutSessionRequest(
        @NotNull(message = "Booking ID is required")
        Long bookingId,

        @NotNull(message = "User ID is required")
        Long userId,

        @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be greater than 0")
        Double amount,

        String successUrl,  // Optional: Override default success URL
        String cancelUrl    // Optional: Override default cancel URL
) {
}

