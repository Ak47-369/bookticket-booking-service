package com.bookticket.booking_service.dto;

public record DLQStats(
        int pendingCount,
        int failedCount,
        int totalCount
) {
}