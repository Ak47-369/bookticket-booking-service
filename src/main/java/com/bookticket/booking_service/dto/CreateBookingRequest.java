package com.bookticket.booking_service.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateBookingRequest(
        @NotNull Long showId,
        @NotNull List<Long> seatIds

) {
}
