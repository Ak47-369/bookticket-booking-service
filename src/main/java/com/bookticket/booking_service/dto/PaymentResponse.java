package com.bookticket.booking_service.dto;

public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        String paymentStatus, // "SUCCESS", "FAILED", "PENDING"
        String transactionId,
        Double amount,
        String message
) {
}

