package com.bookticket.booking_service.dto;

public record BookingFailedEvent(
        Long bookingId,
        Long userId,
        Long showId,
        Double totalAmount,
        String reason
) {
}
