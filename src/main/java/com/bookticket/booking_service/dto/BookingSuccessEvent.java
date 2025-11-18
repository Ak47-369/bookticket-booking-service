package com.bookticket.booking_service.dto;

public record BookingSuccessEvent(
        Long bookingId,
        Long userId,
        Long showId,
        Double totalAmount
) {
}
