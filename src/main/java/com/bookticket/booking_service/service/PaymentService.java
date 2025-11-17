package com.bookticket.booking_service.service;

import com.bookticket.booking_service.configuration.PaymentPollingProperties;
import com.bookticket.booking_service.dto.CheckoutSessionRequest;
import com.bookticket.booking_service.dto.CheckoutSessionResponse;
import com.bookticket.booking_service.dto.PaymentRequest;
import com.bookticket.booking_service.dto.PaymentResponse;
import com.bookticket.booking_service.exception.PaymentFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final PaymentPollingProperties pollingProperties;

    public PaymentService(@Qualifier("paymentRestClient") RestClient paymentRestClient, PaymentPollingProperties pollingProperties) {
        this.paymentRestClient = paymentRestClient;
        this.pollingProperties = pollingProperties;
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
                    .uri("/api/v1/internal/payments/process")
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

    /**
     * Create a Stripe Checkout Session
     *
     * @param request Checkout session request with booking details
     * @return CheckoutSessionResponse containing sessionId and paymentUrl
     */
    public CheckoutSessionResponse createCheckoutSession(CheckoutSessionRequest request) {
        log.info("Creating checkout session for booking: {}", request.bookingId());

        try {
            CheckoutSessionResponse response = paymentRestClient.post()
                    .uri("/api/v1/internal/payments/checkout/create")
                    .body(request)
                    .retrieve()
                    .body(CheckoutSessionResponse.class);

            log.info("Checkout session created successfully. SessionId: {}, PaymentUrl: {}",
                    response.sessionId(), response.paymentUrl());

            return response;

        } catch (Exception e) {
            log.error("Failed to create checkout session for booking: {}", request.bookingId(), e);
            throw new RuntimeException("Failed to create checkout session: " + e.getMessage(), e);
        }
    }

    /**
     * Verify Checkout Session status
     *
     * @param sessionId Stripe Checkout Session ID
     * @return PaymentResponse with current payment status
     */
    public PaymentResponse verifyCheckoutSession(String sessionId) {
        log.info("Verifying checkout session: {}", sessionId);

        try {
            PaymentResponse response = paymentRestClient.get()
                    .uri("/api/v1/internal/payments/checkout/verify/{sessionId}", sessionId)
                    .retrieve()
                    .body(PaymentResponse.class);

            log.info("Checkout session verified. SessionId: {}, Status: {}",
                    sessionId, response.paymentStatus());

            return response;

        } catch (Exception e) {
            log.error("Failed to verify checkout session: {}", sessionId, e);
            throw new RuntimeException("Failed to verify checkout session: " + e.getMessage(), e);
        }
    }

    /**
     * Get payment status from database (no Stripe API call)
     *
     * @param transactionId Payment transaction ID
     * @return PaymentResponse with payment status
     */
    public PaymentResponse getPaymentStatus(String transactionId) {
        log.info("Getting payment status for transaction: {}", transactionId);

        try {
            PaymentResponse response = paymentRestClient.get()
                    .uri("/api/v1/internal/payments/status/{transactionId}", transactionId)
                    .retrieve()
                    .body(PaymentResponse.class);

            log.info("Payment status retrieved. TransactionId: {}, Status: {}",
                    transactionId, response.paymentStatus());

            return response;

        } catch (Exception e) {
            log.error("Failed to get payment status for transaction: {}", transactionId, e);
            throw new RuntimeException("Failed to get payment status: " + e.getMessage(), e);
        }
    }

    /**
     * Poll payment status until it's COMPLETED or FAILED
     * Uses exponential backoff with configurable max attempts
     *
     * @param sessionId Stripe Checkout Session ID
     * @return PaymentResponse with final payment status
     * @throws RuntimeException if polling times out or payment verification fails
     */
    public PaymentResponse pollPaymentStatus(String sessionId) {
        log.info("Starting payment status polling for session: {}", sessionId);

        int attempts = 0;
        long startTime = System.currentTimeMillis();

        while (attempts < pollingProperties.getMaxAttempts()) {
            attempts++;

            // Check if total timeout exceeded
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > pollingProperties.getTimeoutMs()) {
                log.error("Payment polling timeout exceeded for session: {}. Elapsed: {}ms",
                        sessionId, elapsedTime);
                throw new RuntimeException("Payment verification timeout exceeded");
            }

            try {
                log.debug("Polling attempt {}/{} for session: {}",
                        attempts, pollingProperties.getMaxAttempts(), sessionId);

                PaymentResponse response = verifyCheckoutSession(sessionId);
                String status = response.paymentStatus();

                // Check if payment is in final state
                if ("COMPLETED".equalsIgnoreCase(status)) {
                    log.info("Payment completed successfully for session: {} after {} attempts",
                            sessionId, attempts);
                    return response;
                }

                if ("FAILED".equalsIgnoreCase(status)) {
                    log.warn("Payment failed for session: {} after {} attempts",
                            sessionId, attempts);
                    return response;
                }

                // Payment still pending, wait before next poll
                log.debug("Payment still pending for session: {}. Status: {}. Waiting {}ms before next poll",
                        sessionId, status, pollingProperties.getIntervalMs());

                Thread.sleep(pollingProperties.getIntervalMs());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Payment polling interrupted for session: {}", sessionId, e);
                throw new RuntimeException("Payment polling interrupted", e);
            } catch (Exception e) {
                log.error("Error during payment polling attempt {} for session: {}",
                        attempts, sessionId, e);

                // If this is the last attempt, throw exception
                if (attempts >= pollingProperties.getMaxAttempts()) {
                    throw new RuntimeException("Payment verification failed after " + attempts + " attempts", e);
                }

                // Otherwise, wait and retry
                try {
                    Thread.sleep(pollingProperties.getIntervalMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Payment polling interrupted", ie);
                }
            }
        }

        // Max attempts reached without final status
        log.error("Payment polling max attempts ({}) reached for session: {}",
                pollingProperties.getMaxAttempts(), sessionId);
        throw new RuntimeException("Payment verification timeout: max polling attempts reached");
    }
}

