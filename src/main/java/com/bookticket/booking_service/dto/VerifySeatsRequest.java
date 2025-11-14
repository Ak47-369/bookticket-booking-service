package com.bookticket.booking_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record VerifySeatsRequest(
       @NotNull Long showId,
        @NotEmpty List<Long> seatIds
) {
}
