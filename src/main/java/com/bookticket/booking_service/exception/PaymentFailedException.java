package com.bookticket.booking_service.exception;

/**
 * Exception thrown when payment processing fails
 */
public class PaymentFailedException extends RuntimeException {
    
    private final String paymentStatus;
    private final String transactionId;
    
    public PaymentFailedException(String message, String paymentStatus, String transactionId) {
        super(message);
        this.paymentStatus = paymentStatus;
        this.transactionId = transactionId;
    }
    
    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
        this.paymentStatus = "FAILED";
        this.transactionId = null;
    }
    
    public String getPaymentStatus() {
        return paymentStatus;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
}

