package com.bookticket.booking_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotNull Long bookingId,
        @NotNull Long userId,
        @NotNull @Positive Double amount,
        String paymentMethod // e.g., "CREDIT_CARD", "UPI", "WALLET"
) {
}

