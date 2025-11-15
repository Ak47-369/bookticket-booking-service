package com.bookticket.booking_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "booking.payment.polling")
@Data
public class PaymentPollingProperties {
    private int maxAttempts;
    private long intervalMs;
    private long timeoutMs;
}

