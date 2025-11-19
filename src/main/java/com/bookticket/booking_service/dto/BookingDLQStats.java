package com.bookticket.booking_service.dto;

import com.bookticket.booking_service.entity.FailedEvent;

import java.util.List;

public record BookingDLQStats(
        Long bookingId,
        int pendingCount,
        int retryingCount,
        int failedCount,
        int processedCount,
        List<FailedEvent> events
) {
}
