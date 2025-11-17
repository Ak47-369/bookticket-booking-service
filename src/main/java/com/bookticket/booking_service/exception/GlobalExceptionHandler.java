package com.bookticket.booking_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle SeatLockException - returns 409 Conflict
     * Thrown when seats cannot be locked due to concurrent booking attempts
     */
    @ExceptionHandler(SeatLockException.class)
    public ProblemDetail handleSeatLockException(SeatLockException ex) {
        log.error("Seat lock conflict: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );

        problemDetail.setTitle("Seats No Longer Available");
        problemDetail.setType(URI.create("https://bookticket.com/errors/seat-lock-conflict"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errorCode", "SEAT_LOCK_CONFLICT");

        return problemDetail;
    }

    /**
     * Handle PaymentFailedException - returns 402 Payment Required
     * Thrown when payment processing fails
     */
    @ExceptionHandler(PaymentFailedException.class)
    public ProblemDetail handlePaymentFailedException(PaymentFailedException ex) {
        log.error("Payment failed: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYMENT_REQUIRED,
                ex.getMessage()
        );

        problemDetail.setTitle("Payment Failed");
        problemDetail.setType(URI.create("https://bookticket.com/errors/payment-failed"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errorCode", "PAYMENT_FAILED");
        problemDetail.setProperty("paymentStatus", ex.getPaymentStatus());

        if (ex.getTransactionId() != null) {
            problemDetail.setProperty("transactionId", ex.getTransactionId());
        }

        return problemDetail;
    }
    
    /**
     * Handle generic RuntimeException - returns 500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred while processing your request"
        );
        
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("https://bookticket.com/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errorCode", "INTERNAL_SERVER_ERROR");
        
        return problemDetail;
    }
    
    /**
     * Handle generic Exception - returns 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred while processing your request"
        );
        
        problemDetail.setTitle(ex.getMessage());
        problemDetail.setType(URI.create("https://bookticket.com/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errorCode", "INTERNAL_SERVER_ERROR");
        
        return problemDetail;
    }
}

