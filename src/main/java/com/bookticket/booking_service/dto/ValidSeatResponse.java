package com.bookticket.booking_service.dto;

public record ValidSeatResponse(
        Long seatId,
        Boolean isAvailable,
        Double seatPrice,
        String seatNumber,
        String seatType
) {
}
