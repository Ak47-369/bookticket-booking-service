package com.bookticket.booking_service.dto;

import java.util.List;

public record LockSeatsRequest(
        Long showId,
        List<Long> seatIds
) {
}
