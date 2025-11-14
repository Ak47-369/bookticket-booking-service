package com.bookticket.booking_service.exception;

/**
 * Exception thrown when seats cannot be locked due to concurrent booking attempts
 * Results in HTTP 409 Conflict response
 */
public class SeatLockException extends RuntimeException {
    
    public SeatLockException(String message) {
        super(message);
    }
    
    public SeatLockException(String message, Throwable cause) {
        super(message, cause);
    }
}

