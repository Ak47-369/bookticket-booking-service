package com.bookticket.theater_service.dto;

import java.util.List;

public record BookSeatsRequest(
        Long showId,
        List<Long> seatIds
) {
}
