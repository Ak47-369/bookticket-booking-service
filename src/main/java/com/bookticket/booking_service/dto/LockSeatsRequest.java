package com.bookticket.theater_service.dto;

import java.util.List;

public record LockSeatsRequest(
        Long showId,
        List<Long> seatIds
) {
}
