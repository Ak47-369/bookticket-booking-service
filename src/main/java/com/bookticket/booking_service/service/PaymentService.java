package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.PaymentRequest;
import com.bookticket.booking_service.dto.PaymentResponse;
import com.bookticket.booking_service.exception.PaymentFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service for handling payment processing with payment service
 */
@Service
@Slf4j
public class PaymentService {
    
    private final RestClient paymentRestClient;
    
    public PaymentService(RestClient paymentRestClient) {
        this.paymentRestClient = paymentRestClient;
    }
    
    /**
     * Process payment for a booking
     * 
     * @param paymentRequest Payment request details
     * @return PaymentResponse from payment service
     * @throws PaymentFailedException if payment fails
     */
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment for booking {} with amount {}", 
                paymentRequest.bookingId(), paymentRequest.amount());
        
        try {
            PaymentResponse paymentResponse = paymentRestClient.post()
                    .uri("/api/v1/payments/process")
                    .body(paymentRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        log.error("Client error while processing payment for booking {}: {}", 
                                paymentRequest.bookingId(), response.getStatusCode());
                        throw new PaymentFailedException(
                                "Payment failed due to client error: " + response.getStatusCode(),
                                "FAILED",
                                null
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        log.error("Server error while processing payment for booking {}: {}", 
                                paymentRequest.bookingId(), response.getStatusCode());
                        throw new PaymentFailedException(
                                "Payment service unavailable. Please try again later.",
                                "FAILED",
                                null
                        );
                    })
                    .body(PaymentResponse.class);
            
            if (paymentResponse == null) {
                log.error("Received null response from payment service for booking {}", 
                        paymentRequest.bookingId());
                throw new PaymentFailedException(
                        "Payment service returned invalid response",
                        "FAILED",
                        null
                );
            }
            
            // Check payment status
            if ("SUCCESS".equalsIgnoreCase(paymentResponse.paymentStatus())) {
                log.info("Payment successful for booking {}. Transaction ID: {}", 
                        paymentRequest.bookingId(), paymentResponse.transactionId());
                return paymentResponse;
            } else {
                log.warn("Payment failed for booking {}. Status: {}, Message: {}", 
                        paymentRequest.bookingId(), 
                        paymentResponse.paymentStatus(), 
                        paymentResponse.message());
                throw new PaymentFailedException(
                        paymentResponse.message() != null ? 
                                paymentResponse.message() : "Payment failed",
                        paymentResponse.paymentStatus(),
                        paymentResponse.transactionId()
                );
            }
            
        } catch (PaymentFailedException e) {
            // Re-throw PaymentFailedException as-is
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while processing payment for booking {}: {}", 
                    paymentRequest.bookingId(), e.getMessage(), e);
            throw new PaymentFailedException(
                    "Failed to process payment due to system error: " + e.getMessage(),
                    e
            );
        }
    }
}

