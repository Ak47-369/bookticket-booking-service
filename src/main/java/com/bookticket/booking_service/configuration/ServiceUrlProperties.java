package com.bookticket.booking_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for external service URLs
 * Maps to 'services' section in application.yml
 */
@Component
@ConfigurationProperties(prefix = "services")
@Data
public class ServiceUrlProperties {
    
    private ServiceConfig theater;
    private ServiceConfig payment;
    
    @Data
    public static class ServiceConfig {
        private String url;
    }
    
    // Helper methods for easy access
    public String getTheaterUrl() {
        return theater != null ? theater.getUrl() : null;
    }
    
    public String getPaymentUrl() {
        return payment != null ? payment.getUrl() : null;
    }
}

