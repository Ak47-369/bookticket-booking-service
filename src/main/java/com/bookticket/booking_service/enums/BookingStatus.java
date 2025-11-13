package com.bookticket.booking_service.enums;

public enum BookingStatus {
    PENDING, // Seats are Locked, awaiting payment
    CONFIRMED, // Payment Successful, booking complete
    FAILED, // Payment Failed or timeout
    CANCELLED // User Cancelled
}
