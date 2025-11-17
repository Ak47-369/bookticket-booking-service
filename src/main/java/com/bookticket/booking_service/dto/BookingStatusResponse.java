package com.bookticket.booking_service.dto;

import com.bookticket.booking_service.enums.BookingStatus;

import java.util.List;

public record BookingStatusResponse(
        Long bookingId,
        Long userId,
        Long showId,
        double totalAmount,
        BookingStatus status,
        List<BookingSeatResponse> seats
) {
}
