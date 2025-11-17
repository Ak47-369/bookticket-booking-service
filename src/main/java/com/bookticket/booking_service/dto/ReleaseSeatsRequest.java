package com.bookticket.theater_service.dto;

import java.util.List;

public record ReleaseSeatsRequest(
        Long showId,
        List<Long> showSeatIds
) {
}
