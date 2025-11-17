package com.bookticket.booking_service.dto;

import java.util.List;

public record BookSeatsRequest(
        Long showId,
        List<Long> seatIds
) {
}
