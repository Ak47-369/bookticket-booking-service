package com.bookticket.booking_service.dto;

/**
 * Response DTO from payment service
 */
public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        String paymentStatus, // "SUCCESS", "FAILED", "PENDING"
        String transactionId,
        Double amount,
        String message
) {
}

