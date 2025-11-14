package com.bookticket.booking_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = "booking.redis.lock")
@Data
public class RedisLockProperties {
    
    /**
     * Key prefix for seat locks in Redis
     * Example: "lock:seat:" results in keys like "lock:seat:showId:seatId"
     */
    private String keyPrefix;
    private Duration ttl;
    private String bookingPrefix;
    private int maxRetries;
    private long retryDelayMs;
    
    /**
     * Generate Redis key for seat lock
     * Format: lock:seat:showId:seatId
     */
    public String generateSeatLockKey(Long showId, Long seatId) {
        return String.format("%s:%d:%d", keyPrefix, showId, seatId);
    }
    
    /**
     * Generate Redis value for booking lock
     * Format: booking:bookingId
     */
    public String generateBookingLockValue(Long bookingId) {
        return String.format("%s:%d", bookingPrefix, bookingId);
    }
}

