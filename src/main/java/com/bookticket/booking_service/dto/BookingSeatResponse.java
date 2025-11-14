package com.bookticket.booking_service.dto;

public record BookingSeatResponse(
        Long bookingSeatId,
        Long seatId,
        String seatNumber,
        String seatType,
        Double price
) {
}
