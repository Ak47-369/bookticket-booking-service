package com.bookticket.booking_service.dto;

public record SeatDetailsResponse(
        Long seatId,
        String seatNumber,
        String seatType,
        Double price
) {
}
