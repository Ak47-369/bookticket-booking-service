package com.bookticket.booking_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "services")
@Data
public class ServiceUrlProperties {
    
    private String theaterUrl;
    private String paymentUrl;
    private String notificationUrl;
}

