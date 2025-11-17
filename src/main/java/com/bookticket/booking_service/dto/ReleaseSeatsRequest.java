package com.bookticket.booking_service.dto;

import java.util.List;

public record ReleaseSeatsRequest(
        Long showId,
        List<Long> showSeatIds
) {
}
