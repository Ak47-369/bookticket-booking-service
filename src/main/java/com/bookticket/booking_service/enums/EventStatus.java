package com.bookticket.booking_service.enums;

public enum EventStatus {
    PENDING,      // Initial state when event is created in DLQ
    RETRYING,     // Currently being retried
    FAILED,       // All retries exhausted
    PROCESSED     // Successfully processed after retry
}

